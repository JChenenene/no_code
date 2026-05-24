package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 当前请求的 RAG 调用上下文。
 */
@Getter
@Builder(toBuilder = true)
public class RagInvocationContext {

    private static final ThreadLocal<RagInvocationContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private final Long appId;

    private final CodeGenTypeEnum codeGenType;

    @Builder.Default
    private final List<ChatHistory> recentHistories = List.of();

    private final String expandedRetrievalQuery;

    private final String rewrittenUserPrompt;

    @Builder.Default
    private final boolean queryExpansionApplied = false;

    public static void setCurrent(RagInvocationContext context) {
        if (context == null) {
            clear();
            return;
        }
        CONTEXT_HOLDER.set(context.toBuilder()
                .recentHistories(context.recentHistories == null ? List.of() : List.copyOf(context.recentHistories))
                .build());
    }

    public static RagInvocationContext getCurrent() {
        return CONTEXT_HOLDER.get();
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
