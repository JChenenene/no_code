package com.lingchuang.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.mapper.AppChatSummaryMapper;
import com.lingchuang.ai.mapper.ChatHistoryMapper;
import com.lingchuang.ai.model.entity.AppChatSummary;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.service.AppChatSummaryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应用对话摘要服务实现。
 */
@Service
@RequiredArgsConstructor
public class AppChatSummaryServiceImpl extends ServiceImpl<AppChatSummaryMapper, AppChatSummary>
        implements AppChatSummaryService {

    private static final int SUMMARY_SOURCE_HISTORY_LIMIT = 30;
    private static final int SUMMARY_TEXT_MAX_CHARS = 2400;
    private static final int SUMMARY_ITEM_MAX_CHARS = 180;

    private final ChatHistoryMapper chatHistoryMapper;

    @Override
    public AppChatSummary refreshConversationSummary(Long appId, Long userId) {
        if (appId == null || appId <= 0 || userId == null || userId <= 0) {
            return null;
        }
        List<ChatHistory> histories = loadRecentHistories(appId, userId, SUMMARY_SOURCE_HISTORY_LIMIT);
        if (CollUtil.isEmpty(histories)) {
            return null;
        }
        String summaryText = buildDeterministicSummary(histories);
        String coveredMessageIds = histories.stream()
                .map(ChatHistory::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        AppChatSummary summary = findConversationSummary(appId, userId);
        if (summary == null) {
            summary = AppChatSummary.builder()
                    .appId(appId)
                    .userId(userId)
                    .summaryType(CONVERSATION_SUMMARY_TYPE)
                    .build();
        }
        summary.setSummaryText(summaryText);
        summary.setCoveredMessageIds(coveredMessageIds);
        summary.setTokenEstimate(estimateTokens(summaryText));
        if (summary.getId() == null) {
            this.save(summary);
        } else {
            this.updateById(summary);
        }
        return summary;
    }

    @Override
    public String getLatestSummaryText(Long appId, Long userId) {
        if (appId == null || appId <= 0) {
            return "";
        }
        AppChatSummary summary = findConversationSummary(appId, userId);
        return summary == null ? "" : StrUtil.blankToDefault(summary.getSummaryText(), "");
    }

    private List<ChatHistory> loadRecentHistories(Long appId, Long userId, int limit) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatHistory::getAppId, appId)
                .orderBy(ChatHistory::getCreateTime, false)
                .limit(1, limit);
        if (userId != null && userId > 0) {
            queryWrapper.eq(ChatHistory::getUserId, userId);
        }
        List<ChatHistory> histories = chatHistoryMapper.selectListByQuery(queryWrapper);
        if (CollUtil.isEmpty(histories)) {
            return List.of();
        }
        return histories.stream()
                .sorted(Comparator.comparing(ChatHistory::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ChatHistory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AppChatSummary findConversationSummary(Long appId, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(AppChatSummary::getAppId, appId)
                .eq(AppChatSummary::getSummaryType, CONVERSATION_SUMMARY_TYPE)
                .orderBy(AppChatSummary::getUpdateTime, false)
                .limit(1);
        if (userId != null && userId > 0) {
            queryWrapper.eq(AppChatSummary::getUserId, userId);
        }
        return this.getOne(queryWrapper);
    }

    private String buildDeterministicSummary(List<ChatHistory> histories) {
        String summary = histories.stream()
                .map(history -> {
                    String role = ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType()) ? "AI" : "USER";
                    return role + ": " + truncate(StrUtil.blankToDefault(history.getMessage(), ""), SUMMARY_ITEM_MAX_CHARS);
                })
                .collect(Collectors.joining("\n"));
        return truncate(summary, SUMMARY_TEXT_MAX_CHARS);
    }

    private int estimateTokens(String summaryText) {
        if (StrUtil.isBlank(summaryText)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(summaryText.length() / 4.0));
    }

    private String truncate(String text, int maxLength) {
        if (StrUtil.isBlank(text) || text.length() <= maxLength) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxLength) + "...（已压缩）";
    }
}
