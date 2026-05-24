package com.lingchuang.ai.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.KnowledgeChunk;
import com.lingchuang.ai.rag.model.KnowledgeDocument;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识索引服务，负责 Elasticsearch 索引构建与检索。
 */
@Service
@Slf4j
public class KnowledgeIndexService {

    private static final String SNAPSHOT_FILE_NAME = "knowledge-cache.json";
    private static final int EMBEDDING_BATCH_SIZE = 32;

    private final RagProperties ragProperties;
    private final KnowledgeCorpusLoader knowledgeCorpusLoader;
    private final KnowledgeChunker knowledgeChunker;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final RetrievedChunkContentMapper retrievedChunkContentMapper;

    private volatile List<KnowledgeChunk> chunkCache = List.of();
    private volatile Map<String, KnowledgeChunk> chunkMap = Map.of();
    private volatile String corpusChecksum = "";
    private volatile RestClient restClient;
    private volatile ElasticsearchEmbeddingStore embeddingStore;

    @Autowired
    public KnowledgeIndexService(RagProperties ragProperties,
                                 KnowledgeCorpusLoader knowledgeCorpusLoader,
                                 KnowledgeChunker knowledgeChunker,
                                 EmbeddingModel embeddingModel,
                                 ObjectMapper objectMapper,
                                 RetrievedChunkContentMapper retrievedChunkContentMapper) {
        this.ragProperties = ragProperties;
        this.knowledgeCorpusLoader = knowledgeCorpusLoader;
        this.knowledgeChunker = knowledgeChunker;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.retrievedChunkContentMapper = retrievedChunkContentMapper;
    }

    KnowledgeIndexService(RagProperties ragProperties,
                          KnowledgeCorpusLoader knowledgeCorpusLoader,
                          KnowledgeChunker knowledgeChunker,
                          EmbeddingModel embeddingModel) {
        this(ragProperties,
                knowledgeCorpusLoader,
                knowledgeChunker,
                embeddingModel,
                new ObjectMapper(),
                new RetrievedChunkContentMapper());
    }

    public void initialize() {
        if (!chunkCache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (!chunkCache.isEmpty()) {
                return;
            }
            List<KnowledgeDocument> documents = loadDocuments();
            String newChecksum = calculateCorpusChecksum(documents);
            if (!loadSnapshotIfMatches(newChecksum)) {
                rebuildFromDocuments(documents, newChecksum);
            }
        }
    }

    public void rebuildAll() {
        synchronized (this) {
            List<KnowledgeDocument> documents = loadDocuments();
            String newChecksum = calculateCorpusChecksum(documents);
            rebuildFromDocuments(documents, newChecksum);
        }
    }

    public List<RetrievedChunk> searchBm25(String query, CodeGenTypeEnum codeGenType, int topK) {
        initialize();
        if (chunkCache.isEmpty() || StrUtil.isBlank(query)) {
            return List.of();
        }
        try {
            ContentRetriever retriever = createBm25ContentRetriever(codeGenType, topK);
            List<RetrievedChunk> results = limitResults(
                    retrievedChunkContentMapper.toRetrievedChunks(
                            retriever.retrieve(Query.from(query)),
                            HybridRetrievalRouteMetadata.ROUTE_BM25
                    ),
                    topK
            );
            log.info("RAG BM25 检索完成，queryHash={}, codeGenType={}, topK={}, hits={}",
                    query.hashCode(), codeGenType.getValue(), topK, results.size());
            return results;
        } catch (Exception e) {
            log.warn("BM25 检索失败，query={}", query, e);
            throw new IllegalStateException("BM25 检索失败", e);
        }
    }

