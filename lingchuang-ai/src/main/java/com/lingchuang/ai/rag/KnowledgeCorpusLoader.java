package com.lingchuang.ai.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.lingchuang.ai.rag.model.KnowledgeDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 语料加载器。
 */
@Component
public class KnowledgeCorpusLoader {

    private static final String FRONT_MATTER_DELIMITER = "---";

    public List<KnowledgeDocument> loadAllDocuments(RagProperties ragProperties) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> knowledgeResources = Arrays.stream(resolver.getResources(ragProperties.getKnowledge().getBasePath()))
                .filter(Resource::isReadable)
                .toList();
        return loadDocuments(knowledgeResources);
    }

    List<KnowledgeDocument> loadDocuments(List<Resource> knowledgeResources) throws IOException {
        List<KnowledgeDocument> documents = new ArrayList<>();
        for (Resource knowledgeResource : knowledgeResources) {
            documents.add(parseKnowledgeResource(knowledgeResource));
        }
        return documents;
    }

    private KnowledgeDocument parseKnowledgeResource(Resource resource) throws IOException {
        String path = resolveResourcePath(resource, "knowledge/");
        String rawContent = readResource(resource);
        ParsedContent parsedContent = parseFrontMatter(rawContent);
        Map<String, String> metadata = parsedContent.metadata();
        String content = parsedContent.content();
        Set<String> codeGenTypes = parseMultiValue(metadata.getOrDefault("codeGenTypes", "all"));
        Set<String> tags = parseMultiValue(metadata.getOrDefault("tags", ""));
        String sourceType = metadata.getOrDefault("sourceType", inferSourceType(path));
        String title = metadata.getOrDefault("title", inferTitle(path));
        int priority = parsePriority(metadata.getOrDefault("priority", "medium"));
        String checksum = SecureUtil.sha256(content + metadata.toString());
        return KnowledgeDocument.builder()
                .docId(buildDocId(path))
                .title(title)
                .path(path)
                .sourceType(sourceType)
                .priority(priority)
                .tags(tags)
                .codeGenTypes(codeGenTypes)
                .checksum(checksum)
                .content(content)
                .build();
    }

    private String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ParsedContent parseFrontMatter(String rawContent) {
        if (!rawContent.startsWith(FRONT_MATTER_DELIMITER)) {
            return new ParsedContent(Map.of(), rawContent);
        }
        int secondDelimiterIndex = rawContent.indexOf("\n" + FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (secondDelimiterIndex < 0) {
            return new ParsedContent(Map.of(), rawContent);
        }
        String metadataContent = rawContent.substring(FRONT_MATTER_DELIMITER.length(), secondDelimiterIndex).trim();
        String content = rawContent.substring(secondDelimiterIndex + FRONT_MATTER_DELIMITER.length() + 1).trim();
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : metadataContent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains(":")) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            metadata.put(parts[0].trim(), parts[1].trim());
        }
        return new ParsedContent(metadata, content);
    }

    private Set<String> parseMultiValue(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : rawValue.split(",")) {
            String trimmed = item.trim();
            if (StrUtil.isNotBlank(trimmed)) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private int parsePriority(String rawPriority) {
        return switch (StrUtil.blankToDefault(rawPriority, "medium").trim().toLowerCase()) {
            case "high" -> 3;
            case "low" -> 1;
            default -> 2;
        };
    }

    private String inferSourceType(String path) {
        String normalized = normalizePath(path);
        if (normalized.startsWith("knowledge/")) {
            normalized = normalized.substring("knowledge/".length());
        }
        int firstSlashIndex = normalized.indexOf('/');
        if (firstSlashIndex < 0) {
            return "knowledge";
        }
        return normalized.substring(0, firstSlashIndex);
    }

    private String inferTitle(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }
        return fileName.replace('-', ' ');
    }

    private String normalizePath(String filename) {
        String normalized = StrUtil.blankToDefault(filename, UUID.randomUUID().toString());
        return normalized.replace('\\', '/');
    }

    private String resolveResourcePath(Resource resource, String anchor) throws IOException {
        try {
            String urlPath = resource.getURL().toString().replace('\\', '/');
            int anchorIndex = urlPath.indexOf(anchor);
            if (anchorIndex >= 0) {
                return urlPath.substring(anchorIndex);
            }
        } catch (Exception ignored) {
            // ignore and fallback
        }
        String filename = normalizePath(resource.getFilename());
        if (filename.startsWith(anchor)) {
            return filename;
        }
        return anchor + filename;
    }

    private String buildDocId(String path) {
        return SecureUtil.md5(path);
    }

    private record ParsedContent(Map<String, String> metadata, String content) {
    }
}
