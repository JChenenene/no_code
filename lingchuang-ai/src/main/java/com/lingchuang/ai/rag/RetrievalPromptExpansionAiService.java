package com.lingchuang.ai.rag;

import dev.langchain4j.service.SystemMessage;

/**
 * 检索 query 与最终提示词重写服务。
 */
public interface RetrievalPromptExpansionAiService {

    /**
     * 一次性产出检索 query 与重写后的用户需求。
     *
     * @param expansionRequest 扩展请求文本
     * @return 结构化扩展结果
     */
    @SystemMessage(fromResource = "prompt/retrieval-prompt-expansion-system-prompt.txt")
    RetrievalPromptExpansionResult expand(String expansionRequest);
}
