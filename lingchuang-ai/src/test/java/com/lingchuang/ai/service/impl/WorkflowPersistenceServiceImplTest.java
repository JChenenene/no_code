package com.lingchuang.ai.service.impl;

import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FinalArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Artifacts;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.mapper.WorkflowArtifactMapper;
import com.lingchuang.ai.mapper.WorkflowStepMapper;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowPersistenceServiceImplTest {

    private WorkflowStepMapper workflowStepMapper;
    private WorkflowArtifactMapper workflowArtifactMapper;
    private WorkflowPersistenceServiceImpl workflowPersistenceService;

    @BeforeEach
    void setUp() {
        workflowStepMapper = mock(WorkflowStepMapper.class);
        workflowArtifactMapper = mock(WorkflowArtifactMapper.class);
        workflowPersistenceService = new WorkflowPersistenceServiceImpl(workflowStepMapper, workflowArtifactMapper);
    }

    @Test
    void shouldPersistAgentTimelineAsWorkflowSteps() {
        WorkflowRun workflowRun = WorkflowRun.builder().id(100L).requestId("req-1").build();
        WorkflowV2Response response = WorkflowV2Response.builder()
                .requestId("req-1")
                .agentTimeline(List.of(
                        AgentExecutionRecord.builder()
                                .agentName("RequirementPlannerAgent")
                                .stage(WorkflowStage.PLANNING)
                                .status("SUCCESS")
                                .inputSummary("需求")
                                .outputSummary("规划完成")
                                .startAt(LocalDateTime.now())
                                .endAt(LocalDateTime.now())
                                .durationMs(12)
                                .build(),
                        AgentExecutionRecord.builder()
                                .agentName("BuildVerifyAgent")
                                .stage(WorkflowStage.VERIFYING)
                                .status("FAILED")
                                .inputSummary("验证")
                                .outputSummary("验证失败")
                                .startAt(LocalDateTime.now())
                                .endAt(LocalDateTime.now())
                                .durationMs(30)
                                .build()
                ))
                .build();
        when(workflowStepMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(0);
        when(workflowStepMapper.insert(any(WorkflowStep.class))).thenReturn(1);

        workflowPersistenceService.saveWorkflowResult(workflowRun, response);

        verify(workflowStepMapper).deleteByQuery(any(QueryWrapper.class));
        verify(workflowStepMapper).insert(argThat(step ->
                step.getRunId().equals(100L)
                        && step.getStepNumber().equals(1)
                        && "RequirementPlannerAgent".equals(step.getAgentName())
                        && "planning".equals(step.getStage())
                        && "succeeded".equals(step.getStatus())
                        && "规划完成".equals(step.getOutputSummary())
        ));
        verify(workflowStepMapper).insert(argThat(step ->
                step.getRunId().equals(100L)
                        && step.getStepNumber().equals(2)
                        && "BuildVerifyAgent".equals(step.getAgentName())
                        && "verifying".equals(step.getStage())
                        && "failed".equals(step.getStatus())
        ));
    }

    @Test
    void shouldPersistSingleRunningWorkflowStep() {
        WorkflowRun workflowRun = WorkflowRun.builder().id(100L).requestId("req-1").build();
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName("RequirementPlannerAgent")
                .stage(WorkflowStage.PLANNING)
                .status("RUNNING")
                .inputSummary("需求")
                .startAt(LocalDateTime.now())
                .build();
        when(workflowStepMapper.insert(any(WorkflowStep.class))).thenReturn(1);

        workflowPersistenceService.saveWorkflowStep(workflowRun, record, 1);

        verify(workflowStepMapper).insert(argThat(step ->
                step.getRunId().equals(100L)
                        && "req-1".equals(step.getRequestId())
                        && step.getStepNumber().equals(1)
                        && "RequirementPlannerAgent".equals(step.getAgentName())
                        && "running".equals(step.getStatus())
        ));
    }

    @Test
    void shouldPersistFailedWorkflowStepWithErrorMessage() {
        WorkflowRun workflowRun = WorkflowRun.builder().id(100L).requestId("req-1").build();
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName("BuildVerifyAgent")
                .stage(WorkflowStage.VERIFYING)
                .status("FAILED")
                .inputSummary("验证")
                .outputSummary("缺少 index.html")
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now())
                .build();
        when(workflowStepMapper.insert(any(WorkflowStep.class))).thenReturn(1);

        workflowPersistenceService.saveWorkflowStep(workflowRun, record, 2);

        verify(workflowStepMapper).insert(argThat(step ->
                step.getRunId().equals(100L)
                        && step.getStepNumber().equals(2)
                        && "BuildVerifyAgent".equals(step.getAgentName())
                        && "failed".equals(step.getStatus())
                        && "缺少 index.html".equals(step.getErrorMessage())
        ));
    }

    @Test
    void shouldPersistWorkflowArtifactsFromResponse() {
        WorkflowRun workflowRun = WorkflowRun.builder().id(100L).requestId("req-1").build();
        WorkflowV2Response response = WorkflowV2Response.builder()
                .requestId("req-1")
                .verificationSummary("构建通过")
                .artifacts(WorkflowV2Artifacts.builder()
                        .codeArtifact(CodeArtifact.builder()
                                .generatedCodeDir("D:/tmp/vue_project_1")
                                .summary("代码生成完成")
                                .build())
                        .verificationArtifact(VerificationArtifact.builder()
                                .passed(true)
                                .summary("构建通过")
                                .build())
                        .finalArtifact(FinalArtifact.builder()
                                .finalStatus(WorkflowFinalStatus.SUCCESS)
                                .summary("执行成功")
                                .build())
                        .build())
                .build();
        when(workflowArtifactMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(0);
        when(workflowArtifactMapper.insert(any(WorkflowArtifact.class))).thenReturn(1);

        workflowPersistenceService.saveWorkflowResult(workflowRun, response);

        verify(workflowArtifactMapper).deleteByQuery(any(QueryWrapper.class));
        verify(workflowArtifactMapper).insert(argThat(artifact ->
                artifact.getRunId().equals(100L)
                        && "code".equals(artifact.getArtifactType())
                        && "代码生成完成".equals(artifact.getSummary())
                        && "D:/tmp/vue_project_1".equals(artifact.getPath())
        ));
        verify(workflowArtifactMapper).insert(argThat(artifact ->
                artifact.getRunId().equals(100L)
                        && "verification".equals(artifact.getArtifactType())
                        && "构建通过".equals(artifact.getSummary())
                        && artifact.getJsonContent().contains("\"passed\":true")
        ));
        verify(workflowArtifactMapper).insert(argThat(artifact ->
                artifact.getRunId().equals(100L)
                        && "final".equals(artifact.getArtifactType())
                        && "执行成功".equals(artifact.getSummary())
                        && artifact.getJsonContent().contains("SUCCESS")
        ));
    }

    @Test
    void shouldBuildLatestDetailWithStepsAndArtifacts() {
        WorkflowRun workflowRun = WorkflowRun.builder().id(100L).requestId("req-1").status("succeeded").build();
        List<WorkflowStep> steps = List.of(WorkflowStep.builder().agentName("FinalResponseAgent").build());
        List<WorkflowArtifact> artifacts = List.of(WorkflowArtifact.builder().artifactType("final").build());
        when(workflowStepMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(steps);
        when(workflowArtifactMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(artifacts);

        var detail = workflowPersistenceService.buildDetail(workflowRun);

        assertEquals(workflowRun, detail.getRun());
        assertEquals(steps, detail.getSteps());
        assertEquals(artifacts, detail.getArtifacts());
    }
}
