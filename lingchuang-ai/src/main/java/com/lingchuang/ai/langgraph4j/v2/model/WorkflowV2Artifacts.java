package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对外返回的 V2 工作流产物集合。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowV2Artifacts implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TaskSpec taskSpec;

    private RetrievalBundle retrievalBundle;

    private AssetPlan assetPlan;

    private CodeArtifact codeArtifact;

    private ReviewArtifact reviewArtifact;

    private FixPlanArtifact fixPlanArtifact;

    private VerificationArtifact verificationArtifact;

    private FinalArtifact finalArtifact;
}
