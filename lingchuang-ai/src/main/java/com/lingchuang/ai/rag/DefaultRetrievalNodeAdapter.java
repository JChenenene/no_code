package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认节点检索适配器。
 */
@Component
public class DefaultRetrievalNodeAdapter implements RetrievalNodeAdapter {

    private final KnowledgeSearchService knowledgeSearchService;

    private final RagProperties ragProperties;

    private final RetrievalPromptExpansionService retrievalPromptExpansionService;

    public DefaultRetrievalNodeAdapter(KnowledgeSearchService knowledgeSearchService,
                                       RagProperties ragProperties,
                                       RetrievalPromptExpansionService retrievalPromptExpansionService) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.ragProperties = ragProperties;
        this.retrievalPromptExpansionService = retrievalPromptExpansionService;
    }

    @Override
    public List<RetrievedChunk> retrieveForNode(String query, CodeGenTypeEnum codeGenType) {
        RetrievalPromptExpansionOutcome expansionOutcome =
                retrievalPromptExpansionService.expandForDirectSearch(query, codeGenType);
        return knowledgeSearchService.search(
                        expansionOutcome.getRetrievalQuery(),
                        codeGenType,
                        ragProperties.getRetrieve().getFinalTopK()
                )
                .getRerankedResults();
    }
}
