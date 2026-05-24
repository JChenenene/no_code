package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalPromptExpansionServiceTest {

    @Mock
    private RetrievalPromptExpansionAiServiceFactory factory;

    @Mock
    private RetrievalPromptExpansionAiService aiService;

    private RagProperties ragProperties;

    private RetrievalPromptExpansionService expansionService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getQueryExpansion().setEnabled(true);
        expansionService = new RetrievalPromptExpansionService(ragProperties, factory);
    }

    @AfterEach
    void tearDown() {
        RagInvocationContext.clear();
    }

    @Test
    void shouldExpandAndCacheResultForUserRequest() {
        RetrievalPromptExpansionResult result = new RetrievalPromptExpansionResult();
        result.setApplied(true);
        result.setRetrievalQuery("高端科技感 AI 官网 模板 品牌 首屏");
        result.setRewrittenUserPrompt("请生成一个高端科技感的 AI 产品官网，突出品牌价值、产品能力、核心卖点和首屏 CTA。");
        when(factory.createRetrievalPromptExpansionAiService()).thenReturn(aiService);
        when(aiService.expand(anyString())).thenReturn(result);
        RagInvocationContext.setCurrent(RagInvocationContext.builder()
                .appId(1L)
                .codeGenType(CodeGenTypeEnum.HTML)
                .recentHistories(List.of(ChatHistory.builder().message("历史消息").build()))
                .build());

        RetrievalPromptExpansionOutcome outcome = expansionService.expandForUserRequest(
                "请生成一个 AI 产品官网",
                List.of(ChatHistory.builder().message("历史消息").build()),
                CodeGenTypeEnum.HTML
        );

        Assertions.assertTrue(outcome.isExpansionApplied());
        Assertions.assertEquals("高端科技感 AI 官网 模板 品牌 首屏", outcome.getRetrievalQuery());
        Assertions.assertTrue(outcome.getRewrittenUserPrompt().contains("高端科技感"));
        Assertions.assertEquals("高端科技感 AI 官网 模板 品牌 首屏",
                RagInvocationContext.getCurrent().getExpandedRetrievalQuery());
        Assertions.assertEquals(outcome.getRewrittenUserPrompt(),
                RagInvocationContext.getCurrent().getRewrittenUserPrompt());
    }

    @Test
    void shouldSkipCodeLikeInputWithoutCallingModel() {
        RetrievalPromptExpansionOutcome outcome = expansionService.expandForUserRequest(
                "<div class=\"hero\">hello</div>",
                List.of(),
                CodeGenTypeEnum.HTML
        );

        Assertions.assertFalse(outcome.isExpansionApplied());
        Assertions.assertEquals("<div class=\"hero\">hello</div>", outcome.getRetrievalQuery());
        Assertions.assertEquals("code_like_input", outcome.getFallbackReason());
        verify(factory, never()).createRetrievalPromptExpansionAiService();
    }

    @Test
    void shouldFallbackWhenModelThrowsException() {
        when(factory.createRetrievalPromptExpansionAiService()).thenReturn(aiService);
        when(aiService.expand(anyString())).thenThrow(new TestExpansionException("boom"));

        RetrievalPromptExpansionOutcome outcome = expansionService.expandForUserRequest(
                "高端科技感官网",
                List.of(),
                CodeGenTypeEnum.HTML
        );

        Assertions.assertFalse(outcome.isExpansionApplied());
        Assertions.assertEquals("高端科技感官网", outcome.getRetrievalQuery());
        Assertions.assertEquals("model_error", outcome.getFallbackReason());
    }

    private static class TestExpansionException extends RuntimeException {

        TestExpansionException(String message) {
            super(message, null, false, false);
        }
    }
}
