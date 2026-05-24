package com.lingchuang.ai.langgraph4j.v2.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.constant.AppConstant;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * V2 工作流生成产物辅助能力。
 */
@Service
public class GeneratedArtifactSupport {

    private static final List<String> CODE_EXTENSIONS = List.of(
            ".html", ".htm", ".css", ".scss", ".js", ".jsx", ".ts", ".tsx", ".vue", ".json", ".md"
    );

    private static final Set<String> ALWAYS_INCLUDE_FILES = Set.of(
            "package.json", "package-lock.json", "pnpm-lock.yaml", "vite.config.ts", "vite.config.js",
            "tsconfig.json", "index.html", "main.ts", "main.js", "app.vue"
    );

    public String resolveGeneratedCodeDir(CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return resolveLegacyGeneratedCodeDir(codeGenTypeEnum, appId);
    }

    public String resolveLegacyGeneratedCodeDir(CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        CodeGenTypeEnum resolvedType = codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
        long safeAppId = appId == null ? 0L : appId;
        return AppConstant.CODE_OUTPUT_ROOT_DIR + "/" + resolvedType.getValue() + "_" + safeAppId;
    }

    public String resolveRunWorkspaceDir(CodeGenTypeEnum codeGenTypeEnum, Long appId, Long runId) {
        CodeGenTypeEnum resolvedType = codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
        long safeAppId = appId == null ? 0L : appId;
        long safeRunId = runId == null ? 0L : runId;
        String workspacePath = AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + safeAppId
                + File.separator + safeRunId
                + File.separator + resolvedType.getValue();
        FileUtil.mkdir(workspacePath);
        return workspacePath;
    }

    public String resolvePreviewUrl(Long appId, Long runId, CodeGenTypeEnum codeGenTypeEnum) {
        CodeGenTypeEnum resolvedType = codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
        long safeAppId = appId == null ? 0L : appId;
        long safeRunId = runId == null ? 0L : runId;
        String baseUrl = "/static/%d/%d/%s/".formatted(safeAppId, safeRunId, resolvedType.getValue());
        if (resolvedType == CodeGenTypeEnum.VUE_PROJECT) {
            return baseUrl + "dist/index.html";
        }
        return baseUrl;
    }

    public String resolveBuildResultDir(String generatedCodeDir) {
        return generatedCodeDir + File.separator + "dist";
    }

    public boolean directoryExists(String directory) {
        if (StrUtil.isBlank(directory)) {
            return false;
        }
        File file = new File(directory);
        return file.exists() && file.isDirectory();
    }

    public boolean hasAnyRelevantFiles(String codeDir) {
        return !listRelevantFiles(codeDir).isEmpty();
    }

    public boolean fileExists(String rootDir, String relativePath) {
        if (StrUtil.isBlank(rootDir) || StrUtil.isBlank(relativePath)) {
            return false;
        }
        return new File(rootDir, relativePath).exists();
    }

    public List<String> listKeyFiles(String codeDir, int maxCount) {
        List<File> files = listRelevantFiles(codeDir);
        if (files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(file -> normalizeRelativePath(codeDir, file))
                .filter(StrUtil::isNotBlank)
                .sorted()
                .limit(Math.max(maxCount, 1))
                .toList();
    }

    public String readCodeContent(String codeDir) {
        List<File> files = listRelevantFiles(codeDir);
        if (files.isEmpty()) {
            return "";
        }
        StringBuilder content = new StringBuilder("# 项目文件结构和代码内容\n\n");
        for (File file : files.stream()
                .sorted(Comparator.comparing(file -> normalizeRelativePath(codeDir, file)))
                .toList()) {
            String relativePath = normalizeRelativePath(codeDir, file);
            content.append("## 文件: ").append(relativePath).append("\n\n");
            content.append(FileUtil.readUtf8String(file)).append("\n\n");
        }
        return content.toString();
    }

    private List<File> listRelevantFiles(String codeDir) {
        if (!directoryExists(codeDir)) {
            return List.of();
        }
        File directory = new File(codeDir);
        List<File> files = new ArrayList<>();
        FileUtil.walkFiles(directory, file -> {
            if (!file.isFile() || shouldSkipFile(file, directory)) {
                return;
            }
            if (isRelevantCodeFile(file)) {
                files.add(file);
            }
        });
        return files;
    }

    private boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = normalizeRelativePath(rootDir.getAbsolutePath(), file);
        if (StrUtil.isBlank(relativePath)) {
            return true;
        }
        if (file.getName().startsWith(".")) {
            return true;
        }
        return relativePath.contains("node_modules/")
                || relativePath.contains("dist/")
                || relativePath.contains("target/")
                || relativePath.contains(".git/");
    }

    private boolean isRelevantCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        if (ALWAYS_INCLUDE_FILES.contains(fileName)) {
            return true;
        }
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String normalizeRelativePath(String rootDir, File file) {
        String relativePath = FileUtil.subPath(rootDir, file.getAbsolutePath());
        return StrUtil.blankToDefault(relativePath, "").replace("\\", "/");
    }
}
