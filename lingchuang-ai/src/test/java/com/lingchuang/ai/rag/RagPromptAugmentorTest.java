package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class RagPromptAugmentorTest {

    @Test
    void shouldBuildStructuredPromptWithRewrittenUserPrompt() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        ragProperties.getRetrieve().setFinalTopK(5);

        AtomicReference<String> searchedQuery = new AtomicReference<>();
        KnowledgeSearchService knowledgeSearchService = (query, codeGenType, limit) -> {
            searchedQuery.set(query);
            return HybridRetrievalResult.builder()
                    .query(query)
                    .rerankedResults(List.of(
                            RetrievedChunk.builder()
                                    .chunkId("chunk-1")
                                    .title("首屏品牌规范")
                                    .content("首屏必须突出品牌名、核心价值和 CTA。")
                                    .sourceType("brand")
                                    .path("knowledge/brand/hero.md")
                                    .score(0.99)
                                    .build()
                    ))
                    .build();
        };
        RetrievalPromptExpansionService expansionService = new StubRetrievalPromptExpansionService(
                ragProperties,
                RetrievalPromptExpansionOutcome.builder()
                        .retrievalQuery("高端科技感 AI 官网 模板 品牌 首屏")
                        .rewrittenUserPrompt("请生成一个高端科技感的 AI 产品官网，首屏突出品牌价值、产品能力和明确 CTA。")
                        .expansionTriggered(true)
                        .expansionApplied(true)
                        .fallbackReason("none")
                        .build()
        );

        RagPromptAugmentor augmentor = new RagPromptAugmentor(ragProperties, knowledgeSearchService, expansionService);
        List<ChatHistory> histories = List.of(
                ChatHistory.builder().messageType(ChatHistoryMessageTypeEnum.USER.getValue()).message("想要高端科技感").build(),
                ChatHistory.builder().messageType(ChatHistoryMessageTypeEnum.AI.getValue()).message("建议突出品牌与产品能力").build()
        );

        String augmentedPrompt = augmentor.augment("请生成一个 AI 产品官网", histories, CodeGenTypeEnum.HTML);

        Assertions.assertEquals("高端科技感 AI 官网 模板 品牌 首屏", searchedQuery.get());
        Assertions.assertTrue(augmentedPrompt.contains("最终执行需求"));
        Assertions.assertTrue(augmentedPrompt.contains("请生成一个高端科技感的 AI 产品官网"));
        Assertions.assertTrue(augmentedPrompt.contains("首屏品牌规范"));
    }

    @Test
    void shouldReturnRewrittenPromptWhenRetrievalReturnsEmpty() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);

        KnowledgeSearchService knowledgeSearchService = (query, codeGenType, limit) -> HybridRetrievalResult.builder()
                .query(query)
                .rerankedResults(List.of())
                .build();
        RetrievalPromptExpansionService expansionService = new StubRetrievalPromptExpansionService(
                ragProperties,
                RetrievalPromptExpansionOutcome.builder()
                        .retrievalQuery("AI 产品官网 品牌 模板")
                        .rewrittenUserPrompt("请生成一个 AI 产品官网，重点体现品牌可信度、产品能力和科技感视觉。")
                        .expansionTriggered(true)
                        .expansionApplied(true)
                        .fallbackReason("none")
                        .build()
        );

        RagPromptAugmentor augmentor = new RagPromptAugmentor(ragProperties, knowledgeSearchService, expansionService);

        String augmentedPrompt = augmentor.augment("请生成一个 AI 产品官网", List.of(), CodeGenTypeEnum.HTML);

        Assertions.assertEquals("请生成一个 AI 产品官网，重点体现品牌可信度、产品能力和科技感视觉。", augmentedPrompt);
    }

    private static final class StubRetrievalPromptExpansionService extends RetrievalPromptExpansionService {

        private final RetrievalPromptExpansionOutcome outcome;

        private StubRetrievalPromptExpansionService(RagProperties ragProperties,
                                                   RetrievalPromptExpansionOutcome outcome) {
            super(ragProperties, new RetrievalPromptExpansionAiServiceFactory());
            this.outcome = outcome;
        }

        @Override
        public RetrievalPromptExpansionOutcome expandForUserRequest(String latestUserMessage,
                                                                    List<ChatHistory> recentHistories,
                                                                    CodeGenTypeEnum codeGenType) {
            return outcome;
        }
    }
}
