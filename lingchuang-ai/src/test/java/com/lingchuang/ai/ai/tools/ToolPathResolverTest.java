package com.lingchuang.ai.ai.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPathResolverTest {

    private final ToolPathResolver toolPathResolver = new ToolPathResolver();

    @Test
    void shouldResolveSafeRelativePathUnderAppWorkspace() {
        Path resolvedPath = toolPathResolver.resolveForWrite("src/App.vue", 1001L);

        assertTrue(resolvedPath.toString().replace("\\", "/").endsWith("/vue_project_1001/src/App.vue"));
    }

    @Test
    void shouldResolveRegisteredRunWorkspaceByWorkspaceId() {
        toolPathResolver.registerWorkspaceRoot(9001L, "D:/tmp/code_output/1001/9001/vue_project");

        Path resolvedPath = toolPathResolver.resolveForWrite("src/App.vue", 9001L);

        assertTrue(resolvedPath.toString().replace("\\", "/")
                .endsWith("/tmp/code_output/1001/9001/vue_project/src/App.vue"));
        toolPathResolver.clearWorkspaceRoot(9001L);
    }

    @Test
    void shouldRejectPathTraversalOutsideAppWorkspace() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> toolPathResolver.resolveForWrite("../application.yml", 1001L));

        assertEquals("文件路径不允许访问项目目录之外", exception.getMessage());
    }

    @Test
    void shouldRejectAbsolutePath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> toolPathResolver.resolveForWrite("D:/tmp/outside.txt", 1001L));

        assertEquals("文件路径必须是相对路径", exception.getMessage());
    }

    @Test
    void shouldRejectSensitiveHiddenFile() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> toolPathResolver.resolveForWrite(".env", 1001L));

        assertEquals("不允许写入敏感文件", exception.getMessage());
    }
}
