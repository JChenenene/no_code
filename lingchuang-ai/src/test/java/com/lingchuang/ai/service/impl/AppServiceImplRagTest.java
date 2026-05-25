package com.lingchuang.ai.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.lingchuang.ai.core.AiCodeGeneratorFacade;
import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.core.handler.StreamHandlerExecutor;
import com.lingchuang.ai.model.entity.App;
import com.lingchuang.ai.model.entity.ChatHistory;
import com.lingchuang.ai.model.entity.User;
import com.lingchuang.ai.model.entity.WorkflowArtifact;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.entity.WorkflowStep;
import com.lingchuang.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.model.enums.WorkflowRunStatusEnum;
import com.lingchuang.ai.model.vo.WorkflowRunDetailVO;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.rag.RagInvocationContext;
import com.lingchuang.ai.service.AppChatSummaryService;
import com.lingchuang.ai.service.ChatHistoryService;
import com.lingchuang.ai.service.ProjectDownloadService;
import com.lingchuang.ai.service.ScreenshotService;
import com.lingchuang.ai.service.WorkflowPersistenceService;
import com.lingchuang.ai.service.WorkflowRunService;
import com.lingchuang.ai.service.WorkflowRuntimeService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppServiceImplRagTest {

    @Spy
    @InjectMocks
    private AppServiceImpl appService;

    @Mock
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private AppChatSummaryService appChatSummaryService;

    @Mock
    private StreamHandlerExecutor streamHandlerExecutor;

    @Mock
    private WorkflowRuntimeService workflowRuntimeService;

    @Mock
    private WorkflowRunService workflowRunService;

    @Mock
    private WorkflowPersistenceService workflowPersistenceService;

    @Mock
    private GeneratedArtifactSupport generatedArtifactSupport;

    @Mock
    private VueProjectBuilder vueProjectBuilder;

    @Mock
    private ProjectDownloadService projectDownloadService;

    @Mock
    private ScreenshotService screenshotService;

    @TempDir
    private Path tempDir;

    @Test
    void shouldPassOriginalPromptAndExposeRagContextToAiServices() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.HTML.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        Flux<String> codeStream = Flux.just("<html></html>");
        List<ChatHistory> histories = List.of(ChatHistory.builder().message("历史消息").build());
        Page<ChatHistory> historyPage = new Page<>(1, 4, 1);
        historyPage.setRecords(histories);

        doReturn(app).when(appService).getById(1L);
        when(chatHistoryService.listAppChatHistoryByPage(1L, 4, null, loginUser))
                .thenReturn(historyPage);
        when(appChatSummaryService.getLatestSummaryText(1L, 1L))
                .thenReturn("用户偏好：页面主角叫小范");
        when(aiCodeGeneratorFacade.generateAndSaveCodeStream("原始需求", CodeGenTypeEnum.HTML, 1L))
                .thenAnswer(invocation -> {
                    RagInvocationContext context = RagInvocationContext.getCurrent();
                    Assertions.assertNotNull(context);
                    Assertions.assertEquals(1L, context.getAppId());
                    Assertions.assertEquals(CodeGenTypeEnum.HTML, context.getCodeGenType());
                    Assertions.assertEquals(histories, context.getRecentHistories());
                    Assertions.assertEquals("用户偏好：页面主角叫小范", context.getMemorySummary());
                    return codeStream;
                });
        when(streamHandlerExecutor.doExecute(codeStream, chatHistoryService, 1L, loginUser, CodeGenTypeEnum.HTML))
                .thenReturn(codeStream);

        List<String> chunks = appService.chatToGenCode(1L, "原始需求", loginUser).collectList().block();

        Assertions.assertEquals(List.of("<html></html>"), chunks);
        Assertions.assertNull(RagInvocationContext.getCurrent());
        verify(aiCodeGeneratorFacade).generateAndSaveCodeStream("原始需求", CodeGenTypeEnum.HTML, 1L);
    }

    @Test
    void shouldUseEmptyHistoryContextWhenHistoryIsEmpty() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.HTML.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        Flux<String> codeStream = Flux.just("<html></html>");
        Page<ChatHistory> historyPage = new Page<>(1, 4, 0);
        historyPage.setRecords(List.of());

        doReturn(app).when(appService).getById(1L);
        when(chatHistoryService.listAppChatHistoryByPage(1L, 4, null, loginUser))
                .thenReturn(historyPage);
        when(aiCodeGeneratorFacade.generateAndSaveCodeStream("原始需求", CodeGenTypeEnum.HTML, 1L))
                .thenAnswer(invocation -> {
                    RagInvocationContext context = RagInvocationContext.getCurrent();
                    Assertions.assertNotNull(context);
                    Assertions.assertEquals(List.of(), context.getRecentHistories());
                    return codeStream;
                });
        when(streamHandlerExecutor.doExecute(codeStream, chatHistoryService, 1L, loginUser, CodeGenTypeEnum.HTML))
                .thenReturn(codeStream);

        appService.chatToGenCode(1L, "原始需求", loginUser).collectList().block();

        verify(aiCodeGeneratorFacade).generateAndSaveCodeStream("原始需求", CodeGenTypeEnum.HTML, 1L);
    }

    @Test
    void shouldPersistWorkflowCompletionDetailAndSummary() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.VUE_PROJECT.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder().id(10L).requestId("req-1").build();
        String agentCompletedEvent = """
                event: agent_completed
                data: {"agentName":"BuildVerifyAgent","stage":"VERIFYING","status":"SUCCESS","inputSummary":"验证","outputSummary":"构建通过"}

                """;
        String completedEvent = """
                event: workflow_completed
                data: {"requestId":"req-1","finalStatus":"SUCCESS","verificationSummary":"构建通过","fixSummary":"无需修复","verificationIssues":[],"agentTimeline":[{"agentName":"BuildVerifyAgent","stage":"VERIFYING","status":"SUCCESS","inputSummary":"验证","outputSummary":"构建通过"}],"artifacts":{"verificationArtifact":{"passed":true,"summary":"构建通过"},"finalArtifact":{"finalStatus":"SUCCESS","summary":"执行成功"}}}

                """;

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.createRunningRun(1L, 1L, "生成 Vue 项目", CodeGenTypeEnum.VUE_PROJECT.getValue()))
                .thenReturn(workflowRun);
        when(generatedArtifactSupport.resolveRunWorkspaceDir(CodeGenTypeEnum.VUE_PROJECT, 1L, 10L))
                .thenReturn("D:/tmp/code_output/1/10/vue_project");
        when(generatedArtifactSupport.resolvePreviewUrl(1L, 10L, CodeGenTypeEnum.VUE_PROJECT))
                .thenReturn("/static/1/10/vue_project/dist/index.html");
        when(workflowRuntimeService.executeWorkflowV2WithFlux(
                "生成 Vue 项目",
                1L,
                "req-1",
                10L,
                "D:/tmp/code_output/1/10/vue_project"
        ))
                .thenReturn(Flux.just(
                        "event: workflow_start\ndata: {\"message\":\"开始执行\"}\n\n",
                        agentCompletedEvent,
                        completedEvent
                ));

        List<String> chunks = appService.chatToGenCodeV2(1L, "生成 Vue 项目", loginUser).collectList().block();

        Assertions.assertEquals(3, chunks.size());
        Assertions.assertTrue(chunks.get(0).contains("\"runId\":10"));
        Assertions.assertTrue(chunks.get(0).contains("\"requestId\":\"req-1\""));
        verify(workflowRunService).attachWorkspace(
                workflowRun,
                "D:/tmp/code_output/1/10/vue_project",
                "/static/1/10/vue_project/dist/index.html"
        );
        verify(workflowRuntimeService).executeWorkflowV2WithFlux(
                "生成 Vue 项目",
                1L,
                "req-1",
                10L,
                "D:/tmp/code_output/1/10/vue_project"
        );
        verify(workflowPersistenceService).saveWorkflowStep(eq(workflowRun), argThat(record ->
                "BuildVerifyAgent".equals(record.getAgentName())
                        && "SUCCESS".equals(record.getStatus())
        ), eq(1));
        verify(workflowPersistenceService).saveWorkflowResult(eq(workflowRun), argThat(response ->
                "req-1".equals(response.getRequestId())
                        && response.getAgentTimeline().size() == 1
                        && response.getArtifacts().getVerificationArtifact().isPassed()
        ));
        verify(workflowRunService).markSucceeded(eq(workflowRun), org.mockito.ArgumentMatchers.contains("\"finalStatus\":\"SUCCESS\""));
        verify(chatHistoryService).addChatMessage(
                1L,
                "V2 工作流执行成功\n\n验证: 构建通过\n\n修复: 无需修复",
                ChatHistoryMessageTypeEnum.AI.getValue(),
                1L
        );
        verify(workflowRunService).markSucceeded(eq(workflowRun), anyString());
    }

    @Test
    void shouldPersistFailedWorkflowCompletionDetailAndMarkRunFailed() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.VUE_PROJECT.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder().id(10L).requestId("req-1").build();
        String completedEvent = """
                event: workflow_completed
                data: {"requestId":"req-1","finalStatus":"VERIFICATION_FAILED","verificationSummary":"构建失败","verificationIssues":["缺少 index.html"],"agentTimeline":[{"agentName":"BuildVerifyAgent","stage":"VERIFYING","status":"FAILED","inputSummary":"验证","outputSummary":"缺少 index.html"}],"artifacts":{"verificationArtifact":{"passed":false,"summary":"构建失败","issues":["缺少 index.html"]},"finalArtifact":{"finalStatus":"VERIFICATION_FAILED","summary":"执行失败"}}}

                """;

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.createRunningRun(1L, 1L, "生成 Vue 项目", CodeGenTypeEnum.VUE_PROJECT.getValue()))
                .thenReturn(workflowRun);
        when(generatedArtifactSupport.resolveRunWorkspaceDir(CodeGenTypeEnum.VUE_PROJECT, 1L, 10L))
                .thenReturn("D:/tmp/code_output/1/10/vue_project");
        when(generatedArtifactSupport.resolvePreviewUrl(1L, 10L, CodeGenTypeEnum.VUE_PROJECT))
                .thenReturn("/static/1/10/vue_project/dist/index.html");
        when(workflowRuntimeService.executeWorkflowV2WithFlux(
                "生成 Vue 项目",
                1L,
                "req-1",
                10L,
                "D:/tmp/code_output/1/10/vue_project"
        ))
                .thenReturn(Flux.just(completedEvent));

        List<String> chunks = appService.chatToGenCodeV2(1L, "生成 Vue 项目", loginUser).collectList().block();

        Assertions.assertEquals(1, chunks.size());
        verify(workflowPersistenceService).saveWorkflowResult(eq(workflowRun), argThat(response ->
                "req-1".equals(response.getRequestId())
                        && response.getFinalStatus().name().equals("VERIFICATION_FAILED")
                        && response.getAgentTimeline().size() == 1
                        && !response.getArtifacts().getVerificationArtifact().isPassed()
        ));
        verify(workflowRunService).markFailed(eq(workflowRun), org.mockito.ArgumentMatchers.contains("V2 工作流执行未通过"));
        verify(chatHistoryService).addChatMessage(
                1L,
                "V2 工作流执行未通过\n\n验证: 构建失败\n\n问题:\n- 缺少 index.html",
                ChatHistoryMessageTypeEnum.AI.getValue(),
                1L
        );
    }

    @Test
    void shouldQueryWorkflowRunDetailStepsAndArtifactsByRunId() {
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder().id(10L).userId(1L).build();
        List<WorkflowStep> steps = List.of(WorkflowStep.builder().runId(10L).agentName("BuildVerifyAgent").build());
        List<WorkflowArtifact> artifacts = List.of(WorkflowArtifact.builder().runId(10L).artifactType("final").build());
        WorkflowRunDetailVO detail = WorkflowRunDetailVO.builder()
                .run(workflowRun)
                .steps(steps)
                .artifacts(artifacts)
                .build();

        when(workflowRunService.getById(10L)).thenReturn(workflowRun);
        when(workflowPersistenceService.buildDetail(workflowRun)).thenReturn(detail);
        when(workflowPersistenceService.listSteps(10L)).thenReturn(steps);
        when(workflowPersistenceService.listArtifacts(10L)).thenReturn(artifacts);

        Assertions.assertEquals(detail, appService.getWorkflowRunDetail(10L, loginUser));
        Assertions.assertEquals(steps, appService.listWorkflowRunSteps(10L, loginUser));
        Assertions.assertEquals(artifacts, appService.listWorkflowRunArtifacts(10L, loginUser));
    }

    @Test
    void shouldRejectNewV2WorkflowWhenAppAlreadyHasRunningRun() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.HTML.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun runningRun = WorkflowRun.builder()
                .id(10L)
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.getRunningRun(1L, 1L)).thenReturn(runningRun);

        Assertions.assertThrows(com.lingchuang.ai.exception.BusinessException.class,
                () -> appService.chatToGenCodeV2(1L, "再次生成", loginUser));
    }

    @Test
    void shouldNotMarkCancelledWorkflowRunSucceededAfterFluxCompletes() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.HTML.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(10L)
                .requestId("req-1")
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        String completedEvent = """
                event: workflow_completed
                data: {"requestId":"req-1","finalStatus":"SUCCESS","verificationSummary":"验证通过","agentTimeline":[],"artifacts":{"finalArtifact":{"finalStatus":"SUCCESS","summary":"执行成功"}}}

                """;

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.getRunningRun(1L, 1L)).thenReturn(null);
        when(workflowRunService.createRunningRun(1L, 1L, "生成 HTML", CodeGenTypeEnum.HTML.getValue()))
                .thenReturn(workflowRun);
        when(generatedArtifactSupport.resolveRunWorkspaceDir(CodeGenTypeEnum.HTML, 1L, 10L))
                .thenReturn("D:/tmp/code_output/1/10/html");
        when(generatedArtifactSupport.resolvePreviewUrl(1L, 10L, CodeGenTypeEnum.HTML))
                .thenReturn("/static/1/10/html/index.html");
        when(workflowRuntimeService.executeWorkflowV2WithFlux(
                "生成 HTML",
                1L,
                "req-1",
                10L,
                "D:/tmp/code_output/1/10/html"
        )).thenReturn(Flux.just(completedEvent));
        when(workflowRunService.isCancelled(10L)).thenReturn(true);

        appService.chatToGenCodeV2(1L, "生成 HTML", loginUser).collectList().block();

        org.mockito.Mockito.verify(workflowRunService, org.mockito.Mockito.never()).markSucceeded(eq(workflowRun), anyString());
        org.mockito.Mockito.verify(chatHistoryService, org.mockito.Mockito.never())
                .addChatMessage(eq(1L), anyString(), eq(ChatHistoryMessageTypeEnum.AI.getValue()), eq(1L));
    }

    @Test
    void shouldCancelRuntimeJobWhenCancellingWorkflowRun() {
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(10L)
                .userId(1L)
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();

        when(workflowRunService.getById(10L)).thenReturn(workflowRun);
        when(workflowRunService.cancelRun(workflowRun, "用户取消 V2 工作流")).thenReturn(true);
        when(workflowRuntimeService.cancelWorkflowJob(10L, "用户取消 V2 工作流")).thenReturn(true);

        Assertions.assertTrue(appService.cancelWorkflowRun(10L, loginUser));

        verify(workflowRuntimeService).cancelWorkflowJob(10L, "用户取消 V2 工作流");
    }

    @Test
    void shouldRetryFailedWorkflowRunWithNewRunWorkspace() {
        App app = App.builder().id(1L).userId(1L).codeGenType(CodeGenTypeEnum.HTML.getValue()).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun failedRun = WorkflowRun.builder()
                .id(10L)
                .appId(1L)
                .userId(1L)
                .prompt("生成 HTML")
                .codeGenType(CodeGenTypeEnum.HTML.getValue())
                .status(WorkflowRunStatusEnum.FAILED.getValue())
                .build();
        WorkflowRun retryRun = WorkflowRun.builder()
                .id(11L)
                .requestId("req-retry")
                .appId(1L)
                .userId(1L)
                .prompt("生成 HTML")
                .codeGenType(CodeGenTypeEnum.HTML.getValue())
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        String completedEvent = """
                event: workflow_completed
                data: {"requestId":"req-retry","finalStatus":"SUCCESS","verificationSummary":"验证通过","agentTimeline":[],"artifacts":{"finalArtifact":{"finalStatus":"SUCCESS","summary":"执行成功"}}}

                """;

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.getById(10L)).thenReturn(failedRun);
        when(workflowRunService.getRunningRun(1L, 1L)).thenReturn(null);
        when(workflowRunService.createRunningRun(1L, 1L, "生成 HTML", CodeGenTypeEnum.HTML.getValue()))
                .thenReturn(retryRun);
        when(generatedArtifactSupport.resolveRunWorkspaceDir(CodeGenTypeEnum.HTML, 1L, 11L))
                .thenReturn("D:/tmp/code_output/1/11/html");
        when(generatedArtifactSupport.resolvePreviewUrl(1L, 11L, CodeGenTypeEnum.HTML))
                .thenReturn("/static/1/11/html/index.html");
        when(workflowRuntimeService.executeWorkflowV2WithFlux(
                "生成 HTML",
                1L,
                "req-retry",
                11L,
                "D:/tmp/code_output/1/11/html"
        )).thenReturn(Flux.just(completedEvent));

        List<String> chunks = appService.retryWorkflowRun(10L, loginUser).collectList().block();

        Assertions.assertEquals(1, chunks.size());
        Assertions.assertTrue(chunks.get(0).contains("\"runId\":11"));
        verify(workflowPersistenceService).saveRetryParentArtifact(retryRun, failedRun);
        verify(workflowRuntimeService).registerWorkflowJob(11L, "req-retry");
        verify(workflowRunService).attachWorkspace(
                retryRun,
                "D:/tmp/code_output/1/11/html",
                "/static/1/11/html/index.html"
        );
        verify(workflowRunService).markSucceeded(eq(retryRun), anyString());
    }

    @Test
    void shouldRejectRetryForRunningWorkflowRun() {
        User loginUser = User.builder().id(1L).build();
        WorkflowRun runningRun = WorkflowRun.builder()
                .id(10L)
                .appId(1L)
                .userId(1L)
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();

        when(workflowRunService.getById(10L)).thenReturn(runningRun);

        Assertions.assertThrows(com.lingchuang.ai.exception.BusinessException.class,
                () -> appService.retryWorkflowRun(10L, loginUser));
        verify(workflowRunService, never()).createRunningRun(any(), any(), anyString(), anyString());
    }

    @Test
    void shouldQueryLatestSucceededWorkflowRunForPreview() {
        App app = App.builder().id(1L).userId(1L).build();
        User loginUser = User.builder().id(1L).build();
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(10L)
                .status("succeeded")
                .previewUrl("/static/1/10/html/")
                .build();

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.getLatestSucceededRun(1L, 1L)).thenReturn(workflowRun);

        Assertions.assertEquals(workflowRun, appService.getLatestSucceededWorkflowRun(1L, loginUser));
        verify(workflowRunService).getLatestSucceededRun(1L, 1L);
    }

    @Test
    void shouldDeployLatestSucceededWorkflowWorkspace() throws Exception {
        App app = App.builder()
                .id(1L)
                .userId(1L)
                .codeGenType(CodeGenTypeEnum.HTML.getValue())
                .deployKey("abc123")
                .build();
        User loginUser = User.builder().id(1L).build();
        Path workspace = Files.createDirectories(tempDir.resolve("code_output/1/10/html"));
        Files.writeString(workspace.resolve("index.html"), "<html>v2</html>");
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(10L)
                .workspacePath(workspace.toString())
                .status("succeeded")
                .build();

        doReturn(app).when(appService).getById(1L);
        doReturn(true).when(appService).updateById(any(App.class));
        when(workflowRunService.getLatestSucceededRun(1L, 1L)).thenReturn(workflowRun);
        setField(appService, "deployHost", "http://localhost:8123/api/deploy");

        String deployUrl = appService.deployApp(1L, loginUser);

        Assertions.assertEquals("http://localhost:8123/api/deploy/abc123/index.html", deployUrl);
        Assertions.assertTrue(Files.exists(Path.of("tmp/code_deploy", "abc123", "index.html")));
        verify(workflowRunService).getLatestSucceededRun(1L, 1L);
    }

    @Test
    void shouldDownloadLatestSucceededWorkflowWorkspace() throws Exception {
        App app = App.builder()
                .id(1L)
                .userId(1L)
                .codeGenType(CodeGenTypeEnum.HTML.getValue())
                .build();
        User loginUser = User.builder().id(1L).build();
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);
        Path workspace = Files.createDirectories(tempDir.resolve("code_output/1/10/html"));
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(10L)
                .workspacePath(workspace.toString())
                .status("succeeded")
                .build();

        doReturn(app).when(appService).getById(1L);
        when(workflowRunService.getLatestSucceededRun(1L, 1L)).thenReturn(workflowRun);

        appService.downloadAppCode(1L, loginUser, response);

        verify(projectDownloadService).downloadProjectAsZip(workspace.toString(), "1", response);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("注入测试字段失败", e);
        }
    }
}
