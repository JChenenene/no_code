package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.core.AiCodeGeneratorFacade;
import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.langgraph4j.v2.service.WorkflowV2PromptComposer;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatchAuthorAgentTest {

    @Mock
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Mock
    private GeneratedArtifactSupport generatedArtifactSupport;

    @Mock
    private WorkflowV2PromptComposer workflowV2PromptComposer;

    @InjectMocks
    private PatchAuthorAgent patchAuthorAgent;

    @Test
    void shouldComposePatchPromptAndRefreshCodeArtifact() {
        AgentSessionState sessionState = AgentSessionState.builder()
                .appId(100L)
                .attemptCount(1)
                .taskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .originalPrompt("生成一个首页")
                        .build())
                .codeArtifact(CodeArtifact.builder()
                        .generatedCodeDir("D:/tmp/old")
                        .keyFiles(List.of("index.html"))
                        .build())
                .fixPlanArtifact(FixPlanArtifact.builder()
                        .issueSource("review")
                        .targetFiles(List.of("index.html"))
                        .attemptLabel("fix-attempt-1")
                        .build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));
        when(workflowV2PromptComposer.composePatchPrompt(sessionState)).thenReturn("patch prompt");
        when(generatedArtifactSupport.resolveGeneratedCodeDir(CodeGenTypeEnum.HTML, 100L)).thenReturn("D:/tmp/generated");
        when(aiCodeGeneratorFacade.generateAndSaveCodeStream("patch prompt", CodeGenTypeEnum.HTML, 100L, false, "D:/tmp/generated", 100L))
                .thenReturn(Flux.empty());
        when(generatedArtifactSupport.directoryExists("D:/tmp/generated")).thenReturn(true);
        when(generatedArtifactSupport.hasAnyRelevantFiles("D:/tmp/generated")).thenReturn(true);
        when(generatedArtifactSupport.listKeyFiles("D:/tmp/generated", 20)).thenReturn(List.of("index.html"));

        patchAuthorAgent.execute(messagesState);

        verify(workflowV2PromptComposer).composePatchPrompt(sessionState);
        verify(aiCodeGeneratorFacade).generateAndSaveCodeStream("patch prompt", CodeGenTypeEnum.HTML, 100L, false, "D:/tmp/generated", 100L);
        assertEquals("D:/tmp/generated", sessionState.getCodeArtifact().getGeneratedCodeDir());
        assertEquals("Patch 修复完成，产物目录: D:/tmp/generated", sessionState.getCodeArtifact().getSummary());
        assertEquals(List.of("index.html"), sessionState.getCodeArtifact().getKeyFiles());
        assertNull(sessionState.getReviewArtifact());
        assertNull(sessionState.getVerificationArtifact());
        assertTrue(sessionState.getAgentTimeline().stream().anyMatch(record -> "PatchAuthorAgent".equals(record.getAgentName())));
    }
}
