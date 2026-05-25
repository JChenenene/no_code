package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 输入增强统一门面。
 */
@Service
@Slf4j
public class RetrievalPromptExpansionService {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_PATTERN = Pattern.compile("(?is)<\\s*(html|body|div|section|main|header|footer|script|style|template|span|img|form|input|button)\\b");
    private static final Pattern JS_PATTERN = Pattern.compile("(?is)(function\\s+\\w+\\s*\\(|const\\s+\\w+\\s*=|let\\s+\\w+\\s*=|var\\s+\\w+\\s*=|document\\.|window\\.|export\\s+default|import\\s+.+from\\s+)");
    private static final Pattern CSS_PATTERN = Pattern.compile("(?is)(\\.[\\w-]+\\s*\\{|#[\\w-]+\\s*\\{|body\\s*\\{|@media\\s+)");
    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("(?is)```.*?```");
    private static final Pattern LEADING_LABEL_PATTERN = Pattern.compile("(?im)^\\s*(retrievalquery|retrieval query|query|检索query|检索 query|检索词|关键词|改写后查询|重写后查询|rewrittenuserprompt|rewritten user prompt|重写后的用户需求|最终提示词|最终用户需求|最终执行需求)\\s*[:：]\\s*");
    private static final Pattern LEADING_NUMBERING_PATTERN = Pattern.compile("(?m)^\\s*(\\d+[\\.、:]|[-*+])\\s*");
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^\\s*#{1,6}\\s*");
    private static final Pattern MULTI_BLANK_LINES_PATTERN = Pattern.compile("\\n{3,}");

    private final RagProperties ragProperties;
    private final RetrievalPromptExpansionAiServiceFactory expansionAiServiceFactory;

    public RetrievalPromptExpansionService(RagProperties ragProperties,
                                          RetrievalPromptExpansionAiServiceFactory expansionAiServiceFactory) {
        this.ragProperties = ragProperties;
        this.expansionAiServiceFactory = expansionAiServiceFactory;
    }

    public RetrievalPromptExpansionOutcome expandForUserRequest(String latestUserMessage,
                                                                List<ChatHistory> recentHistories,
                                                                CodeGenTypeEnum codeGenType) {
        String safeLatestUserMessage = StrUtil.trimToEmpty(latestUserMessage);
        List<ChatHistory> safeRecentHistories = recentHistories == null ? List.of() : List.copyOf(recentHistories);
        RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
        String memorySummary = invocationContext == null ? "" : StrUtil.trimToEmpty(invocationContext.getMemorySummary());
        String baseRetrievalQuery = StrUtil.trimToEmpty(
                RagPromptSupport.buildRetrievalQuery(safeLatestUserMessage, safeRecentHistories, memorySummary)
        );
        if (invocationContext != null && StrUtil.isNotBlank(invocationContext.getExpandedRetrievalQuery())) {
            return RetrievalPromptExpansionOutcome.builder()
                    .retrievalQuery(invocationContext.getExpandedRetrievalQuery())
                    .rewrittenUserPrompt(invocationContext.getRewrittenUserPrompt())
                    .expansionTriggered(invocationContext.isQueryExpansionApplied())
                    .expansionApplied(invocationContext.isQueryExpansionApplied())
                    .fallbackReason(invocationContext.isQueryExpansionApplied() ? "cached" : "not_triggered")
                    .build();
        }
        RetrievalPromptExpansionOutcome outcome = expandInternal(
                safeLatestUserMessage,
                safeRecentHistories,
                codeGenType,
                baseRetrievalQuery,
                memorySummary,
                false
        );
        cacheToInvocationContext(outcome);
        return outcome;
    }

    public RetrievalPromptExpansionOutcome expandForDirectSearch(String rawQuery, CodeGenTypeEnum codeGenType) {
        String baseRetrievalQuery = StrUtil.trimToEmpty(rawQuery);
        return expandInternal(baseRetrievalQuery, List.of(), codeGenType, baseRetrievalQuery, "", true);
    }

