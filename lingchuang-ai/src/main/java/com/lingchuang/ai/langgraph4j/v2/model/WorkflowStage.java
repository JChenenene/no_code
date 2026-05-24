package com.lingchuang.ai.langgraph4j.v2.model;

/**
 * V2 工作流阶段。
 */
public enum WorkflowStage {
    INIT,
    PLANNING,
    RETRIEVAL,
    ASSET_PLANNING,
    AUTHORING,
    REVIEWING,
    FIXING,
    VERIFYING,
    FINALIZING
}
