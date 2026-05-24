package com.lingchuang.ai.controller;

import com.lingchuang.ai.langgraph4j.state.WorkflowContext;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.service.WorkflowRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 控制器
 * 演示 LangGraph4j 工作流的流式输出功能
 */
@RestController
@RequestMapping("/workflow")
@Slf4j
@RequiredArgsConstructor
public class WorkflowSseController {

    private final WorkflowRuntimeService workflowRuntimeService;

    /**
     * 同步执行工作流
     */
    @PostMapping("/execute")
    public WorkflowContext executeWorkflow(@RequestParam String prompt) {
        log.info("收到同步工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflow(prompt);
    }

    /**
     * Flux 流式执行工作流
     */
    @GetMapping(value = "/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeWorkflowWithFlux(@RequestParam String prompt) {
        log.info("收到 Flux 工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflowWithFlux(prompt);
    }

    /**
     * SSE 流式执行工作流
     */
    @GetMapping(value = "/execute-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowWithSse(@RequestParam String prompt) {
        log.info("收到 SSE 工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflowWithSse(prompt);
    }

    /**
     * 同步执行 V2 工作流
     */
    @PostMapping("/v2/execute")
    public WorkflowV2Response executeWorkflowV2(@RequestParam String prompt) {
        log.info("收到 V2 同步工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflowV2(prompt);
    }

    /**
     * Flux 流式执行 V2 工作流
     */
    @GetMapping(value = "/v2/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeWorkflowV2WithFlux(@RequestParam String prompt) {
        log.info("收到 V2 Flux 工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflowV2WithFlux(prompt);
    }

    /**
     * SSE 流式执行 V2 工作流
     */
    @GetMapping(value = "/v2/execute-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowV2WithSse(@RequestParam String prompt) {
        log.info("收到 V2 SSE 工作流执行请求: {}", prompt);
        return workflowRuntimeService.executeWorkflowV2WithSse(prompt);
    }
}
