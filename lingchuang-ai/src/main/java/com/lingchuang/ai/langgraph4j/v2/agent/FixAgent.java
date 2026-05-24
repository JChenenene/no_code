package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 修复协调 Agent。
 */
@Component
@Slf4j
public class FixAgent {

    private static final String AGENT_NAME = "FixAgent";

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.FIXING,
                "attempt=%d".formatted(sessionState.getAttemptCount()),
                "rule-based"
        );
        sessionState.setAttemptCount(sessionState.getAttemptCount() + 1);
        FixPlanArtifact fixPlanArtifact = buildFixPlan(sessionState);
        sessionState.setFixPlanArtifact(fixPlanArtifact);
        sessionState.setReviewArtifact(null);
        sessionState.setVerificationArtifact(null);
        sessionState.setFinalArtifact(null);
        sessionState.finishAgentExecution(
                executionRecord,
                "SUCCESS",
                "issueSource=%s, targetFiles=%d".formatted(
                        fixPlanArtifact.getIssueSource(),
                        fixPlanArtifact.getTargetFiles() == null ? 0 : fixPlanArtifact.getTargetFiles().size()),
                "unavailable"
        );
        log.info("requestId={}, agent={}, attemptCount={}, reviewSummary={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                sessionState.getAttemptCount(),
                fixPlanArtifact.getAttemptLabel());
        return AgentSessionState.saveState(sessionState);
    }

    private FixPlanArtifact buildFixPlan(AgentSessionState sessionState) {
        ReviewArtifact reviewArtifact = sessionState.getReviewArtifact();
        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        CodeArtifact codeArtifact = sessionState.getCodeArtifact();
        TaskSpec taskSpec = sessionState.getTaskSpec();
        List<String> blockingIssues = new ArrayList<>();
        List<String> patchInstructions = new ArrayList<>();

        if (reviewArtifact != null) {
            blockingIssues.addAll(CollUtil.emptyIfNull(reviewArtifact.getBlockerIssues()));
            blockingIssues.addAll(CollUtil.emptyIfNull(reviewArtifact.getMajorIssues()));
            patchInstructions.addAll(CollUtil.emptyIfNull(reviewArtifact.getFixSuggestions()));
        }
        if (verificationArtifact != null) {
            blockingIssues.addAll(CollUtil.emptyIfNull(verificationArtifact.getIssues()));
            if (StrUtil.isNotBlank(verificationArtifact.getErrorMessage())) {
                blockingIssues.add(verificationArtifact.getErrorMessage());
            }
            if (StrUtil.isNotBlank(verificationArtifact.getSummary()) && !verificationArtifact.isPassed()) {
                patchInstructions.add("解决验证失败问题: " + verificationArtifact.getSummary());
            }
        }

        if (patchInstructions.isEmpty() && !blockingIssues.isEmpty()) {
            for (String issue : blockingIssues) {
                patchInstructions.add("修复问题: " + issue);
            }
        }

        return FixPlanArtifact.builder()
                .issueSource(resolveIssueSource(reviewArtifact, verificationArtifact))
                .targetFiles(matchTargetFiles(codeArtifact, blockingIssues, patchInstructions))
                .blockingIssues(deduplicate(blockingIssues))
                .patchInstructions(deduplicate(patchInstructions))
                .mustKeepConstraints(resolveConstraints(taskSpec))
                .attemptLabel("fix-attempt-%d".formatted(sessionState.getAttemptCount()))
                .build();
    }

    private String resolveIssueSource(ReviewArtifact reviewArtifact, VerificationArtifact verificationArtifact) {
        boolean hasReviewIssue = reviewArtifact != null
                && (!CollUtil.isEmpty(reviewArtifact.getBlockerIssues()) || !CollUtil.isEmpty(reviewArtifact.getMajorIssues()));
        boolean hasVerificationIssue = verificationArtifact != null && !verificationArtifact.isPassed();
        if (hasReviewIssue && hasVerificationIssue) {
            return "review+verification";
        }
        if (hasVerificationIssue) {
            return "verification";
        }
        return "review";
    }

    private List<String> matchTargetFiles(CodeArtifact codeArtifact, List<String> blockingIssues, List<String> patchInstructions) {
        if (codeArtifact == null || CollUtil.isEmpty(codeArtifact.getKeyFiles())) {
            return List.of();
        }
        String issueText = String.join("\n", deduplicate(blockingIssues)) + "\n" + String.join("\n", deduplicate(patchInstructions));
        String normalized = issueText.toLowerCase();
        List<String> targetFiles = new ArrayList<>();
        for (String keyFile : codeArtifact.getKeyFiles()) {
            String fileName = keyFile.contains("/") ? keyFile.substring(keyFile.lastIndexOf('/') + 1) : keyFile;
            if (normalized.contains(keyFile.toLowerCase()) || normalized.contains(fileName.toLowerCase())) {
                targetFiles.add(keyFile);
            }
        }
        return deduplicate(targetFiles);
    }

    private List<String> resolveConstraints(TaskSpec taskSpec) {
        if (taskSpec == null) {
            return List.of();
        }
        List<String> constraints = new ArrayList<>();
        constraints.addAll(CollUtil.emptyIfNull(taskSpec.getTechnicalConstraints()));
        constraints.addAll(CollUtil.emptyIfNull(taskSpec.getAcceptanceCriteria()));
        return deduplicate(constraints);
    }

    private List<String> deduplicate(List<String> source) {
        Set<String> values = new LinkedHashSet<>();
        for (String item : CollUtil.emptyIfNull(source)) {
            if (StrUtil.isNotBlank(item)) {
                values.add(item.trim());
            }
        }
        return List.copyOf(values);
    }
}
