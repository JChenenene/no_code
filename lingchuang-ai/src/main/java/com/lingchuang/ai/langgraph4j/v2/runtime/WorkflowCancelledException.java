package com.lingchuang.ai.langgraph4j.v2.runtime;

/**
 * V2 工作流协作式取消异常。
 */
public class WorkflowCancelledException extends RuntimeException {

    public WorkflowCancelledException(String message) {
        super(message);
    }
}
