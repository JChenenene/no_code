package com.lingchuang.ai.ai.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPathResolverTest {

    private final ToolPathResolver toolPathResolver = new ToolPathResolver();

    @TempDir
    private Path tempDir;

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

    @Test
    void shouldRejectSensitiveHiddenFileForRead() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> toolPathResolver.resolveForRead(".env", 1001L));

        assertEquals("不允许读取敏感文件", exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedWriteExtension() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> toolPathResolver.resolveForWrite("src/evil.exe", 1001L));

        assertEquals("不允许写入该类型文件", exception.getMessage());
    }

    @Test
    void writeToolShouldRejectOversizedContent() {
        long workspaceId = 9901L;
        toolPathResolver.registerWorkspaceRoot(workspaceId, tempDir.toString());
        try {
            FileWriteTool fileWriteTool = new FileWriteTool(toolPathResolver);
            String oversizedContent = "a".repeat(1024 * 1024 + 1);

            String result = fileWriteTool.writeFile("src/App.vue", oversizedContent, workspaceId);

            assertTrue(result.contains("文件内容超过 1MB 限制"));
            assertFalse(Files.exists(tempDir.resolve("src/App.vue")));
        } finally {
            toolPathResolver.clearWorkspaceRoot(workspaceId);
        }
    }

    @Test
    void readToolShouldRejectSensitiveFile() throws Exception {
        long workspaceId = 9902L;
        toolPathResolver.registerWorkspaceRoot(workspaceId, tempDir.toString());
        try {
            Files.writeString(tempDir.resolve(".env"), "SECRET=1");
            FileReadTool fileReadTool = new FileReadTool(toolPathResolver);

            String result = fileReadTool.readFile(".env", workspaceId);

            assertEquals("读取文件失败: 不允许读取敏感文件", result);
        } finally {
            toolPathResolver.clearWorkspaceRoot(workspaceId);
        }
    }

    @Test
    void readToolShouldRejectOversizedFile() throws Exception {
        long workspaceId = 9903L;
        toolPathResolver.registerWorkspaceRoot(workspaceId, tempDir.toString());
        try {
            Path sourcePath = Files.createDirectories(tempDir.resolve("src")).resolve("App.vue");
            Files.writeString(sourcePath, "a".repeat(1024 * 1024 + 1));
            FileReadTool fileReadTool = new FileReadTool(toolPathResolver);

            String result = fileReadTool.readFile("src/App.vue", workspaceId);

            assertEquals("读取文件失败: 文件内容超过 1MB 限制", result);
        } finally {
            toolPathResolver.clearWorkspaceRoot(workspaceId);
        }
    }

    @Test
    void writeToolExecutedResultShouldSummarizeLongContent() {
        FileWriteTool fileWriteTool = new FileWriteTool(toolPathResolver);
        cn.hutool.json.JSONObject arguments = new cn.hutool.json.JSONObject();
        arguments.set("relativeFilePath", "src/App.vue");
        arguments.set("content", "a".repeat(2_000));

        String result = fileWriteTool.generateToolExecutedResult(arguments);

        assertTrue(result.contains("内容已截断"));
        assertFalse(result.contains("a".repeat(2_000)));
    }
}
