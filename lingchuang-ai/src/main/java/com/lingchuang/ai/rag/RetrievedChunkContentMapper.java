package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RetrievedChunk} 与 LangChain4j {@link Content} 转换器。
 */
@Component
public class RetrievedChunkContentMapper {

    public static final String DOC_ID = "docId";
    public static final String CHUNK_ID = "chunkId";
    public static final String TITLE = "title";
    public static final String PATH = "path";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String PRIORITY = "priority";
    public static final String TAGS = "tags";
    public static final String CODE_GEN_TYPES = "codeGenTypes";
    public static final String CHECKSUM = "checksum";

    public List<RetrievedChunk> toRetrievedChunks(List<Content> contents, String defaultScoreSource) {
        return contents.stream()
                .map(content -> toRetrievedChunk(content, defaultScoreSource))
                .toList();
    }

    public RetrievedChunk toRetrievedChunk(Content content, String defaultScoreSource) {
        Metadata metadata = content.textSegment().metadata();
        String scoreSource = metadata.containsKey(HybridRetrievalRouteMetadata.RETRIEVAL_ROUTE)
                ? metadata.getString(HybridRetrievalRouteMetadata.RETRIEVAL_ROUTE)
                : defaultScoreSource;
        return RetrievedChunk.builder()
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
                .scoreSource(StrUtil.blankToDefault(scoreSource, defaultScoreSource))
                .build();
    }

    public List<Content> toContents(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(this::toContent)
                .toList();
    }

    public Content toContent(RetrievedChunk chunk) {
        Metadata metadata = new Metadata();
        metadata.put(DOC_ID, StrUtil.blankToDefault(chunk.getDocId(), ""));
        metadata.put(CHUNK_ID, StrUtil.blankToDefault(chunk.getChunkId(), ""));
        metadata.put(TITLE, StrUtil.blankToDefault(chunk.getTitle(), ""));
        metadata.put(PATH, StrUtil.blankToDefault(chunk.getPath(), ""));
        metadata.put(SOURCE_TYPE, StrUtil.blankToDefault(chunk.getSourceType(), ""));
        metadata.put(PRIORITY, chunk.getPriority());
        metadata.put(TAGS, encodeMultiValue(chunk.getTags()));
        metadata.put(CODE_GEN_TYPES, encodeMultiValue(chunk.getCodeGenTypes()));
        metadata.put(CHECKSUM, StrUtil.blankToDefault(chunk.getChecksum(), ""));
        if (StrUtil.isNotBlank(chunk.getScoreSource())
                && (HybridRetrievalRouteMetadata.ROUTE_BM25.equals(chunk.getScoreSource())
                || HybridRetrievalRouteMetadata.ROUTE_DENSE.equals(chunk.getScoreSource()))) {
            metadata.put(HybridRetrievalRouteMetadata.RETRIEVAL_ROUTE, chunk.getScoreSource());
        }
        Map<ContentMetadata, Object> contentMetadata = new EnumMap<>(ContentMetadata.class);
        ContentMetadata scoreKey = "rerank".equals(chunk.getScoreSource())
                ? ContentMetadata.RERANKED_SCORE
                : ContentMetadata.SCORE;
        contentMetadata.put(scoreKey, chunk.getScore());
        return Content.from(TextSegment.from(StrUtil.blankToDefault(chunk.getContent(), ""), metadata), contentMetadata);
    }

    public Content tagRoute(Content content, String routeName) {
        Metadata metadata = content.textSegment().metadata().copy();
        metadata.put(HybridRetrievalRouteMetadata.RETRIEVAL_ROUTE, routeName);
        return Content.from(TextSegment.from(content.textSegment().text(), metadata), content.metadata());
    }

    private double extractScore(Content content) {
        Object rerankedScore = content.metadata().get(ContentMetadata.RERANKED_SCORE);
        if (rerankedScore instanceof Number number) {
            return number.doubleValue();
        }
        Object score = content.metadata().get(ContentMetadata.SCORE);
        return score instanceof Number number ? number.doubleValue() : 0D;
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
}
