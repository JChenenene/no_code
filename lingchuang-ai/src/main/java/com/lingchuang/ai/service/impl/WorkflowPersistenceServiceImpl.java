package com.lingchuang.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Artifacts;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.mapper.WorkflowArtifactMapper;
import com.lingchuang.ai.mapper.WorkflowStepMapper;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;
import com.lingchuang.ai.service.WorkflowPersistenceService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * V2 工作流步骤与产物持久化服务实现。
 */
@Service
@RequiredArgsConstructor
public class WorkflowPersistenceServiceImpl implements WorkflowPersistenceService {

    private final WorkflowStepMapper workflowStepMapper;
    private final WorkflowArtifactMapper workflowArtifactMapper;

    @Override
    public void saveWorkflowResult(WorkflowRun workflowRun, WorkflowV2Response response) {
        if (workflowRun == null || workflowRun.getId() == null || response == null) {
            return;
        }
        workflowStepMapper.deleteByQuery(QueryWrapper.create().eq("runId", workflowRun.getId()));
        workflowArtifactMapper.deleteByQuery(QueryWrapper.create().eq("runId", workflowRun.getId()));
        saveSteps(workflowRun, response);
        saveArtifacts(workflowRun, response);
    }

    @Override
    public void saveWorkflowStep(WorkflowRun workflowRun, AgentExecutionRecord record, int stepNumber) {
        if (workflowRun == null || workflowRun.getId() == null || record == null || stepNumber <= 0) {
            return;
        }
        WorkflowStep step = buildWorkflowStep(
                workflowRun,
                record,
                StrUtil.blankToDefault(workflowRun.getRequestId(), null),
                stepNumber
        );
        workflowStepMapper.insert(step);
    }

    @Override
    public WorkflowRunDetailVO buildDetail(WorkflowRun workflowRun) {
        if (workflowRun == null || workflowRun.getId() == null) {
            return WorkflowRunDetailVO.builder().run(workflowRun).build();
        }
        return WorkflowRunDetailVO.builder()
                .run(workflowRun)
                .steps(listSteps(workflowRun.getId()))
                .artifacts(listArtifacts(workflowRun.getId()))
                .build();
    }

    @Override
    public List<WorkflowStep> listSteps(Long runId) {
        if (runId == null) {
            return List.of();
        }
        List<WorkflowStep> steps = workflowStepMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .orderBy("stepNumber", true));
        return CollUtil.emptyIfNull(steps);
    }

    @Override
    public List<WorkflowArtifact> listArtifacts(Long runId) {
        if (runId == null) {
            return List.of();
        }
        List<WorkflowArtifact> artifacts = workflowArtifactMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .orderBy("createTime", true));
        return CollUtil.emptyIfNull(artifacts);
    }

    private void saveSteps(WorkflowRun workflowRun, WorkflowV2Response response) {
        List<AgentExecutionRecord> timeline = CollUtil.emptyIfNull(response.getAgentTimeline());
        int stepNumber = 1;
        for (AgentExecutionRecord record : timeline) {
            if (record == null) {
                continue;
            }
            workflowStepMapper.insert(buildWorkflowStep(
                    workflowRun,
                    record,
                    StrUtil.blankToDefault(response.getRequestId(), workflowRun.getRequestId()),
                    stepNumber++
            ));
        }
    }

    private WorkflowStep buildWorkflowStep(WorkflowRun workflowRun,
                                           AgentExecutionRecord record,
                                           String requestId,
                                           int stepNumber) {
        return WorkflowStep.builder()
                .runId(workflowRun.getId())
                .requestId(requestId)
                .stepNumber(stepNumber)
                .agentName(record.getAgentName())
                .stage(record.getStage() == null ? null : record.getStage().name().toLowerCase(Locale.ROOT))
                .status(normalizeStatus(record.getStatus()))
                .inputSummary(record.getInputSummary())
                .outputSummary(record.getOutputSummary())
                .errorMessage(isFailed(record.getStatus()) ? record.getOutputSummary() : null)
                .startedTime(record.getStartAt())
                .finishedTime(record.getEndAt())
                .durationMs(record.getDurationMs())
                .build();
    }

    private void saveArtifacts(WorkflowRun workflowRun, WorkflowV2Response response) {
        WorkflowV2Artifacts artifacts = response.getArtifacts();
        if (artifacts == null) {
            return;
        }
        List<WorkflowArtifact> records = new ArrayList<>();
        addArtifact(records, workflowRun, "task_spec",
                artifacts.getTaskSpec() == null ? null : artifacts.getTaskSpec().getGoal(),
                null, artifacts.getTaskSpec());
        addArtifact(records, workflowRun, "retrieval",
                artifacts.getRetrievalBundle() == null ? null : artifacts.getRetrievalBundle().getSummary(),
                null, artifacts.getRetrievalBundle());
        addArtifact(records, workflowRun, "asset",
                artifacts.getAssetPlan() == null ? null : artifacts.getAssetPlan().getSummary(),
                null, artifacts.getAssetPlan());
        CodeArtifact codeArtifact = artifacts.getCodeArtifact();
        addArtifact(records, workflowRun, "code",
                codeArtifact == null ? null : codeArtifact.getSummary(),
                codeArtifact == null ? null : codeArtifact.getGeneratedCodeDir(),
                codeArtifact);
        addArtifact(records, workflowRun, "review",
                artifacts.getReviewArtifact() == null ? null : artifacts.getReviewArtifact().getReviewSummary(),
                null, artifacts.getReviewArtifact());
        addArtifact(records, workflowRun, "fix",
                artifacts.getFixPlanArtifact() == null ? null : artifacts.getFixPlanArtifact().getAttemptLabel(),
                null, artifacts.getFixPlanArtifact());
        addArtifact(records, workflowRun, "verification",
                artifacts.getVerificationArtifact() == null ? null : artifacts.getVerificationArtifact().getSummary(),
                artifacts.getVerificationArtifact() == null ? null : artifacts.getVerificationArtifact().getBuildResultDir(),
                artifacts.getVerificationArtifact());
        addArtifact(records, workflowRun, "final",
                artifacts.getFinalArtifact() == null ? null : artifacts.getFinalArtifact().getSummary(),
                null, artifacts.getFinalArtifact());
        for (WorkflowArtifact record : records) {
            workflowArtifactMapper.insert(record);
        }
    }

    private void addArtifact(List<WorkflowArtifact> records,
                             WorkflowRun workflowRun,
                             String artifactType,
                             String summary,
                             String path,
                             Object payload) {
        if (payload == null) {
            return;
        }
        records.add(WorkflowArtifact.builder()
                .runId(workflowRun.getId())
                .artifactType(artifactType)
                .summary(summary)
                .path(path)
                .jsonContent(JSONUtil.toJsonStr(payload))
                .build());
    }

    private String normalizeStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return "succeeded";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> "succeeded";
            case "FAILED", "ERROR" -> "failed";
            case "SKIPPED" -> "skipped";
            case "DEGRADED" -> "degraded";
            case "RUNNING" -> "running";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    private boolean isFailed(String status) {
        return "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status);
    }
}
