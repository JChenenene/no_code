package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.RetrievedChunk;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG query 与 prompt 组装公共方法。
 */
public final class RagPromptSupport {

    private static final int RECENT_RAW_HISTORY_COUNT = 4;
    private static final int HISTORY_ITEM_MAX_CHARS = 320;

    private RagPromptSupport() {
    }

    public static String buildRetrievalQuery(String latestUserMessage, List<ChatHistory> recentHistories) {
        return buildRetrievalQuery(latestUserMessage, recentHistories, "");
    }

    public static String buildRetrievalQuery(String latestUserMessage,
                                             List<ChatHistory> recentHistories,
                                             String memorySummary) {
        String historySummary = buildHistorySummary(recentHistories);
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(memorySummary)) {
            builder.append("[长期摘要记忆]\n").append(memorySummary).append("\n");
        }
        if (StrUtil.isNotBlank(historySummary)) {
            builder.append(historySummary).append("\n");
        }
        builder.append(StrUtil.blankToDefault(latestUserMessage, ""));
        return builder.toString().trim();
    }

    public static String buildAugmentedPrompt(String effectiveUserPrompt,
                                              List<ChatHistory> recentHistories,
                                              CodeGenTypeEnum codeGenType,
                                              List<RetrievedChunk> chunks) {
        return buildAugmentedPrompt(effectiveUserPrompt, recentHistories, "", codeGenType, chunks);
    }

    public static String buildAugmentedPrompt(String effectiveUserPrompt,
                                              List<ChatHistory> recentHistories,
                                              String memorySummary,
                                              CodeGenTypeEnum codeGenType,
                                              List<RetrievedChunk> chunks) {
        String resolvedUserPrompt = StrUtil.blankToDefault(effectiveUserPrompt, "");
        if (CollUtil.isEmpty(chunks)) {
            return resolvedUserPrompt;
        }
        String historySummary = buildHistorySummary(recentHistories);
        return """
                任务目标：
                你需要根据用户需求生成可直接使用的页面或项目代码，优先保证结构清晰、视觉风格统一、可部署可预览。

                最终执行需求：
                %s

                近期对话上下文：
                %s

                长期摘要记忆：
                %s

                检索到的相关规范/模板/示例：
                %s

                输出约束：
                1. 优先遵循检索到的规范、模板和示例。
                2. 如果检索信息与用户当前明确需求冲突，以用户当前需求为准。
                3. 输出必须完整、可运行、与 %s 模式相匹配。
                4. 避免输出与品牌无关的角色、署名和课程化表述。
                """.formatted(
                resolvedUserPrompt,
                historySummary.isBlank() ? "无" : historySummary,
                StrUtil.blankToDefault(memorySummary, "无"),
                formatRetrievedChunks(chunks),
                codeGenType == null ? "unknown" : codeGenType.getValue()
        ).trim();
    }

    public static String buildHistorySummary(List<ChatHistory> recentHistories) {
        if (CollUtil.isEmpty(recentHistories)) {
            return "";
        }
        int compactBoundary = Math.max(0, recentHistories.size() - RECENT_RAW_HISTORY_COUNT);
        String recentRawHistory = recentHistories.stream()
                .skip(compactBoundary)
                .map(history -> {
                    String role = ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType()) ? "AI" : "USER";
                    return role + ": " + truncate(StrUtil.blankToDefault(history.getMessage(), ""), HISTORY_ITEM_MAX_CHARS);
                })
                .collect(Collectors.joining("\n"));
        if (compactBoundary == 0) {
            return recentRawHistory;
        }
        long compactedTurns = Math.max(1, compactBoundary / 2);
        String compactedSummary = recentHistories.stream()
                .limit(compactBoundary)
                .map(history -> {
                    String role = ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType()) ? "AI" : "USER";
                    return role + ": " + truncate(StrUtil.blankToDefault(history.getMessage(), ""), 80);
                })
                .collect(Collectors.joining("；"));
        return """
                [历史摘要] 已压缩 %d 轮较早对话：%s
                [最近原文]
                %s
                """.formatted(compactedTurns, compactedSummary, recentRawHistory).trim();
    }

    public static String formatRetrievedChunks(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> """
                        - 标题：%s
                          来源：%s
                          路径：%s
                          片段：%s
                        """.formatted(
                        chunk.getTitle(),
                        StrUtil.blankToDefault(chunk.getSourceType(), "unknown"),
                        StrUtil.blankToDefault(chunk.getPath(), "unknown"),
                        chunk.getContent()
                ).trim())
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(String text, int maxLength) {
        if (StrUtil.isBlank(text) || text.length() <= maxLength) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxLength) + "...（已压缩）";
    }
}
