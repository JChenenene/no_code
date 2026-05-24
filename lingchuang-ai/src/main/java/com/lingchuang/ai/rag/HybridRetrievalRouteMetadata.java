package com.lingchuang.ai.rag;

/**
 * 双路检索路由元数据。
 */
public final class HybridRetrievalRouteMetadata {

    public static final String RETRIEVAL_ROUTE = "_retrievalRoute";
    public static final String ROUTE_BM25 = "bm25";
    public static final String ROUTE_DENSE = "dense";

    private HybridRetrievalRouteMetadata() {
    }
}
