package com.lingchuang.ai.ai.tools;

import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.constant.AppConstant;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 文件工具路径解析器，确保工具只能访问当前应用工作目录。
 */
@Component
public class ToolPathResolver {

    private static final Set<String> SENSITIVE_FILE_NAMES = Set.of(
            ".env", ".env.local", ".env.development", ".env.production",
            "application.yml", "application-local.yml", "application-prod.yml"
    );

    private static final Map<Long, Path> WORKSPACE_ROOTS = new ConcurrentHashMap<>();

    public Path resolveForWrite(String relativePath, Long appId) {
        return resolve(relativePath, appId, true);
    }

    public Path resolveForRead(String relativePath, Long appId) {
        return resolve(relativePath, appId, false);
    }

    public Path resolveWorkspaceRoot(Long appId) {
        Path registeredRoot = WORKSPACE_ROOTS.get(appId);
        if (registeredRoot != null) {
            return registeredRoot;
        }
        long safeAppId = appId == null ? 0L : appId;
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + safeAppId)
                .toAbsolutePath()
                .normalize();
    }

    public void registerWorkspaceRoot(Long workspaceId, String workspaceRoot) {
        if (workspaceId == null || StrUtil.isBlank(workspaceRoot)) {
            return;
        }
        WORKSPACE_ROOTS.put(workspaceId, Paths.get(workspaceRoot).toAbsolutePath().normalize());
    }

    public void clearWorkspaceRoot(Long workspaceId) {
        if (workspaceId == null) {
            return;
        }
        WORKSPACE_ROOTS.remove(workspaceId);
    }

    private Path resolve(String relativePath, Long appId, boolean writeMode) {
        if (StrUtil.isBlank(relativePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        Path inputPath = Paths.get(relativePath);
        if (inputPath.isAbsolute()) {
            throw new IllegalArgumentException("文件路径必须是相对路径");
        }
        Path workspaceRoot = resolveWorkspaceRoot(appId);
        Path resolvedPath = workspaceRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("文件路径不允许访问项目目录之外");
        }
        if (writeMode && isSensitiveFile(resolvedPath)) {
            throw new IllegalArgumentException("不允许写入敏感文件");
        }
        return resolvedPath;
    }

    private boolean isSensitiveFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String normalizedFileName = fileName.toString().toLowerCase();
        return normalizedFileName.startsWith(".")
                || SENSITIVE_FILE_NAMES.contains(normalizedFileName);
    }
}
