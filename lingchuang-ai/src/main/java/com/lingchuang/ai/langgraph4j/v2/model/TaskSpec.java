package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 规划 Agent 输出的结构化任务说明。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户原始输入。
     */
    private String originalPrompt;

    /**
     * 任务目标摘要。
     */
    private String goal;

    /**
     * 页面或项目范围。
     */
    private String pageScope;

    /**
     * 技术约束。
     */
    @Builder.Default
    private List<String> technicalConstraints = List.of();

    /**
     * 验收标准。
     */
    @Builder.Default
    private List<String> acceptanceCriteria = List.of();

    /**
     * 候选代码生成类型。
     */
    @Builder.Default
    private List<String> codeGenTypeCandidates = List.of();

    /**
     * 目标代码生成类型，使用 value 形式，例如 html / vue_project。
     */
    private String targetCodeGenType;

    /**
     * 是否需要检索增强。
     */
    private boolean needsRetrieval;

    /**
     * 是否需要素材规划。
     */
    private boolean needsAssetPlanning;

    /**
     * 验证等级，例如 basic / standard / build。
     */
    private String verificationLevel;
}
