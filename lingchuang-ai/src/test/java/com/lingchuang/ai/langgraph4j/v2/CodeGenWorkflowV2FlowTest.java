package com.lingchuang.ai.langgraph4j.v2;

import com.lingchuang.ai.langgraph4j.v2.agent.AssetPlanningAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.BuildVerifyAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.CodeAuthorAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.ContextRetrievalAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.FinalResponseAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.FixAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.PatchAuthorAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.RequirementPlannerAgent;
import com.lingchuang.ai.langgraph4j.v2.agent.ReviewAgent;
import com.lingchuang.ai.langgraph4j.v2.model.AssetPlan;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import com.lingchuang.ai.langgraph4j.v2.service.WorkflowV2ResponseMapper;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeGenWorkflowV2FlowTest {

    @Mock
    private RequirementPlannerAgent requirementPlannerAgent;

    @Mock
    private ContextRetrievalAgent contextRetrievalAgent;

    @Mock
    private AssetPlanningAgent assetPlanningAgent;

    @Mock
    private CodeAuthorAgent codeAuthorAgent;

    @Mock
    private PatchAuthorAgent patchAuthorAgent;

    @Mock
    private ReviewAgent reviewAgent;

    @Mock
    private BuildVerifyAgent buildVerifyAgent;

    private CodeGenWorkflowV2 codeGenWorkflowV2;

    @BeforeEach
    void setUp() {
        codeGenWorkflowV2 = new CodeGenWorkflowV2(
                requirementPlannerAgent,
                contextRetrievalAgent,
                assetPlanningAgent,
                codeAuthorAgent,
                patchAuthorAgent,
                reviewAgent,
                new FixAgent(),
                buildVerifyAgent,
                new FinalResponseAgent(),
                new WorkflowSupervisorDecider(),
                new WorkflowV2ResponseMapper()
        );
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(contextRetrievalAgent).execute(any());
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(assetPlanningAgent).execute(any());
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(codeAuthorAgent).execute(any());
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(patchAuthorAgent).execute(any());
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(reviewAgent).execute(any());
        lenient().doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> { }))
                .when(buildVerifyAgent).execute(any());
    }

    @Test
    void shouldLoopThroughFixAndPatchWhenReviewFailsThenPasses() {
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setTaskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .needsRetrieval(false)
                        .needsAssetPlanning(false)
                        .build()))).when(requirementPlannerAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/html")
                        .keyFiles(List.of("index.html"))
                        .summary("首轮生成完成")
                        .build()))).when(codeAuthorAgent).execute(any());
        AtomicInteger reviewCounter = new AtomicInteger();
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> {
                    if (reviewCounter.getAndIncrement() == 0) {
                        state.setReviewArtifact(ReviewArtifact.builder()
                                .approved(false)
                                .canFix(true)
                                .blockerIssues(List.of("index.html 需要补齐空状态"))
                                .fixSuggestions(List.of("修复首页空状态"))
                                .reviewSummary("首次 review 未通过")
                                .build());
                    } else {
                        state.setReviewArtifact(ReviewArtifact.builder()
                                .approved(true)
                                .reviewSummary("review 通过")
                                .build());
                    }
                })).when(reviewAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/html")
                        .keyFiles(List.of("index.html"))
                        .summary("Patch 修复完成")
                        .build()))).when(patchAuthorAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setVerificationArtifact(VerificationArtifact.builder()
                        .passed(true)
                        .summary("静态验证通过")
                        .build()))).when(buildVerifyAgent).execute(any());

        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow("生成一个 HTML 页面");

        assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        assertEquals(1, response.getAttemptCount());
        assertNotNull(response.getArtifacts().getFixPlanArtifact());
        verify(codeAuthorAgent, times(1)).execute(any());
        verify(patchAuthorAgent, times(1)).execute(any());
        verify(reviewAgent, times(2)).execute(any());
        verify(buildVerifyAgent, times(1)).execute(any());
    }

    @Test
    void shouldLoopThroughFixAndPatchWhenVerificationFailsThenPasses() {
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setTaskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .needsRetrieval(false)
                        .needsAssetPlanning(false)
                        .build()))).when(requirementPlannerAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/html")
                        .keyFiles(List.of("index.html", "script.js"))
                        .summary("首轮生成完成")
                        .build()))).when(codeAuthorAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setReviewArtifact(ReviewArtifact.builder()
                        .approved(true)
                        .reviewSummary("review 通过")
                        .build()))).when(reviewAgent).execute(any());
        AtomicInteger verifyCounter = new AtomicInteger();
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> {
                    if (verifyCounter.getAndIncrement() == 0) {
                        state.setVerificationArtifact(VerificationArtifact.builder()
                                .passed(false)
                                .canFix(true)
                                .issues(List.of("script.js 存在语法错误"))
                                .summary("静态验证失败")
                                .failureType("syntax")
                                .build());
                    } else {
                        state.setVerificationArtifact(VerificationArtifact.builder()
                                .passed(true)
                                .summary("静态验证通过")
                                .build());
                    }
                })).when(buildVerifyAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/html")
                        .keyFiles(List.of("index.html", "script.js"))
                        .summary("Patch 修复完成")
                        .build()))).when(patchAuthorAgent).execute(any());

        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow("生成一个 HTML 页面");

        assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        assertEquals(1, response.getAttemptCount());
        verify(patchAuthorAgent, times(1)).execute(any());
        verify(reviewAgent, times(2)).execute(any());
        verify(buildVerifyAgent, times(2)).execute(any());
    }

    @Test
    void shouldContinueWhenRetrievalAndAssetPlanningDegrade() {
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setTaskSpec(TaskSpec.builder()
                        .targetCodeGenType("vue_project")
                        .needsRetrieval(true)
                        .needsAssetPlanning(true)
                        .build()))).when(requirementPlannerAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setRetrievalBundle(RetrievalBundle.builder()
                        .enabled(true)
                        .degraded(true)
                        .summary("检索降级")
                        .build()))).when(contextRetrievalAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setAssetPlan(AssetPlan.builder()
                        .degraded(true)
                        .summary("素材规划降级")
                        .build()))).when(assetPlanningAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/vue")
                        .keyFiles(List.of("package.json", "src/App.vue"))
                        .summary("Vue 生成完成")
                        .build()))).when(codeAuthorAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setReviewArtifact(ReviewArtifact.builder()
                        .approved(true)
                        .reviewSummary("review 通过")
                        .build()))).when(reviewAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setVerificationArtifact(VerificationArtifact.builder()
                        .passed(true)
                        .summary("构建通过")
                        .build()))).when(buildVerifyAgent).execute(any());

        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow("生成一个 Vue 项目");

        assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        assertNotNull(response.getArtifacts().getRetrievalBundle());
        assertNotNull(response.getArtifacts().getAssetPlan());
        verify(contextRetrievalAgent, times(1)).execute(any());
        verify(assetPlanningAgent, times(1)).execute(any());
    }

    @Test
    void shouldUseProvidedAppIdInInitialWorkflowState() {
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setTaskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .needsRetrieval(false)
                        .needsAssetPlanning(false)
                        .build()))).when(requirementPlannerAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setCodeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/html")
                        .keyFiles(List.of("index.html"))
                        .summary("生成完成")
                        .build()))).when(codeAuthorAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> state.setReviewArtifact(ReviewArtifact.builder()
                        .approved(true)
                        .reviewSummary("review 通过")
                        .build()))).when(reviewAgent).execute(any());
        doAnswer(invocation -> mutateState(invocation.getArgument(0), state -> {
                    assertEquals(8899L, state.getAppId());
                    state.setVerificationArtifact(VerificationArtifact.builder()
                            .passed(true)
                            .summary("验证通过")
                            .build());
                })).when(buildVerifyAgent).execute(any());

        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow("生成一个 HTML 页面", 8899L);

        assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        assertEquals(8899L, response.getArtifacts().getCodeArtifact().getAppId());
    }

    private Map<String, Object> mutateState(MessagesState<String> messagesState, Consumer<AgentSessionState> consumer) {
        AgentSessionState sessionState = AgentSessionState.getState(messagesState);
        consumer.accept(sessionState);
        return AgentSessionState.saveState(sessionState);
    }
}
