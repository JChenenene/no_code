package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.service.BrowserVerificationService;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class BuildVerifyAgentTest {

    @Mock
    private VueProjectBuilder vueProjectBuilder;

    @Mock
    private BrowserVerificationService browserVerificationService;

    @TempDir
    Path tempDir;

    private BuildVerifyAgent buildVerifyAgent;

    @BeforeEach
    void setUp() {
        buildVerifyAgent = new BuildVerifyAgent(vueProjectBuilder, new GeneratedArtifactSupport(), browserVerificationService);
    }

    @Test
    void shouldFailHtmlVerificationWhenStaticReferenceMissing() throws IOException {
        Path htmlDir = Files.createDirectories(tempDir.resolve("html-app"));
        Files.writeString(htmlDir.resolve("index.html"), """
                <html>
                  <head><link rel="stylesheet" href="style.css"></head>
                  <body><img src="images/logo.png" /></body>
                </html>
                """);
        Files.writeString(htmlDir.resolve("style.css"), "body { color: #333; }");

        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("html").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(htmlDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertFalse(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.isCanFix());
        assertTrue(verificationArtifact.getIssues().stream().anyMatch(issue -> issue.contains("静态资源引用不存在")));
    }

    @Test
    void shouldIgnoreNonFileStaticReferences() throws IOException {
        Path htmlDir = Files.createDirectories(tempDir.resolve("html-app-with-links"));
        Files.writeString(htmlDir.resolve("index.html"), """
                <html>
                  <body>
                    <h1>小范</h1>
                    <a href="javascript:void(0);">按钮</a>
                    <a href="mailto:xiaofan@example.dev">邮箱</a>
                    <a href="tel:13812345678">电话</a>
                    <a href="#contact">联系</a>
                  </body>
                </html>
                """);

        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("html").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(htmlDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertTrue(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.getIssues().isEmpty());
    }

    @Test
    void shouldPassVueVerificationWhenBuildSucceeds() throws IOException {
        Path vueDir = Files.createDirectories(tempDir.resolve("vue-app"));
        Files.createDirectories(vueDir.resolve("src"));
        Files.createDirectories(vueDir.resolve("dist"));
        Files.writeString(vueDir.resolve("package.json"), "{\"name\":\"demo\",\"scripts\":{\"build\":\"vite build\"}}");
        Files.writeString(vueDir.resolve("src/main.ts"), "console.log('hello');");
        Files.writeString(vueDir.resolve("src/App.vue"), "<template><div>Hello</div></template>");
        Files.writeString(vueDir.resolve("dist/index.html"), "<html></html>");

        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("vue_project").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(vueDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));
        when(vueProjectBuilder.buildProject(vueDir.toString())).thenReturn(true);

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertTrue(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.isBuildRequired());
        assertTrue(verificationArtifact.getBuildResultDir().endsWith("dist"));
    }

    @Test
    void shouldFailWhenGeneratedArtifactIsTooLarge() throws IOException {
        Path htmlDir = Files.createDirectories(tempDir.resolve("large-html-app"));
        Files.writeString(htmlDir.resolve("index.html"), "<html><body>hello</body></html>");
        Files.write(htmlDir.resolve("large.bin"), new byte[2 * 1024 * 1024 + 1]);

        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("html").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(htmlDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertFalse(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.isCanFix());
        assertTrue(verificationArtifact.getIssues().stream().anyMatch(issue -> issue.contains("产物目录过大")));
        assertEquals("artifact_size", verificationArtifact.getFailureType());
    }

    @Test
    void shouldFailHtmlVerificationWhenFirstScreenHasNoVisibleText() throws IOException {
        Path htmlDir = Files.createDirectories(tempDir.resolve("empty-html-app"));
        Files.writeString(htmlDir.resolve("index.html"), """
                <html>
                  <head>
                    <style>body { min-height: 100vh; }</style>
                  </head>
                  <body>
                    <script>console.log('only script');</script>
                    <div>
                      &nbsp;
                    </div>
                  </body>
                </html>
                """);

        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder().targetCodeGenType("html").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(htmlDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertFalse(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.getIssues().stream().anyMatch(issue -> issue.contains("首屏内容为空")));
        assertEquals("first_screen_empty", verificationArtifact.getFailureType());
        assertTrue(verificationArtifact.getDetails().stream().anyMatch(detail -> detail.contains("首屏文本长度")));
    }

    @Test
    void shouldFailWhenBrowserVerificationFindsConsoleErrors() throws IOException {
        Path htmlDir = Files.createDirectories(tempDir.resolve("browser-error-html-app"));
        Files.writeString(htmlDir.resolve("index.html"), "<html><body><h1>hello</h1><script>console.error('boom')</script></body></html>");
        when(browserVerificationService.verify(any(), any())).thenReturn(BrowserVerificationResult.builder()
                .enabled(true)
                .passed(false)
                .previewUrl("http://localhost:8123/api/static/1/10/html/")
                .screenshotPath("D:/tmp/browser-screenshot.jpg")
                .screenshotUrl("/static/1/10/html/verification/browser-screenshot.jpg")
                .firstScreenTextLength(5)
                .consoleErrors(List.of("SEVERE boom"))
                .summary("浏览器验证失败，发现 1 个 console error")
                .errorMessage("console error: SEVERE boom")
                .failureType("console_error")
                .build());

        AgentSessionState sessionState = AgentSessionState.builder()
                .appId(1L)
                .workflowRunId(10L)
                .workspacePath(htmlDir.toString())
                .taskSpec(TaskSpec.builder().targetCodeGenType("html").build())
                .codeArtifact(CodeArtifact.builder().generatedCodeDir(htmlDir.toString()).build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        buildVerifyAgent.execute(messagesState);

        VerificationArtifact verificationArtifact = sessionState.getVerificationArtifact();
        assertFalse(verificationArtifact.isPassed());
        assertTrue(verificationArtifact.isCanFix());
        assertEquals("console_error", verificationArtifact.getFailureType());
        assertTrue(verificationArtifact.getIssues().stream().anyMatch(issue -> issue.contains("console error")));
        assertTrue(verificationArtifact.getDetails().stream().anyMatch(detail -> detail.contains("浏览器截图")));
        assertEquals("/static/1/10/html/verification/browser-screenshot.jpg",
                verificationArtifact.getBrowserVerification().getScreenshotUrl());
    }
}
