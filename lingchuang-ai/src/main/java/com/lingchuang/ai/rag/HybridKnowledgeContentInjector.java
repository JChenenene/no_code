package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 复用现有业务 prompt 模板的内容注入器。
 */
@Component
public class HybridKnowledgeContentInjector implements ContentInjector {

    private final RetrievedChunkContentMapper retrievedChunkContentMapper;

    public HybridKnowledgeContentInjector(RetrievedChunkContentMapper retrievedChunkContentMapper) {
        this.retrievedChunkContentMapper = retrievedChunkContentMapper;
    }

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        if (!(chatMessage instanceof UserMessage userMessage)) {
            return chatMessage;
        }
        RagInvocationContext invocationContext = RagInvocationContext.getCurrent();
        if (invocationContext == null || invocationContext.getCodeGenType() == null) {
            return chatMessage;
        }
        String effectiveUserPrompt = StrUtil.blankToDefault(
                invocationContext.getRewrittenUserPrompt(),
                userMessage.singleText()
        );
        String augmentedPrompt = RagPromptSupport.buildAugmentedPrompt(
                effectiveUserPrompt,
                invocationContext.getRecentHistories(),
                invocationContext.getCodeGenType(),
                CollUtil.isEmpty(contents)
                        ? List.of()
                        : retrievedChunkContentMapper.toRetrievedChunks(contents, "retrieved")
        );
        if (augmentedPrompt.equals(userMessage.singleText())) {
            return chatMessage;
        }
        return UserMessage.from(augmentedPrompt);
    }
}
