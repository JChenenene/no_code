package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromptSupportTest {

    @Test
    void shouldCompactOldHistoryAndKeepRecentMessages() {
        List<ChatHistory> histories = List.of(
                history(ChatHistoryMessageTypeEnum.USER.getValue(), "旧需求一".repeat(100)),
                history(ChatHistoryMessageTypeEnum.AI.getValue(), "旧回复一".repeat(100)),
                history(ChatHistoryMessageTypeEnum.USER.getValue(), "旧需求二".repeat(100)),
                history(ChatHistoryMessageTypeEnum.AI.getValue(), "旧回复二".repeat(100)),
                history(ChatHistoryMessageTypeEnum.USER.getValue(), "最近用户需求"),
                history(ChatHistoryMessageTypeEnum.AI.getValue(), "最近 AI 回复")
        );

        String summary = RagPromptSupport.buildHistorySummary(histories);

        assertTrue(summary.contains("[历史摘要]"));
        assertTrue(summary.contains("已压缩 1 轮较早对话"));
        assertTrue(summary.contains("USER: 最近用户需求"));
        assertTrue(summary.contains("AI: 最近 AI 回复"));
        assertFalse(summary.contains("旧需求一".repeat(90)));
    }

    @Test
    void shouldIncludePersistentMemorySummaryInRetrievalQuery() {
        String query = RagPromptSupport.buildRetrievalQuery(
                "继续生成页面",
                List.of(history(ChatHistoryMessageTypeEnum.USER.getValue(), "最近想法")),
                "用户偏好：主角叫小范，页面极简"
        );

        assertTrue(query.contains("[长期摘要记忆]"));
        assertTrue(query.contains("主角叫小范"));
        assertTrue(query.contains("USER: 最近想法"));
        assertTrue(query.contains("继续生成页面"));
    }

    private ChatHistory history(String messageType, String message) {
        return ChatHistory.builder()
                .messageType(messageType)
                .message(message)
                .build();
    }
}
