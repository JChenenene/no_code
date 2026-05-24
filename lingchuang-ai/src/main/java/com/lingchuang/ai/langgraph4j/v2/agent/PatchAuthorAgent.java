package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.core.AiCodeGeneratorFacade;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.langgraph4j.v2.service.WorkflowV2PromptComposer;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 增量修复代码产物的 Patch Author Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatchAuthorAgent {

    private static final String AGENT_NAME = "PatchAuthorAgent";

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;
    private final GeneratedArtifactSupport generatedArtifactSupport;
    private final WorkflowV2PromptComposer workflowV2PromptComposer;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.AUTHORING,
                "attempt=%d".formatted(sessionState.getAttemptCount()),
                "AiCodeGeneratorFacade"
        );
        sessionState.setReviewArtifact(null);
        sessionState.setVerificationArtifact(null);
        sessionState.setFinalArtifact(null);

        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(sessionState.getTaskSpec());
        String patchPrompt = workflowV2PromptComposer.composePatchPrompt(sessionState);
        String generatedCodeDir = resolveGeneratedCodeDir(sessionState, codeGenTypeEnum);
        CodeArtifact.CodeArtifactBuilder artifactBuilder = CodeArtifact.builder()
                .appId(sessionState.getAppId())
                .authorPrompt(patchPrompt)
                .generatedCodeDir(generatedCodeDir);
        try {
            boolean buildAfterGenerate = false;
            Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                    patchPrompt,
                    codeGenTypeEnum,
                    sessionState.getAppId(),
                    buildAfterGenerate,
                    generatedCodeDir,
                    resolveToolMemoryId(sessionState));
            codeStream.blockLast(Duration.ofMinutes(10));
            if (!generatedArtifactSupport.directoryExists(generatedCodeDir)
                    || !generatedArtifactSupport.hasAnyRelevantFiles(generatedCodeDir)) {
                artifactBuilder.summary("Patch 修复结束，但未检测到有效代码产物")
                        .errorMessage("未找到有效的修复后代码目录或关键文件");
            } else {
                List<String> keyFiles = generatedArtifactSupport.listKeyFiles(generatedCodeDir, 20);
                artifactBuilder.keyFiles(keyFiles)
                        .summary("Patch 修复完成，产物目录: %s".formatted(generatedCodeDir));
            }
        } catch (Exception e) {
            log.error("requestId={}, agent={}, Patch 修复失败: {}",
                    sessionState.getRequestId(), AGENT_NAME, e.getMessage(), e);
            artifactBuilder.summary("Patch 修复失败")
                    .errorMessage(e.getMessage());
        }
        CodeArtifact codeArtifact = artifactBuilder.build();
        sessionState.setCodeArtifact(codeArtifact);
        sessionState.finishAgentExecution(
                executionRecord,
                StrUtil.isBlank(codeArtifact.getErrorMessage()) ? "SUCCESS" : "FAILED",
                StrUtil.blankToDefault(codeArtifact.getSummary(), "Patch 修复完成"),
                "unavailable"
        );
        log.info("requestId={}, agent={}, generatedDir={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                generatedCodeDir,
                executionRecord.getDurationMs());
        return AgentSessionState.saveState(sessionState);
    }

    private CodeGenTypeEnum resolveCodeGenType(TaskSpec taskSpec) {
        if (taskSpec == null) {
            return CodeGenTypeEnum.HTML;
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(taskSpec.getTargetCodeGenType());
        return codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
    }

    private String resolveGeneratedCodeDir(AgentSessionState sessionState, CodeGenTypeEnum codeGenTypeEnum) {
        if (StrUtil.isNotBlank(sessionState.getWorkspacePath())) {
            return sessionState.getWorkspacePath();
        }
        return generatedArtifactSupport.resolveGeneratedCodeDir(codeGenTypeEnum, sessionState.getAppId());
    }

    private Long resolveToolMemoryId(AgentSessionState sessionState) {
        return sessionState.getWorkflowRunId() == null ? sessionState.getAppId() : sessionState.getWorkflowRunId();
    }
}
