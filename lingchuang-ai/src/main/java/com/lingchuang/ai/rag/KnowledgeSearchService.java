package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;

/**
 * 知识检索服务。
 */
public interface KnowledgeSearchService {

    /**
     * 搜索相关知识片段。
     *
     * @param query       查询文本
     * @param codeGenType 代码生成类型
     * @param maxResults  期望返回条数
     * @return 混合检索结果
     */
    HybridRetrievalResult search(String query, CodeGenTypeEnum codeGenType, int maxResults);
}
