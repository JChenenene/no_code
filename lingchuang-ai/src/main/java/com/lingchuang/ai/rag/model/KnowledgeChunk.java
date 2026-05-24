package com.lingchuang.ai.rag.model;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 文档切片。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

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

    public boolean matches(CodeGenTypeEnum codeGenType) {
        return codeGenTypes == null
                || codeGenTypes.isEmpty()
                || codeGenTypes.contains("all")
                || codeGenTypes.contains(codeGenType.getValue());
    }
}
