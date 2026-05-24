package com.lingchuang.ai.service;

import com.lingchuang.ai.model.entity.WorkflowRun;
import com.mybatisflex.core.service.IService;

public interface WorkflowRunService extends IService<WorkflowRun> {

    WorkflowRun createRunningRun(Long appId, Long userId, String prompt);

    WorkflowRun createRunningRun(Long appId, Long userId, String prompt, String codeGenType);

    void attachWorkspace(WorkflowRun workflowRun, String workspacePath, String previewUrl);

    void markSucceeded(WorkflowRun workflowRun, String responseJson);

    void markFailed(WorkflowRun workflowRun, String errorMessage);

    WorkflowRun getLatestRun(Long appId, Long userId);

    WorkflowRun getLatestSucceededRun(Long appId, Long userId);
}
