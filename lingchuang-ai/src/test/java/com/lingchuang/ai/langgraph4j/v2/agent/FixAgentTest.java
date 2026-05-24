package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.ReviewArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixAgentTest {

    private final FixAgent fixAgent = new FixAgent();

    @Test
    void shouldGenerateFixPlanFromReviewAndVerificationArtifacts() {
        AgentSessionState sessionState = AgentSessionState.builder()
                .attemptCount(0)
                .taskSpec(TaskSpec.builder()
                        .technicalConstraints(List.of("保持 Vue 3 结构"))
                        .acceptanceCriteria(List.of("构建必须通过"))
                        .build())
                .codeArtifact(CodeArtifact.builder()
                        .keyFiles(List.of("src/App.vue", "package.json"))
                        .build())
                .reviewArtifact(ReviewArtifact.builder()
                        .approved(false)
                        .canFix(true)
                        .blockerIssues(List.of("src/App.vue 中存在未处理的空状态"))
                        .majorIssues(List.of("App.vue 交互逻辑需要补全"))
                        .fixSuggestions(List.of("补充空状态渲染", "修复任务切换逻辑"))
                        .build())
                .verificationArtifact(VerificationArtifact.builder()
                        .passed(false)
                        .canFix(true)
                        .issues(List.of("package.json 中缺少 build 脚本"))
                        .summary("构建失败")
                        .build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        fixAgent.execute(messagesState);

        FixPlanArtifact fixPlanArtifact = sessionState.getFixPlanArtifact();
        assertEquals(1, sessionState.getAttemptCount());
        assertEquals("review+verification", fixPlanArtifact.getIssueSource());
        assertEquals("fix-attempt-1", fixPlanArtifact.getAttemptLabel());
        assertTrue(fixPlanArtifact.getTargetFiles().contains("src/App.vue"));
        assertTrue(fixPlanArtifact.getPatchInstructions().contains("补充空状态渲染"));
        assertTrue(fixPlanArtifact.getPatchInstructions().stream().anyMatch(item -> item.contains("构建失败")));
        assertTrue(fixPlanArtifact.getMustKeepConstraints().contains("保持 Vue 3 结构"));
        assertTrue(fixPlanArtifact.getMustKeepConstraints().contains("构建必须通过"));
        assertNull(sessionState.getReviewArtifact());
        assertNull(sessionState.getVerificationArtifact());
    }
}
