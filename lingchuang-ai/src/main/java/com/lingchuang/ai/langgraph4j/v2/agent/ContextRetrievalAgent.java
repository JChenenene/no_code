package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.rag.KnowledgeSearchService;
import com.lingchuang.ai.rag.RetrievalPromptExpansionOutcome;
import com.lingchuang.ai.rag.RetrievalPromptExpansionService;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索 Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextRetrievalAgent {

    private static final String AGENT_NAME = "ContextRetrievalAgent";

    private final KnowledgeSearchService knowledgeSearchService;
    private final RetrievalPromptExpansionService retrievalPromptExpansionService;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.RETRIEVAL,
                "targetType=%s".formatted(sessionState.getTaskSpec() == null ? "unknown" : sessionState.getTaskSpec().getTargetCodeGenType()),
                "RetrievalPromptExpansionService+KnowledgeSearchService"
        );
        TaskSpec taskSpec = sessionState.getTaskSpec();
        if (taskSpec != null && !taskSpec.isNeedsRetrieval()) {
            RetrievalBundle retrievalBundle = RetrievalBundle.builder()
                    .enabled(false)
                    .degraded(false)
                    .summary("规划阶段判定无需检索增强")
                    .build();
            sessionState.setRetrievalBundle(retrievalBundle);
            sessionState.finishAgentExecution(executionRecord, "SKIPPED", retrievalBundle.getSummary(), "unavailable");
            return AgentSessionState.saveState(sessionState);
        }
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(taskSpec);
        String baseQuery = buildRetrievalInput(taskSpec);
        RetrievalBundle retrievalBundle;
        String status = "SUCCESS";
        try {
            RetrievalPromptExpansionOutcome expansionOutcome =
                    retrievalPromptExpansionService.expandForDirectSearch(baseQuery, codeGenTypeEnum);
            String retrievalQuery = StrUtil.blankToDefault(expansionOutcome.getRetrievalQuery(), baseQuery);
            HybridRetrievalResult retrievalResult = knowledgeSearchService.search(retrievalQuery, codeGenTypeEnum, 6);
            List<RetrievedChunk> selectedChunks = selectChunks(retrievalResult);
            retrievalBundle = RetrievalBundle.builder()
                    .enabled(true)
                    .degraded(false)
                    .retrievalQuery(retrievalQuery)
                    .summary(buildSummary(selectedChunks))
                    .sources(selectedChunks.stream()
                            .map(chunk -> "%s | %s".formatted(
                                    StrUtil.blankToDefault(chunk.getTitle(), "未命名文档"),
                                    StrUtil.blankToDefault(chunk.getPath(), "unknown")))
                            .toList())
                    .snippets(selectedChunks.stream()
                            .map(chunk -> truncate(StrUtil.blankToDefault(chunk.getContent(), ""), 220))
                            .toList())
                    .build();
        } catch (Exception e) {
            log.warn("requestId={}, agent={}, 检索执行失败，降级继续: {}",
                    sessionState.getRequestId(), AGENT_NAME, e.getMessage());
            status = "DEGRADED";
            retrievalBundle = RetrievalBundle.builder()
                    .enabled(true)
                    .degraded(true)
                    .retrievalQuery(baseQuery)
                    .summary("检索降级，继续执行主流程")
                    .errorMessage(e.getMessage())
                    .build();
        }
        sessionState.setRetrievalBundle(retrievalBundle);
        sessionState.finishAgentExecution(
                executionRecord,
                status,
                "degraded=%s, snippets=%d".formatted(
                        retrievalBundle.isDegraded(),
                        retrievalBundle.getSnippets() == null ? 0 : retrievalBundle.getSnippets().size()),
                "unavailable"
        );
        log.info("requestId={}, agent={}, degraded={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                retrievalBundle.isDegraded(),
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

    private String buildRetrievalInput(TaskSpec taskSpec) {
        if (taskSpec == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(StrUtil.blankToDefault(taskSpec.getGoal(), taskSpec.getOriginalPrompt()));
        if (StrUtil.isNotBlank(taskSpec.getPageScope()) && !"未明确".equals(taskSpec.getPageScope())) {
            builder.append("\n页面范围: ").append(taskSpec.getPageScope());
        }
        if (CollUtil.isNotEmpty(taskSpec.getTechnicalConstraints())) {
            builder.append("\n技术约束: ").append(String.join("；", taskSpec.getTechnicalConstraints()));
        }
        if (CollUtil.isNotEmpty(taskSpec.getAcceptanceCriteria())) {
            builder.append("\n验收标准: ").append(String.join("；", taskSpec.getAcceptanceCriteria()));
        }
        return builder.toString().trim();
    }

    private List<RetrievedChunk> selectChunks(HybridRetrievalResult retrievalResult) {
        if (retrievalResult == null) {
            return List.of();
        }
        if (CollUtil.isNotEmpty(retrievalResult.getRerankedResults())) {
            return retrievalResult.getRerankedResults();
        }
        if (CollUtil.isNotEmpty(retrievalResult.getFusedResults())) {
            return retrievalResult.getFusedResults();
        }
        if (CollUtil.isNotEmpty(retrievalResult.getDenseResults())) {
            return retrievalResult.getDenseResults();
        }
        return CollUtil.emptyIfNull(retrievalResult.getBm25Results());
    }

    private String buildSummary(List<RetrievedChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return "未检索到额外上下文";
        }
        return "检索命中 %d 个上下文片段，可用于补充规范与模板约束".formatted(chunks.size());
    }

    private String truncate(String text, int maxLength) {
        if (StrUtil.isBlank(text) || text.length() <= maxLength) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxLength) + "...";
    }
}
