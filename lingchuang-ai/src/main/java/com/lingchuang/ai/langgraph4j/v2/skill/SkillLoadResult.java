package com.lingchuang.ai.langgraph4j.v2.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Skill 按需加载结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillLoadResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<String> loadedSkillIds = List.of();

    @Builder.Default
    private List<String> missingSkillIds = List.of();

    @Builder.Default
    private List<String> skillContents = List.of();
}
