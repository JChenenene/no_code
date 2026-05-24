package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * V2 工作流同步响应。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowV2Response implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String requestId;

    private String workflowVersion;

    private WorkflowFinalStatus finalStatus;

    private String currentAgent;

    private int attemptCount;

    private String reviewSummary;

    private String verificationSummary;

    private String fixSummary;

    @Builder.Default
    private List<String> verificationIssues = List.of();

    @Builder.Default
    private List<AgentExecutionRecord> agentTimeline = List.of();

    @Builder.Default
    private List<RouteDecisionRecord> routeDecisions = List.of();

    private WorkflowV2Artifacts artifacts;
}
