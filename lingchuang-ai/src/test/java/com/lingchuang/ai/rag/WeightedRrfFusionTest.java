package com.lingchuang.ai.rag;

import com.lingchuang.ai.rag.model.RetrievedChunk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class WeightedRrfFusionTest {

    @Test
    void shouldMergeAndDeduplicateDenseAndBm25Results() {
        List<RetrievedChunk> bm25Results = List.of(
                RetrievedChunk.builder().chunkId("A").title("A").content("A").score(0.9).build(),
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.8).build()
        );
        List<RetrievedChunk> denseResults = List.of(
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.95).build(),
                RetrievedChunk.builder().chunkId("C").title("C").content("C").score(0.92).build()
        );

        List<RetrievedChunk> fusedResults = WeightedRrfFusion.fuse(bm25Results, denseResults, 10);

        Assertions.assertEquals(3, fusedResults.size());
        Assertions.assertEquals("B", fusedResults.get(0).getChunkId());
        Assertions.assertEquals(List.of("B", "A", "C"),
                fusedResults.stream().map(RetrievedChunk::getChunkId).toList());
    }
}
