package com.lingchuang.ai.service;

import com.lingchuang.ai.model.entity.AppChatSummary;
import com.mybatisflex.core.service.IService;

/**
 * 应用对话摘要服务。
 */
public interface AppChatSummaryService extends IService<AppChatSummary> {

    String CONVERSATION_SUMMARY_TYPE = "conversation";

    /**
     * 刷新应用对话摘要。
     */
    AppChatSummary refreshConversationSummary(Long appId, Long userId);

    /**
     * 获取最新可用摘要。
     */
    String getLatestSummaryText(Long appId, Long userId);
}
