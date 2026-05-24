package com.lingchuang.ai.service;

import com.lingchuang.ai.langgraph4j.CodeGenWorkflow;
import com.lingchuang.ai.langgraph4j.state.WorkflowContext;
import com.lingchuang.ai.langgraph4j.v2.CodeGenWorkflowV2;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流运行时服务，统一承接 V1 / V2 调用。
 */
@Service
@RequiredArgsConstructor
public class WorkflowRuntimeService {

    private final CodeGenWorkflow codeGenWorkflow;
    private final CodeGenWorkflowV2 codeGenWorkflowV2;

    public WorkflowContext executeWorkflow(String prompt) {
        return codeGenWorkflow.executeWorkflow(prompt);
    }

    public Flux<String> executeWorkflowWithFlux(String prompt) {
        return codeGenWorkflow.executeWorkflowWithFlux(prompt);
    }

    public SseEmitter executeWorkflowWithSse(String prompt) {
        return codeGenWorkflow.executeWorkflowWithSse(prompt);
    }

    public WorkflowV2Response executeWorkflowV2(String prompt) {
        return codeGenWorkflowV2.executeWorkflow(prompt);
    }

    public WorkflowV2Response executeWorkflowV2(String prompt, Long appId) {
        return codeGenWorkflowV2.executeWorkflow(prompt, appId);
    }

    public WorkflowV2Response executeWorkflowV2(String prompt, Long appId, String requestId) {
        return codeGenWorkflowV2.executeWorkflow(prompt, appId, requestId);
    }

    public WorkflowV2Response executeWorkflowV2(String prompt, Long appId, String requestId, Long workflowRunId, String workspacePath) {
        return codeGenWorkflowV2.executeWorkflow(prompt, appId, requestId, workflowRunId, workspacePath);
    }

    public Flux<String> executeWorkflowV2WithFlux(String prompt) {
        return codeGenWorkflowV2.executeWorkflowWithFlux(prompt);
    }

    public Flux<String> executeWorkflowV2WithFlux(String prompt, Long appId) {
        return codeGenWorkflowV2.executeWorkflowWithFlux(prompt, appId);
    }

    public Flux<String> executeWorkflowV2WithFlux(String prompt, Long appId, String requestId) {
        return codeGenWorkflowV2.executeWorkflowWithFlux(prompt, appId, requestId);
    }

    public Flux<String> executeWorkflowV2WithFlux(String prompt, Long appId, String requestId, Long workflowRunId, String workspacePath) {
        return codeGenWorkflowV2.executeWorkflowWithFlux(prompt, appId, requestId, workflowRunId, workspacePath);
    }

    public SseEmitter executeWorkflowV2WithSse(String prompt) {
        return codeGenWorkflowV2.executeWorkflowWithSse(prompt);
    }

    public SseEmitter executeWorkflowV2WithSse(String prompt, Long appId) {
        return codeGenWorkflowV2.executeWorkflowWithSse(prompt, appId);
    }

    public SseEmitter executeWorkflowV2WithSse(String prompt, Long appId, String requestId) {
        return codeGenWorkflowV2.executeWorkflowWithSse(prompt, appId, requestId);
    }

    public SseEmitter executeWorkflowV2WithSse(String prompt, Long appId, String requestId, Long workflowRunId, String workspacePath) {
        return codeGenWorkflowV2.executeWorkflowWithSse(prompt, appId, requestId, workflowRunId, workspacePath);
    }
}
