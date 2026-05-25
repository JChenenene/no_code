package com.lingchuang.ai.service;

import com.lingchuang.ai.model.entity.WorkflowRun;
import com.mybatisflex.core.service.IService;

public interface WorkflowRunService extends IService<WorkflowRun> {

    WorkflowRun createRunningRun(Long appId, Long userId, String prompt);

    WorkflowRun createRunningRun(Long appId, Long userId, String prompt, String codeGenType);

    void attachWorkspace(WorkflowRun workflowRun, String workspacePath, String previewUrl);

    void markSucceeded(WorkflowRun workflowRun, String responseJson);

    void markFailed(WorkflowRun workflowRun, String errorMessage);

    boolean cancelRun(WorkflowRun workflowRun, String reason);

    boolean isCancelled(Long runId);

    WorkflowRun getLatestRun(Long appId, Long userId);

    WorkflowRun getLatestSucceededRun(Long appId, Long userId);

    WorkflowRun getRunningRun(Long appId, Long userId);
}
