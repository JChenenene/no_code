package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 SiliconFlow OpenAI 兼容接口的 EmbeddingModel。
 */
@Component
public class SiliconFlowEmbeddingProvider implements EmbeddingModel {

    private final RagProperties ragProperties;

    private volatile EmbeddingModel delegate;

    public SiliconFlowEmbeddingProvider(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (StrUtil.isBlank(ragProperties.getEmbedding().getApiKey())) {
            throw new IllegalStateException("未配置 SiliconFlow Embedding API Key");
        }
        return getDelegate().embedAll(textSegments);
    }

    @Override
    public int dimension() {
        return ragProperties.getElasticsearch().getDimension();
    }

    private EmbeddingModel getDelegate() {
        EmbeddingModel localDelegate = delegate;
        if (localDelegate != null) {
            return localDelegate;
        }
        synchronized (this) {
            if (delegate == null) {
                delegate = OpenAiEmbeddingModel.builder()
                        .baseUrl(ragProperties.getEmbedding().getBaseUrl())
                        .apiKey(ragProperties.getEmbedding().getApiKey())
                        .modelName(ragProperties.getEmbedding().getModel())
                        .encodingFormat("float")
                        .timeout(Duration.ofMillis(ragProperties.getTimeout().getEmbeddingMs()))
                        .maxRetries(2)
                        .build();
            }
            return delegate;
        }
    }
}
