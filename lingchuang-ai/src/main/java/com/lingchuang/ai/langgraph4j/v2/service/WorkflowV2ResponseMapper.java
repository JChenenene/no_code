package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Artifacts;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import cn.hutool.core.collection.CollUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V2 工作流状态到响应对象的映射器。
 */
@Component
public class WorkflowV2ResponseMapper {

    public WorkflowV2Response toResponse(AgentSessionState state) {
        if (state == null) {
            return WorkflowV2Response.builder()
                    .workflowVersion("v2")
                    .finalStatus(WorkflowFinalStatus.ERROR)
                    .build();
        }
        CodeArtifact codeArtifact = enrichCodeArtifact(state);
        return WorkflowV2Response.builder()
                .requestId(state.getRequestId())
                .workflowVersion(state.getWorkflowVersion())
                .finalStatus(state.getFinalArtifact() == null ? WorkflowFinalStatus.ERROR : state.getFinalArtifact().getFinalStatus())
                .currentAgent(state.getCurrentAgent())
                .attemptCount(state.getAttemptCount())
                .reviewSummary(state.getReviewArtifact() == null ? null : state.getReviewArtifact().getReviewSummary())
                .verificationSummary(state.getVerificationArtifact() == null ? null : state.getVerificationArtifact().getSummary())
                .fixSummary(buildFixSummary(state))
                .verificationIssues(state.getVerificationArtifact() == null
                        ? List.of()
                        : CollUtil.emptyIfNull(state.getVerificationArtifact().getIssues()))
                .agentTimeline(state.getAgentTimeline() == null ? List.of() : List.copyOf(state.getAgentTimeline()))
                .routeDecisions(state.getRouteDecisions() == null ? List.of() : List.copyOf(state.getRouteDecisions()))
                .artifacts(WorkflowV2Artifacts.builder()
                        .taskSpec(state.getTaskSpec())
                        .retrievalBundle(state.getRetrievalBundle())
                        .assetPlan(state.getAssetPlan())
                        .codeArtifact(codeArtifact)
                        .reviewArtifact(state.getReviewArtifact())
                        .fixPlanArtifact(state.getFixPlanArtifact())
                        .verificationArtifact(state.getVerificationArtifact())
                        .finalArtifact(state.getFinalArtifact())
                        .build())
                .build();
    }

    private String buildFixSummary(AgentSessionState state) {
        if (state == null || state.getFixPlanArtifact() == null) {
            return null;
        }
        return "%s | 来源=%s | 目标文件=%d | 问题数=%d".formatted(
                state.getFixPlanArtifact().getAttemptLabel(),
                state.getFixPlanArtifact().getIssueSource(),
                state.getFixPlanArtifact().getTargetFiles() == null ? 0 : state.getFixPlanArtifact().getTargetFiles().size(),
                state.getFixPlanArtifact().getBlockingIssues() == null ? 0 : state.getFixPlanArtifact().getBlockingIssues().size()
        );
    }

    private CodeArtifact enrichCodeArtifact(AgentSessionState state) {
        if (state == null || state.getCodeArtifact() == null || state.getCodeArtifact().getAppId() != null) {
            return state == null ? null : state.getCodeArtifact();
        }
        return state.getCodeArtifact().toBuilder()
                .appId(state.getAppId())
                .build();
    }
}
