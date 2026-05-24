package com.lingchuang.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * RAG 启动预热。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class RagBootstrapRunner implements ApplicationRunner {

    private final RagProperties ragProperties;

    private final KnowledgeIndexService knowledgeIndexService;

    public RagBootstrapRunner(RagProperties ragProperties, KnowledgeIndexService knowledgeIndexService) {
        this.ragProperties = ragProperties;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.isEnabled() || !ragProperties.getBootstrap().isEnabled()) {
            return;
        }
        try {
            knowledgeIndexService.initialize();
            log.info("RAG 索引预热完成");
        } catch (Exception e) {
            log.warn("RAG 索引预热失败，将在首次检索时按需构建", e);
        }
    }
}
