package com.lingchuang.ai.rag;

import java.util.List;

/**
 * 重排序提供者。
 */
public interface RerankProvider {

    /**
     * 执行重排。
     *
     * @param query     查询文本
     * @param documents 候选文档
     * @param topN      返回数量
     * @return 重排结果
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);

    /**
     * 单条重排结果。
     */
    record RerankResult(int index, double relevanceScore) {
    }
}
