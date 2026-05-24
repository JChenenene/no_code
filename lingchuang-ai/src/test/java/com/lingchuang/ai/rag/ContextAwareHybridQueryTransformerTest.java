package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextAwareHybridQueryTransformerTest {

    @Mock
    private RetrievalPromptExpansionService retrievalPromptExpansionService;

    @AfterEach
    void tearDown() {
        RagInvocationContext.clear();
    }

    @Test
    void shouldUseExpandedRetrievalQueryFromExpansionService() {
        ContextAwareHybridQueryTransformer transformer =
                new ContextAwareHybridQueryTransformer(retrievalPromptExpansionService);
        RagInvocationContext.setCurrent(RagInvocationContext.builder()
                .appId(1L)
                .codeGenType(CodeGenTypeEnum.HTML)
                .recentHistories(List.of(ChatHistory.builder().message("历史消息").build()))
                .build());
        when(retrievalPromptExpansionService.expandForUserRequest(
                "请生成一个 AI 产品官网",
                List.of(ChatHistory.builder().message("历史消息").build()),
                CodeGenTypeEnum.HTML
        )).thenReturn(RetrievalPromptExpansionOutcome.builder()
                .retrievalQuery("高端科技感 AI 官网 模板 品牌 首屏")
                .rewrittenUserPrompt("请生成一个高端科技感的 AI 产品官网，突出品牌价值、产品能力和首屏 CTA。")
                .expansionTriggered(true)
                .expansionApplied(true)
                .fallbackReason("none")
                .build());

        List<Query> transformedQueries = transformer.transform(Query.from("请生成一个 AI 产品官网"));

        Assertions.assertEquals(1, transformedQueries.size());
        Assertions.assertEquals("高端科技感 AI 官网 模板 品牌 首屏", transformedQueries.getFirst().text());
    }

    @Test
    void shouldReturnOriginalQueryWhenInvocationContextMissing() {
        ContextAwareHybridQueryTransformer transformer =
                new ContextAwareHybridQueryTransformer(retrievalPromptExpansionService);

        List<Query> transformedQueries = transformer.transform(Query.from("请生成一个 AI 产品官网"));

        Assertions.assertEquals(1, transformedQueries.size());
        Assertions.assertEquals("请生成一个 AI 产品官网", transformedQueries.getFirst().text());
    }
}
