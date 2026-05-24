package com.lingchuang.ai.langgraph4j.v2.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 需求规划 AI 服务工厂。
 */
@Configuration
public class RequirementPlannerAiServiceFactory {

    @Resource(name = "routingChatModelPrototype")
    private ChatModel routingChatModel;

    public RequirementPlannerAiService createRequirementPlannerAiService() {
        return AiServices.builder(RequirementPlannerAiService.class)
                .chatModel(routingChatModel)
                .build();
    }

    @Bean
    public RequirementPlannerAiService requirementPlannerAiService() {
        return createRequirementPlannerAiService();
    }
}
