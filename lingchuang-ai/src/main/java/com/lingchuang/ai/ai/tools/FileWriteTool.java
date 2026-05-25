package com.lingchuang.ai.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriteTool extends BaseTool {

    private static final int MAX_FILE_CONTENT_BYTES = 1024 * 1024;
    private static final int TOOL_RESULT_PREVIEW_CHARS = 1200;

    private final ToolPathResolver toolPathResolver;

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = toolPathResolver.resolveForWrite(relativeFilePath, appId);
            byte[] bytes = content == null ? new byte[0] : content.getBytes();
            if (bytes.length > MAX_FILE_CONTENT_BYTES) {
                return "文件写入失败: 文件内容超过 1MB 限制";
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IllegalArgumentException e) {
            return "文件写入失败: " + e.getMessage();
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        String displayContent = summarizeContent(content);
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, displayContent);
    }

    private String summarizeContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= TOOL_RESULT_PREVIEW_CHARS) {
            return content;
        }
        return content.substring(0, TOOL_RESULT_PREVIEW_CHARS)
                + "\n... 内容已截断，原始长度 " + content.length() + " 字符";
    }
}
