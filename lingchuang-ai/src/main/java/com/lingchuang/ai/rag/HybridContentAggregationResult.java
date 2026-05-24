package com.lingchuang.ai.rag;

import com.lingchuang.ai.rag.model.RetrievedChunk;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 聚合后的阶段性检索结果。
 */
@Getter
@Builder
public class HybridContentAggregationResult {

    @Builder.Default
    private final List<RetrievedChunk> fusedResults = List.of();

    @Builder.Default
    private final List<RetrievedChunk> rerankedResults = List.of();
}
