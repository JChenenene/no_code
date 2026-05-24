package com.lingchuang.ai.model.vo;

import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * V2 工作流运行详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private WorkflowRun run;

    @Builder.Default
    private List<WorkflowStep> steps = List.of();

    @Builder.Default
    private List<WorkflowArtifact> artifacts = List.of();
}
