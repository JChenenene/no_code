package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.skill.SkillLoadResult;
import com.lingchuang.ai.langgraph4j.v2.skill.SkillRegistryService;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.rag.KnowledgeSearchService;
import com.lingchuang.ai.rag.RetrievalPromptExpansionOutcome;
import com.lingchuang.ai.rag.RetrievalPromptExpansionService;
import com.lingchuang.ai.rag.model.HybridRetrievalResult;
import com.lingchuang.ai.rag.model.RetrievedChunk;
import com.lingchuang.ai.service.AppChatSummaryService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextRetrievalAgentTest {

    @Mock
    private KnowledgeSearchService knowledgeSearchService;

    @Mock
    private RetrievalPromptExpansionService retrievalPromptExpansionService;

    @Mock
    private SkillRegistryService skillRegistryService;

    @Mock
    private AppChatSummaryService appChatSummaryService;

    @Test
    void shouldLoadRequiredSkillsEvenWhenRagRetrievalIsSkipped() {
        ContextRetrievalAgent agent = new ContextRetrievalAgent(
                knowledgeSearchService,
                retrievalPromptExpansionService,
                skillRegistryService,
                appChatSummaryService
        );
        AgentSessionState sessionState = AgentSessionState.builder()
                .taskSpec(TaskSpec.builder()
                        .targetCodeGenType("html")
                        .needsRetrieval(false)
                        .requiredSkills(List.of("vue-project"))
                        .build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));
        when(skillRegistryService.loadSkills(List.of("vue-project"))).thenReturn(SkillLoadResult.builder()
                .loadedSkillIds(List.of("vue-project"))
                .skillContents(List.of("<skill name=\"vue-project\">\nVue 项目生成规范\n</skill>"))
                .build());

        agent.execute(messagesState);

        RetrievalBundle retrievalBundle = sessionState.getRetrievalBundle();
        assertFalse(retrievalBundle.isDegraded());
        assertEquals(List.of("vue-project"), retrievalBundle.getLoadedSkills());
        assertEquals(List.of("<skill name=\"vue-project\">\nVue 项目生成规范\n</skill>"), retrievalBundle.getSkillContents());
        verifyNoInteractions(knowledgeSearchService, retrievalPromptExpansionService);
    }

    @Test
    void shouldIncludePersistentSummaryWhenBuildingRetrievalContext() {
        ContextRetrievalAgent agent = new ContextRetrievalAgent(
                knowledgeSearchService,
                retrievalPromptExpansionService,
                skillRegistryService,
                appChatSummaryService
        );
        AgentSessionState sessionState = AgentSessionState.builder()
                .appId(1001L)
                .taskSpec(TaskSpec.builder()
                        .originalPrompt("生成一个小范自我介绍页")
                        .goal("生成一个自我介绍页面")
                        .targetCodeGenType("html")
                        .needsRetrieval(true)
                        .build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));
        when(appChatSummaryService.getLatestSummaryText(1001L, null))
                .thenReturn("用户偏好：页面主角叫小范，风格极简。");
        when(retrievalPromptExpansionService.expandForDirectSearch(org.mockito.ArgumentMatchers.contains("页面主角叫小范"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(RetrievalPromptExpansionOutcome.builder()
                        .retrievalQuery("小范 自我介绍 极简 页面")
                        .build());
        when(knowledgeSearchService.search("小范 自我介绍 极简 页面", com.lingchuang.ai.model.enums.CodeGenTypeEnum.HTML, 6))
                .thenReturn(HybridRetrievalResult.builder()
                        .rerankedResults(List.of(RetrievedChunk.builder()
                                .title("自我介绍页面规范")
                                .path("knowledge/design.md")
                                .content("自我介绍页面应突出姓名、简介和联系入口。")
                                .build()))
                        .build());

        agent.execute(messagesState);

        RetrievalBundle retrievalBundle = sessionState.getRetrievalBundle();
        assertEquals("小范 自我介绍 极简 页面", retrievalBundle.getRetrievalQuery());
        assertEquals("用户偏好：页面主角叫小范，风格极简。", retrievalBundle.getMemorySummary());
        assertTrue(retrievalBundle.getSnippets().getFirst().contains("自我介绍页面"));
    }
}
