package com.lingchuang.ai.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 检索结果片段。
 */
@Data
@Builder(toBuilder = true)
public class RetrievedChunk {

    private String docId;

    private String chunkId;

    private String title;

    private String path;

    private String sourceType;

    private int priority;

    private Set<String> tags;

    private Set<String> codeGenTypes;

    private String checksum;

    private String content;

    private double score;

    private String scoreSource;

    public static RetrievedChunk fromChunk(KnowledgeChunk chunk, double score, String scoreSource) {
        return RetrievedChunk.builder()
                .docId(chunk.getDocId())
                .chunkId(chunk.getChunkId())
                .title(chunk.getTitle())
                .path(chunk.getPath())
                .sourceType(chunk.getSourceType())
                .priority(chunk.getPriority())
                .tags(chunk.getTags())
                .codeGenTypes(chunk.getCodeGenTypes())
                .checksum(chunk.getChecksum())
                .content(chunk.getContent())
                .score(score)
                .scoreSource(scoreSource)
                .build();
    }
}
