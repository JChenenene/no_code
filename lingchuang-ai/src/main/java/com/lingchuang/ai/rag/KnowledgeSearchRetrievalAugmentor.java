package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * 基于 LangChain4j 原生 RAG 组件的知识检索增强器。
 */
@Component
@Slf4j
public class KnowledgeSearchRetrievalAugmentor implements RetrievalAugmentor {

    private final RagProperties ragProperties;
    private final RetrievalAugmentor delegate;

    public KnowledgeSearchRetrievalAugmentor(RagProperties ragProperties,
                                             ContextAwareHybridQueryTransformer queryTransformer,
                                             ContextAwareHybridQueryRouter queryRouter,
                                             HybridContentAggregator contentAggregator,
                                             HybridKnowledgeContentInjector contentInjector) {
        this.ragProperties = ragProperties;
        this.delegate = new DefaultRetrievalAugmentor(
                queryTransformer,
                queryRouter,
                contentAggregator,
                contentInjector,
                ForkJoinPool.commonPool()
        );
    }

    @Override
    public AugmentationResult augment(AugmentationRequest augmentationRequest) {
        if (!ragProperties.isEnabled()
                || augmentationRequest == null
                || !(augmentationRequest.chatMessage() instanceof UserMessage userMessage)
                || StrUtil.isBlank(userMessage.singleText())
                || RagInvocationContext.getCurrent() == null) {
            return new AugmentationResult(
                    augmentationRequest == null ? null : augmentationRequest.chatMessage(),
                    List.of()
            );
        }
        try {
            AugmentationResult augmentationResult = delegate.augment(augmentationRequest);
            ChatMessage finalChatMessage = augmentationResult.chatMessage();
            RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
            if (finalChatMessage instanceof UserMessage augmentedUserMessage
                    && invocationContext != null
                    && StrUtil.isNotBlank(invocationContext.getRewrittenUserPrompt())
                    && StrUtil.equals(augmentedUserMessage.singleText(), userMessage.singleText())) {
                finalChatMessage = UserMessage.from(invocationContext.getRewrittenUserPrompt());
            }
            log.info("AiServices RAG 增强完成，queryHash={}, retrievedContents={}",
                    userMessage.singleText().hashCode(),
                    augmentationResult.contents() == null ? 0 : augmentationResult.contents().size());
            return new AugmentationResult(finalChatMessage, augmentationResult.contents());
        } catch (Exception e) {
            log.warn("AiServices RAG 增强失败，继续使用原始提示词，queryHash={}", userMessage.singleText().hashCode(), e);
            return new AugmentationResult(augmentationRequest.chatMessage(), List.of());
        }
    }
}
