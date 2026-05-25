package com.lingchuang.ai.langgraph4j.v2.agent;

import com.lingchuang.ai.langgraph4j.ai.ImageCollectionPlanService;
import com.lingchuang.ai.langgraph4j.model.ImageCollectionPlan;
import com.lingchuang.ai.langgraph4j.tools.ImageSearchTool;
import com.lingchuang.ai.langgraph4j.tools.LogoGeneratorTool;
import com.lingchuang.ai.langgraph4j.tools.MermaidDiagramTool;
import com.lingchuang.ai.langgraph4j.tools.UndrawIllustrationTool;
import com.lingchuang.ai.langgraph4j.v2.model.AssetPlan;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetPlanningAgentTest {

    @Mock
    private ImageCollectionPlanService imageCollectionPlanService;

    @Mock
    private ImageSearchTool imageSearchTool;

    @Mock
    private UndrawIllustrationTool undrawIllustrationTool;

    @Mock
    private MermaidDiagramTool mermaidDiagramTool;

    @Mock
    private LogoGeneratorTool logoGeneratorTool;

    private AssetPlanningAgent assetPlanningAgent;

    @BeforeEach
    void setUp() {
        assetPlanningAgent = new AssetPlanningAgent(
                imageCollectionPlanService,
                imageSearchTool,
                undrawIllustrationTool,
                mermaidDiagramTool,
                logoGeneratorTool
        );
        ReflectionTestUtils.setField(assetPlanningAgent, "mermaidCliCommand", "missing-mmdc-for-test");
    }

    @Test
    void shouldDegradeBeforeCallingToolsWhenPlannedAssetDependenciesAreMissing() {
        ImageCollectionPlan imageCollectionPlan = new ImageCollectionPlan();
        imageCollectionPlan.setContentImageTasks(List.of(new ImageCollectionPlan.ImageSearchTask("profile portrait")));
        imageCollectionPlan.setDiagramTasks(List.of(new ImageCollectionPlan.DiagramTask("graph TD; A-->B", "流程图")));
        imageCollectionPlan.setLogoTasks(List.of(new ImageCollectionPlan.LogoTask("小范个人品牌")));
        when(imageCollectionPlanService.planImageCollection(anyString())).thenReturn(imageCollectionPlan);

        AgentSessionState sessionState = AgentSessionState.builder()
                .requestId("test-request")
                .taskSpec(TaskSpec.builder()
                        .goal("生成小范自我介绍页面")
                        .needsAssetPlanning(true)
                        .build())
                .build();
        MessagesState<String> messagesState = mock(MessagesState.class);
        when(messagesState.data()).thenReturn(Map.of(AgentSessionState.STATE_KEY, sessionState));

        assetPlanningAgent.execute(messagesState);

        AssetPlan assetPlan = sessionState.getAssetPlan();
        assertNotNull(assetPlan);
        assertTrue(assetPlan.isDegraded());
        assertTrue(assetPlan.getErrorMessage().contains("Pexels API Key"));
        assertTrue(assetPlan.getErrorMessage().contains("DashScope API Key"));
        assertTrue(assetPlan.getErrorMessage().contains("Mermaid CLI"));
        verify(imageSearchTool, never()).searchContentImages(anyString());
        verify(mermaidDiagramTool, never()).generateMermaidDiagram(anyString(), anyString());
        verify(logoGeneratorTool, never()).generateLogos(anyString());
    }
}
