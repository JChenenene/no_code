package com.lingchuang.ai.rag;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;

/**
 * 输入增强 AI 服务工厂。
 */
@Configuration
public class RetrievalPromptExpansionAiServiceFactory {

    @Resource(name = "routingChatModelPrototype")
    private ChatModel routingChatModel;

    public RetrievalPromptExpansionAiService createRetrievalPromptExpansionAiService() {
        return AiServices.builder(RetrievalPromptExpansionAiService.class)
                .chatModel(routingChatModel)
                .build();
    }
}
