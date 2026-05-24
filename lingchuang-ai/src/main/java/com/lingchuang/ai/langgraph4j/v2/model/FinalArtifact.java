package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 最终响应产物。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FinalArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private WorkflowFinalStatus finalStatus;

    private String summary;

    private String failureReason;
}
