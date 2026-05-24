package com.lingchuang.ai.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 混合检索结果。
 */
@Data
@Builder(toBuilder = true)
public class HybridRetrievalResult {

    private String query;

    @Builder.Default
    private List<RetrievedChunk> bm25Results = List.of();

    @Builder.Default
    private List<RetrievedChunk> denseResults = List.of();

    @Builder.Default
    private List<RetrievedChunk> fusedResults = List.of();

    @Builder.Default
    private List<RetrievedChunk> rerankedResults = List.of();
}
