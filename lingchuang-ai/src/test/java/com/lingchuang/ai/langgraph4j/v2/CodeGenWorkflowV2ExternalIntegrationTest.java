package com.lingchuang.ai.langgraph4j.v2;

import com.lingchuang.ai.langgraph4j.v2.model.CodeArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowFinalStatus;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowV2Response;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

/**
 * V2 工作流外部模型集成测试。
 */
@EnabledIfSystemProperty(named = "run.external.integration.tests", matches = "true")
@SpringBootTest(properties = {
        "spring.config.additional-location=optional:file:./application-local.yml",
        "rag.bootstrap.enabled=false"
})
class CodeGenWorkflowV2ExternalIntegrationTest {

    @Resource
    private CodeGenWorkflowV2 codeGenWorkflowV2;

    @Test
    void shouldGenerateHtmlSuccessfullyWithExternalModels() {
        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow(
                "创建一个简洁的个人主页，包含个人介绍、技能列表和联系方式，使用原生 HTML/CSS/JS，页面风格清爽");

        Assertions.assertNotNull(response);
        Assertions.assertEquals("v2", response.getWorkflowVersion());
        Assertions.assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        Assertions.assertNotNull(response.getArtifacts());
        Assertions.assertNotNull(response.getArtifacts().getTaskSpec());
        Assertions.assertEquals("html", response.getArtifacts().getTaskSpec().getTargetCodeGenType());

        CodeArtifact codeArtifact = response.getArtifacts().getCodeArtifact();
        Assertions.assertNotNull(codeArtifact);
        Assertions.assertNotNull(codeArtifact.getGeneratedCodeDir());
        Assertions.assertTrue(new File(codeArtifact.getGeneratedCodeDir()).isDirectory());
        Assertions.assertFalse(codeArtifact.getKeyFiles().isEmpty());
    }

    @Test
    void shouldGenerateVueProjectSuccessfullyWithExternalModels() {
        WorkflowV2Response response = codeGenWorkflowV2.executeWorkflow(
                "创建一个最小可运行的 Vue 3 待办应用，支持新增任务、切换完成状态、删除任务，界面简洁，不要引入多余依赖");

        Assertions.assertNotNull(response);
        Assertions.assertEquals("v2", response.getWorkflowVersion());
        Assertions.assertEquals(WorkflowFinalStatus.SUCCESS, response.getFinalStatus());
        Assertions.assertNotNull(response.getArtifacts());
        Assertions.assertNotNull(response.getArtifacts().getTaskSpec());
        Assertions.assertEquals("vue_project", response.getArtifacts().getTaskSpec().getTargetCodeGenType());

        CodeArtifact codeArtifact = response.getArtifacts().getCodeArtifact();
        Assertions.assertNotNull(codeArtifact);
        Assertions.assertNotNull(codeArtifact.getGeneratedCodeDir());
        Assertions.assertTrue(new File(codeArtifact.getGeneratedCodeDir()).isDirectory());

        VerificationArtifact verificationArtifact = response.getArtifacts().getVerificationArtifact();
        Assertions.assertNotNull(verificationArtifact);
        Assertions.assertTrue(verificationArtifact.isBuildRequired());
        Assertions.assertTrue(verificationArtifact.isPassed());
        Assertions.assertNotNull(verificationArtifact.getBuildResultDir());
        Assertions.assertTrue(new File(verificationArtifact.getBuildResultDir()).isDirectory());
    }
}
