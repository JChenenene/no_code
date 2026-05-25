package com.lingchuang.ai.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.lingchuang.ai.model.dto.app.AppAddRequest;
import com.lingchuang.ai.model.dto.app.AppQueryRequest;
import com.lingchuang.ai.model.entity.App;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.User;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.vo.AppVO;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用服务层。
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 通过 V2 多 Agent 工作流生成应用代码。
     */
    Flux<String> chatToGenCodeV2(Long appId, String message, User loginUser);

    /**
     * 查询当前用户在应用上的最近一次 V2 工作流运行记录。
     */
    WorkflowRun getLatestWorkflowRun(Long appId, User loginUser);

    /**
     * 查询当前用户在应用上的最近一次 V2 工作流运行详情。
     */
    WorkflowRunDetailVO getLatestWorkflowRunDetail(Long appId, User loginUser);

    /**
     * 查询当前用户在应用上的最近一次成功 V2 工作流运行记录。
     */
    WorkflowRun getLatestSucceededWorkflowRun(Long appId, User loginUser);

    /**
     * 查询当前用户可访问的指定 V2 工作流运行详情。
     */
    WorkflowRunDetailVO getWorkflowRunDetail(Long runId, User loginUser);

    /**
     * 查询当前用户可访问的指定 V2 工作流运行状态。
     */
    WorkflowRun getWorkflowRunStatus(Long runId, User loginUser);

    /**
     * 取消当前用户可访问的运行中 V2 工作流。
     */
    boolean cancelWorkflowRun(Long runId, User loginUser);

    /**
     * 基于失败或已取消的 V2 工作流重新发起一次完整生成。
     */
    Flux<String> retryWorkflowRun(Long runId, User loginUser);

    /**
     * 查询指定 V2 工作流运行的步骤时间线。
     */
    List<WorkflowStep> listWorkflowRunSteps(Long runId, User loginUser);

    /**
     * 查询指定 V2 工作流运行的产物列表。
     */
    List<WorkflowArtifact> listWorkflowRunArtifacts(Long runId, User loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 应用部署
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 下载应用代码。
     */
    void downloadAppCode(Long appId, User loginUser, HttpServletResponse response);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

}
