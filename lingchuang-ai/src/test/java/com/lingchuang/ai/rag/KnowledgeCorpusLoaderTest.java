package com.lingchuang.ai.rag;

import com.lingchuang.ai.rag.model.KnowledgeDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

class KnowledgeCorpusLoaderTest {

    @Test
    void shouldParseKnowledgeFrontMatterWithoutMergingPromptResources() throws Exception {
        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader();

        Resource knowledgeResource = new NamedByteArrayResource(
                """
                        ---
                        title: 高端科技感落地页规范
                        codeGenTypes: html, vue_project
                        sourceType: brand
                        priority: high
                        tags: landing-page, hero-section
                        ---
                        # 首屏规范
                        主标题需要突出品牌和核心能力。
                        """.getBytes(StandardCharsets.UTF_8),
                "brand/high-end-landing.md"
        );

        List<KnowledgeDocument> documents = loader.loadDocuments(List.of(knowledgeResource));

        Assertions.assertEquals(1, documents.size());

        KnowledgeDocument knowledgeDocument = documents.stream()
                .filter(document -> "knowledge/brand/high-end-landing.md".equals(document.getPath()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals("高端科技感落地页规范", knowledgeDocument.getTitle());
        Assertions.assertEquals("brand", knowledgeDocument.getSourceType());
        Assertions.assertEquals(3, knowledgeDocument.getPriority());
        Assertions.assertEquals(Set.of("landing-page", "hero-section"), knowledgeDocument.getTags());
        Assertions.assertEquals(Set.of("html", "vue_project"), knowledgeDocument.getCodeGenTypes());
    }

    @Test
    void shouldLoadOnlyKnowledgeResourcesFromClasspath() throws Exception {
        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader();
        RagProperties ragProperties = new RagProperties();

        List<KnowledgeDocument> documents = loader.loadAllDocuments(ragProperties);

        Assertions.assertFalse(documents.isEmpty());
        Assertions.assertTrue(documents.stream().allMatch(document -> document.getPath().startsWith("knowledge/")));
        Assertions.assertTrue(documents.stream().noneMatch(document -> document.getPath().startsWith("prompt/")));
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
