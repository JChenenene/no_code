package com.lingchuang.ai.rag;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.RetrievedChunk;

import java.util.List;

/**
 * 为后续 LangGraph4j 节点预留的检索适配接口。
 */
public interface RetrievalNodeAdapter {

    /**
     * 面向工作流节点返回最终可用的知识片段。
     *
     * @param query       查询文本
     * @param codeGenType 代码生成类型
     * @return 检索结果
     */
    List<RetrievedChunk> retrieveForNode(String query, CodeGenTypeEnum codeGenType);
}
