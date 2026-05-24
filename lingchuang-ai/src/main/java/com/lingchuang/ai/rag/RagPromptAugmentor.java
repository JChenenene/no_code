package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG Prompt 增强器。
 */
@Component
@Slf4j
public class RagPromptAugmentor {

    private final RagProperties ragProperties;

    private final KnowledgeSearchService knowledgeSearchService;

    private final RetrievalPromptExpansionService retrievalPromptExpansionService;

    public RagPromptAugmentor(RagProperties ragProperties,
                              KnowledgeSearchService knowledgeSearchService,
                              RetrievalPromptExpansionService retrievalPromptExpansionService) {
        this.ragProperties = ragProperties;
        this.knowledgeSearchService = knowledgeSearchService;
        this.retrievalPromptExpansionService = retrievalPromptExpansionService;
    }

    public String augment(String latestUserMessage, List<ChatHistory> recentHistories, CodeGenTypeEnum codeGenType) {
        if (!ragProperties.isEnabled()) {
            return latestUserMessage;
        }
        RetrievalPromptExpansionOutcome expansionOutcome = retrievalPromptExpansionService.expandForUserRequest(
                latestUserMessage,
                recentHistories,
                codeGenType
        );
        String retrievalQuery = expansionOutcome.getRetrievalQuery();
        String effectiveUserPrompt = StrUtil.blankToDefault(
                expansionOutcome.getRewrittenUserPrompt(),
                latestUserMessage
        );
        HybridRetrievalResult retrievalResult = knowledgeSearchService.search(
                retrievalQuery,
                codeGenType,
                ragProperties.getRetrieve().getFinalTopK()
        );
        if (retrievalResult == null || CollUtil.isEmpty(retrievalResult.getRerankedResults())) {
            log.info("RAG Prompt 增强未命中，queryHash={}, codeGenType={}",
                    retrievalQuery.hashCode(), codeGenType.getValue());
            return effectiveUserPrompt;
        }
        log.info("RAG Prompt 增强命中，queryHash={}, codeGenType={}, rerankedHits={}",
                retrievalQuery.hashCode(), codeGenType.getValue(), retrievalResult.getRerankedResults().size());
        return RagPromptSupport.buildAugmentedPrompt(
                effectiveUserPrompt,
                recentHistories,
                codeGenType,
                retrievalResult.getRerankedResults()
        );
    }
}
