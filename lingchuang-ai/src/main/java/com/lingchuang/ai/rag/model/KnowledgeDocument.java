package com.lingchuang.ai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 知识文档。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private String docId;

    private String title;

    private String path;

    private String sourceType;

    private int priority;

    private Set<String> tags;

    private Set<String> codeGenTypes;

    private String checksum;

    private String content;
}
