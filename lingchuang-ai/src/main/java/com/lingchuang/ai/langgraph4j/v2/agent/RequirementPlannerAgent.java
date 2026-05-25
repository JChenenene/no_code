package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.ai.AiCodeGenTypeRoutingService;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.service.RequirementPlannerAiService;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 需求规划 Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequirementPlannerAgent {

    private static final String AGENT_NAME = "RequirementPlannerAgent";

    private final RequirementPlannerAiService requirementPlannerAiService;
    private final AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.PLANNING,
                "originalPromptLength=%d".formatted(resolveOriginalPrompt(sessionState).length()),
                "RequirementPlannerAiService"
        );
        String originalPrompt = resolveOriginalPrompt(sessionState);
        TaskSpec taskSpec;
        try {
            taskSpec = normalizeTaskSpec(requirementPlannerAiService.plan(originalPrompt), originalPrompt);
        } catch (Exception e) {
            log.warn("requestId={}, agent={}, planner 执行失败，使用回退策略: {}",
                    sessionState.getRequestId(), AGENT_NAME, e.getMessage());
            taskSpec = fallbackTaskSpec(originalPrompt);
        }
        sessionState.setTaskSpec(taskSpec);
        sessionState.finishAgentExecution(
                executionRecord,
                "SUCCESS",
                "targetType=%s, retrieval=%s, asset=%s, verification=%s".formatted(
                        taskSpec.getTargetCodeGenType(),
                        taskSpec.isNeedsRetrieval(),
                        taskSpec.isNeedsAssetPlanning(),
                        taskSpec.getVerificationLevel()),
                "unavailable"
        );
        log.info("requestId={}, agent={}, targetType={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                taskSpec.getTargetCodeGenType(),
                executionRecord.getDurationMs());
        return AgentSessionState.saveState(sessionState);
    }

    private TaskSpec normalizeTaskSpec(TaskSpec rawTaskSpec, String originalPrompt) {
        TaskSpec safeTaskSpec = rawTaskSpec == null ? TaskSpec.builder().build() : rawTaskSpec;
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(safeTaskSpec, originalPrompt);
        List<String> candidates = new ArrayList<>();
        if (CollUtil.isNotEmpty(safeTaskSpec.getCodeGenTypeCandidates())) {
            for (String candidate : safeTaskSpec.getCodeGenTypeCandidates()) {
                CodeGenTypeEnum matched = CodeGenTypeEnum.getEnumByValue(candidate);
                if (matched != null) {
                    candidates.add(matched.getValue());
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(codeGenTypeEnum.getValue());
        }
        List<String> requiredSkills = resolveRequiredSkills(safeTaskSpec, originalPrompt, codeGenTypeEnum);
        return TaskSpec.builder()
                .originalPrompt(originalPrompt)
                .goal(StrUtil.blankToDefault(safeTaskSpec.getGoal(), originalPrompt))
                .pageScope(StrUtil.blankToDefault(safeTaskSpec.getPageScope(), "未明确"))
                .technicalConstraints(CollUtil.emptyIfNull(safeTaskSpec.getTechnicalConstraints()))
                .acceptanceCriteria(CollUtil.isEmpty(safeTaskSpec.getAcceptanceCriteria())
                        ? List.of("生成结果应与需求一致", "输出应可直接运行或继续构建验证")
                        : safeTaskSpec.getAcceptanceCriteria())
                .requiredSkills(requiredSkills)
                .codeGenTypeCandidates(candidates)
                .targetCodeGenType(codeGenTypeEnum.getValue())
                .needsRetrieval(resolveNeedsRetrieval(safeTaskSpec, originalPrompt, codeGenTypeEnum))
                .needsAssetPlanning(resolveNeedsAssetPlanning(safeTaskSpec, originalPrompt, codeGenTypeEnum))
                .verificationLevel(resolveVerificationLevel(codeGenTypeEnum, safeTaskSpec, originalPrompt))
                .build();
    }

    private TaskSpec fallbackTaskSpec(String originalPrompt) {
        CodeGenTypeEnum codeGenTypeEnum = safeRouteCodeGenType(originalPrompt);
        return TaskSpec.builder()
                .originalPrompt(originalPrompt)
                .goal(originalPrompt)
                .pageScope("未明确")
                .technicalConstraints(List.of())
                .acceptanceCriteria(List.of("生成结果应与需求一致", "输出应可直接运行或继续构建验证"))
                .requiredSkills(resolveRequiredSkills(null, originalPrompt, codeGenTypeEnum))
                .codeGenTypeCandidates(List.of(codeGenTypeEnum.getValue()))
                .targetCodeGenType(codeGenTypeEnum.getValue())
                .needsRetrieval(resolveNeedsRetrieval(null, originalPrompt, codeGenTypeEnum))
                .needsAssetPlanning(resolveNeedsAssetPlanning(null, originalPrompt, codeGenTypeEnum))
                .verificationLevel(resolveVerificationLevel(codeGenTypeEnum, null, originalPrompt))
                .build();
    }

    private CodeGenTypeEnum resolveCodeGenType(TaskSpec taskSpec, String originalPrompt) {
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(taskSpec.getTargetCodeGenType());
        if (codeGenTypeEnum != null) {
            return codeGenTypeEnum;
        }
        if (CollUtil.isNotEmpty(taskSpec.getCodeGenTypeCandidates())) {
            for (String candidate : taskSpec.getCodeGenTypeCandidates()) {
                CodeGenTypeEnum matched = CodeGenTypeEnum.getEnumByValue(candidate);
                if (matched != null) {
                    return matched;
                }
            }
        }
        return safeRouteCodeGenType(originalPrompt);
    }

    private CodeGenTypeEnum safeRouteCodeGenType(String originalPrompt) {
        try {
            CodeGenTypeEnum routed = aiCodeGenTypeRoutingService.routeCodeGenType(originalPrompt);
            return routed == null ? CodeGenTypeEnum.HTML : routed;
        } catch (Exception e) {
            log.warn("RequirementPlannerAgent 路由代码生成类型失败，使用 HTML 回退: {}", e.getMessage());
            return CodeGenTypeEnum.HTML;
        }
    }

    private String resolveOriginalPrompt(AgentSessionState sessionState) {
        TaskSpec taskSpec = sessionState.getTaskSpec();
        return StrUtil.blankToDefault(taskSpec == null ? null : taskSpec.getOriginalPrompt(), "");
    }

    private boolean resolveNeedsRetrieval(TaskSpec taskSpec, String originalPrompt, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return true;
        }
        String combinedText = buildCombinedText(taskSpec, originalPrompt);
        return containsAny(combinedText,
                "品牌", "brand", "模板", "template", "视觉", "visual",
                "设计规范", "style guide", "design system", "业务规范", "规范");
    }

    private boolean resolveNeedsAssetPlanning(TaskSpec taskSpec, String originalPrompt, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return true;
        }
        String combinedText = buildCombinedText(taskSpec, originalPrompt);
        return containsAny(combinedText,
                "素材", "图片", "图像", "插画", "illustration", "logo",
                "图标", "icon", "diagram", "图表", "封面", "asset");
    }

    private String resolveVerificationLevel(CodeGenTypeEnum codeGenTypeEnum, TaskSpec taskSpec, String originalPrompt) {
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        }
        String combinedText = buildCombinedText(taskSpec, originalPrompt);
        if (containsAny(combinedText, "严格", "验收", "验证", "校验", "strict")) {
            return "standard";
        }
        return codeGenTypeEnum == CodeGenTypeEnum.MULTI_FILE ? "standard" : "basic";
    }

    private List<String> resolveRequiredSkills(TaskSpec taskSpec, String originalPrompt, CodeGenTypeEnum codeGenTypeEnum) {
        List<String> requiredSkills = new ArrayList<>();
        if (taskSpec != null && CollUtil.isNotEmpty(taskSpec.getRequiredSkills())) {
            for (String requiredSkill : taskSpec.getRequiredSkills()) {
                addSkillIfAbsent(requiredSkills, requiredSkill);
            }
        }
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            addSkillIfAbsent(requiredSkills, "vue-project");
        }
        if (resolveNeedsAssetPlanning(taskSpec, originalPrompt, codeGenTypeEnum)) {
            addSkillIfAbsent(requiredSkills, "asset-collection");
        }
        String combinedText = buildCombinedText(taskSpec, originalPrompt);
        if (containsAny(combinedText, "页面", "首页", "视觉", "设计", "美化", "ui", "layout", "responsive", "自我介绍")) {
            addSkillIfAbsent(requiredSkills, "design-ui");
        }
        if (containsAny(combinedText, "部署", "上线", "预览", "download", "deploy", "发布")) {
            addSkillIfAbsent(requiredSkills, "deployment");
        }
        return requiredSkills;
    }

    private void addSkillIfAbsent(List<String> requiredSkills, String skillId) {
        String normalizedSkillId = StrUtil.blankToDefault(skillId, "")
                .trim()
                .toLowerCase()
                .replace('_', '-');
        if (StrUtil.isBlank(normalizedSkillId) || requiredSkills.contains(normalizedSkillId)) {
            return;
        }
        requiredSkills.add(normalizedSkillId);
    }

    private String buildCombinedText(TaskSpec taskSpec, String originalPrompt) {
        StringBuilder builder = new StringBuilder(StrUtil.blankToDefault(originalPrompt, ""));
        if (taskSpec != null) {
            builder.append('\n').append(StrUtil.blankToDefault(taskSpec.getGoal(), ""));
            builder.append('\n').append(StrUtil.blankToDefault(taskSpec.getPageScope(), ""));
            if (CollUtil.isNotEmpty(taskSpec.getRequiredSkills())) {
                builder.append('\n').append(String.join(" ", taskSpec.getRequiredSkills()));
            }
            if (CollUtil.isNotEmpty(taskSpec.getTechnicalConstraints())) {
                builder.append('\n').append(String.join(" ", taskSpec.getTechnicalConstraints()));
            }
            if (CollUtil.isNotEmpty(taskSpec.getAcceptanceCriteria())) {
                builder.append('\n').append(String.join(" ", taskSpec.getAcceptanceCriteria()));
            }
        }
        return builder.toString().toLowerCase();
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
