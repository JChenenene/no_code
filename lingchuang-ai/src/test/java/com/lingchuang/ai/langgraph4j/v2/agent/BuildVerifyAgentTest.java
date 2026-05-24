package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildVerifyAgentTest {

    @Mock
    private VueProjectBuilder vueProjectBuilder;

    @TempDir
    Path tempDir;

    private BuildVerifyAgent buildVerifyAgent;

    @BeforeEach
    void setUp() {
        buildVerifyAgent = new BuildVerifyAgent(vueProjectBuilder, new GeneratedArtifactSupport());
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
}
