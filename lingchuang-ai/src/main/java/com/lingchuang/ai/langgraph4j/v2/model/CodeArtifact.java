package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码生成产物。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodeArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long appId;

    private String authorPrompt;

    private String generatedCodeDir;

    @Builder.Default
    private List<String> keyFiles = List.of();

    private String summary;

    private String errorMessage;
}
