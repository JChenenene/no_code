package com.lingchuang.ai.langgraph4j.v2.model;

import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.Builder;

/**
 * 浏览器级预览验证输入。
 */
@Builder
public record BrowserVerificationRequest(
        Long appId,
        Long workflowRunId,
        CodeGenTypeEnum codeGenType,
        String generatedCodeDir,
        String buildResultDir,
        String previewUrl
) {
}
