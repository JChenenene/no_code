package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Review Agent 输出。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean approved;

    private boolean canFix;

    @Builder.Default
    private List<String> blockerIssues = List.of();

    @Builder.Default
    private List<String> majorIssues = List.of();

    @Builder.Default
    private List<String> minorIssues = List.of();

    @Builder.Default
    private List<String> fixSuggestions = List.of();

    private String reviewSummary;
}
