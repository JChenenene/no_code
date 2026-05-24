package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 路由决策记录。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RouteDecisionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private WorkflowStage stage;

    private String fromNode;

    private String decision;

    private String targetNode;

    private String reason;

    private LocalDateTime decidedAt;
}
