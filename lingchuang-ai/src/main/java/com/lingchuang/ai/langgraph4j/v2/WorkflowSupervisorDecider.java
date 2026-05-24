package com.lingchuang.ai.langgraph4j.v2;

import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Component;

/**
 * V2 工作流主管路由决策器。
 */
@Component
public class WorkflowSupervisorDecider {

    public String routeAfterPlanning(AgentSessionState state) {
        TaskSpec taskSpec = state == null ? null : state.getTaskSpec();
        boolean needsRetrieval = taskSpec != null && taskSpec.isNeedsRetrieval();
        boolean needsAssetPlanning = taskSpec != null && taskSpec.isNeedsAssetPlanning();
        if (needsRetrieval && needsAssetPlanning) {
            record(state, WorkflowStage.PLANNING, "planner", "prepare_both", "prepare_both",
                    "任务需要同时执行检索增强和素材规划");
            return "prepare_both";
        }
        if (needsRetrieval) {
            record(state, WorkflowStage.PLANNING, "planner", "prepare_retrieval_only", "prepare_retrieval_only",
                    "任务仅需要检索增强");
            return "prepare_retrieval_only";
        }
        if (needsAssetPlanning) {
            record(state, WorkflowStage.PLANNING, "planner", "prepare_asset_only", "prepare_asset_only",
                    "任务仅需要素材规划");
            return "prepare_asset_only";
        }
        record(state, WorkflowStage.PLANNING, "planner", "prepare_none", "prepare_none",
                "任务无需额外准备阶段，直接进入首轮生成");
        return "prepare_none";
    }

    public String routeAfterReview(AgentSessionState state) {
        ReviewArtifact reviewArtifact = state.getReviewArtifact();
        if (reviewArtifact == null) {
            record(state, WorkflowStage.REVIEWING, "review", "final", "final_response", "缺少 review 结果，直接结束");
            return "final";
        }
        if (reviewArtifact.isApproved()) {
            record(state, WorkflowStage.REVIEWING, "review", "verify", "build_verify", "review 通过，进入统一验证阶段");
            return "verify";
        }
        if (reviewArtifact.isCanFix() && state.getAttemptCount() < state.getMaxFixLoops()) {
            record(state, WorkflowStage.REVIEWING, "review", "fix", "fix",
                    "review 未通过但可修复，进入修复闭环");
            return "fix";
        }
        record(state, WorkflowStage.REVIEWING, "review", "final", "final_response",
                "review 未通过且不可修复或已耗尽修复预算");
        return "final";
    }

    public String routeAfterVerify(AgentSessionState state) {
        VerificationArtifact verificationArtifact = state.getVerificationArtifact();
        if (verificationArtifact == null) {
            record(state, WorkflowStage.VERIFYING, "build_verify", "final", "final_response", "缺少验证结果，直接结束");
            return "final";
        }
        if (verificationArtifact.isPassed()) {
            record(state, WorkflowStage.VERIFYING, "build_verify", "final", "final_response", "验证通过");
            return "final";
        }
        if (verificationArtifact.isCanFix() && state.getAttemptCount() < state.getMaxFixLoops()) {
            record(state, WorkflowStage.VERIFYING, "build_verify", "fix", "fix",
                    "验证失败但仍可修复，进入修复闭环");
            return "fix";
        }
        record(state, WorkflowStage.VERIFYING, "build_verify", "final", "final_response",
                "验证失败且不可修复或已耗尽修复预算");
        return "final";
    }

    private CodeGenTypeEnum resolveCodeGenType(AgentSessionState state) {
        if (state == null || state.getTaskSpec() == null) {
            return CodeGenTypeEnum.HTML;
        }
        CodeGenTypeEnum resolved = CodeGenTypeEnum.getEnumByValue(state.getTaskSpec().getTargetCodeGenType());
        return resolved == null ? CodeGenTypeEnum.HTML : resolved;
    }

    private void record(AgentSessionState state,
                        WorkflowStage stage,
                        String fromNode,
                        String decision,
                        String targetNode,
                        String reason) {
        if (state != null) {
            state.recordRouteDecision(stage, fromNode, decision, targetNode, reason);
        }
    }
}
