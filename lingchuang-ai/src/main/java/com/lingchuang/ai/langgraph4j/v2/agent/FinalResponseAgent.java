package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.FinalArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 最终响应汇总 Agent。
 */
@Component
@Slf4j
public class FinalResponseAgent {

    private static final String AGENT_NAME = "FinalResponseAgent";

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.FINALIZING,
                "attempt=%d".formatted(sessionState.getAttemptCount()),
                "rule-based"
        );
        FinalArtifact finalArtifact = buildFinalArtifact(sessionState);
        sessionState.setFinalArtifact(finalArtifact);
        sessionState.finishAgentExecution(
                executionRecord,
                WorkflowFinalStatus.SUCCESS.equals(finalArtifact.getFinalStatus()) ? "SUCCESS" : "FAILED",
                finalArtifact.getSummary(),
                "unavailable"
        );
        log.info("requestId={}, agent={}, finalStatus={}, summary={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                finalArtifact.getFinalStatus(),
                finalArtifact.getSummary());
        return AgentSessionState.saveState(sessionState);
    }

    private FinalArtifact buildFinalArtifact(AgentSessionState sessionState) {
        if (sessionState.getCodeArtifact() == null || StrUtil.isNotBlank(sessionState.getCodeArtifact().getErrorMessage())) {
            return FinalArtifact.builder()
                    .finalStatus(WorkflowFinalStatus.GENERATION_FAILED)
                    .summary("V2 工作流执行结束，代码生成阶段失败")
                    .failureReason(sessionState.getCodeArtifact() == null
                            ? "未生成代码产物"
                            : sessionState.getCodeArtifact().getErrorMessage())
                    .build();
        }
        if (sessionState.getReviewArtifact() != null && !sessionState.getReviewArtifact().isApproved()) {
            return FinalArtifact.builder()
                    .finalStatus(WorkflowFinalStatus.REVIEW_FAILED)
                    .summary("V2 工作流执行结束，review 未通过")
                    .failureReason(StrUtil.blankToDefault(
                            sessionState.getReviewArtifact().getReviewSummary(),
                            "review 未通过"))
                    .build();
        }
        if (sessionState.getVerificationArtifact() != null && !sessionState.getVerificationArtifact().isPassed()) {
            return FinalArtifact.builder()
                    .finalStatus(WorkflowFinalStatus.VERIFICATION_FAILED)
                    .summary("V2 工作流执行结束，验证未通过")
                    .failureReason(StrUtil.blankToDefault(
                            sessionState.getVerificationArtifact().getErrorMessage(),
                            sessionState.getVerificationArtifact().getSummary()))
                    .build();
        }
        if (sessionState.getReviewArtifact() != null && sessionState.getReviewArtifact().isApproved()) {
            String buildResultDir = sessionState.getVerificationArtifact() == null
                    ? null
                    : sessionState.getVerificationArtifact().getBuildResultDir();
            return FinalArtifact.builder()
                    .finalStatus(WorkflowFinalStatus.SUCCESS)
                    .summary(buildResultDir == null
                            ? "V2 工作流执行成功，代码已通过 review 和验证"
                            : "V2 工作流执行成功，代码已通过 review 和验证")
                    .failureReason(null)
                    .build();
        }
        return FinalArtifact.builder()
                .finalStatus(WorkflowFinalStatus.ERROR)
                .summary("V2 工作流执行结束，但未能产出完整结论")
                .failureReason("缺少 review/final 结果")
                .build();
    }
}
