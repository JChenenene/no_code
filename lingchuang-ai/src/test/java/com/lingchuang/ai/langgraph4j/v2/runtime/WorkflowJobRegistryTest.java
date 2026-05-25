package com.lingchuang.ai.langgraph4j.v2.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowJobRegistryTest {

    @Test
    void shouldRegisterCancelAndRemoveWorkflowJob() {
        WorkflowJobRegistry registry = new WorkflowJobRegistry();

        WorkflowCancelToken cancelToken = registry.register(1001L, "req-1");

        assertSame(cancelToken, registry.getCancelToken(1001L));
        assertTrue(registry.cancel(1001L, "用户取消"));
        assertTrue(cancelToken.isCancelled());
        assertTrue(cancelToken.getCancelReason().contains("用户取消"));

        registry.remove(1001L);

        assertNull(registry.getCancelToken(1001L));
    }

    @Test
    void shouldReturnFalseWhenCancellingMissingJob() {
        WorkflowJobRegistry registry = new WorkflowJobRegistry();

        assertFalse(registry.cancel(1001L, "用户取消"));
    }
}