    public List<RetrievedChunk> searchDense(String query, CodeGenTypeEnum codeGenType, int topK) {
        initialize();
        if (chunkCache.isEmpty() || StrUtil.isBlank(query)) {
            return List.of();
        }
        try {
            ContentRetriever retriever = createDenseContentRetriever(codeGenType, topK);
            List<RetrievedChunk> results = limitResults(
                    retrievedChunkContentMapper.toRetrievedChunks(
                            retriever.retrieve(Query.from(query)),
                            HybridRetrievalRouteMetadata.ROUTE_DENSE
                    ),
                    topK
            );
            log.info("RAG Dense 检索完成，queryHash={}, codeGenType={}, topK={}, hits={}",
                    query.hashCode(), codeGenType.getValue(), topK, results.size());
            return results;
        } catch (Exception e) {
            log.warn("Dense 检索失败，query={}", query, e);
            throw new IllegalStateException("Dense 检索失败", e);
        }
    }

    private void rebuildFromDocuments(List<KnowledgeDocument> documents, String newChecksum) {
        List<KnowledgeChunk> chunks = documents.stream()
                .flatMap(document -> knowledgeChunker.split(document).stream())
                .toList();
        Map<String, KnowledgeChunk> newChunkMap = chunks.stream()
                .collect(Collectors.toMap(KnowledgeChunk::getChunkId, chunk -> chunk, (left, right) -> left, LinkedHashMap::new));

        if (CollUtil.isEmpty(chunks)) {
            deleteIndexIfExists();
            this.chunkCache = List.of();
            this.chunkMap = Map.of();
            this.corpusChecksum = newChecksum;
            persistSnapshot(newChecksum, List.of());
            return;
        }

        List<TextSegment> segments = chunks.stream().map(this::toTextSegment).toList();
        List<Embedding> embeddings = buildEmbeddings(segments);
        rebuildElasticsearchIndex(chunks, segments, embeddings);

        this.chunkCache = List.copyOf(chunks);
        this.chunkMap = Map.copyOf(newChunkMap);
        this.corpusChecksum = newChecksum;
        persistSnapshot(newChecksum, chunks);
    }

    private List<KnowledgeDocument> loadDocuments() {
        try {
            return knowledgeCorpusLoader.loadAllDocuments(ragProperties);
        } catch (IOException e) {
            throw new IllegalStateException("加载知识语料失败", e);
        }
    }

    private String calculateCorpusChecksum(List<KnowledgeDocument> documents) {
        String merged = documents.stream()
                .map(document -> document.getDocId() + ":" + document.getChecksum())
                .sorted()
                .collect(Collectors.joining("|"));
        return SecureUtil.sha256(merged);
    }

