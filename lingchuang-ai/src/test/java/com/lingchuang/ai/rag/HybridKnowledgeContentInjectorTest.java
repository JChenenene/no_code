package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class HybridKnowledgeContentInjectorTest {

    private final RetrievedChunkContentMapper retrievedChunkContentMapper = new RetrievedChunkContentMapper();

    @AfterEach
    void tearDown() {
        RagInvocationContext.clear();
    }

    @Test
    void shouldUseRewrittenPromptWhenNoRetrievedContents() {
        HybridKnowledgeContentInjector injector = new HybridKnowledgeContentInjector(retrievedChunkContentMapper);
        RagInvocationContext.setCurrent(RagInvocationContext.builder()
                .appId(1L)
                .codeGenType(CodeGenTypeEnum.HTML)
                .rewrittenUserPrompt("请生成一个高端科技感的 AI 产品官网，突出品牌价值与首屏 CTA。")
                .build());

        ChatMessage injectedMessage = injector.inject(List.of(), UserMessage.from("请生成一个 AI 产品官网"));

        Assertions.assertInstanceOf(UserMessage.class, injectedMessage);
        Assertions.assertEquals("请生成一个高端科技感的 AI 产品官网，突出品牌价值与首屏 CTA。",
                ((UserMessage) injectedMessage).singleText());
    }

    @Test
    void shouldBuildAugmentedPromptWithRewrittenPromptWhenContentsExist() {
        HybridKnowledgeContentInjector injector = new HybridKnowledgeContentInjector(retrievedChunkContentMapper);
        RagInvocationContext.setCurrent(RagInvocationContext.builder()
                .appId(1L)
                .codeGenType(CodeGenTypeEnum.HTML)
                .recentHistories(List.of(ChatHistory.builder().message("历史消息").build()))
                .rewrittenUserPrompt("请生成一个高端科技感的 AI 产品官网，首屏突出品牌价值与产品能力。")
                .build());
        List<Content> contents = retrievedChunkContentMapper.toContents(List.of(
                RetrievedChunk.builder()
                        .chunkId("chunk-1")
                        .title("品牌首屏模板")
                        .content("首屏应突出品牌价值、核心卖点和 CTA。")
                        .sourceType("brand")
                        .path("knowledge/brand/hero.md")
                        .score(0.95)
                        .scoreSource("bm25")
                        .build()
        ));

        ChatMessage injectedMessage = injector.inject(contents, UserMessage.from("请生成一个 AI 产品官网"));

        Assertions.assertInstanceOf(UserMessage.class, injectedMessage);
        String finalPrompt = ((UserMessage) injectedMessage).singleText();
        Assertions.assertTrue(finalPrompt.contains("最终执行需求"));
        Assertions.assertTrue(finalPrompt.contains("请生成一个高端科技感的 AI 产品官网"));
        Assertions.assertTrue(finalPrompt.contains("品牌首屏模板"));
    }
}
