package com.lingchuang.ai.rag;

import lombok.Builder;
import lombok.Getter;

/**
 * 输入增强归一化结果。
 */
@Getter
@Builder
public class RetrievalPromptExpansionOutcome {

    private final String retrievalQuery;

    private final String rewrittenUserPrompt;

    private final boolean expansionTriggered;

    private final boolean expansionApplied;

    private final String fallbackReason;
}
