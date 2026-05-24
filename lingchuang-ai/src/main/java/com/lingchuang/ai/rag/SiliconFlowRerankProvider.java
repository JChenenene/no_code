package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 SiliconFlow Rerank 接口的 LangChain4j ScoringModel 适配器。
 */
@Component
@Slf4j
public class SiliconFlowRerankProvider implements ScoringModel {

    private final RagProperties ragProperties;

    private final ObjectMapper objectMapper;

    public SiliconFlowRerankProvider(RagProperties ragProperties, ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (StrUtil.isBlank(query) || CollUtil.isEmpty(segments)) {
            return Response.from(List.of());
        }
        if (StrUtil.isBlank(ragProperties.getRerank().getApiKey())) {
            throw new IllegalStateException("未配置 SiliconFlow Rerank API Key");
        }
        List<String> documents = segments.stream().map(TextSegment::text).toList();
        try {
            Map<String, Object> payload = Map.of(
                    "model", ragProperties.getRerank().getModel(),
                    "query", query,
                    "documents", documents,
                    "top_n", documents.size(),
                    "return_documents", false
            );
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint(ragProperties.getRerank().getBaseUrl(), "/rerank")))
                    .timeout(Duration.ofMillis(ragProperties.getTimeout().getRerankMs()))
                    .header("Authorization", "Bearer " + ragProperties.getRerank().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(ragProperties.getTimeout().getRerankMs()))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Rerank 请求失败，status=" + response.statusCode() + ", body=" + response.body());
            }
            return Response.from(parseScores(response.body(), documents.size()));
        } catch (IOException e) {
            log.warn("请求 SiliconFlow Rerank 失败", e);
            throw new IllegalStateException("请求 SiliconFlow Rerank 失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("请求 SiliconFlow Rerank 被中断", e);
            throw new IllegalStateException("请求 SiliconFlow Rerank 被中断", e);
        }
    }

    private List<Double> parseScores(String responseBody, int documentCount) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray()) {
            throw new IllegalStateException("Rerank 响应缺少 results 字段");
        }
        List<Double> scores = new ArrayList<>(java.util.Collections.nCopies(documentCount, 0D));
        for (JsonNode resultNode : resultsNode) {
            int index = resultNode.path("index").asInt(-1);
            if (index < 0 || index >= documentCount) {
                continue;
            }
            scores.set(index, resultNode.path("relevance_score").asDouble());
        }
        return scores;
    }

    private String resolveEndpoint(String baseUrl, String suffix) {
        if (StrUtil.isBlank(baseUrl)) {
            return "https://api.siliconflow.cn/v1" + suffix;
        }
        String normalized = StrUtil.removeSuffix(baseUrl.trim(), "/");
        if (normalized.endsWith(suffix)) {
            return normalized;
        }
        return normalized + suffix;
    }
}
