package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 构建与验证产物。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class VerificationArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean buildRequired;

    private boolean passed;

    private String buildResultDir;

    @Builder.Default
    private List<String> details = List.of();

    @Builder.Default
    private List<String> issues = List.of();

    private String summary;

    private String errorMessage;

    private boolean canFix;

    private String failureType;

    private BrowserVerificationResult browserVerification;
}
