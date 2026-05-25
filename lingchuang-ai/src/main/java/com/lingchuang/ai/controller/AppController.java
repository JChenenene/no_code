package com.lingchuang.ai.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.lingchuang.ai.annotation.AuthCheck;
import com.lingchuang.ai.common.BaseResponse;
import com.lingchuang.ai.common.DeleteRequest;
import com.lingchuang.ai.common.ResultUtils;
import com.lingchuang.ai.config.CosClientConfig;
import com.lingchuang.ai.constant.AppConstant;
import com.lingchuang.ai.constant.UserConstant;
import com.lingchuang.ai.exception.BusinessException;
import com.lingchuang.ai.exception.ErrorCode;
import com.lingchuang.ai.exception.ThrowUtils;
import com.lingchuang.ai.model.dto.app.*;
import com.lingchuang.ai.model.entity.User;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.vo.AppVO;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;
import com.lingchuang.ai.ratelimter.annotation.RateLimit;
import com.lingchuang.ai.ratelimter.enums.RateLimitType;
import com.lingchuang.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import com.lingchuang.ai.model.entity.App;
import com.lingchuang.ai.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * 应用控制层。
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    @Resource
    private CosClientConfig cosClientConfig;

    private final HttpClient coverHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（SSE 流式返回）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
        return contentFlux
                .map(chunk -> {
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    @GetMapping(value = "/chat/gen/code/v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCodeV2(@RequestParam Long appId,
                                                         @RequestParam String message,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        User loginUser = userService.getLoginUser(request);
        return appService.chatToGenCodeV2(appId, message, loginUser)
                .map(this::parseRawWorkflowSseEvent)
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    private ServerSentEvent<String> parseRawWorkflowSseEvent(String rawSseEvent) {
        if (StrUtil.isBlank(rawSseEvent)) {
            return ServerSentEvent.<String>builder().data("").build();
        }
        String eventType = null;
        StringBuilder dataBuilder = new StringBuilder();
        String normalizedEvent = rawSseEvent.replace("\r\n", "\n");
        for (String line : normalizedEvent.split("\n")) {
            if (line.startsWith("event:")) {
                eventType = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!dataBuilder.isEmpty()) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(line.substring("data:".length()).trim());
            }
        }
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder();
        if (StrUtil.isNotBlank(eventType)) {
            builder.event(eventType);
        }
        return builder.data(dataBuilder.toString()).build();
    }

    @GetMapping("/workflow/latest")
    public BaseResponse<WorkflowRun> getLatestWorkflowRun(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getLatestWorkflowRun(appId, loginUser));
    }

    @GetMapping("/workflow/latest/detail")
    public BaseResponse<WorkflowRunDetailVO> getLatestWorkflowRunDetail(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getLatestWorkflowRunDetail(appId, loginUser));
    }

    @GetMapping("/workflow/latest/succeeded")
    public BaseResponse<WorkflowRun> getLatestSucceededWorkflowRun(@RequestParam Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getLatestSucceededWorkflowRun(appId, loginUser));
    }

    @GetMapping("/workflow/{runId}")
    public BaseResponse<WorkflowRunDetailVO> getWorkflowRunDetail(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getWorkflowRunDetail(runId, loginUser));
    }

    @GetMapping("/workflow/{runId}/status")
    public BaseResponse<WorkflowRun> getWorkflowRunStatus(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getWorkflowRunStatus(runId, loginUser));
    }

    @PostMapping("/workflow/{runId}/cancel")
    public BaseResponse<Boolean> cancelWorkflowRun(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.cancelWorkflowRun(runId, loginUser));
    }

    @GetMapping(value = "/workflow/{runId}/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> retryWorkflowRun(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return appService.retryWorkflowRun(runId, loginUser)
                .map(this::parseRawWorkflowSseEvent)
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    @GetMapping("/workflow/{runId}/steps")
    public BaseResponse<List<WorkflowStep>> listWorkflowRunSteps(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.listWorkflowRunSteps(runId, loginUser));
    }

    @GetMapping("/workflow/{runId}/artifacts")
    public BaseResponse<List<WorkflowArtifact>> listWorkflowRunArtifacts(@PathVariable Long runId, HttpServletRequest request) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 id 错误");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.listWorkflowRunArtifacts(runId, loginUser));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        // 检查部署请求是否为空
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取应用 ID
        Long appId = appDeployRequest.getAppId();
        // 检查应用 ID 是否为空
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        // 返回部署 URL
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        User loginUser = userService.getLoginUser(request);
        appService.downloadAppCode(appId, loginUser, response);
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 代理应用封面图，避免浏览器直接加载外部 COS 域名被客户端拦截
     *
     * @param appId 应用 id
     * @return 封面图片内容
     */
    @GetMapping("/cover/{appId}")
    public ResponseEntity<byte[]> getAppCover(@PathVariable Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        App app = appService.getById(appId);
        if (app == null || StrUtil.isBlank(app.getCover()) || !isAllowedCosCoverUrl(app.getCover())) {
            return ResponseEntity.notFound().build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(app.getCover()))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = coverHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != HttpStatus.OK.value() || response.body() == null || response.body().length == 0) {
                return ResponseEntity.notFound().build();
            }
            String contentType = response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .orElse(MediaType.IMAGE_JPEG_VALUE);
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(response.body());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean isAllowedCosCoverUrl(String coverUrl) {
        try {
            URI coverUri = URI.create(coverUrl);
            URI cosHostUri = URI.create(cosClientConfig.getHost());
            return coverUri.getHost() != null
                    && coverUri.getHost().equalsIgnoreCase(cosHostUri.getHost())
                    && coverUri.getPath() != null
                    && coverUri.getPath().startsWith("/screenshots/");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(com.lingchuang.ai.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }
}
