package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.ai.CodeQualityCheckService;
import com.lingchuang.ai.langgraph4j.model.QualityResult;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 模型审查 Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewAgent {

    private static final String AGENT_NAME = "ReviewAgent";

    private final CodeQualityCheckService codeQualityCheckService;
    private final GeneratedArtifactSupport generatedArtifactSupport;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.REVIEWING,
                "generatedDir=%s".formatted(sessionState.getCodeArtifact() == null ? "unknown" : sessionState.getCodeArtifact().getGeneratedCodeDir()),
                "CodeQualityCheckService"
        );
        ReviewArtifact reviewArtifact = review(sessionState);
        sessionState.setReviewArtifact(reviewArtifact);
        sessionState.finishAgentExecution(
                executionRecord,
                reviewArtifact.isApproved() ? "SUCCESS" : (reviewArtifact.isCanFix() ? "FAILED" : "DEGRADED"),
                reviewArtifact.getReviewSummary(),
                "unavailable"
        );
        log.info("requestId={}, agent={}, approved={}, canFix={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                reviewArtifact.isApproved(),
                reviewArtifact.isCanFix(),
                executionRecord.getDurationMs());
        return AgentSessionState.saveState(sessionState);
    }

    private ReviewArtifact review(AgentSessionState sessionState) {
        CodeArtifact codeArtifact = sessionState.getCodeArtifact();
        if (codeArtifact == null) {
            return missingArtifactReview("未生成代码产物");
        }
        if (StrUtil.isNotBlank(codeArtifact.getErrorMessage())) {
            return ReviewArtifact.builder()
                    .approved(false)
                    .canFix(false)
                    .blockerIssues(List.of("CodeAuthorAgent 执行失败: " + codeArtifact.getErrorMessage()))
                    .reviewSummary("代码生成阶段失败，无法进入正常审查")
                    .build();
        }
        if (!generatedArtifactSupport.directoryExists(codeArtifact.getGeneratedCodeDir())) {
            return missingArtifactReview("生成代码目录不存在: " + codeArtifact.getGeneratedCodeDir());
        }
        String codeContent = generatedArtifactSupport.readCodeContent(codeArtifact.getGeneratedCodeDir());
        if (StrUtil.isBlank(codeContent)) {
            return missingArtifactReview("未找到可审查的代码文件");
        }
        try {
            QualityResult qualityResult = codeQualityCheckService.checkCodeQuality(codeContent);
            return mapQualityResult(qualityResult);
        } catch (Exception e) {
            log.warn("requestId={}, agent={}, 审查服务异常，降级放行: {}",
                    sessionState.getRequestId(), AGENT_NAME, e.getMessage());
            return ReviewArtifact.builder()
                    .approved(true)
                    .canFix(false)
                    .minorIssues(List.of("Review 服务异常，已降级放行到后续验证阶段"))
                    .reviewSummary("审查服务异常，已降级放行")
                    .build();
        }
    }

    private ReviewArtifact mapQualityResult(QualityResult qualityResult) {
        if (qualityResult == null) {
            return ReviewArtifact.builder()
                    .approved(false)
                    .canFix(false)
                    .blockerIssues(List.of("审查结果为空"))
                    .reviewSummary("Review 未返回有效结果")
                    .build();
        }
        boolean approved = Boolean.TRUE.equals(qualityResult.getIsValid());
        List<String> errors = CollUtil.emptyIfNull(qualityResult.getErrors());
        List<String> suggestions = CollUtil.emptyIfNull(qualityResult.getSuggestions());
        return ReviewArtifact.builder()
                .approved(approved)
                .canFix(!approved && (!errors.isEmpty() || !suggestions.isEmpty()))
                .blockerIssues(errors)
                .majorIssues(approved ? List.of() : suggestions)
                .minorIssues(approved ? suggestions : List.of())
                .fixSuggestions(approved ? List.of() : suggestions)
                .reviewSummary(approved
                        ? "Review 通过%s".formatted(suggestions.isEmpty() ? "" : "，包含 %d 条优化建议".formatted(suggestions.size()))
                        : "Review 未通过，发现 %d 个 blocker、%d 条修复建议".formatted(errors.size(), suggestions.size()))
                .build();
    }

    private ReviewArtifact missingArtifactReview(String reason) {
        return ReviewArtifact.builder()
                .approved(false)
                .canFix(false)
                .blockerIssues(List.of(reason))
                .reviewSummary(reason)
                .build();
    }
}
