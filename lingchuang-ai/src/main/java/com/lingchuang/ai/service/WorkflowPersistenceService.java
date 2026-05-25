package com.lingchuang.ai.service;

import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;

import java.util.List;

/**
 * V2 工作流步骤与产物持久化服务。
 */
public interface WorkflowPersistenceService {

    void saveWorkflowResult(WorkflowRun workflowRun, WorkflowV2Response response);

    void saveWorkflowStep(WorkflowRun workflowRun, AgentExecutionRecord record, int stepNumber);

    void saveRetryParentArtifact(WorkflowRun retryRun, WorkflowRun parentRun);

    WorkflowRunDetailVO buildDetail(WorkflowRun workflowRun);

    List<WorkflowStep> listSteps(Long runId);

    List<WorkflowArtifact> listArtifacts(Long runId);
}
