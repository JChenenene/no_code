package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WorkflowV2PromptComposerTest {

    private final WorkflowV2PromptComposer workflowV2PromptComposer =
            new WorkflowV2PromptComposer(mock(GeneratedArtifactSupport.class));

    @Test
    void shouldInjectOnlyLoadedSkillContentsIntoAuthorPrompt() {
        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder()
                        .originalPrompt("生成一个 Vue 自我介绍页")
                        .goal("生成自我介绍页")
                        .targetCodeGenType("vue_project")
                        .requiredSkills(List.of("vue-project"))
                        .build())
                .retrievalBundle(RetrievalBundle.builder()
                        .loadedSkills(List.of("vue-project"))
                        .skillContents(List.of("<skill name=\"vue-project\">\n必须包含 package.json 和 src/App.vue\n</skill>"))
                        .build())
                .build();

        String prompt = workflowV2PromptComposer.composeAuthorPrompt(sessionState);

        assertTrue(prompt.contains("## 按需加载 Skill"));
        assertTrue(prompt.contains("必须包含 package.json 和 src/App.vue"));
        assertFalse(prompt.contains("<skill name=\"design-ui\">"));
    }
}
