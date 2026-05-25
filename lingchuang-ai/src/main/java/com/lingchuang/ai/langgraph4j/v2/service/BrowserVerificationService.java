package com.lingchuang.ai.langgraph4j.v2.service;

import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationRequest;
import com.lingchuang.ai.langgraph4j.v2.model.BrowserVerificationResult;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;

/**
 * 浏览器级预览验证服务。
 */
public interface BrowserVerificationService {

    BrowserVerificationResult verify(BrowserVerificationRequest request, VerificationArtifact localVerification);
}
