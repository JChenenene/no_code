package com.lingchuang.ai.rag;

import java.util.List;

/**
 * 向量化提供者。
 */
public interface EmbeddingProvider {

    /**
     * 批量生成文本向量。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<List<Float>> embedAll(List<String> texts);

    /**
     * 生成单条文本向量。
     *
     * @param text 文本
     * @return 向量
     */
    default List<Float> embed(String text) {
        List<List<Float>> embeddings = embedAll(List.of(text));
        return embeddings.isEmpty() ? List.of() : embeddings.get(0);
    }
}
