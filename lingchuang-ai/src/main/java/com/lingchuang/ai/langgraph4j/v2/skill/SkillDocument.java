package com.lingchuang.ai.langgraph4j.v2.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 本地 Skill 文档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String description;

    private String content;
}
