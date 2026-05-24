package com.lingchuang.ai.langgraph4j.v2;

import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowSupervisorDeciderTest {

    private final WorkflowSupervisorDecider decider = new WorkflowSupervisorDecider();

    @Test
    void shouldRouteToRetrievalAfterPlanningWhenTargetIsVueProject() {
        AgentSessionState state = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder()
                        .targetCodeGenType("vue_project")
                        .needsRetrieval(true)
                        .needsAssetPlanning(true)
                        .build())
                .build();

        assertEquals("prepare_both", decider.routeAfterPlanning(state));
    }

    @Test
    void shouldRouteDirectlyToAuthorAfterPlanningWhenTargetIsHtml() {
        AgentSessionState state = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .needsRetrieval(false)
                        .needsAssetPlanning(false)
                        .build())
                .build();

        assertEquals("prepare_none", decider.routeAfterPlanning(state));
    }

    @Test
    void shouldRouteToVerifyWhenReviewApproved() {
        AgentSessionState state = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("vue_project").build())
                .reviewArtifact(ReviewArtifact.builder().approved(true).build())
                .build();

        assertEquals("verify", decider.routeAfterReview(state));
    }

    @Test
    void shouldRouteToFixWhenReviewCanFixAndAttemptsRemain() {
        AgentSessionState state = AgentSessionState.builder()
                .attemptCount(1)
                .maxFixLoops(2)
                .reviewArtifact(ReviewArtifact.builder().approved(false).canFix(true).build())
                .build();

        assertEquals("fix", decider.routeAfterReview(state));
    }

    @Test
    void shouldRouteToFinalWhenFixAttemptsAreExhausted() {
        AgentSessionState state = AgentSessionState.builder()
                .attemptCount(2)
                .maxFixLoops(2)
                .reviewArtifact(ReviewArtifact.builder().approved(false).canFix(true).build())
                .build();

        assertEquals("final", decider.routeAfterReview(state));
    }

    @Test
    void shouldRouteToFixAfterVerifyWhenCanFixAndAttemptsRemain() {
        AgentSessionState state = AgentSessionState.builder()
                .attemptCount(1)
                .maxFixLoops(2)
                .verificationArtifact(VerificationArtifact.builder()
                        .passed(false)
                        .canFix(true)
                        .failureType("build")
                        .build())
                .build();

        assertEquals("fix", decider.routeAfterVerify(state));
    }

    @Test
    void shouldRouteToFinalAfterVerifyWhenPassed() {
        AgentSessionState state = AgentSessionState.builder()
                .verificationArtifact(VerificationArtifact.builder()
                        .passed(true)
                        .build())
                .build();

        assertEquals("final", decider.routeAfterVerify(state));
    }
}
