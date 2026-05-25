package com.lingchuang.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lingchuang.ai.ai.AiCodeGenTypeRoutingService;
import com.lingchuang.ai.ai.AiCodeGenTypeRoutingServiceFactory;
import com.lingchuang.ai.constant.AppConstant;
import com.lingchuang.ai.core.AiCodeGeneratorFacade;
import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.core.handler.StreamHandlerExecutor;
import com.lingchuang.ai.exception.BusinessException;
import com.lingchuang.ai.exception.ErrorCode;
import com.lingchuang.ai.exception.ThrowUtils;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.model.dto.app.AppAddRequest;
import com.lingchuang.ai.model.dto.app.AppQueryRequest;
import com.lingchuang.ai.model.entity.App;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.mapper.AppMapper;
import com.lingchuang.ai.model.entity.User;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.model.enums.WorkflowRunStatusEnum;
import com.lingchuang.ai.model.vo.AppVO;
import com.lingchuang.ai.model.vo.UserVO;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;
import com.lingchuang.ai.monitor.MonitorContext;
import com.lingchuang.ai.monitor.MonitorContextHolder;
import com.lingchuang.ai.rag.RagInvocationContext;
import com.lingchuang.ai.service.AppChatSummaryService;
import com.lingchuang.ai.service.AppService;
import com.lingchuang.ai.service.ChatHistoryService;
import com.lingchuang.ai.service.ProjectDownloadService;
import com.lingchuang.ai.service.ScreenshotService;
import com.lingchuang.ai.service.UserService;
import com.lingchuang.ai.service.WorkflowPersistenceService;
import com.lingchuang.ai.service.WorkflowRunService;
import com.lingchuang.ai.service.WorkflowRuntimeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 应用服务层实现。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AppChatSummaryService appChatSummaryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private ProjectDownloadService projectDownloadService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private WorkflowRuntimeService workflowRuntimeService;

    @Resource
    private WorkflowRunService workflowRunService;

    @Resource
    private WorkflowPersistenceService workflowPersistenceService;

    @Resource
    private GeneratedArtifactSupport generatedArtifactSupport;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        App app = validateChatRequest(appId, message, loginUser);
        // 4. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        // 5. 获取最近 4 条历史对话，用于 RAG 检索增强
        List<ChatHistory> recentHistories = List.of();
        try {
            Page<ChatHistory> historyPage = chatHistoryService.listAppChatHistoryByPage(appId, 4, null, loginUser);
            recentHistories = historyPage == null || historyPage.getRecords() == null ? List.of() : historyPage.getRecords();
        } catch (Exception e) {
            log.warn("获取最近历史对话失败，继续使用原始提示词，appId: {}", appId, e);
        }
        String memorySummary = "";
        try {
            memorySummary = appChatSummaryService.getLatestSummaryText(appId, loginUser.getId());
        } catch (Exception e) {
            log.warn("获取对话摘要失败，继续使用最近历史，appId: {}", appId, e);
        }
        // 6. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 7. 在创建 Flux 前捕获本次请求的 RAG 与监控上下文
        RagInvocationContext ragInvocationContext = RagInvocationContext.builder()
                .appId(appId)
                .codeGenType(codeGenTypeEnum)
                .recentHistories(recentHistories)
                .memorySummary(memorySummary)
                .build();
        MonitorContext monitorContext = MonitorContext.builder()
                .userId(loginUser.getId().toString())
                .appId(appId.toString())
                .build();
        Flux<String> codeStream;
        RagInvocationContext.setCurrent(ragInvocationContext);
        MonitorContextHolder.setContext(monitorContext);
        try {
            // 8. 调用 AI 生成代码（流式），RAG 在 AiServices 内部触发
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        } finally {
            RagInvocationContext.clear();
            MonitorContextHolder.clearContext();
        }
        // 9. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
    }

    @Override
    public Flux<String> chatToGenCodeV2(Long appId, String message, User loginUser) {
        App app = validateChatRequest(appId, message, loginUser);
        if (CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        WorkflowRun runningRun = workflowRunService.getRunningRun(appId, loginUser.getId());
        if (runningRun != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前应用已有运行中的 V2 工作流，请等待完成或取消后重试");
        }
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        return startWorkflowV2Run(app, appId, message, loginUser, null);
    }

    private Flux<String> startWorkflowV2Run(App app, Long appId, String message, User loginUser, WorkflowRun parentRun) {
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        WorkflowRun workflowRun = workflowRunService.createRunningRun(appId, loginUser.getId(), message, codeGenTypeEnum.getValue());
        workflowRuntimeService.registerWorkflowJob(workflowRun.getId(), workflowRun.getRequestId());
        String workspacePath = generatedArtifactSupport.resolveRunWorkspaceDir(codeGenTypeEnum, appId, workflowRun.getId());
        String previewUrl = generatedArtifactSupport.resolvePreviewUrl(appId, workflowRun.getId(), codeGenTypeEnum);
        workflowRunService.attachWorkspace(workflowRun, workspacePath, previewUrl);
        if (parentRun != null) {
            workflowPersistenceService.saveRetryParentArtifact(workflowRun, parentRun);
        }
        Flux<String> workflowFlux = workflowRuntimeService.executeWorkflowV2WithFlux(
                message,
                appId,
                workflowRun.getRequestId(),
                workflowRun.getId(),
                workspacePath
        );
        StringBuilder responseBuilder = new StringBuilder();
        AtomicInteger persistedStepCounter = new AtomicInteger(0);
        return workflowFlux
                .map(rawEvent -> enrichWorkflowSseEvent(rawEvent, workflowRun))
                .doOnNext(rawEvent -> persistWorkflowStepEvent(rawEvent, workflowRun, persistedStepCounter))
                .doOnNext(responseBuilder::append)
                .doOnComplete(() -> {
                    WorkflowCompletionResult completionResult = buildWorkflowCompletionResult(responseBuilder.toString());
                    if (workflowRunService.isCancelled(workflowRun.getId())) {
                        log.info("workflowRunId={} 已取消，跳过完成态覆盖", workflowRun.getId());
                        return;
                    }
                    if (completionResult.response() != null) {
                        workflowPersistenceService.saveWorkflowResult(workflowRun, completionResult.response());
                    }
                    if (completionResult.success()) {
                        workflowRunService.markSucceeded(workflowRun, completionResult.responseJson());
                    } else {
                        workflowRunService.markFailed(workflowRun, completionResult.summary());
                    }
                    chatHistoryService.addChatMessage(app.getId(), completionResult.summary(), ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    if (!workflowRunService.isCancelled(workflowRun.getId())) {
                        workflowRunService.markFailed(workflowRun, error.getMessage());
                    }
                })
                .doFinally(signalType -> workflowRuntimeService.removeWorkflowJob(workflowRun.getId()));
    }

    @Override
    public WorkflowRun getLatestWorkflowRun(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        return workflowRunService.getLatestRun(appId, loginUser.getId());
    }

    @Override
    public WorkflowRunDetailVO getLatestWorkflowRunDetail(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        WorkflowRun latestRun = workflowRunService.getLatestRun(appId, loginUser.getId());
        return workflowPersistenceService.buildDetail(latestRun);
    }

    @Override
    public WorkflowRun getLatestSucceededWorkflowRun(Long appId, User loginUser) {
        validateAppOwner(appId, loginUser);
        return workflowRunService.getLatestSucceededRun(appId, loginUser.getId());
    }

    @Override
    public WorkflowRunDetailVO getWorkflowRunDetail(Long runId, User loginUser) {
        WorkflowRun workflowRun = validateWorkflowRunOwner(runId, loginUser);
        return workflowPersistenceService.buildDetail(workflowRun);
    }

    @Override
    public WorkflowRun getWorkflowRunStatus(Long runId, User loginUser) {
        return validateWorkflowRunOwner(runId, loginUser);
    }

    @Override
    public boolean cancelWorkflowRun(Long runId, User loginUser) {
        WorkflowRun workflowRun = validateWorkflowRunOwner(runId, loginUser);
        if (!WorkflowRunStatusEnum.RUNNING.getValue().equals(workflowRun.getStatus())) {
            return false;
        }
        workflowRuntimeService.cancelWorkflowJob(workflowRun.getId(), "用户取消 V2 工作流");
        return workflowRunService.cancelRun(workflowRun, "用户取消 V2 工作流");
    }

    @Override
    public Flux<String> retryWorkflowRun(Long runId, User loginUser) {
        WorkflowRun parentRun = validateWorkflowRunOwner(runId, loginUser);
        if (!isRetryableWorkflowRun(parentRun)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有失败或已取消的 V2 工作流可以重试");
        }
        WorkflowRun runningRun = workflowRunService.getRunningRun(parentRun.getAppId(), loginUser.getId());
        if (runningRun != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前应用已有运行中的 V2 工作流，请等待完成或取消后重试");
        }
        App app = validateAppOwner(parentRun.getAppId(), loginUser);
        return startWorkflowV2Run(app, parentRun.getAppId(), parentRun.getPrompt(), loginUser, parentRun);
    }

    @Override
    public List<WorkflowStep> listWorkflowRunSteps(Long runId, User loginUser) {
        WorkflowRun workflowRun = validateWorkflowRunOwner(runId, loginUser);
        return workflowPersistenceService.listSteps(workflowRun.getId());
    }

    @Override
    public List<WorkflowArtifact> listWorkflowRunArtifacts(Long runId, User loginUser) {
        WorkflowRun workflowRun = validateWorkflowRunOwner(runId, loginUser);
        return workflowPersistenceService.listArtifacts(workflowRun.getId());
    }

    private App validateChatRequest(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        return validateAppOwner(appId, loginUser);
    }

    private App validateAppOwner(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        return app;
    }

    private WorkflowRun validateWorkflowRunOwner(Long runId, User loginUser) {
        ThrowUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR, "工作流运行 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        WorkflowRun workflowRun = workflowRunService.getById(runId);
        ThrowUtils.throwIf(workflowRun == null, ErrorCode.NOT_FOUND_ERROR, "工作流运行记录不存在");
        if (!loginUser.getId().equals(workflowRun.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该工作流运行记录");
        }
        return workflowRun;
    }

    private boolean isRetryableWorkflowRun(WorkflowRun workflowRun) {
        if (workflowRun == null || StrUtil.isBlank(workflowRun.getStatus())) {
            return false;
        }
        return WorkflowRunStatusEnum.FAILED.getValue().equals(workflowRun.getStatus())
                || WorkflowRunStatusEnum.CANCELLED.getValue().equals(workflowRun.getStatus());
    }

    private WorkflowCompletionResult buildWorkflowCompletionResult(String rawSseStream) {
        JSONObject payload = extractWorkflowCompletedPayload(rawSseStream);
        if (payload == null) {
            return new WorkflowCompletionResult(true, "V2 工作流执行完成", null, null);
        }
        String finalStatus = payload.getStr("finalStatus");
        boolean success = StrUtil.isBlank(finalStatus) || "SUCCESS".equalsIgnoreCase(finalStatus);
        StringBuilder summary = new StringBuilder("V2 工作流执行")
                .append(success ? "成功" : "未通过");
        String verificationSummary = payload.getStr("verificationSummary");
        if (StrUtil.isNotBlank(verificationSummary)) {
            summary.append("\n\n验证: ").append(verificationSummary);
        }
        String fixSummary = payload.getStr("fixSummary");
        if (StrUtil.isNotBlank(fixSummary)) {
            summary.append("\n\n修复: ").append(fixSummary);
        }
        JSONArray verificationIssues = payload.getJSONArray("verificationIssues");
        if (verificationIssues != null && !verificationIssues.isEmpty()) {
            summary.append("\n\n问题:");
            for (Object issue : verificationIssues) {
                summary.append("\n- ").append(issue);
            }
        }
        return new WorkflowCompletionResult(success, summary.toString(), payload.toString(), payload.toBean(com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response.class));
    }

    private JSONObject extractWorkflowCompletedPayload(String rawSseStream) {
        if (StrUtil.isBlank(rawSseStream)) {
            return null;
        }
        String normalizedStream = rawSseStream.replace("\r\n", "\n");
        String[] eventBlocks = normalizedStream.split("\n\n");
        for (String eventBlock : eventBlocks) {
            WorkflowSsePayload payload = parseWorkflowSsePayload(eventBlock);
            if (payload != null && "workflow_completed".equals(payload.eventType())) {
                return payload.data();
            }
        }
        return null;
    }

    private String enrichWorkflowSseEvent(String rawSseEvent, WorkflowRun workflowRun) {
        if (StrUtil.isBlank(rawSseEvent) || workflowRun == null) {
            return rawSseEvent;
        }
        WorkflowSsePayload payload = parseWorkflowSsePayload(rawSseEvent);
        if (payload == null) {
            return rawSseEvent;
        }
        JSONObject data = payload.data();
        data.set("runId", workflowRun.getId());
        if (StrUtil.isBlank(data.getStr("requestId"))) {
            data.set("requestId", workflowRun.getRequestId());
        }
        return "event: " + payload.eventType() + "\ndata: " + data + "\n\n";
    }

    private void persistWorkflowStepEvent(String rawSseEvent,
                                          WorkflowRun workflowRun,
                                          AtomicInteger persistedStepCounter) {
        WorkflowSsePayload payload = parseWorkflowSsePayload(rawSseEvent);
        if (payload == null || !"agent_completed".equals(payload.eventType())) {
            return;
        }
        try {
            AgentExecutionRecord record = payload.data().toBean(AgentExecutionRecord.class);
            workflowPersistenceService.saveWorkflowStep(workflowRun, record, persistedStepCounter.incrementAndGet());
        } catch (Exception e) {
            log.warn("持久化 V2 agent step 事件失败: {}", e.getMessage());
        }
    }

    private WorkflowSsePayload parseWorkflowSsePayload(String rawSseEvent) {
        if (StrUtil.isBlank(rawSseEvent)) {
            return null;
        }
        String normalizedEvent = rawSseEvent.replace("\r\n", "\n");
        String eventType = null;
        StringBuilder dataBuilder = new StringBuilder();
        for (String line : normalizedEvent.split("\n")) {
            if (line.startsWith("event:")) {
                eventType = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                dataBuilder.append(line.substring("data:".length()).trim());
            }
        }
        if (StrUtil.isBlank(eventType) || dataBuilder.isEmpty()) {
            return null;
        }
        try {
            return new WorkflowSsePayload(eventType, JSONUtil.parseObj(dataBuilder.toString()));
        } catch (Exception e) {
            log.warn("解析 V2 SSE 事件失败: {}", e.getMessage());
            return null;
        }
    }

    private record WorkflowCompletionResult(
            boolean success,
            String summary,
            String responseJson,
            com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response response
    ) {
    }

    private record WorkflowSsePayload(String eventType, JSONObject data) {
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型（多例模式）
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，优先使用最近一次成功 V2 工作流产物
        String codeGenType = app.getCodeGenType();
        String sourceDirPath = resolveDeploySourceDir(app, loginUser);
        // 6. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/index.html", deployHost, deployKey);
        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    @Override
    public void downloadAppCode(Long appId, User loginUser, HttpServletResponse response) {
        App app = validateAppOwner(appId, loginUser);
        String sourceDirPath = resolveGeneratedSourceDir(app, loginUser);
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        projectDownloadService.downloadProjectAsZip(sourceDirPath, String.valueOf(appId), response);
    }

    private String resolveDeploySourceDir(App app, User loginUser) {
        WorkflowRun latestSucceededRun = resolveLatestSucceededWorkflowRun(app, loginUser);
        if (latestSucceededRun != null && StrUtil.isNotBlank(latestSucceededRun.getWorkspacePath())) {
            return latestSucceededRun.getWorkspacePath();
        }
        return resolveLegacyGeneratedSourceDir(app);
    }

    private String resolveGeneratedSourceDir(App app, User loginUser) {
        WorkflowRun latestSucceededRun = resolveLatestSucceededWorkflowRun(app, loginUser);
        if (latestSucceededRun != null && StrUtil.isNotBlank(latestSucceededRun.getWorkspacePath())) {
            return latestSucceededRun.getWorkspacePath();
        }
        return resolveLegacyGeneratedSourceDir(app);
    }

    private WorkflowRun resolveLatestSucceededWorkflowRun(App app, User loginUser) {
        if (app == null || app.getId() == null || loginUser == null || loginUser.getId() == null) {
            return null;
        }
        return workflowRunService.getLatestSucceededRun(app.getId(), loginUser.getId());
    }

    private String resolveLegacyGeneratedSourceDir(App app) {
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }
}
