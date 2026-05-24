package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 需求规划 AI 服务。
 */
public interface RequirementPlannerAiService {

    @SystemMessage(fromResource = "prompt/codegen-v2-requirement-planner-system-prompt.txt")
    TaskSpec plan(@UserMessage String userPrompt);
}
