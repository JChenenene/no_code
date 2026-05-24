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

    private RagPromptSupport() {
    }

    public static String buildRetrievalQuery(String latestUserMessage, List<ChatHistory> recentHistories) {
        String historySummary = buildHistorySummary(recentHistories);
        if (historySummary.isBlank()) {
            return StrUtil.blankToDefault(latestUserMessage, "");
        }
        return historySummary + "\n" + StrUtil.blankToDefault(latestUserMessage, "");
    }

    public static String buildAugmentedPrompt(String effectiveUserPrompt,
                                              List<ChatHistory> recentHistories,
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
                formatRetrievedChunks(chunks),
                codeGenType == null ? "unknown" : codeGenType.getValue()
        ).trim();
    }

    public static String buildHistorySummary(List<ChatHistory> recentHistories) {
        if (CollUtil.isEmpty(recentHistories)) {
            return "";
        }
        return recentHistories.stream()
                .map(history -> {
                    String role = ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType()) ? "AI" : "USER";
                    return role + ": " + StrUtil.blankToDefault(history.getMessage(), "");
                })
                .collect(Collectors.joining("\n"));
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
}
