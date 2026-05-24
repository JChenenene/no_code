package com.lingchuang.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.model.KnowledgeDocument;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
@Tag("docker")
class KnowledgeIndexServiceTest {

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.17.1")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @BeforeAll
    static void beforeAll() {
        ELASTICSEARCH.start();
    }

    @Test
    void shouldBuildElasticsearchIndexAndSupportBm25AndDenseSearch() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getElasticsearch().setUrl(ELASTICSEARCH.getHttpHostAddress());
        ragProperties.getElasticsearch().setIndexName("rag-test-" + UUID.randomUUID());

        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader() {
            @Override
            public List<KnowledgeDocument> loadAllDocuments(RagProperties ignored) {
                return List.of(
                        KnowledgeDocument.builder()
                                .docId("doc-1")
                                .title("高端科技感官网模板")
                                .path("knowledge/templates/high-end-landing.md")
                                .sourceType("templates")
                                .priority(3)
                                .tags(Set.of("landing-page"))
                                .codeGenTypes(Set.of("html"))
                                .checksum("checksum-1")
                                .content("高端科技感官网需要突出品牌首屏、产品价值和部署速度。")
                                .build(),
                        KnowledgeDocument.builder()
                                .docId("doc-2")
                                .title("部署说明")
                                .path("knowledge/deploy/deploy-guide.md")
                                .sourceType("deploy")
                                .priority(2)
                                .tags(Set.of("deploy"))
                                .codeGenTypes(Set.of("all"))
                                .checksum("checksum-2")
                                .content("部署说明聚焦 dist 目录、Nginx 配置和预览访问。")
                                .build()
                );
            }
        };

        EmbeddingModel embeddingModel = segments -> Response.from(segments.stream()
                .map(segment -> {
                    String text = segment.text();
                    if (text.contains("高端科技感") || text.contains("品牌首屏")) {
                        return Embedding.from(List.of(1.0F, 0.0F, 0.0F));
                    }
                    if (text.contains("部署") || text.contains("Nginx")) {
                        return Embedding.from(List.of(0.0F, 1.0F, 0.0F));
                    }
                    return Embedding.from(List.of(0.0F, 0.0F, 1.0F));
                })
                .toList());

        KnowledgeIndexService knowledgeIndexService = new KnowledgeIndexService(
                ragProperties,
                loader,
                new KnowledgeChunker(120, 20),
                embeddingModel
        );

        knowledgeIndexService.rebuildAll();

        List<RetrievedChunk> bm25Results = knowledgeIndexService.searchBm25("高端科技感品牌官网", CodeGenTypeEnum.HTML, 5);
        List<RetrievedChunk> denseResults = knowledgeIndexService.searchDense("高端科技感品牌官网", CodeGenTypeEnum.HTML, 5);

        Assertions.assertFalse(bm25Results.isEmpty());
        Assertions.assertEquals("doc-1", bm25Results.get(0).getDocId());
        Assertions.assertFalse(denseResults.isEmpty());
        Assertions.assertEquals("doc-1", denseResults.get(0).getDocId());
    }

    @Test
    void shouldSkipReindexWhenChecksumMatches() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getElasticsearch().setUrl(ELASTICSEARCH.getHttpHostAddress());
        ragProperties.getElasticsearch().setIndexName("rag-snapshot-" + UUID.randomUUID());

        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader() {
            @Override
            public List<KnowledgeDocument> loadAllDocuments(RagProperties ignored) {
                return List.of(
                        KnowledgeDocument.builder()
                                .docId("doc-1")
                                .title("零创AI 首页规范")
                                .path("knowledge/brand/home-page.md")
                                .sourceType("brand")
                                .priority(3)
                                .tags(Set.of("brand"))
                                .codeGenTypes(Set.of("html"))
                                .checksum("checksum-1")
                                .content("首页需要突出零创AI 的首屏价值、品牌语义和行动按钮。")
                                .build()
                );
            }
        };

        CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel();

        KnowledgeChunker knowledgeChunker = new KnowledgeChunker(120, 20);
        KnowledgeIndexService snapshotBuilder = new KnowledgeIndexService(
                ragProperties,
                loader,
                knowledgeChunker,
                embeddingModel
        );
        snapshotBuilder.rebuildAll();

        KnowledgeIndexService snapshotLoader = new KnowledgeIndexService(
                ragProperties,
                loader,
                knowledgeChunker,
                embeddingModel
        );

        snapshotLoader.initialize();

        Assertions.assertEquals(1, embeddingModel.embedAllCallCount);
    }

    @Test
    void shouldSplitEmbeddingsIntoBatchesOfThirtyTwo() throws Exception {
        RagProperties ragProperties = new RagProperties();
        CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel();
        KnowledgeIndexService knowledgeIndexService = new KnowledgeIndexService(
                ragProperties,
                new KnowledgeCorpusLoader() {
                    @Override
                    public List<KnowledgeDocument> loadAllDocuments(RagProperties ignored) {
                        return List.of();
                    }
                },
                new KnowledgeChunker(120, 20),
                embeddingModel
        );

        java.lang.reflect.Method buildEmbeddingsMethod = KnowledgeIndexService.class
                .getDeclaredMethod("buildEmbeddings", List.class);
        buildEmbeddingsMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Embedding> embeddings = (List<Embedding>) buildEmbeddingsMethod.invoke(
                knowledgeIndexService,
                java.util.stream.IntStream.range(0, 35)
                        .mapToObj(index -> TextSegment.from("chunk-" + index))
                        .toList()
        );

        Assertions.assertEquals(35, embeddings.size());
        Assertions.assertEquals(2, embeddingModel.embedAllCallCount);
    }

    @Test
    void shouldCreateKeywordSubfieldForCodeGenTypesMetadata() throws Exception {
        KnowledgeIndexService knowledgeIndexService = new KnowledgeIndexService(
                new RagProperties(),
                new KnowledgeCorpusLoader() {
                    @Override
                    public List<KnowledgeDocument> loadAllDocuments(RagProperties ignored) {
                        return List.of();
                    }
                },
                new KnowledgeChunker(120, 20),
                new CountingEmbeddingModel()
        );

        Method buildIndexDefinitionMethod = KnowledgeIndexService.class.getDeclaredMethod("buildIndexDefinition");
        buildIndexDefinitionMethod.setAccessible(true);
        String indexDefinition = (String) buildIndexDefinitionMethod.invoke(knowledgeIndexService);

        JsonNode codeGenTypesNode = new ObjectMapper()
                .readTree(indexDefinition)
                .path("mappings")
                .path("properties")
                .path("metadata")
                .path("properties")
                .path(RetrievedChunkContentMapper.CODE_GEN_TYPES);

        Assertions.assertEquals("text", codeGenTypesNode.path("type").asText());
        Assertions.assertEquals("keyword", codeGenTypesNode.path("fields").path("keyword").path("type").asText());
    }

    private static class CountingEmbeddingModel implements EmbeddingModel {

        private int embedAllCallCount;

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> texts) {
            embedAllCallCount++;
            return Response.from(texts.stream()
                    .map(text -> Embedding.from(List.of(1.0F, 0.2F, 0.1F)))
                    .toList());
        }
    }
}
