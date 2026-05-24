package com.lingchuang.ai.rag;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 输入增强结构化输出。
 */
@Data
public class RetrievalPromptExpansionResult {

    @Description("增强后的检索 query，只返回一条适合 BM25 和向量检索的中文查询语句")
    private String retrievalQuery;

    @Description("重写后的最终用户需求，仅用于最终代码生成输入；direct_search_only 模式返回空字符串")
    private String rewrittenUserPrompt;

    @Description("是否应用了输入增强；若无需增强则返回 false")
    private Boolean applied;
}
