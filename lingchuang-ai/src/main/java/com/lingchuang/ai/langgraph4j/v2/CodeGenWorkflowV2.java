package com.lingchuang.ai.langgraph4j.v2;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.json.JSONUtil;
import com.lingchuang.ai.exception.BusinessException;
import com.lingchuang.ai.exception.ErrorCode;
import com.lingchuang.ai.langgraph4j.v2.agent.AssetPlanningAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.BuildVerifyAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.CodeAuthorAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.ContextRetrievalAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.FinalResponseAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.FixAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.PatchAuthorAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.RequirementPlannerAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.ReviewAgent;
import com.lingchuang.ai.langgraph4j.v2.model.FinalArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.langgraph4j.v2.service.WorkflowV2ResponseMapper;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成 V2 多 Agent 工作流。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CodeGenWorkflowV2 {

    private final RequirementPlannerAgent requirementPlannerAgent;
    private final ContextRetrievalAgent contextRetrievalAgent;
    private final AssetPlanningAgent assetPlanningAgent;
    private final CodeAuthorAgent codeAuthorAgent;
    private final PatchAuthorAgent patchAuthorAgent;
    private final ReviewAgent reviewAgent;
    private final FixAgent fixAgent;
    private final BuildVerifyAgent buildVerifyAgent;
    private final FinalResponseAgent finalResponseAgent;
    private final WorkflowSupervisorDecider workflowSupervisorDecider;
    private final WorkflowV2ResponseMapper workflowV2ResponseMapper;

    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("planner", node_async(requirementPlannerAgent::execute))
                    .addNode("prepare_both", node_async(this::passThroughState))
                    .addNode("prepare_retrieval_only", node_async(this::passThroughState))
                    .addNode("prepare_asset_only", node_async(this::passThroughState))
                    .addNode("prepare_none", node_async(this::passThroughState))
                    .addNode("retrieval", node_async(contextRetrievalAgent::execute))
                    .addNode("asset_planning", node_async(assetPlanningAgent::execute))
                    .addNode("author", node_async(codeAuthorAgent::execute))
                    .addNode("patch_author", node_async(patchAuthorAgent::execute))
                    .addNode("review", node_async(reviewAgent::execute))
                    .addNode("fix", node_async(fixAgent::execute))
                    .addNode("build_verify", node_async(buildVerifyAgent::execute))
                    .addNode("final_response", node_async(finalResponseAgent::execute))
                    .addEdge(START, "planner")
                    .addConditionalEdges("planner",
                            edge_async(this::routeAfterPlanning),
                            Map.of(
                                    "prepare_both", "prepare_both",
                                    "prepare_retrieval_only", "prepare_retrieval_only",
                                    "prepare_asset_only", "prepare_asset_only",
                                    "prepare_none", "prepare_none"
                            ))
                    .addEdge("prepare_both", "retrieval")
                    .addEdge("prepare_both", "asset_planning")
                    .addEdge("prepare_retrieval_only", "retrieval")
                    .addEdge("prepare_asset_only", "asset_planning")
                    .addEdge("prepare_none", "author")
                    .addEdge("asset_planning", "author")
                    .addEdge("retrieval", "author")
                    .addEdge("author", "review")
                    .addConditionalEdges("review",
                            edge_async(this::routeAfterReview),
                            Map.of(
                                    "verify", "build_verify",
                                    "fix", "fix",
                                    "final", "final_response"
                            ))
                    .addEdge("fix", "patch_author")
                    .addEdge("patch_author", "review")
                    .addConditionalEdges("build_verify",
                            edge_async(this::routeAfterVerify),
                            Map.of(
                                    "fix", "fix",
                                    "final", "final_response"
                            ))
                    .addEdge("final_response", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "V2 工作流创建失败");
        }
    }

    public WorkflowV2Response executeWorkflow(String originalPrompt) {
        return executeWorkflow(originalPrompt, null);
    }

    public WorkflowV2Response executeWorkflow(String originalPrompt, Long appId) {
        return executeWorkflow(originalPrompt, appId, null);
    }

    public WorkflowV2Response executeWorkflow(String originalPrompt, Long appId, String requestId) {
        return executeWorkflow(originalPrompt, appId, requestId, null, null);
    }

    public WorkflowV2Response executeWorkflow(String originalPrompt,
                                              Long appId,
                                              String requestId,
                                              Long workflowRunId,
                                              String workspacePath) {
        AgentSessionState initialState = createInitialState(originalPrompt, appId, requestId, workflowRunId, workspacePath);
        try {
            AgentSessionState finalState = executeWorkflowInternal(initialState, null);
            return workflowV2ResponseMapper.toResponse(finalState);
        } catch (Exception e) {
            log.error("requestId={}, V2 工作流执行失败: {}", initialState.getRequestId(), e.getMessage(), e);
            return workflowV2ResponseMapper.toResponse(buildErrorState(initialState, e));
        }
    }

    public Flux<String> executeWorkflowWithFlux(String originalPrompt) {
        return executeWorkflowWithFlux(originalPrompt, null);
    }

    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        return executeWorkflowWithFlux(originalPrompt, appId, null);
    }

    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId, String requestId) {
        return executeWorkflowWithFlux(originalPrompt, appId, requestId, null, null);
    }

    public Flux<String> executeWorkflowWithFlux(String originalPrompt,
                                                Long appId,
                                                String requestId,
                                                Long workflowRunId,
                                                String workspacePath) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            AgentSessionState initialState = createInitialState(originalPrompt, appId, requestId, workflowRunId, workspacePath);
            try {
                sink.next(formatSseEvent("workflow_start", Map.of(
                        "requestId", initialState.getRequestId(),
                        "appId", initialState.getAppId(),
                        "workflowVersion", initialState.getWorkflowVersion(),
                        "message", "开始执行 V2 多 Agent 工作流"
                )));
                int[] timelineCursor = {0};
                int[] routeCursor = {0};
                AgentSessionState finalState = executeWorkflowInternal(initialState, (stepNumber, currentState) -> {
                    emitLatestAgentEvents(currentState, timelineCursor[0], sink::next);
                    timelineCursor[0] = currentState.getAgentTimeline() == null ? 0 : currentState.getAgentTimeline().size();
                    emitLatestRouteEvents(currentState, routeCursor[0], sink::next);
                    routeCursor[0] = currentState.getRouteDecisions() == null ? 0 : currentState.getRouteDecisions().size();
                    sink.next(formatSseEvent("step_completed", Map.of(
                            "stepNumber", stepNumber,
                            "requestId", currentState.getRequestId(),
                            "currentAgent", currentState.getCurrentAgent(),
                            "currentStage", currentState.getCurrentStage(),
                            "attemptCount", currentState.getAttemptCount()
                    )));
                });
                sink.next(formatSseEvent("workflow_completed", workflowV2ResponseMapper.toResponse(finalState)));
                sink.complete();
            } catch (Exception e) {
                log.error("requestId={}, V2 Flux 工作流执行失败: {}", initialState.getRequestId(), e.getMessage(), e);
                sink.next(formatSseEvent("workflow_error", Map.of(
                        "requestId", initialState.getRequestId(),
                        "error", e.getMessage(),
                        "message", "V2 工作流执行失败"
                )));
                sink.error(e);
            }
        }));
    }

    public SseEmitter executeWorkflowWithSse(String originalPrompt) {
        return executeWorkflowWithSse(originalPrompt, null);
    }

    public SseEmitter executeWorkflowWithSse(String originalPrompt, Long appId) {
        return executeWorkflowWithSse(originalPrompt, appId, null);
    }

    public SseEmitter executeWorkflowWithSse(String originalPrompt, Long appId, String requestId) {
        return executeWorkflowWithSse(originalPrompt, appId, requestId, null, null);
    }

    public SseEmitter executeWorkflowWithSse(String originalPrompt,
                                             Long appId,
                                             String requestId,
                                             Long workflowRunId,
                                             String workspacePath) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Thread.startVirtualThread(() -> {
            AgentSessionState initialState = createInitialState(originalPrompt, appId, requestId, workflowRunId, workspacePath);
            try {
                sendSseEvent(emitter, "workflow_start", Map.of(
                        "requestId", initialState.getRequestId(),
                        "appId", initialState.getAppId(),
                        "workflowVersion", initialState.getWorkflowVersion(),
                        "message", "开始执行 V2 多 Agent 工作流"
                ));
                int[] timelineCursor = {0};
                int[] routeCursor = {0};
                AgentSessionState finalState = executeWorkflowInternal(initialState, (stepNumber, currentState) -> {
                    emitLatestSseAgentEvents(currentState, timelineCursor[0], emitter);
                    timelineCursor[0] = currentState.getAgentTimeline() == null ? 0 : currentState.getAgentTimeline().size();
                    emitLatestSseRouteEvents(currentState, routeCursor[0], emitter);
                    routeCursor[0] = currentState.getRouteDecisions() == null ? 0 : currentState.getRouteDecisions().size();
                    sendSseEvent(emitter, "step_completed", Map.of(
                            "stepNumber", stepNumber,
                            "requestId", currentState.getRequestId(),
                            "currentAgent", currentState.getCurrentAgent(),
                            "currentStage", currentState.getCurrentStage(),
                            "attemptCount", currentState.getAttemptCount()
                    ));
                });
                sendSseEvent(emitter, "workflow_completed", workflowV2ResponseMapper.toResponse(finalState));
                emitter.complete();
            } catch (Exception e) {
                log.error("requestId={}, V2 SSE 工作流执行失败: {}", initialState.getRequestId(), e.getMessage(), e);
                sendSseEvent(emitter, "workflow_error", Map.of(
                        "requestId", initialState.getRequestId(),
                        "error", e.getMessage(),
                        "message", "V2 工作流执行失败"
                ));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private AgentSessionState executeWorkflowInternal(AgentSessionState initialState,
                                                      BiConsumer<Integer, AgentSessionState> stepConsumer) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        GraphRepresentation graphRepresentation = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("V2 工作流图:\n{}", graphRepresentation.content());
        AgentSessionState finalState = initialState;
        int stepCounter = 1;
        ExecutorService parallelPreparationExecutor = ExecutorBuilder.create()
                .setCorePoolSize(4)
                .setMaxPoolSize(8)
                .setWorkQueue(new LinkedBlockingQueue<>(32))
                .setThreadFactory(ThreadFactoryBuilder.create().setNamePrefix("v2-prepare-").build())
                .build();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor("prepare_both", parallelPreparationExecutor)
                .build();
        try {
            for (NodeOutput<MessagesState<String>> step : workflow.stream(
                    Map.of(AgentSessionState.STATE_KEY, initialState), runnableConfig)) {
                AgentSessionState currentState = AgentSessionState.getState(step.state());
                if (currentState != null) {
                    finalState = currentState;
                    log.info("requestId={}, stepNumber={}, currentAgent={}, currentStage={}",
                            currentState.getRequestId(), stepCounter, currentState.getCurrentAgent(), currentState.getCurrentStage());
                    if (stepConsumer != null) {
                        stepConsumer.accept(stepCounter, currentState);
                    }
                }
                stepCounter++;
            }
        } finally {
            parallelPreparationExecutor.shutdown();
        }
        return finalState;
    }

    private AgentSessionState createInitialState(String originalPrompt, Long appId) {
        return createInitialState(originalPrompt, appId, null);
    }

    private AgentSessionState createInitialState(String originalPrompt, Long appId, String requestId) {
        return createInitialState(originalPrompt, appId, requestId, null, null);
    }

    private AgentSessionState createInitialState(String originalPrompt,
                                                 Long appId,
                                                 String requestId,
                                                 Long workflowRunId,
                                                 String workspacePath) {
        String resolvedRequestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        return AgentSessionState.builder()
                .requestId(resolvedRequestId)
                .workflowVersion("v2")
                .currentStage(WorkflowStage.INIT)
                .currentAgent("Supervisor")
                .attemptCount(0)
                .maxFixLoops(2)
                .appId(resolveAppId(appId, resolvedRequestId))
                .workflowRunId(workflowRunId)
                .workspacePath(workspacePath)
                .taskSpec(TaskSpec.builder().originalPrompt(originalPrompt).build())
                .build();
    }

    private AgentSessionState buildErrorState(AgentSessionState baseState, Exception exception) {
        baseState.setCurrentStage(WorkflowStage.FINALIZING);
        baseState.setCurrentAgent("CodeGenWorkflowV2");
        baseState.setFinalArtifact(FinalArtifact.builder()
                .finalStatus(WorkflowFinalStatus.ERROR)
                .summary("V2 工作流执行异常")
                .failureReason(exception.getMessage())
                .build());
        return baseState;
    }

    private Long deriveAppId(String requestId) {
        try {
            String hex = requestId.replace("-", "");
            return Long.parseUnsignedLong(hex.substring(0, 15), 16);
        } catch (Exception e) {
            return Math.abs((long) requestId.hashCode()) + 1L;
        }
    }

    private Long resolveAppId(Long appId, String requestId) {
        return appId == null || appId <= 0 ? deriveAppId(requestId) : appId;
    }

    private String routeAfterPlanning(MessagesState<String> state) {
        return workflowSupervisorDecider.routeAfterPlanning(AgentSessionState.getState(state));
    }

    private String routeAfterReview(MessagesState<String> state) {
        return workflowSupervisorDecider.routeAfterReview(AgentSessionState.getState(state));
    }

    private String routeAfterVerify(MessagesState<String> state) {
        return workflowSupervisorDecider.routeAfterVerify(AgentSessionState.getState(state));
    }

    private Map<String, Object> passThroughState(MessagesState<String> state) {
        return AgentSessionState.saveState(AgentSessionState.getState(state));
    }

    private String formatSseEvent(String eventType, Object data) {
        try {
            return "event: " + eventType + "\ndata: " + JSONUtil.toJsonStr(data) + "\n\n";
        } catch (Exception e) {
            log.error("格式化 V2 SSE 事件失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventType, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventType).data(data));
        } catch (IOException e) {
            log.error("发送 V2 SSE 事件失败: {}", e.getMessage(), e);
        }
    }

    private void emitLatestAgentEvents(AgentSessionState state,
                                       int fromIndex,
                                       java.util.function.Consumer<String> consumer) {
        if (state == null || state.getAgentTimeline() == null) {
            return;
        }
        for (int i = fromIndex; i < state.getAgentTimeline().size(); i++) {
            consumer.accept(formatSseEvent("agent_started", state.getAgentTimeline().get(i)));
            consumer.accept(formatSseEvent("agent_completed", state.getAgentTimeline().get(i)));
        }
    }

    private void emitLatestSseAgentEvents(AgentSessionState state,
                                          int fromIndex,
                                          SseEmitter emitter) {
        if (state == null || state.getAgentTimeline() == null) {
            return;
        }
        for (int i = fromIndex; i < state.getAgentTimeline().size(); i++) {
            sendSseEvent(emitter, "agent_started", state.getAgentTimeline().get(i));
            sendSseEvent(emitter, "agent_completed", state.getAgentTimeline().get(i));
        }
    }

    private void emitLatestRouteEvents(AgentSessionState state,
                                       int fromIndex,
                                       java.util.function.Consumer<String> consumer) {
        if (state == null || state.getRouteDecisions() == null) {
            return;
        }
        for (int i = fromIndex; i < state.getRouteDecisions().size(); i++) {
            consumer.accept(formatSseEvent("route_decision", state.getRouteDecisions().get(i)));
        }
    }

    private void emitLatestSseRouteEvents(AgentSessionState state,
                                          int fromIndex,
                                          SseEmitter emitter) {
        if (state == null || state.getRouteDecisions() == null) {
            return;
        }
        for (int i = fromIndex; i < state.getRouteDecisions().size(); i++) {
            sendSseEvent(emitter, "route_decision", state.getRouteDecisions().get(i));
        }
    }
}
