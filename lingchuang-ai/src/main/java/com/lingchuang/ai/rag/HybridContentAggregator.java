package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 双路内容聚合器，保留 RRF 融合与 rerank 回退语义。
 */
@Component
@Slf4j
public class HybridContentAggregator implements ContentAggregator {

    private final RagProperties ragProperties;
    private final RerankService rerankService;
    private final RetrievedChunkContentMapper retrievedChunkContentMapper;

    public HybridContentAggregator(RagProperties ragProperties,
                                   RerankService rerankService,
                                   RetrievedChunkContentMapper retrievedChunkContentMapper) {
        this.ragProperties = ragProperties;
        this.rerankService = rerankService;
        this.retrievedChunkContentMapper = retrievedChunkContentMapper;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        if (queryToContents == null || queryToContents.isEmpty()) {
            return List.of();
        }
        Map.Entry<Query, Collection<List<Content>>> firstEntry = queryToContents.entrySet().iterator().next();
        Query query = firstEntry.getKey();
        List<Content> bm25Contents = List.of();
        List<Content> denseContents = List.of();
        for (List<Content> contents : firstEntry.getValue()) {
            String route = resolveRoute(contents);
            if (HybridRetrievalRouteMetadata.ROUTE_BM25.equals(route)) {
                bm25Contents = contents;
            } else if (HybridRetrievalRouteMetadata.ROUTE_DENSE.equals(route)) {
                denseContents = contents;
            }
        }
        HybridContentAggregationResult aggregationResult = aggregateSearch(
                query,
                bm25Contents,
                denseContents,
                Math.max(1, ragProperties.getRetrieve().getFusedTopK()),
                Math.max(1, ragProperties.getRetrieve().getFinalTopK())
        );
        return retrievedChunkContentMapper.toContents(aggregationResult.getRerankedResults());
    }

    public HybridContentAggregationResult aggregateSearch(Query query,
                                                          List<Content> bm25Contents,
                                                          List<Content> denseContents,
                                                          int fusedTopK,
                                                          int finalTopK) {
        List<RetrievedChunk> bm25Results = retrievedChunkContentMapper.toRetrievedChunks(
                bm25Contents == null ? List.of() : bm25Contents,
                HybridRetrievalRouteMetadata.ROUTE_BM25
        );
        List<RetrievedChunk> denseResults = retrievedChunkContentMapper.toRetrievedChunks(
                denseContents == null ? List.of() : denseContents,
                HybridRetrievalRouteMetadata.ROUTE_DENSE
        );
        if (CollUtil.isEmpty(bm25Results) && CollUtil.isEmpty(denseResults)) {
            return HybridContentAggregationResult.builder().build();
        }

        List<RetrievedChunk> fusedResults;
        if (CollUtil.isEmpty(bm25Results)) {
            fusedResults = limitResults(denseResults, fusedTopK);
        } else if (CollUtil.isEmpty(denseResults)) {
            fusedResults = limitResults(bm25Results, fusedTopK);
        } else {
            fusedResults = WeightedRrfFusion.fuse(bm25Results, denseResults, fusedTopK);
        }
        log.info("RAG 融合完成，queryHash={}, bm25Hits={}, denseHits={}, fusedHits={}",
                query.text().hashCode(), bm25Results.size(), denseResults.size(), fusedResults.size());

        List<RetrievedChunk> rerankedResults;
        try {
            rerankedResults = rerankService.rerank(query.text(), fusedResults, finalTopK);
            if (CollUtil.isEmpty(rerankedResults)) {
                rerankedResults = limitResults(fusedResults, finalTopK);
            }
        } catch (Exception e) {
            log.warn("Rerank 失败，query={}", query.text(), e);
            rerankedResults = limitResults(fusedResults, finalTopK);
        }
        return HybridContentAggregationResult.builder()
                .fusedResults(fusedResults)
                .rerankedResults(rerankedResults)
                .build();
    }

    private String resolveRoute(List<Content> contents) {
        if (CollUtil.isEmpty(contents)) {
            return "";
        }
        return contents.getFirst().textSegment().metadata().getString(HybridRetrievalRouteMetadata.RETRIEVAL_ROUTE);
    }

    private List<RetrievedChunk> limitResults(List<RetrievedChunk> results, int limit) {
        if (results.size() <= limit) {
            return results;
        }
        return new ArrayList<>(results.subList(0, limit));
    }
}
