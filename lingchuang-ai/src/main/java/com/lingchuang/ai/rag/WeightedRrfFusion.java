package com.lingchuang.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.query.Query;
import com.lingchuang.ai.rag.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 LangChain4j DefaultContentAggregator 的 RRF 融合。
 */
public final class WeightedRrfFusion {

    private static final String DOC_ID = "docId";
    private static final String CHUNK_ID = "chunkId";
    private static final String TITLE = "title";
    private static final String PATH = "path";
    private static final String SOURCE_TYPE = "sourceType";
    private static final String PRIORITY = "priority";
    private static final String TAGS = "tags";
    private static final String CODE_GEN_TYPES = "codeGenTypes";
    private static final String CHECKSUM = "checksum";

    private WeightedRrfFusion() {
    }

    public static List<RetrievedChunk> fuse(List<RetrievedChunk> bm25Results,
                                            List<RetrievedChunk> denseResults,
                                            int limit) {
        Query query = Query.from("hybrid-fusion");
        List<Content> fusedContents = new DefaultContentAggregator().aggregate(Map.of(
                query, List.of(toContents(bm25Results), toContents(denseResults))
        ));
        return toRetrievedChunks(fusedContents, limit);
    }

    public static List<RetrievedChunk> fuse(List<RetrievedChunk> bm25Results,
                                            List<RetrievedChunk> denseResults,
                                            double bm25Weight,
                                            double denseWeight,
                                            int limit) {
        return fuse(bm25Results, denseResults, limit);
    }

    private static List<Content> toContents(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> Content.from(TextSegment.from(
                        chunk.getContent(),
                        metadataFromChunk(chunk)
                )))
                .toList();
    }

    private static Metadata metadataFromChunk(RetrievedChunk chunk) {
        Metadata metadata = new Metadata();
        metadata.put(DOC_ID, chunk.getDocId() == null ? "" : chunk.getDocId());
        metadata.put(CHUNK_ID, chunk.getChunkId() == null ? "" : chunk.getChunkId());
        metadata.put(TITLE, chunk.getTitle() == null ? "" : chunk.getTitle());
        metadata.put(PATH, chunk.getPath() == null ? "" : chunk.getPath());
        metadata.put(SOURCE_TYPE, chunk.getSourceType() == null ? "" : chunk.getSourceType());
        metadata.put(PRIORITY, chunk.getPriority());
        metadata.put(TAGS, encodeMultiValue(chunk.getTags()));
        metadata.put(CODE_GEN_TYPES, encodeMultiValue(chunk.getCodeGenTypes()));
        metadata.put(CHECKSUM, chunk.getChecksum() == null ? "" : chunk.getChecksum());
        return metadata;
    }

    private static List<RetrievedChunk> toRetrievedChunks(List<Content> fusedContents, int limit) {
        List<RetrievedChunk> results = new ArrayList<>();
        for (Content content : fusedContents) {
            Metadata metadata = content.textSegment().metadata();
            results.add(RetrievedChunk.builder()
                    .docId(metadata.getString(DOC_ID))
                    .chunkId(metadata.getString(CHUNK_ID))
                    .title(metadata.getString(TITLE))
                    .path(metadata.getString(PATH))
                    .sourceType(metadata.getString(SOURCE_TYPE))
                    .priority(metadata.getInteger(PRIORITY) == null ? 0 : metadata.getInteger(PRIORITY))
                    .tags(decodeMultiValue(metadata.getString(TAGS)))
                    .codeGenTypes(decodeMultiValue(metadata.getString(CODE_GEN_TYPES)))
                    .checksum(metadata.getString(CHECKSUM))
                    .content(content.textSegment().text())
                    .score(extractScore(content))
                    .scoreSource("rrf")
                    .build());
        }
        if (results.size() <= limit) {
            return results;
        }
        return new ArrayList<>(results.subList(0, limit));
    }

    private static double extractScore(Content content) {
        Object score = content.metadata().get(ContentMetadata.SCORE);
        return score instanceof Number number ? number.doubleValue() : 0D;
    }

    private static String encodeMultiValue(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "|";
        }
        return values.stream()
                .sorted()
                .reduce("|", (left, value) -> left + value + "|");
    }

    private static Set<String> decodeMultiValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || "|".equals(rawValue)) {
            return Set.of();
        }
        return java.util.Arrays.stream(rawValue.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
