package com.lingchuang.ai.langgraph4j.v2.state;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.AssetPlan;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FinalArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.RouteDecisionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * V2 多 Agent 黑板状态。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STATE_KEY = "agentSessionState";

    private String requestId;

    private String workflowVersion;

    private WorkflowStage currentStage;

    private String currentAgent;

    private int attemptCount;

    private int maxFixLoops;

    private Long appId;

    private Long workflowRunId;

    private String workspacePath;

    private TaskSpec taskSpec;

    private RetrievalBundle retrievalBundle;

    private AssetPlan assetPlan;

    private CodeArtifact codeArtifact;

    private ReviewArtifact reviewArtifact;

    private FixPlanArtifact fixPlanArtifact;

    private VerificationArtifact verificationArtifact;

    private FinalArtifact finalArtifact;

    @Builder.Default
    private List<AgentExecutionRecord> agentTimeline = new CopyOnWriteArrayList<>();

    @Builder.Default
    private List<RouteDecisionRecord> routeDecisions = new CopyOnWriteArrayList<>();

    public synchronized AgentExecutionRecord beginAgentExecution(String agentName,
                                                                 WorkflowStage stage,
                                                                 String inputSummary,
                                                                 String modelName) {
        this.currentStage = stage;
        this.currentAgent = agentName;
        if (agentTimeline == null) {
            agentTimeline = new CopyOnWriteArrayList<>();
        }
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName(agentName)
                .stage(stage)
                .startAt(LocalDateTime.now())
                .status("RUNNING")
                .inputSummary(StrUtil.blankToDefault(inputSummary, "无输入摘要"))
                .modelName(StrUtil.blankToDefault(modelName, "unavailable"))
                .tokenUsage("unavailable")
                .build();
        agentTimeline.add(record);
        return record;
    }

    public synchronized void finishAgentExecution(AgentExecutionRecord record,
                                                  String status,
                                                  String outputSummary,
                                                  String tokenUsage) {
        if (record == null) {
            return;
        }
        LocalDateTime endAt = LocalDateTime.now();
        record.setEndAt(endAt);
        record.setDurationMs(Duration.between(record.getStartAt(), endAt).toMillis());
        record.setStatus(StrUtil.blankToDefault(status, "SUCCESS"));
        record.setOutputSummary(StrUtil.blankToDefault(outputSummary, "无输出摘要"));
        record.setTokenUsage(StrUtil.blankToDefault(tokenUsage, "unavailable"));
    }

    public synchronized void recordRouteDecision(WorkflowStage stage,
                                                 String fromNode,
                                                 String decision,
                                                 String targetNode,
                                                 String reason) {
        if (routeDecisions == null) {
            routeDecisions = new CopyOnWriteArrayList<>();
        }
        routeDecisions.add(RouteDecisionRecord.builder()
                .stage(stage)
                .fromNode(fromNode)
                .decision(decision)
                .targetNode(targetNode)
                .reason(reason)
                .decidedAt(LocalDateTime.now())
                .build());
    }

    public static AgentSessionState getState(MessagesState<String> state) {
        return (AgentSessionState) state.data().get(STATE_KEY);
    }

    public static Map<String, Object> saveState(AgentSessionState sessionState) {
        return Map.of(STATE_KEY, sessionState);
    }
}
