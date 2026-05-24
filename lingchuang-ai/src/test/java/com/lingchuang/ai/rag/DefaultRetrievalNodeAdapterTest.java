package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class DefaultRetrievalNodeAdapterTest {

    @Test
    void shouldSearchWithExpandedQueryForDirectRetrieval() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getRetrieve().setFinalTopK(5);
        AtomicReference<String> searchedQuery = new AtomicReference<>();
        KnowledgeSearchService knowledgeSearchService = (query, codeGenType, limit) -> {
            searchedQuery.set(query);
            return HybridRetrievalResult.builder()
                    .rerankedResults(List.of(RetrievedChunk.builder().chunkId("chunk-1").build()))
                    .build();
        };
        RetrievalPromptExpansionService expansionService = new RetrievalPromptExpansionService(
                ragProperties,
                new RetrievalPromptExpansionAiServiceFactory()
        ) {
            @Override
            public RetrievalPromptExpansionOutcome expandForDirectSearch(String rawQuery, CodeGenTypeEnum codeGenType) {
                return RetrievalPromptExpansionOutcome.builder()
                        .retrievalQuery("高端科技感 AI 官网 模板")
                        .rewrittenUserPrompt(null)
                        .expansionTriggered(true)
                        .expansionApplied(true)
                        .fallbackReason("none")
                        .build();
            }
        };

        DefaultRetrievalNodeAdapter adapter =
                new DefaultRetrievalNodeAdapter(knowledgeSearchService, ragProperties, expansionService);
        List<RetrievedChunk> results = adapter.retrieveForNode("高端科技感官网", CodeGenTypeEnum.HTML);

        Assertions.assertEquals("高端科技感 AI 官网 模板", searchedQuery.get());
        Assertions.assertEquals(1, results.size());
    }
}
