package com.lingchuang.ai.service;

import com.lingchuang.ai.langgraph4j.CodeGenWorkflow;
import com.lingchuang.ai.langgraph4j.state.WorkflowContext;
import com.lingchuang.ai.langgraph4j.v2.CodeGenWorkflowV2;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRuntimeServiceTest {

    @Mock
    private CodeGenWorkflow codeGenWorkflow;

    @Mock
    private CodeGenWorkflowV2 codeGenWorkflowV2;

    @InjectMocks
    private WorkflowRuntimeService workflowRuntimeService;

    @Test
    void shouldDelegateV1ExecutionMethods() {
        WorkflowContext context = WorkflowContext.builder().currentStep("完成").build();
        Flux<String> flux = Flux.just("event");
        SseEmitter emitter = new SseEmitter();
        when(codeGenWorkflow.executeWorkflow("prompt")).thenReturn(context);
        when(codeGenWorkflow.executeWorkflowWithFlux("prompt")).thenReturn(flux);
        when(codeGenWorkflow.executeWorkflowWithSse("prompt")).thenReturn(emitter);

        assertSame(context, workflowRuntimeService.executeWorkflow("prompt"));
        assertSame(flux, workflowRuntimeService.executeWorkflowWithFlux("prompt"));
        assertSame(emitter, workflowRuntimeService.executeWorkflowWithSse("prompt"));
    }

    @Test
    void shouldDelegateV2ExecutionMethods() {
        WorkflowV2Response response = WorkflowV2Response.builder()
                .workflowVersion("v2")
                .finalStatus(WorkflowFinalStatus.SUCCESS)
                .build();
        Flux<String> flux = Flux.just("event");
        SseEmitter emitter = new SseEmitter();
        when(codeGenWorkflowV2.executeWorkflow("prompt")).thenReturn(response);
        when(codeGenWorkflowV2.executeWorkflowWithFlux("prompt")).thenReturn(flux);
        when(codeGenWorkflowV2.executeWorkflowWithSse("prompt")).thenReturn(emitter);

        assertSame(response, workflowRuntimeService.executeWorkflowV2("prompt"));
        assertSame(flux, workflowRuntimeService.executeWorkflowV2WithFlux("prompt"));
        assertSame(emitter, workflowRuntimeService.executeWorkflowV2WithSse("prompt"));
    }

    @Test
    void shouldDelegateV2ExecutionWithRunWorkspace() {
        Flux<String> flux = Flux.just("event");
        when(codeGenWorkflowV2.executeWorkflowWithFlux(
                "prompt",
                1001L,
                "req-1",
                9001L,
                "D:/tmp/code_output/1001/9001/vue_project"
        )).thenReturn(flux);

        assertSame(flux, workflowRuntimeService.executeWorkflowV2WithFlux(
                "prompt",
                1001L,
                "req-1",
                9001L,
                "D:/tmp/code_output/1001/9001/vue_project"
        ));
        verify(codeGenWorkflowV2).executeWorkflowWithFlux(
                "prompt",
                1001L,
                "req-1",
                9001L,
                "D:/tmp/code_output/1001/9001/vue_project"
        );
    }
}
