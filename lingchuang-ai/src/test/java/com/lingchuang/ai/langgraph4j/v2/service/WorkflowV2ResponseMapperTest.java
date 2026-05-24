package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FinalArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.RouteDecisionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowV2ResponseMapperTest {

    private final WorkflowV2ResponseMapper mapper = new WorkflowV2ResponseMapper();

    @Test
    void shouldMapAgentSessionStateToWorkflowV2Response() {
        AgentSessionState state = AgentSessionState.builder()
                .requestId("req-1")
                .workflowVersion("v2")
                .currentAgent("FinalResponseAgent")
                .attemptCount(1)
                .taskSpec(TaskSpec.builder()
                        .originalPrompt("生成一个控制台首页")
                        .targetCodeGenType("vue_project")
                        .build())
                .codeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("/tmp/code_output/vue_project_1")
                        .keyFiles(List.of("package.json", "src/App.vue"))
                        .build())
                .reviewArtifact(ReviewArtifact.builder()
                        .approved(true)
                        .reviewSummary("Review 通过")
                        .build())
                .fixPlanArtifact(FixPlanArtifact.builder()
                        .issueSource("review")
                        .targetFiles(List.of("src/App.vue"))
                        .blockingIssues(List.of("修复一个 blocker"))
                        .attemptLabel("fix-attempt-1")
                        .build())
                .verificationArtifact(VerificationArtifact.builder()
                        .passed(true)
                        .summary("Vue 项目构建验证通过")
                        .issues(List.of("无"))
                        .build())
                .finalArtifact(FinalArtifact.builder()
                        .finalStatus(WorkflowFinalStatus.SUCCESS)
                        .summary("执行成功")
                        .build())
                .agentTimeline(List.of(AgentExecutionRecord.builder()
                        .agentName("RequirementPlannerAgent")
                        .stage(WorkflowStage.PLANNING)
                        .startAt(LocalDateTime.now())
                        .endAt(LocalDateTime.now())
                        .durationMs(12)
                        .status("SUCCESS")
                        .inputSummary("in")
                        .outputSummary("out")
                        .modelName("planner")
                        .tokenUsage("unavailable")
                        .build()))
                .routeDecisions(List.of(RouteDecisionRecord.builder()
                        .stage(WorkflowStage.PLANNING)
                        .fromNode("planner")
                        .decision("prepare_both")
                        .targetNode("prepare_both")
                        .reason("需要检索和素材")
                        .decidedAt(LocalDateTime.now())
                        .build()))
                .build();

        WorkflowV2Response response = mapper.toResponse(state);

        assertEquals("req-1", response.getRequestId());
        assertEquals("v2", response.getWorkflowVersion());
        assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        assertEquals("FinalResponseAgent", response.getCurrentAgent());
        assertEquals(1, response.getAttemptCount());
        assertEquals("Review 通过", response.getReviewSummary());
        assertEquals("Vue 项目构建验证通过", response.getVerificationSummary());
        assertNotNull(response.getFixSummary());
        assertEquals(1, response.getVerificationIssues().size());
        assertEquals(1, response.getAgentTimeline().size());
        assertEquals(1, response.getRouteDecisions().size());
        assertNotNull(response.getArtifacts());
        assertEquals("/tmp/code_output/vue_project_1", response.getArtifacts().getCodeArtifact().getGeneratedCodeDir());
        assertEquals("review", response.getArtifacts().getFixPlanArtifact().getIssueSource());
        assertEquals(WorkflowFinalStatus.SUCCESS, response.getArtifacts().getFinalArtifact().getFinalStatus());
    }
}
