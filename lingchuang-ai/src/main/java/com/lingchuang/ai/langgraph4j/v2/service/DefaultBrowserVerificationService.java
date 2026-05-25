package com.lingchuang.ai.langgraph4j.v2.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationRequest;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认浏览器级预览验证服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultBrowserVerificationService implements BrowserVerificationService {

    private final BrowserPreviewProbe browserPreviewProbe;

    @Value("${workflow.verification.browser.enabled:true}")
    private boolean browserVerificationEnabled;

    @Value("${workflow.verification.browser.base-url:http://localhost:8123/api}")
    private String browserBaseUrl;

    @Override
    public BrowserVerificationResult verify(BrowserVerificationRequest request, VerificationArtifact localVerification) {
        if (!browserVerificationEnabled) {
            return BrowserVerificationResult.builder()
                    .enabled(false)
                    .passed(true)
                    .summary("浏览器验证未启用")
                    .build();
        }
        if (request == null || StrUtil.isBlank(request.previewUrl())) {
            return skipped("缺少预览地址，跳过浏览器验证");
        }
        if (localVerification != null && !localVerification.isPassed()) {
            return skipped("本地验证未通过，跳过浏览器验证");
        }
        String absolutePreviewUrl = resolveAbsolutePreviewUrl(request.previewUrl());
        String screenshotPath = resolveScreenshotPath(request);
        try {
            BrowserVerificationResult result = browserPreviewProbe.probe(absolutePreviewUrl, screenshotPath);
            if (result == null) {
                return failed(absolutePreviewUrl, screenshotPath, "浏览器验证未返回结果", "browser_probe");
            }
            String screenshotUrl = resolveScreenshotUrl(request, screenshotPath, result.getScreenshotUrl());
            return result.toBuilder()
                    .enabled(true)
                    .previewUrl(absolutePreviewUrl)
                    .screenshotPath(StrUtil.blankToDefault(result.getScreenshotPath(), screenshotPath))
                    .screenshotUrl(screenshotUrl)
                    .build();
        } catch (Exception e) {
            log.warn("浏览器验证失败，previewUrl={}, message={}", absolutePreviewUrl, e.getMessage(), e);
            return failed(absolutePreviewUrl, screenshotPath, "浏览器验证异常: " + e.getMessage(), "browser_environment");
        }
    }

    private BrowserVerificationResult skipped(String summary) {
        return BrowserVerificationResult.builder()
                .enabled(false)
                .passed(true)
                .summary(summary)
                .build();
    }

    private BrowserVerificationResult failed(String previewUrl, String screenshotPath, String issue, String failureType) {
        return BrowserVerificationResult.builder()
                .enabled(true)
                .passed(false)
                .previewUrl(previewUrl)
                .screenshotPath(screenshotPath)
                .issues(List.of(issue))
                .summary("浏览器验证失败")
                .errorMessage(issue)
                .failureType(failureType)
                .build();
    }

    private String resolveAbsolutePreviewUrl(String previewUrl) {
        if (previewUrl.startsWith("http://") || previewUrl.startsWith("https://")) {
            return previewUrl;
        }
        String normalizedBaseUrl = StrUtil.removeSuffix(browserBaseUrl, "/");
        String normalizedPreviewUrl = previewUrl.startsWith("/") ? previewUrl : "/" + previewUrl;
        return normalizedBaseUrl + normalizedPreviewUrl;
    }

    private String resolveScreenshotPath(BrowserVerificationRequest request) {
        String rootDir = StrUtil.blankToDefault(
                request.buildResultDir(),
                StrUtil.blankToDefault(request.generatedCodeDir(), System.getProperty("user.dir") + "/tmp/browser_verification"));
        File screenshotDir = new File(rootDir, "verification");
        FileUtil.mkdir(screenshotDir);
        return new File(screenshotDir, "browser-screenshot.jpg").getAbsolutePath();
    }

    private String resolveScreenshotUrl(BrowserVerificationRequest request, String screenshotPath, String existingUrl) {
        if (StrUtil.isNotBlank(existingUrl)) {
            return existingUrl;
        }
        if (StrUtil.isBlank(request.previewUrl()) || StrUtil.isBlank(screenshotPath)) {
            return existingUrl;
        }
        String normalizedPreviewUrl = request.previewUrl();
        int indexIndex = normalizedPreviewUrl.indexOf("index.html");
        String baseUrl = indexIndex >= 0
                ? normalizedPreviewUrl.substring(0, indexIndex)
                : StrUtil.addSuffixIfNot(normalizedPreviewUrl, "/");
        return baseUrl + "verification/browser-screenshot.jpg";
    }
}
