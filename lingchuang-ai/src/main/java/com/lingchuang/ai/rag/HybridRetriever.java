package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 混合检索器，组合 BM25、Dense、融合与 Rerank。
 */
@Service
@Slf4j
public class HybridRetriever implements KnowledgeSearchService {

    private final RagProperties ragProperties;
    private final KnowledgeIndexService knowledgeIndexService;
    private final HybridContentAggregator hybridContentAggregator;
    private final RetrievedChunkContentMapper retrievedChunkContentMapper;

    private final Cache<String, HybridRetrievalResult> searchCache = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public HybridRetriever(RagProperties ragProperties,
                           KnowledgeIndexService knowledgeIndexService,
                           HybridContentAggregator hybridContentAggregator,
                           RetrievedChunkContentMapper retrievedChunkContentMapper) {
        this.ragProperties = ragProperties;
        this.knowledgeIndexService = knowledgeIndexService;
        this.hybridContentAggregator = hybridContentAggregator;
        this.retrievedChunkContentMapper = retrievedChunkContentMapper;
    }

    @Override
    public HybridRetrievalResult search(String query, CodeGenTypeEnum codeGenType, int maxResults) {
        String cacheKey = codeGenType.getValue() + ":" + maxResults + ":" + query.hashCode();
        HybridRetrievalResult cachedResult = searchCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        HybridRetrievalResult retrievalResult = doSearch(query, codeGenType, maxResults);
        searchCache.put(cacheKey, retrievalResult);
        return retrievalResult;
    }

    private HybridRetrievalResult doSearch(String query, CodeGenTypeEnum codeGenType, int maxResults) {
        int bm25TopK = Math.max(1, ragProperties.getRetrieve().getBm25TopK());
        int denseTopK = Math.max(1, ragProperties.getRetrieve().getDenseTopK());
        int fusedTopK = Math.max(1, ragProperties.getRetrieve().getFusedTopK());
        int finalTopK = Math.max(1, Math.min(maxResults, ragProperties.getRetrieve().getFinalTopK()));

        Query ragQuery = Query.from(query);
        List<Content> bm25Contents = retrieveSafely(
                knowledgeIndexService.createBm25ContentRetriever(codeGenType, bm25TopK),
                ragQuery,
                "BM25"
        );
        List<Content> denseContents = retrieveSafely(
                knowledgeIndexService.createDenseContentRetriever(codeGenType, denseTopK),
                ragQuery,
                "Dense"
        );
        List<RetrievedChunk> bm25Results = retrievedChunkContentMapper.toRetrievedChunks(
                bm25Contents,
                HybridRetrievalRouteMetadata.ROUTE_BM25
        );
        List<RetrievedChunk> denseResults = retrievedChunkContentMapper.toRetrievedChunks(
                denseContents,
                HybridRetrievalRouteMetadata.ROUTE_DENSE
        );
        if (CollUtil.isEmpty(bm25Results) && CollUtil.isEmpty(denseResults)) {
            return HybridRetrievalResult.builder().query(query).build();
        }

        HybridContentAggregationResult aggregationResult = hybridContentAggregator.aggregateSearch(
                ragQuery,
                bm25Contents,
                denseContents,
                fusedTopK,
                finalTopK
        );
        List<RetrievedChunk> rerankedResults = aggregationResult.getRerankedResults();
        log.info("RAG 检索链路完成，queryHash={}, finalHits={}", query.hashCode(), rerankedResults.size());
        return HybridRetrievalResult.builder()
                .query(query)
                .bm25Results(bm25Results)
                .denseResults(denseResults)
                .fusedResults(aggregationResult.getFusedResults())
                .rerankedResults(rerankedResults)
                .build();
    }

    private List<Content> retrieveSafely(ContentRetriever contentRetriever, Query query, String routeLabel) {
        try {
            return contentRetriever.retrieve(query);
        } catch (Exception e) {
            if (e.getStackTrace().length == 0) {
                log.warn("{} 检索失败，query={}, errorType={}, errorMessage={}",
                        routeLabel, query.text(), e.getClass().getSimpleName(), e.getMessage());
            } else {
                log.warn("{} 检索失败，query={}", routeLabel, query.text(), e);
            }
            return List.of();
        }
    }
}
