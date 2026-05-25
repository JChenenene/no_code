package com.lingchuang.ai.service.impl;

import com.lingchuang.ai.mapper.AppChatSummaryMapper;
import com.lingchuang.ai.mapper.ChatHistoryMapper;
import com.lingchuang.ai.model.entity.AppChatSummary;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppChatSummaryServiceImplTest {

    private AppChatSummaryMapper appChatSummaryMapper;
    private ChatHistoryMapper chatHistoryMapper;
    private AppChatSummaryServiceImpl appChatSummaryService;

    @BeforeEach
    void setUp() {
        appChatSummaryMapper = mock(AppChatSummaryMapper.class);
        chatHistoryMapper = mock(ChatHistoryMapper.class);
        appChatSummaryService = new AppChatSummaryServiceImpl(chatHistoryMapper);
        setMapper(appChatSummaryService, appChatSummaryMapper);
    }

    @Test
    void shouldCreateConversationSummaryFromRecentHistories() {
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                history(2L, ChatHistoryMessageTypeEnum.AI.getValue(), "好的，我会生成小范自我介绍页面"),
                history(1L, ChatHistoryMessageTypeEnum.USER.getValue(), "生成一个叫小范的自我介绍页面")
        ));
        when(appChatSummaryMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(appChatSummaryMapper.insert(any(AppChatSummary.class), eq(true))).thenReturn(1);

        AppChatSummary summary = appChatSummaryService.refreshConversationSummary(1001L, 2002L);

        assertNotNull(summary);
        assertEquals(1001L, summary.getAppId());
        assertEquals(2002L, summary.getUserId());
        assertEquals("conversation", summary.getSummaryType());
        assertEquals("1,2", summary.getCoveredMessageIds());
        assertTrue(summary.getSummaryText().contains("小范"));
        assertTrue(summary.getTokenEstimate() > 0);
        verify(appChatSummaryMapper).insert(any(AppChatSummary.class), eq(true));
    }

    @Test
    void shouldUpdateExistingConversationSummary() {
        AppChatSummary existing = AppChatSummary.builder()
                .id(9001L)
                .appId(1001L)
                .userId(2002L)
                .summaryType("conversation")
                .summaryText("旧摘要")
                .build();
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                history(3L, ChatHistoryMessageTypeEnum.USER.getValue(), "继续优化成极简风格")
        ));
        when(appChatSummaryMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existing);
        when(appChatSummaryMapper.update(any(AppChatSummary.class), eq(true))).thenReturn(1);

        AppChatSummary summary = appChatSummaryService.refreshConversationSummary(1001L, 2002L);

        assertEquals(9001L, summary.getId());
        assertEquals("3", summary.getCoveredMessageIds());
        assertTrue(summary.getSummaryText().contains("极简风格"));
        verify(appChatSummaryMapper).update(any(AppChatSummary.class), eq(true));
    }

    private ChatHistory history(Long id, String messageType, String message) {
        return ChatHistory.builder()
                .id(id)
                .messageType(messageType)
                .message(message)
                .build();
    }

    private void setMapper(AppChatSummaryServiceImpl service, AppChatSummaryMapper mapper) {
        try {
            Field mapperField = ServiceImpl.class.getDeclaredField("mapper");
            mapperField.setAccessible(true);
            mapperField.set(service, mapper);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("注入测试 Mapper 失败", e);
        }
    }
}