    private RetrievalPromptExpansionOutcome expandInternal(String latestUserMessage,
                                                           List<ChatHistory> recentHistories,
                                                           CodeGenTypeEnum codeGenType,
                                                           String baseRetrievalQuery,
                                                           String memorySummary,
                                                           boolean directSearchOnly) {
        if (!ragProperties.getQueryExpansion().isEnabled()) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, false, "disabled");
        }
        if (StrUtil.isBlank(baseRetrievalQuery)) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, false, "blank_query");
        }
        String skipReason = detectSkipReason(latestUserMessage);
        if (skipReason != null) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, false, skipReason);
        }
        if (!shouldTriggerExpansion(latestUserMessage, recentHistories, baseRetrievalQuery)) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, false, "not_triggered");
        }
        try {
            RetrievalPromptExpansionAiService aiService =
                    expansionAiServiceFactory.createRetrievalPromptExpansionAiService();
            RetrievalPromptExpansionResult rawResult = CompletableFuture
                    .supplyAsync(() -> aiService.expand(buildExpansionRequest(
                            latestUserMessage,
                            recentHistories,
                            codeGenType,
                            baseRetrievalQuery,
                            memorySummary,
                            directSearchOnly
                    )))
                    .orTimeout(ragProperties.getQueryExpansion().getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .join();
            return resolveModelResult(latestUserMessage, codeGenType, baseRetrievalQuery, directSearchOnly, rawResult);
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logExpansionFailure(baseRetrievalQuery, codeGenType, cause);
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, true, "model_error");
        } catch (Exception e) {
            logExpansionFailure(baseRetrievalQuery, codeGenType, e);
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, true, "model_error");
        }
    }

    private RetrievalPromptExpansionOutcome resolveModelResult(String latestUserMessage,
                                                               CodeGenTypeEnum codeGenType,
                                                               String baseRetrievalQuery,
                                                               boolean directSearchOnly,
                                                               RetrievalPromptExpansionResult rawResult) {
        if (rawResult == null) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, true, "empty_result");
        }
        String sanitizedRetrievalQuery = sanitizeRetrievalQuery(rawResult.getRetrievalQuery(), baseRetrievalQuery);
        String sanitizedRewrittenPrompt = directSearchOnly
                ? ""
                : sanitizeRewrittenPrompt(rawResult.getRewrittenUserPrompt(), latestUserMessage);
        boolean retrievalChanged = !StrUtil.equals(sanitizedRetrievalQuery, baseRetrievalQuery);
        boolean rewrittenChanged = !directSearchOnly
                && StrUtil.isNotBlank(sanitizedRewrittenPrompt)
                && !StrUtil.equals(sanitizedRewrittenPrompt, StrUtil.trimToEmpty(latestUserMessage));
        if (!Boolean.TRUE.equals(rawResult.getApplied())) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, true, "model_declined");
        }
        if (!retrievalChanged && !rewrittenChanged) {
            return buildFallbackOutcome(latestUserMessage, codeGenType, baseRetrievalQuery, true, "no_material_change");
        }
        RetrievalPromptExpansionOutcome outcome = RetrievalPromptExpansionOutcome.builder()
                .retrievalQuery(sanitizedRetrievalQuery)
                .rewrittenUserPrompt(rewrittenChanged ? sanitizedRewrittenPrompt : null)
                .expansionTriggered(true)
                .expansionApplied(true)
                .fallbackReason("none")
                .build();
        logOutcome(codeGenType, baseRetrievalQuery, outcome);
        return outcome;
    }

    private RetrievalPromptExpansionOutcome buildFallbackOutcome(String latestUserMessage,
                                                                CodeGenTypeEnum codeGenType,
                                                                String baseRetrievalQuery,
                                                                boolean expansionTriggered,
                                                                String fallbackReason) {
        RetrievalPromptExpansionOutcome outcome = RetrievalPromptExpansionOutcome.builder()
                .retrievalQuery(baseRetrievalQuery)
                .rewrittenUserPrompt(null)
                .expansionTriggered(expansionTriggered)
                .expansionApplied(false)
                .fallbackReason(fallbackReason)
                .build();
        logOutcome(codeGenType, baseRetrievalQuery, outcome);
        return outcome;
    }

    private boolean shouldTriggerExpansion(String latestUserMessage,
                                           List<ChatHistory> recentHistories,
                                           String baseRetrievalQuery) {
        RagProperties.QueryExpansion queryExpansion = ragProperties.getQueryExpansion();
        if (latestUserMessage.length() <= queryExpansion.getShortQueryChars()) {
            return true;
        }
        return CollUtil.isEmpty(recentHistories)
                && baseRetrievalQuery.length() <= queryExpansion.getSparseQueryMaxChars();
    }

    private String detectSkipReason(String latestUserMessage) {
        String safeInput = StrUtil.trimToEmpty(latestUserMessage);
        if (safeInput.isBlank()) {
            return "blank_input";
        }
        if (safeInput.length() > 300) {
            return "input_too_long";
        }
        if (URL_PATTERN.matcher(safeInput).find()) {
            return "contains_url";
        }
        if (!ragProperties.getQueryExpansion().isSkipCodeLikeInput()) {
            return null;
        }
        if (safeInput.contains("```")) {
            return "code_block_input";
        }
        if (HTML_PATTERN.matcher(safeInput).find()
                || JS_PATTERN.matcher(safeInput).find()
                || CSS_PATTERN.matcher(safeInput).find()) {
            return "code_like_input";
        }
        return null;
    }

    private String buildExpansionRequest(String latestUserMessage,
                                         List<ChatHistory> recentHistories,
                                         CodeGenTypeEnum codeGenType,
                                         String baseRetrievalQuery,
                                         String memorySummary,
                                         boolean directSearchOnly) {
        String historySummary = RagPromptSupport.buildHistorySummary(recentHistories);
        return """
                task_mode: %s
                code_gen_type: %s
                base_retrieval_query:
                %s

                latest_user_message:
                %s

                recent_histories:
                %s

                persistent_memory_summary:
                %s
                """.formatted(
                directSearchOnly ? "direct_search_only" : "user_request",
                getCodeGenTypeValue(codeGenType),
                baseRetrievalQuery,
                StrUtil.blankToDefault(latestUserMessage, ""),
                historySummary.isBlank() ? "无" : historySummary,
                StrUtil.blankToDefault(memorySummary, "无")
        ).trim();
    }

    private String sanitizeRetrievalQuery(String candidate, String baseRetrievalQuery) {
        String cleaned = StrUtil.trimToEmpty(candidate);
        if (cleaned.isBlank()) {
            return baseRetrievalQuery;
        }
        cleaned = MARKDOWN_FENCE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = LEADING_LABEL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEADING_NUMBERING_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("[\"'`“”]", " ");
        cleaned = cleaned.replace('\r', ' ').replace('\n', ' ');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank()) {
            return baseRetrievalQuery;
        }
        return truncate(cleaned, ragProperties.getQueryExpansion().getMaxRetrievalQueryChars());
    }

    private String sanitizeRewrittenPrompt(String candidate, String latestUserMessage) {
        String cleaned = StrUtil.trimToEmpty(candidate);
        if (cleaned.isBlank()) {
            return "";
        }
        cleaned = MARKDOWN_FENCE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = MARKDOWN_HEADING_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEADING_LABEL_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEADING_NUMBERING_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace('\r', '\n');
        cleaned = cleaned.replaceAll("[\"`“”]", "");
        cleaned = MULTI_BLANK_LINES_PATTERN.matcher(cleaned).replaceAll("\n\n");
        cleaned = cleaned.replaceAll("[ \\t]+", " ").trim();
        if (cleaned.isBlank()) {
            return "";
        }
        String truncated = truncate(cleaned, ragProperties.getQueryExpansion().getMaxRewrittenPromptChars());
        return StrUtil.equals(truncated, StrUtil.trimToEmpty(latestUserMessage)) ? "" : truncated;
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).trim();
    }

    private void cacheToInvocationContext(RetrievalPromptExpansionOutcome outcome) {
        RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
        if (invocationContext == null || outcome == null) {
            return;
        }
        RagInvocationContext.setCurrent(invocationContext.toBuilder()
                .expandedRetrievalQuery(outcome.getRetrievalQuery())
                .rewrittenUserPrompt(outcome.getRewrittenUserPrompt())
                .queryExpansionApplied(outcome.isExpansionApplied())
                .build());
    }

    private void logOutcome(CodeGenTypeEnum codeGenType,
                            String baseRetrievalQuery,
                            RetrievalPromptExpansionOutcome outcome) {
        int rewrittenPromptHash = StrUtil.isBlank(outcome.getRewrittenUserPrompt())
                ? 0
                : outcome.getRewrittenUserPrompt().hashCode();
        log.info("RAG 输入增强完成，originalQueryHash={}, expandedQueryHash={}, rewrittenPromptHash={}, codeGenType={}, expansionTriggered={}, expansionApplied={}, fallbackReason={}, expansionModel=routingChatModel",
                baseRetrievalQuery.hashCode(),
                StrUtil.blankToDefault(outcome.getRetrievalQuery(), "").hashCode(),
                rewrittenPromptHash,
                getCodeGenTypeValue(codeGenType),
                outcome.isExpansionTriggered(),
                outcome.isExpansionApplied(),
                StrUtil.blankToDefault(outcome.getFallbackReason(), "none"));
    }

    private String getCodeGenTypeValue(CodeGenTypeEnum codeGenType) {
        return codeGenType == null ? "unknown" : codeGenType.getValue();
    }

    private void logExpansionFailure(String baseRetrievalQuery, CodeGenTypeEnum codeGenType, Throwable throwable) {
        if (throwable.getStackTrace().length == 0) {
            log.warn("RAG 输入增强执行失败，originalQueryHash={}, codeGenType={}, errorType={}, errorMessage={}",
                    baseRetrievalQuery.hashCode(),
                    getCodeGenTypeValue(codeGenType),
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage());
            return;
        }
        log.warn("RAG 输入增强执行失败，originalQueryHash={}, codeGenType={}",
                baseRetrievalQuery.hashCode(), getCodeGenTypeValue(codeGenType), throwable);
    }
}
