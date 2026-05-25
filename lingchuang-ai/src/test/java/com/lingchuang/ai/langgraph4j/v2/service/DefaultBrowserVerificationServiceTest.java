package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationRequest;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultBrowserVerificationServiceTest {

    @Mock
    private BrowserPreviewProbe browserPreviewProbe;

    private DefaultBrowserVerificationService browserVerificationService;

    @BeforeEach
    void setUp() {
        browserVerificationService = new DefaultBrowserVerificationService(browserPreviewProbe);
        ReflectionTestUtils.setField(browserVerificationService, "browserVerificationEnabled", true);
        ReflectionTestUtils.setField(browserVerificationService, "browserBaseUrl", "http://localhost:8123/api");
    }

    @Test
    void shouldSkipBrowserProbeWhenLocalVerificationFailed() {
        BrowserVerificationResult result = browserVerificationService.verify(BrowserVerificationRequest.builder()
                        .appId(1L)
                        .workflowRunId(10L)
                        .codeGenType(CodeGenTypeEnum.HTML)
                        .previewUrl("/static/1/10/html/")
                        .build(),
                VerificationArtifact.builder().passed(false).summary("本地验证失败").build());

        assertFalse(result.isEnabled());
        assertTrue(result.isPassed());
        verify(browserPreviewProbe, never()).probe(anyString(), anyString());
    }

    @Test
    void shouldResolveAbsolutePreviewUrlAndScreenshotUrl() {
        when(browserPreviewProbe.probe(anyString(), anyString())).thenReturn(BrowserVerificationResult.builder()
                .enabled(true)
                .passed(true)
                .firstScreenTextLength(8)
                .summary("浏览器验证通过")
                .build());

        BrowserVerificationResult result = browserVerificationService.verify(BrowserVerificationRequest.builder()
                        .appId(1L)
                        .workflowRunId(10L)
                        .codeGenType(CodeGenTypeEnum.HTML)
                        .generatedCodeDir("D:/tmp/code_output/1/10/html")
                        .previewUrl("/static/1/10/html/")
                        .build(),
                VerificationArtifact.builder().passed(true).summary("静态验证通过").build());

        assertTrue(result.isEnabled());
        assertTrue(result.isPassed());
        assertEquals("http://localhost:8123/api/static/1/10/html/", result.getPreviewUrl());
        assertEquals("/static/1/10/html/verification/browser-screenshot.jpg", result.getScreenshotUrl());
        verify(browserPreviewProbe).probe(anyString(), anyString());
    }
}
