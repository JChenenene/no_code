package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 修复计划产物。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FixPlanArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String issueSource;

    @Builder.Default
    private List<String> targetFiles = List.of();

    @Builder.Default
    private List<String> blockingIssues = List.of();

    @Builder.Default
    private List<String> patchInstructions = List.of();

    @Builder.Default
    private List<String> mustKeepConstraints = List.of();

    private String attemptLabel;
}
