package com.lingchuang.ai.langgraph4j.v2.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryServiceTest {

    private final SkillRegistryService skillRegistryService = new SkillRegistryService();

    @Test
    void shouldListAvailableSkillsFromClasspath() {
        List<SkillDocument> skills = skillRegistryService.listSkills();

        assertTrue(skills.stream().anyMatch(skill ->
                "design-ui".equals(skill.getId()) && skill.getDescription().contains("界面")));
        assertTrue(skills.stream().anyMatch(skill ->
                "vue-project".equals(skill.getId()) && skill.getDescription().contains("Vue")));
        assertTrue(skillRegistryService.describeAvailableSkills().contains("- design-ui:"));
    }

    @Test
    void shouldLoadOnlyRequestedSkillsAndReportMissingOnes() {
        SkillLoadResult result = skillRegistryService.loadSkills(List.of("vue-project", "missing-skill", "vue-project"));

        assertEquals(List.of("vue-project"), result.getLoadedSkillIds());
        assertEquals(List.of("missing-skill"), result.getMissingSkillIds());
        assertEquals(1, result.getSkillContents().size());
        assertTrue(result.getSkillContents().getFirst().contains("<skill name=\"vue-project\">"));
        assertFalse(result.getSkillContents().getFirst().contains("<skill name=\"design-ui\">"));
    }
}
