package com.lingchuang.ai.langgraph4j.v2.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.model.ImageResource;
import com.lingchuang.ai.langgraph4j.v2.model.AssetPlan;
import com.lingchuang.ai.langgraph4j.v2.model.FixPlanArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.RetrievalBundle;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一组装 Author Agent 输入提示词。
 */
@Service
@RequiredArgsConstructor
public class WorkflowV2PromptComposer {

    private final GeneratedArtifactSupport generatedArtifactSupport;

    public String composeAuthorPrompt(AgentSessionState state) {
        TaskSpec taskSpec = state.getTaskSpec();
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(taskSpec);
        StringBuilder prompt = new StringBuilder("""
                你是代码生成执行 Agent。
                目标是基于结构化需求、检索上下文和素材计划，直接产出可运行、可继续验证的最终代码。
                除代码外，不要输出解释、分析过程或多余说明。

                """);
        prompt.append("## 用户原始需求\n")
                .append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getOriginalPrompt(), ""))
                .append("\n\n");
        prompt.append("## 结构化任务说明\n")
                .append("- 任务目标：").append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getGoal(), "按用户需求实现")).append("\n")
                .append("- 页面范围：").append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getPageScope(), "未明确")).append("\n")
                .append("- 输出模式：").append(codeGenTypeEnum.getValue()).append("\n\n");

        appendListSection(prompt, "技术约束", taskSpec == null ? List.of() : taskSpec.getTechnicalConstraints(), "无明确技术约束，按稳妥最佳实践实现");
        appendListSection(prompt, "验收标准", taskSpec == null ? List.of() : taskSpec.getAcceptanceCriteria(), "生成结果应与需求一致，并具备可运行性");

        appendRetrievalSection(prompt, state.getRetrievalBundle());
        appendSkillSection(prompt, state.getRetrievalBundle());
        appendAssetSection(prompt, state.getAssetPlan());

        prompt.append("""
                ## 交付要求
                1. 保持实现完整，避免省略关键文件或占位代码。
                2. 严格围绕当前需求输出，不要引入无关页面和无关依赖。
                3. 输出内容必须适配当前代码生成模式。
                """);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            prompt.append("""

                    4. 请输出完整的 Vue 项目文件集合，确保 package.json、入口文件和主要页面结构可用于后续构建验证。
                    """);
        } else {
            prompt.append("""

                    4. 请直接输出最终可用代码，不要只返回设计说明。
                    """);
        }
        return prompt.toString().trim();
    }

    public String composePatchPrompt(AgentSessionState state) {
        TaskSpec taskSpec = state.getTaskSpec();
        FixPlanArtifact fixPlanArtifact = state.getFixPlanArtifact();
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(taskSpec);
        StringBuilder prompt = new StringBuilder("""
                你是代码修复执行 Agent。
                目标是基于既有代码产物和修复计划，做最小但完整的增量修复。
                除代码外，不要输出解释、分析过程或多余说明。

                """);
        prompt.append("## 原始任务\n")
                .append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getOriginalPrompt(), ""))
                .append("\n\n");
        prompt.append("## 结构化任务说明\n")
                .append("- 任务目标：").append(StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getGoal(), "按用户需求实现")).append("\n")
                .append("- 输出模式：").append(codeGenTypeEnum.getValue()).append("\n")
                .append("- 当前修复轮次：").append(fixPlanArtifact == null ? "未知" : StrUtil.blankToDefault(fixPlanArtifact.getAttemptLabel(), "未知")).append("\n\n");

        appendListSection(prompt, "必须保持的约束",
                fixPlanArtifact == null ? List.of() : fixPlanArtifact.getMustKeepConstraints(),
                "继续满足原始需求和现有验收标准");
        appendFixPlanSection(prompt, fixPlanArtifact);
        appendCurrentCodeSection(prompt, state);

        prompt.append("""
                ## 修复要求
                1. 默认只修改修复计划中命中的目标文件。
                2. 如果目标文件为空，允许回退为整包修复，但不要扩张需求范围。
                3. 优先解决 blocker 和验证失败问题，再考虑小优化。
                4. 输出内容必须仍然满足当前代码生成模式。
                """);
        return prompt.toString().trim();
    }

    private void appendRetrievalSection(StringBuilder prompt, RetrievalBundle retrievalBundle) {
        if (retrievalBundle == null) {
            return;
        }
        if (StrUtil.isNotBlank(retrievalBundle.getMemorySummary())) {
            prompt.append("\n## 长期对话摘要记忆\n")
                    .append(truncate(retrievalBundle.getMemorySummary(), 1800))
                    .append("\n");
        }
        prompt.append("\n## 检索上下文\n");
        if (retrievalBundle.isDegraded()) {
            prompt.append("- 检索服务降级：").append(StrUtil.blankToDefault(retrievalBundle.getErrorMessage(), "未获取到额外上下文")).append("\n");
            return;
        }
        if (CollUtil.isEmpty(retrievalBundle.getSnippets())) {
            prompt.append("- 未检索到额外上下文，按通用最佳实践实现。\n");
            return;
        }
        prompt.append("- 检索摘要：").append(StrUtil.blankToDefault(retrievalBundle.getSummary(), "已提供参考片段")).append("\n");
        for (int i = 0; i < retrievalBundle.getSnippets().size(); i++) {
            prompt.append("- 参考片段 ").append(i + 1).append("：")
                    .append(retrievalBundle.getSnippets().get(i)).append("\n");
        }
    }

    private void appendSkillSection(StringBuilder prompt, RetrievalBundle retrievalBundle) {
        if (retrievalBundle == null || CollUtil.isEmpty(retrievalBundle.getSkillContents())) {
            return;
        }
        prompt.append("\n## 按需加载 Skill\n");
        for (String skillContent : retrievalBundle.getSkillContents()) {
            prompt.append(truncate(skillContent, 3000)).append("\n\n");
        }
        if (CollUtil.isNotEmpty(retrievalBundle.getMissingSkills())) {
            prompt.append("- 未找到以下 Skill，按通用最佳实践降级：")
                    .append(String.join("，", retrievalBundle.getMissingSkills()))
                    .append("\n");
        }
    }

    private void appendAssetSection(StringBuilder prompt, AssetPlan assetPlan) {
        if (assetPlan == null) {
            return;
        }
        prompt.append("\n## 素材计划\n");
        if (assetPlan.isDegraded()) {
            prompt.append("- 素材收集降级：").append(StrUtil.blankToDefault(assetPlan.getErrorMessage(), "未获取到额外素材")).append("\n");
            return;
        }
        if (CollUtil.isEmpty(assetPlan.getAssets())) {
            prompt.append("- 当前无需额外素材，可直接生成页面/项目。\n");
            return;
        }
        for (ImageResource asset : assetPlan.getAssets()) {
            prompt.append("- ")
                    .append(asset.getCategory().getText())
                    .append("：")
                    .append(StrUtil.blankToDefault(asset.getDescription(), "未命名素材"))
                    .append("（")
                    .append(StrUtil.blankToDefault(asset.getUrl(), "无 URL"))
                    .append("）\n");
        }
    }

    private void appendFixPlanSection(StringBuilder prompt, FixPlanArtifact fixPlanArtifact) {
        if (fixPlanArtifact == null) {
            return;
        }
        prompt.append("\n## 当前轮次必须修复的问题\n");
        prompt.append("- 问题来源：").append(StrUtil.blankToDefault(fixPlanArtifact.getIssueSource(), "unknown")).append("\n");
        for (String issue : CollUtil.emptyIfNull(fixPlanArtifact.getBlockingIssues())) {
            prompt.append("- Blocker：").append(issue).append("\n");
        }
        for (String suggestion : CollUtil.emptyIfNull(fixPlanArtifact.getPatchInstructions())) {
            prompt.append("- 修复建议：").append(suggestion).append("\n");
        }
        if (CollUtil.isNotEmpty(fixPlanArtifact.getTargetFiles())) {
            prompt.append("- 目标文件：").append(String.join("，", fixPlanArtifact.getTargetFiles())).append("\n");
        } else {
            prompt.append("- 目标文件：未能精确定位，允许回退为整包修复。\n");
        }
    }

    private void appendCurrentCodeSection(StringBuilder prompt, AgentSessionState state) {
        prompt.append("\n## 当前代码产物\n");
        if (state.getCodeArtifact() == null) {
            prompt.append("- 当前没有可用代码产物，只能基于任务说明重新修复。\n");
            return;
        }
        prompt.append("- 产物目录：")
                .append(StrUtil.blankToDefault(state.getCodeArtifact().getGeneratedCodeDir(), "未知"))
                .append("\n");
        if (CollUtil.isNotEmpty(state.getCodeArtifact().getKeyFiles())) {
            prompt.append("- 关键文件：").append(String.join("，", state.getCodeArtifact().getKeyFiles())).append("\n");
        }
        String codeContent = generatedArtifactSupport.readCodeContent(state.getCodeArtifact().getGeneratedCodeDir());
        if (StrUtil.isBlank(codeContent)) {
            prompt.append("- 未读取到现有代码内容摘要。\n");
            return;
        }
        prompt.append("\n### 现有代码摘要\n");
        prompt.append(truncate(codeContent, 12000)).append("\n");
    }

    private void appendListSection(StringBuilder prompt, String title, List<String> items, String fallback) {
        prompt.append("## ").append(title).append("\n");
        if (CollUtil.isEmpty(items)) {
            prompt.append("- ").append(fallback).append("\n\n");
            return;
        }
        for (String item : items) {
            prompt.append("- ").append(item).append("\n");
        }
        prompt.append("\n");
    }

    private CodeGenTypeEnum resolveCodeGenType(TaskSpec taskSpec) {
        if (taskSpec == null) {
            return CodeGenTypeEnum.HTML;
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(taskSpec.getTargetCodeGenType());
        return codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
    }

    private String truncate(String content, int maxLength) {
        if (StrUtil.isBlank(content) || content.length() <= maxLength) {
            return StrUtil.blankToDefault(content, "");
        }
        return content.substring(0, maxLength) + "\n...（内容已截断）";
    }
}
