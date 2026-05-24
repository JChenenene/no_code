package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 执行记录。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentName;

    private WorkflowStage stage;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private long durationMs;

    private String status;

    private String inputSummary;

    private String outputSummary;

    private String modelName;

    private String tokenUsage;
}
