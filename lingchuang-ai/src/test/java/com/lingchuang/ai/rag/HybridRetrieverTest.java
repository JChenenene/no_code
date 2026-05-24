package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock
    private KnowledgeIndexService knowledgeIndexService;

    @Mock
    private HybridContentAggregator hybridContentAggregator;

    private final RetrievedChunkContentMapper retrievedChunkContentMapper = new RetrievedChunkContentMapper();

    @Test
    void shouldFuseAndRerankResults() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getRetrieve().setBm25TopK(10);
        ragProperties.getRetrieve().setDenseTopK(10);
        ragProperties.getRetrieve().setFinalTopK(10);
        ragProperties.getRetrieve().setFusedTopK(12);

        HybridRetriever hybridRetriever = new HybridRetriever(
                ragProperties,
                knowledgeIndexService,
                hybridContentAggregator,
                retrievedChunkContentMapper
        );

        List<RetrievedChunk> bm25Results = List.of(
                RetrievedChunk.builder().chunkId("A").title("A").content("A").score(0.8).scoreSource("bm25").build(),
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.7).scoreSource("bm25").build()
        );
        List<RetrievedChunk> denseResults = List.of(
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.95).scoreSource("dense").build(),
                RetrievedChunk.builder().chunkId("C").title("C").content("C").score(0.92).scoreSource("dense").build()
        );
        List<RetrievedChunk> fusedResults = WeightedRrfFusion.fuse(bm25Results, denseResults, 12);
        List<RetrievedChunk> rerankedResults = List.of(
                RetrievedChunk.builder().chunkId("C").title("C").content("C").score(0.99).scoreSource("rerank").build(),
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.98).scoreSource("rerank").build()
        );

        ContentRetriever bm25Retriever = query -> retrievedChunkContentMapper.toContents(bm25Results);
        ContentRetriever denseRetriever = query -> retrievedChunkContentMapper.toContents(denseResults);

        when(knowledgeIndexService.createBm25ContentRetriever(CodeGenTypeEnum.HTML, 10)).thenReturn(bm25Retriever);
        when(knowledgeIndexService.createDenseContentRetriever(CodeGenTypeEnum.HTML, 10)).thenReturn(denseRetriever);
        when(hybridContentAggregator.aggregateSearch(any(), any(), any(), eq(12), eq(10)))
                .thenReturn(HybridContentAggregationResult.builder()
                        .fusedResults(fusedResults)
                        .rerankedResults(rerankedResults)
                        .build());

        HybridRetrievalResult result = hybridRetriever.search("高端科技感官网", CodeGenTypeEnum.HTML, 10);

        Assertions.assertEquals(List.of("A", "B"),
                result.getBm25Results().stream().map(RetrievedChunk::getChunkId).toList());
        Assertions.assertEquals(List.of("B", "C"),
                result.getDenseResults().stream().map(RetrievedChunk::getChunkId).toList());
        Assertions.assertEquals(List.of("C", "B"),
                result.getRerankedResults().stream().map(RetrievedChunk::getChunkId).toList());
        Assertions.assertEquals(List.of("B", "A", "C"),
                result.getFusedResults().stream().map(RetrievedChunk::getChunkId).toList());
    }

    @Test
    void shouldFallbackToBm25WhenDenseRouteFails() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getRetrieve().setBm25TopK(5);
        ragProperties.getRetrieve().setDenseTopK(5);
        ragProperties.getRetrieve().setFusedTopK(12);
        ragProperties.getRetrieve().setFinalTopK(5);

        HybridRetriever hybridRetriever = new HybridRetriever(
                ragProperties,
                knowledgeIndexService,
                hybridContentAggregator,
                retrievedChunkContentMapper
        );

        List<RetrievedChunk> bm25Results = List.of(
                RetrievedChunk.builder().chunkId("A").title("A").content("A").score(0.8).scoreSource("bm25").build(),
                RetrievedChunk.builder().chunkId("B").title("B").content("B").score(0.7).scoreSource("bm25").build()
        );

        when(knowledgeIndexService.createBm25ContentRetriever(CodeGenTypeEnum.HTML, 5))
                .thenReturn(query -> retrievedChunkContentMapper.toContents(bm25Results));
        when(knowledgeIndexService.createDenseContentRetriever(CodeGenTypeEnum.HTML, 5))
                .thenReturn(query -> {
                    throw new TestRouteUnavailableException("dense unavailable");
                });
        when(hybridContentAggregator.aggregateSearch(any(), any(), any(), eq(12), eq(5)))
                .thenReturn(HybridContentAggregationResult.builder()
                        .fusedResults(bm25Results)
                        .rerankedResults(bm25Results)
                        .build());

        HybridRetrievalResult result = hybridRetriever.search("高端科技感官网", CodeGenTypeEnum.HTML, 5);

        Assertions.assertEquals(List.of("A", "B"),
                result.getRerankedResults().stream().map(RetrievedChunk::getChunkId).toList());
        Assertions.assertEquals(List.of(), result.getDenseResults());
    }

    private static class TestRouteUnavailableException extends RuntimeException {

        TestRouteUnavailableException(String message) {
            super(message, null, false, false);
        }
    }
}
