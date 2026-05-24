package com.lingchuang.ai.rag;

import com.lingchuang.ai.rag.model.KnowledgeChunk;
import com.lingchuang.ai.rag.model.KnowledgeDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class KnowledgeChunkerTest {

    @Test
    void shouldKeepMarkdownStructureAndNotBreakCodeFence() {
        KnowledgeChunker chunker = new KnowledgeChunker(80, 12);
        KnowledgeDocument document = KnowledgeDocument.builder()
                .docId("doc-1")
                .title("页面模板")
                .path("templates/landing-page.md")
                .sourceType("templates")
                .priority(2)
                .checksum("checksum-1")
                .tags(Set.of("landing-page"))
                .codeGenTypes(Set.of("html"))
                .content("""
                        # 首屏
                        这里是一段较长的页面说明，用来确保分片逻辑会真实发生，并且能够保留标题信息。

                        ```html
                        <section class="hero">
                          <h1>零创AI</h1>
                        </section>
                        ```

                        ## 亮点区
                        亮点区要强调产品能力、部署速度和页面气质。
                        """)
                .build();

        List<KnowledgeChunk> chunks = chunker.split(document);

        Assertions.assertTrue(chunks.size() >= 2);
        Assertions.assertTrue(chunks.get(0).getContent().contains("# 首屏"));
        Assertions.assertTrue(chunks.stream()
                .filter(chunk -> chunk.getContent().contains("```html"))
                .allMatch(chunk -> chunk.getContent().contains("```")));
        Assertions.assertTrue(chunks.stream().allMatch(chunk -> chunk.getChunkId().startsWith("doc-1#")));
    }

    @Test
    void shouldSplitCuratedKnowledgeTemplatesIntoMultipleChunks() throws Exception {
        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader();
        KnowledgeChunker chunker = new KnowledgeChunker();
        RagProperties ragProperties = new RagProperties();

        Map<String, KnowledgeDocument> documentsByPath = loader.loadAllDocuments(ragProperties).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getPath, document -> document));

        List<String> curatedPaths = documentsByPath.keySet().stream()
                .filter(path -> path.startsWith("knowledge/templates/") || path.startsWith("knowledge/patterns/"))
                .sorted()
                .toList();

        Assertions.assertFalse(curatedPaths.isEmpty());

        for (String path : curatedPaths) {
            KnowledgeDocument document = documentsByPath.get(path);
            Assertions.assertNotNull(document, () -> "缺少知识文档: " + path);
            Assertions.assertTrue(
                    chunker.split(document).size() >= 2,
                    () -> "文档未切分为多个 chunk: " + path
            );
        }
    }
}
