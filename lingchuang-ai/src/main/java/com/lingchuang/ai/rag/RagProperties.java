package com.lingchuang.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置。
 */
@Configuration
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    /**
     * 是否启用 RAG。
     */
    private boolean enabled = false;

    private Bootstrap bootstrap = new Bootstrap();

    private Knowledge knowledge = new Knowledge();

    private Elasticsearch elasticsearch = new Elasticsearch();

    private Embedding embedding = new Embedding();

    private Rerank rerank = new Rerank();

    private Retrieve retrieve = new Retrieve();

    private QueryExpansion queryExpansion = new QueryExpansion();

    private Timeout timeout = new Timeout();

    @Data
    public static class Bootstrap {
        private boolean enabled = false;
    }

    @Data
    public static class Knowledge {
        private String basePath = "classpath*:knowledge/**/*.md";
    }

    @Data
    public static class Elasticsearch {
        private String url = "http://127.0.0.1:9200";
        private String apiKey;
        private String username;
        private String password;
        private String indexName = "lc_knowledge_chunks";
        private Integer dimension = 1024;
    }

    @Data
    public static class Embedding {
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey;
        private String model = "BAAI/bge-large-zh-v1.5";
    }

    @Data
    public static class Rerank {
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey;
        private String model = "BAAI/bge-reranker-v2-m3";
    }

    @Data
    public static class Retrieve {
        private int bm25TopK = 20;
        private int denseTopK = 20;
        private int fusedTopK = 12;
        private int finalTopK = 5;
        private double denseWeight = 0.55;
        private double bm25Weight = 0.45;
    }

    @Data
    public static class QueryExpansion {
        private boolean enabled = true;
        private int timeoutMs = 3_000;
        private int shortQueryChars = 80;
        private int sparseQueryMaxChars = 180;
        private int maxRetrievalQueryChars = 240;
        private int maxRewrittenPromptChars = 600;
        private boolean skipCodeLikeInput = true;
    }

    @Data
    public static class Timeout {
        private int embeddingMs = 10_000;
        private int rerankMs = 10_000;
    }
}
