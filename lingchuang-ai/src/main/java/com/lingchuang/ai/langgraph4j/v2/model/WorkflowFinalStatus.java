package com.lingchuang.ai.langgraph4j.v2.model;

/**
 * V2 工作流最终状态。
 */
public enum WorkflowFinalStatus {
    SUCCESS,
    REVIEW_FAILED,
    VERIFICATION_FAILED,
    GENERATION_FAILED,
    ERROR
}
