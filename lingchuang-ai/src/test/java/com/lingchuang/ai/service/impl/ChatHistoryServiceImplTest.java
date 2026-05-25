package com.lingchuang.ai.service.impl;

import com.lingchuang.ai.mapper.ChatHistoryMapper;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.service.AppChatSummaryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceImplTest {

    private ChatHistoryMapper chatHistoryMapper;
    private AppChatSummaryService appChatSummaryService;
    private ChatHistoryServiceImpl chatHistoryService;

    @BeforeEach
    void setUp() {
        chatHistoryMapper = mock(ChatHistoryMapper.class);
        appChatSummaryService = mock(AppChatSummaryService.class);
        chatHistoryService = new ChatHistoryServiceImpl();
        setMapper(chatHistoryService, chatHistoryMapper);
        setField(chatHistoryService, "appChatSummaryService", appChatSummaryService);
    }

    @Test
    void shouldRefreshSummaryAfterSavingMessage() {
        when(chatHistoryMapper.insert(any(ChatHistory.class), eq(true))).thenReturn(1);

        boolean saved = chatHistoryService.addChatMessage(
                1001L,
                "生成一个小范自我介绍页面",
                ChatHistoryMessageTypeEnum.USER.getValue(),
                2002L
        );

        assertTrue(saved);
        verify(appChatSummaryService).refreshConversationSummary(1001L, 2002L);
    }

    @Test
    void shouldLoadPersistentSummaryAndRecentRawHistoriesToMemory() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        when(appChatSummaryService.getLatestSummaryText(eq(1001L), isNull()))
                .thenReturn("用户要做小范自我介绍页，偏极简风格");
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                history(ChatHistoryMessageTypeEnum.AI.getValue(), "已生成基础页面"),
                history(ChatHistoryMessageTypeEnum.USER.getValue(), "继续优化")
        ));

        int loadedCount = chatHistoryService.loadChatHistoryToMemory(1001L, chatMemory, 20);

        List<ChatMessage> messages = chatMemory.messages();
        assertEquals(3, loadedCount);
        assertEquals(ChatMessageType.SYSTEM, messages.get(0).type());
        assertTrue(messages.get(0).toString().contains("小范自我介绍页"));
        assertEquals(ChatMessageType.USER, messages.get(1).type());
        assertEquals(ChatMessageType.AI, messages.get(2).type());
    }

    private ChatHistory history(String messageType, String message) {
        return ChatHistory.builder()
                .messageType(messageType)
                .message(message)
                .build();
    }

    private void setMapper(ChatHistoryServiceImpl service, ChatHistoryMapper mapper) {
        setField(service, "mapper", mapper, ServiceImpl.class);
    }

    private void setField(Object target, String fieldName, Object value) {
        setField(target, fieldName, value, target.getClass());
    }

    private void setField(Object target, String fieldName, Object value, Class<?> declaringClass) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("注入测试字段失败: " + fieldName, e);
        }
    }
}
