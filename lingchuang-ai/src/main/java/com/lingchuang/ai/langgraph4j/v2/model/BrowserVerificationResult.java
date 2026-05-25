package com.lingchuang.ai.langgraph4j.v2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 浏览器级预览验证结果。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BrowserVerificationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean enabled;

    private boolean passed;

    private String previewUrl;

    private String screenshotPath;

    private String screenshotUrl;

    private int firstScreenTextLength;

    @Builder.Default
    private List<String> consoleErrors = List.of();

    @Builder.Default
    private List<String> issues = List.of();

    private String summary;

    private String errorMessage;

    private String failureType;
}