    private List<Embedding> buildEmbeddings(List<TextSegment> segments) {
        try {
            List<Embedding> embeddings = new ArrayList<>(segments.size());
            for (int start = 0; start < segments.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, segments.size());
                List<TextSegment> batch = segments.subList(start, end);
                embeddings.addAll(embeddingModel.embedAll(batch).content());
            }
            if (embeddings.size() != segments.size()) {
                throw new IllegalStateException("Embedding 返回数量与知识切片数量不一致");
            }
            return embeddings;
        } catch (Exception e) {
            throw new IllegalStateException("构建 Embedding 失败", e);
        }
    }

    private void rebuildElasticsearchIndex(List<KnowledgeChunk> chunks,
                                           List<TextSegment> segments,
                                           List<Embedding> embeddings) {
        try {
            deleteIndexIfExists();
            createIndex();
            getEmbeddingStore().addAll(
                    chunks.stream().map(KnowledgeChunk::getChunkId).toList(),
                    embeddings,
                    segments
            );
            log.info("Elasticsearch 知识索引重建完成，index={}, chunks={}", resolveIndexName(), chunks.size());
        } catch (Exception e) {
            throw new IllegalStateException("重建 Elasticsearch 索引失败", e);
        }
    }

    private boolean loadSnapshotIfMatches(String checksum) {
        Path snapshotPath = resolveSnapshotPath();
        if (!Files.exists(snapshotPath)) {
            return false;
        }
        try {
            KnowledgeIndexSnapshot snapshot = objectMapper.readValue(snapshotPath.toFile(), KnowledgeIndexSnapshot.class);
            if (!Objects.equals(snapshot.getCorpusChecksum(), checksum) || CollUtil.isEmpty(snapshot.getChunks())) {
                return false;
            }
            if (!indexHasExpectedMapping()) {
                return false;
            }
            this.chunkCache = List.copyOf(snapshot.getChunks());
            this.chunkMap = snapshot.getChunks().stream()
                    .collect(Collectors.toMap(KnowledgeChunk::getChunkId, chunk -> chunk, (left, right) -> left, LinkedHashMap::new));
            this.corpusChecksum = checksum;
            return true;
        } catch (Exception e) {
            log.warn("加载知识快照失败，将执行全量重建", e);
            return false;
        }
    }

    private void persistSnapshot(String checksum, List<KnowledgeChunk> chunks) {
        try {
            Path snapshotPath = resolveSnapshotPath();
            Files.createDirectories(snapshotPath.getParent());
            objectMapper.writeValue(snapshotPath.toFile(), new KnowledgeIndexSnapshot(checksum, chunks));
        } catch (Exception e) {
            log.warn("持久化知识快照失败", e);
        }
    }

    private Path resolveSnapshotPath() {
        String sanitizedIndexName = resolveIndexName().replaceAll("[^a-zA-Z0-9-_]", "_");
        String basePath = FileUtil.normalize("tmp/rag/elasticsearch/" + sanitizedIndexName);
        return Path.of(basePath).resolve(SNAPSHOT_FILE_NAME);
    }

    private String resolveIndexName() {
        return ragProperties.getElasticsearch().getIndexName();
    }

    private ElasticsearchEmbeddingStore getEmbeddingStore() {
        ElasticsearchEmbeddingStore localStore = embeddingStore;
        if (localStore != null) {
            return localStore;
        }
        synchronized (this) {
            if (embeddingStore == null) {
                embeddingStore = ElasticsearchEmbeddingStore.builder()
                        .restClient(getRestClient())
                        .indexName(resolveIndexName())
                        .dimension(ragProperties.getElasticsearch().getDimension())
                        .configuration(ElasticsearchConfigurationKnn.builder().build())
                        .build();
            }
            return embeddingStore;
        }
    }

    private RestClient getRestClient() {
        RestClient localClient = restClient;
        if (localClient != null) {
            return localClient;
        }
        synchronized (this) {
            if (restClient == null) {
                RestClientBuilder builder = RestClient.builder(HttpHost.create(ragProperties.getElasticsearch().getUrl()));
                builder.setDefaultHeaders(buildDefaultHeaders());
                builder.setHttpClientConfigCallback(httpClientBuilder -> {
                    CredentialsProvider credentialsProvider = buildCredentialsProvider();
                    if (credentialsProvider != null) {
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return httpClientBuilder;
                });
                restClient = builder.build();
            }
            return restClient;
        }
    }

    private Header[] buildDefaultHeaders() {
        if (StrUtil.isBlank(ragProperties.getElasticsearch().getApiKey())) {
            return new Header[0];
        }
        return new Header[]{
                new BasicHeader("Authorization", "ApiKey " + ragProperties.getElasticsearch().getApiKey())
        };
    }

    private CredentialsProvider buildCredentialsProvider() {
        if (StrUtil.hasBlank(ragProperties.getElasticsearch().getUsername(), ragProperties.getElasticsearch().getPassword())) {
            return null;
        }
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(
                        ragProperties.getElasticsearch().getUsername(),
                        ragProperties.getElasticsearch().getPassword()
                )
        );
        return credentialsProvider;
    }

    private boolean indexExists() {
        try {
            getRestClient().performRequest(new Request("HEAD", "/" + resolveIndexName()));
            return true;
        } catch (ResponseException e) {
            return e.getResponse().getStatusLine().getStatusCode() != 404;
        } catch (Exception e) {
            log.warn("检查 Elasticsearch 索引失败，index={}", resolveIndexName(), e);
            return false;
        }
    }

    private void deleteIndexIfExists() {
        try {
            if (!indexExists()) {
                return;
            }
            getRestClient().performRequest(new Request("DELETE", "/" + resolveIndexName()));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw new IllegalStateException("删除 Elasticsearch 索引失败", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("删除 Elasticsearch 索引失败", e);
        }
    }

    private void createIndex() {
        Request request = new Request("PUT", "/" + resolveIndexName());
        request.setJsonEntity(buildIndexDefinition());
        try {
            getRestClient().performRequest(request);
        } catch (Exception e) {
            throw new IllegalStateException("创建 Elasticsearch 索引失败", e);
        }
    }

    private boolean indexHasExpectedMapping() {
        try {
            if (!indexExists()) {
                return false;
            }
            Request request = new Request("GET", "/" + resolveIndexName() + "/_mapping");
            JsonNode root = objectMapper.readTree(getRestClient().performRequest(request).getEntity().getContent());
            JsonNode vectorNode = root.path(resolveIndexName())
                    .path("mappings")
                    .path("properties")
                    .path("vector");
            JsonNode codeGenTypesNode = root.path(resolveIndexName())
                    .path("mappings")
                    .path("properties")
                    .path("metadata")
                    .path("properties")
                    .path(RetrievedChunkContentMapper.CODE_GEN_TYPES);
            return "dense_vector".equals(vectorNode.path("type").asText())
                    && "text".equals(codeGenTypesNode.path("type").asText())
                    && "keyword".equals(codeGenTypesNode.path("fields").path("keyword").path("type").asText());
        } catch (Exception e) {
            log.warn("检查 Elasticsearch 索引映射失败，index={}", resolveIndexName(), e);
            return false;
        }
    }

    private Filter buildCodeGenTypeFilter(CodeGenTypeEnum codeGenType) {
        String targetValue = encodeMultiValue(Set.of(codeGenType.getValue()));
        String allValue = encodeMultiValue(Set.of("all"));
        return MetadataFilterBuilder.metadataKey(RetrievedChunkContentMapper.CODE_GEN_TYPES).isIn(targetValue, allValue);
    }

    private TextSegment toTextSegment(KnowledgeChunk chunk) {
        Metadata metadata = new Metadata();
        metadata.put(RetrievedChunkContentMapper.DOC_ID, StrUtil.blankToDefault(chunk.getDocId(), ""));
        metadata.put(RetrievedChunkContentMapper.CHUNK_ID, StrUtil.blankToDefault(chunk.getChunkId(), ""));
        metadata.put(RetrievedChunkContentMapper.TITLE, StrUtil.blankToDefault(chunk.getTitle(), ""));
        metadata.put(RetrievedChunkContentMapper.PATH, StrUtil.blankToDefault(chunk.getPath(), ""));
        metadata.put(RetrievedChunkContentMapper.SOURCE_TYPE, StrUtil.blankToDefault(chunk.getSourceType(), ""));
        metadata.put(RetrievedChunkContentMapper.PRIORITY, chunk.getPriority());
        metadata.put(RetrievedChunkContentMapper.TAGS, encodeMultiValue(chunk.getTags()));
        metadata.put(RetrievedChunkContentMapper.CODE_GEN_TYPES, encodeMultiValue(chunk.getCodeGenTypes()));
        metadata.put(RetrievedChunkContentMapper.CHECKSUM, StrUtil.blankToDefault(chunk.getChecksum(), ""));
        return TextSegment.from(StrUtil.blankToDefault(chunk.getContent(), ""), metadata);
    }

    private List<RetrievedChunk> limitResults(List<RetrievedChunk> results, int limit) {
        if (results.size() <= limit) {
            return results;
        }
        return new ArrayList<>(results.subList(0, limit));
    }

    private String encodeMultiValue(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "|";
        }
        return values.stream()
                .filter(StrUtil::isNotBlank)
                .sorted()
                .collect(Collectors.joining("|", "|", "|"));
    }

    private Set<String> decodeMultiValue(String rawValue) {
        if (StrUtil.isBlank(rawValue) || "|".equals(rawValue)) {
            return Set.of();
        }
        return java.util.Arrays.stream(rawValue.split("\\|"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildIndexDefinition() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> mappings = new LinkedHashMap<>();
            Map<String, Object> properties = new LinkedHashMap<>();

            properties.put("text", Map.of("type", "text"));
            Map<String, Object> vectorField = new LinkedHashMap<>();
            vectorField.put("type", "dense_vector");
            vectorField.put("dims", ragProperties.getElasticsearch().getDimension());
            vectorField.put("index", true);
            vectorField.put("similarity", "cosine");
            properties.put("vector", vectorField);

            Map<String, Object> metadataProperties = new LinkedHashMap<>();
            metadataProperties.put(RetrievedChunkContentMapper.DOC_ID, keywordField());
            metadataProperties.put(RetrievedChunkContentMapper.CHUNK_ID, keywordField());
            metadataProperties.put(RetrievedChunkContentMapper.TITLE, textWithKeywordField());
            metadataProperties.put(RetrievedChunkContentMapper.PATH, keywordField());
            metadataProperties.put(RetrievedChunkContentMapper.SOURCE_TYPE, keywordField());
            metadataProperties.put(RetrievedChunkContentMapper.PRIORITY, Map.of("type", "integer"));
            metadataProperties.put(RetrievedChunkContentMapper.TAGS, keywordField());
            metadataProperties.put(RetrievedChunkContentMapper.CODE_GEN_TYPES, textWithKeywordField());
            metadataProperties.put(RetrievedChunkContentMapper.CHECKSUM, keywordField());
            properties.put("metadata", Map.of("properties", metadataProperties));

            mappings.put("properties", properties);
            root.put("mappings", mappings);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建 Elasticsearch 索引定义失败", e);
        }
    }

    ContentRetriever createBm25ContentRetriever(CodeGenTypeEnum codeGenType, int topK) {
        initialize();
        return ElasticsearchContentRetriever.builder()
                .restClient(getRestClient())
                .indexName(resolveIndexName())
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .embeddingModel(embeddingModel)
                .maxResults(Math.max(topK * 3, topK))
                .minScore(0D)
                .filter(buildCodeGenTypeFilter(codeGenType))
                .build();
    }

    ContentRetriever createDenseContentRetriever(CodeGenTypeEnum codeGenType, int topK) {
        initialize();
        return ElasticsearchContentRetriever.builder()
                .restClient(getRestClient())
                .indexName(resolveIndexName())
                .configuration(ElasticsearchConfigurationKnn.builder()
                        .numCandidates(Math.max(topK * 4, topK))
                        .build())
                .embeddingModel(embeddingModel)
                .maxResults(Math.max(topK * 3, topK))
                .minScore(0D)
                .filter(buildCodeGenTypeFilter(codeGenType))
                .build();
    }

    private Map<String, Object> keywordField() {
        return Map.of("type", "keyword");
    }

    private Map<String, Object> textWithKeywordField() {
        return Map.of(
                "type", "text",
                "fields", Map.of(
                        "keyword", Map.of(
                                "type", "keyword",
                                "ignore_above", 256
                        )
                )
        );
    }

    @PreDestroy
    public void close() {
        RestClient localClient = restClient;
        if (localClient == null) {
            return;
        }
        try {
            localClient.close();
        } catch (IOException e) {
            log.warn("关闭 Elasticsearch RestClient 失败", e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KnowledgeIndexSnapshot {

        private String corpusChecksum;
        private List<KnowledgeChunk> chunks = List.of();

        public KnowledgeIndexSnapshot() {
        }

        public KnowledgeIndexSnapshot(String corpusChecksum, List<KnowledgeChunk> chunks) {
            this.corpusChecksum = corpusChecksum;
            this.chunks = chunks;
        }
    }
}
