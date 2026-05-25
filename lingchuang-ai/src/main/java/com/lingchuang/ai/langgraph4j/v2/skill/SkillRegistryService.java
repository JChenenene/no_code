package com.lingchuang.ai.langgraph4j.v2.skill;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 Skill 注册表，提供低成本目录和按需正文加载。
 */
@Service
@Slf4j
public class SkillRegistryService {

    private static final String SKILL_PATTERN = "classpath*:skills/*/SKILL.md";

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public List<SkillDocument> listSkills() {
        return new ArrayList<>(loadSkillMap().values());
    }

    public String describeAvailableSkills() {
        List<SkillDocument> skills = listSkills();
        if (skills.isEmpty()) {
            return "无可用 Skill";
        }
        StringBuilder builder = new StringBuilder();
        for (SkillDocument skill : skills) {
            builder.append("- ")
                    .append(skill.getId())
                    .append(": ")
                    .append(StrUtil.blankToDefault(skill.getDescription(), "无描述"))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    public SkillLoadResult loadSkills(List<String> requiredSkills) {
        if (CollUtil.isEmpty(requiredSkills)) {
            return SkillLoadResult.builder().build();
        }
        Map<String, SkillDocument> skillMap = loadSkillMap();
        List<String> loadedSkillIds = new ArrayList<>();
        List<String> missingSkillIds = new ArrayList<>();
        List<String> skillContents = new ArrayList<>();
        for (String requiredSkill : requiredSkills) {
            String normalizedId = normalizeSkillId(requiredSkill);
            if (StrUtil.isBlank(normalizedId) || loadedSkillIds.contains(normalizedId) || missingSkillIds.contains(normalizedId)) {
                continue;
            }
            SkillDocument skill = skillMap.get(normalizedId);
            if (skill == null) {
                missingSkillIds.add(normalizedId);
                continue;
            }
            loadedSkillIds.add(normalizedId);
            skillContents.add("""
                    <skill name="%s">
                    %s
                    </skill>
                    """.formatted(normalizedId, StrUtil.blankToDefault(skill.getContent(), "")).trim());
        }
        return SkillLoadResult.builder()
                .loadedSkillIds(loadedSkillIds)
                .missingSkillIds(missingSkillIds)
                .skillContents(skillContents)
                .build();
    }

    private Map<String, SkillDocument> loadSkillMap() {
        Map<String, SkillDocument> skills = new LinkedHashMap<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(SKILL_PATTERN);
            List<Resource> sortedResources = List.of(resources).stream()
                    .sorted(Comparator.comparing(this::resolveResourceDescription))
                    .toList();
            for (Resource resource : sortedResources) {
                SkillDocument skill = parseSkill(resource);
                if (skill != null && StrUtil.isNotBlank(skill.getId())) {
                    skills.put(skill.getId(), skill);
                }
            }
        } catch (IOException e) {
            log.warn("加载 Skill 目录失败: {}", e.getMessage());
        }
        return skills;
    }

    private SkillDocument parseSkill(Resource resource) {
        try {
            String rawContent = resource.getContentAsString(StandardCharsets.UTF_8);
            String fallbackId = resolveFallbackId(resource);
            ParsedSkillContent parsed = parseFrontMatter(rawContent);
            String id = normalizeSkillId(StrUtil.blankToDefault(parsed.metadata().get("name"), fallbackId));
            return SkillDocument.builder()
                    .id(id)
                    .name(StrUtil.blankToDefault(parsed.metadata().get("name"), id))
                    .description(StrUtil.blankToDefault(parsed.metadata().get("description"), ""))
                    .content(StrUtil.blankToDefault(parsed.body(), ""))
                    .build();
        } catch (IOException e) {
            log.warn("读取 Skill 文档失败，resource={}, error={}", resolveResourceDescription(resource), e.getMessage());
            return null;
        }
    }

    private ParsedSkillContent parseFrontMatter(String rawContent) {
        if (StrUtil.isBlank(rawContent) || !rawContent.startsWith("---")) {
            return new ParsedSkillContent(Map.of(), StrUtil.blankToDefault(rawContent, ""));
        }
        String normalizedContent = rawContent.replace("\r\n", "\n");
        int endIndex = normalizedContent.indexOf("\n---", 3);
        if (endIndex < 0) {
            return new ParsedSkillContent(Map.of(), rawContent);
        }
        String metadataText = normalizedContent.substring(3, endIndex).trim();
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : metadataText.split("\n")) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (StrUtil.isNotBlank(key)) {
                metadata.put(key, stripQuotes(value));
            }
        }
        String body = normalizedContent.substring(endIndex + "\n---".length()).trim();
        return new ParsedSkillContent(metadata, body);
    }

    private String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return StrUtil.blankToDefault(value, "");
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String normalizeSkillId(String skillId) {
        return StrUtil.blankToDefault(skillId, "")
                .trim()
                .toLowerCase()
                .replace('_', '-');
    }

    private String resolveFallbackId(Resource resource) throws IOException {
        if (resource.getFile().getParentFile() == null) {
            return "";
        }
        return normalizeSkillId(resource.getFile().getParentFile().getName());
    }

    private String resolveResourceDescription(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    private record ParsedSkillContent(Map<String, String> metadata, String body) {
    }
}
