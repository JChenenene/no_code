package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;

/**
 * 实际浏览器探测器，便于单元测试替换 Selenium 环境。
 */
public interface BrowserPreviewProbe {

    BrowserVerificationResult probe(String previewUrl, String screenshotPath);
}
