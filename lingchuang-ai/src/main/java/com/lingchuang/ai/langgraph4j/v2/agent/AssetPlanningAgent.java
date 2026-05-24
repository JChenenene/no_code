package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.ai.ImageCollectionPlanService;
import com.lingchuang.ai.langgraph4j.model.ImageCollectionPlan;
import com.lingchuang.ai.langgraph4j.model.ImageResource;
import com.lingchuang.ai.langgraph4j.tools.ImageSearchTool;
import com.lingchuang.ai.langgraph4j.tools.LogoGeneratorTool;
import com.lingchuang.ai.langgraph4j.tools.MermaidDiagramTool;
import com.lingchuang.ai.langgraph4j.tools.UndrawIllustrationTool;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.AssetPlan;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 素材规划 Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetPlanningAgent {

    private static final String AGENT_NAME = "AssetPlanningAgent";

    private final ImageCollectionPlanService imageCollectionPlanService;
    private final ImageSearchTool imageSearchTool;
    private final UndrawIllustrationTool undrawIllustrationTool;
    private final MermaidDiagramTool mermaidDiagramTool;
    private final LogoGeneratorTool logoGeneratorTool;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.ASSET_PLANNING,
                "goal=%s".formatted(sessionState.getTaskSpec() == null ? "unknown" : StrUtil.blankToDefault(sessionState.getTaskSpec().getGoal(), "unknown")),
                "ImageCollectionPlanService"
        );
        if (sessionState.getTaskSpec() != null && !sessionState.getTaskSpec().isNeedsAssetPlanning()) {
            AssetPlan assetPlan = AssetPlan.builder()
                    .degraded(false)
                    .assets(List.of())
                    .summary("规划阶段判定无需素材规划")
                    .build();
            sessionState.setAssetPlan(assetPlan);
            sessionState.finishAgentExecution(executionRecord, "SKIPPED", assetPlan.getSummary(), "unavailable");
            return AgentSessionState.saveState(sessionState);
        }
        AssetPlan assetPlan;
        String status = "SUCCESS";
        try {
            ImageCollectionPlan imageCollectionPlan = imageCollectionPlanService.planImageCollection(buildPlanPrompt(sessionState));
            if (imageCollectionPlan == null) {
                assetPlan = AssetPlan.builder()
                        .degraded(false)
                        .assets(List.of())
                        .summary("未规划额外素材需求")
                        .build();
            } else {
                assetPlan = collectAssets(imageCollectionPlan, sessionState.getRequestId());
            }
        } catch (Exception e) {
            log.warn("requestId={}, agent={}, 素材规划失败，降级继续: {}",
                    sessionState.getRequestId(), AGENT_NAME, e.getMessage());
            status = "DEGRADED";
            assetPlan = AssetPlan.builder()
                    .degraded(true)
                    .assets(List.of())
                    .summary("素材规划降级，继续执行主流程")
                    .errorMessage(e.getMessage())
                    .build();
        }
        sessionState.setAssetPlan(assetPlan);
        sessionState.finishAgentExecution(
                executionRecord,
                assetPlan.isDegraded() ? "DEGRADED" : status,
                "degraded=%s, assets=%d".formatted(
                        assetPlan.isDegraded(),
                        assetPlan.getAssets() == null ? 0 : assetPlan.getAssets().size()),
                "unavailable"
        );
        log.info("requestId={}, agent={}, degraded={}, assetCount={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                assetPlan.isDegraded(),
                assetPlan.getAssets() == null ? 0 : assetPlan.getAssets().size(),
                executionRecord.getDurationMs());
        return AgentSessionState.saveState(sessionState);
    }

    private AssetPlan collectAssets(ImageCollectionPlan imageCollectionPlan, String requestId) {
        List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
        AtomicBoolean degraded = new AtomicBoolean(false);

        if (CollUtil.isNotEmpty(imageCollectionPlan.getContentImageTasks())) {
            for (ImageCollectionPlan.ImageSearchTask task : imageCollectionPlan.getContentImageTasks()) {
                futures.add(CompletableFuture.supplyAsync(() -> imageSearchTool.searchContentImages(task.query()))
                        .exceptionally(ex -> handleAssetError(requestId, "content", degraded, ex)));
            }
        }
        if (CollUtil.isNotEmpty(imageCollectionPlan.getIllustrationTasks())) {
            for (ImageCollectionPlan.IllustrationTask task : imageCollectionPlan.getIllustrationTasks()) {
                futures.add(CompletableFuture.supplyAsync(() -> undrawIllustrationTool.searchIllustrations(task.query()))
                        .exceptionally(ex -> handleAssetError(requestId, "illustration", degraded, ex)));
            }
        }
        if (CollUtil.isNotEmpty(imageCollectionPlan.getDiagramTasks())) {
            for (ImageCollectionPlan.DiagramTask task : imageCollectionPlan.getDiagramTasks()) {
                futures.add(CompletableFuture.supplyAsync(() -> mermaidDiagramTool.generateMermaidDiagram(task.mermaidCode(), task.description()))
                        .exceptionally(ex -> handleAssetError(requestId, "diagram", degraded, ex)));
            }
        }
        if (CollUtil.isNotEmpty(imageCollectionPlan.getLogoTasks())) {
            for (ImageCollectionPlan.LogoTask task : imageCollectionPlan.getLogoTasks()) {
                futures.add(CompletableFuture.supplyAsync(() -> logoGeneratorTool.generateLogos(task.description()))
                        .exceptionally(ex -> handleAssetError(requestId, "logo", degraded, ex)));
            }
        }

        if (futures.isEmpty()) {
            return AssetPlan.builder()
                    .degraded(false)
                    .imageCollectionPlan(imageCollectionPlan)
                    .assets(List.of())
                    .summary("已完成素材规划，但本次无需额外素材")
                    .build();
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<ImageResource> assets = futures.stream()
                .flatMap(future -> future.join().stream())
                .toList();
        return AssetPlan.builder()
                .degraded(degraded.get())
                .imageCollectionPlan(imageCollectionPlan)
                .assets(assets)
                .summary("素材规划完成，共收集 %d 个素材资源".formatted(assets.size()))
                .errorMessage(degraded.get() ? "部分素材任务执行失败，已降级继续" : null)
                .build();
    }

    private List<ImageResource> handleAssetError(String requestId,
                                                 String taskType,
                                                 AtomicBoolean degraded,
                                                 Throwable throwable) {
        degraded.set(true);
        log.warn("requestId={}, agent={}, 素材子任务执行失败，taskType={}, message={}",
                requestId, AGENT_NAME, taskType, throwable.getMessage());
        return List.of();
    }

    private String buildPlanPrompt(AgentSessionState sessionState) {
        TaskSpec taskSpec = sessionState.getTaskSpec();
        StringBuilder builder = new StringBuilder();
        builder.append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getGoal(), ""));
        if (taskSpec != null && StrUtil.isNotBlank(taskSpec.getPageScope())) {
            builder.append("\n页面范围: ").append(taskSpec.getPageScope());
        }
        if (taskSpec != null && CollUtil.isNotEmpty(taskSpec.getTechnicalConstraints())) {
            builder.append("\n技术约束: ").append(String.join("；", taskSpec.getTechnicalConstraints()));
        }
        return builder.toString().trim();
    }
}
