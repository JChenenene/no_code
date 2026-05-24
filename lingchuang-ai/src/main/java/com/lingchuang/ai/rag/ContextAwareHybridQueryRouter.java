package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定双路 fan-out 的 query router。
 */
@Component
@Slf4j
public class ContextAwareHybridQueryRouter implements QueryRouter {

    private final KnowledgeIndexService knowledgeIndexService;
    private final RagProperties ragProperties;
    private final RetrievedChunkContentMapper retrievedChunkContentMapper;

    public ContextAwareHybridQueryRouter(KnowledgeIndexService knowledgeIndexService,
                                         RagProperties ragProperties,
                                         RetrievedChunkContentMapper retrievedChunkContentMapper) {
        this.knowledgeIndexService = knowledgeIndexService;
        this.ragProperties = ragProperties;
        this.retrievedChunkContentMapper = retrievedChunkContentMapper;
    }

    @Override
    public List<ContentRetriever> route(Query query) {
        RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
        if (!ragProperties.isEnabled()
                || invocationContext == null
                || invocationContext.getCodeGenType() == null
                || StrUtil.isBlank(query.text())) {
            return List.of();
        }
        CodeGenTypeEnum codeGenType = invocationContext.getCodeGenType();
        int bm25TopK = Math.max(1, ragProperties.getRetrieve().getBm25TopK());
        int denseTopK = Math.max(1, ragProperties.getRetrieve().getDenseTopK());
        return List.of(
                new SafeRouteContentRetriever(
                        "BM25",
                        HybridRetrievalRouteMetadata.ROUTE_BM25,
                        knowledgeIndexService.createBm25ContentRetriever(codeGenType, bm25TopK)
                ),
                new SafeRouteContentRetriever(
                        "Dense",
                        HybridRetrievalRouteMetadata.ROUTE_DENSE,
                        knowledgeIndexService.createDenseContentRetriever(codeGenType, denseTopK)
                )
        );
    }

    private class SafeRouteContentRetriever implements ContentRetriever {

        private final String routeLabel;
        private final String routeName;
        private final ContentRetriever delegate;

        private SafeRouteContentRetriever(String routeLabel, String routeName, ContentRetriever delegate) {
            this.routeLabel = routeLabel;
            this.routeName = routeName;
            this.delegate = delegate;
        }

        @Override
        public List<Content> retrieve(Query query) {
            try {
                return delegate.retrieve(query).stream()
                        .map(content -> retrievedChunkContentMapper.tagRoute(content, routeName))
                        .toList();
            } catch (Exception e) {
                log.warn("{} 检索失败，query={}", routeLabel, query.text(), e);
                return List.of();
            }
        }
    }
}
