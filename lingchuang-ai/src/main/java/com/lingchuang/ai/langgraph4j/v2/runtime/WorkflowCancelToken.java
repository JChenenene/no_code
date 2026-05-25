package com.lingchuang.ai.langgraph4j.v2.runtime;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * V2 工作流取消令牌。用于同一 JVM 内的运行中任务协作式取消。
 */
public class WorkflowCancelToken {

    private final Long runId;
    private final String requestId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String cancelReason;
    private volatile LocalDateTime cancelledAt;

    public WorkflowCancelToken(Long runId, String requestId) {
        this.runId = runId;
        this.requestId = requestId;
    }

    public boolean cancel(String reason) {
        boolean changed = cancelled.compareAndSet(false, true);
        if (changed) {
            this.cancelReason = StrUtil.blankToDefault(reason, "V2 工作流已取消");
            this.cancelledAt = LocalDateTime.now();
        }
        return changed;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new WorkflowCancelledException(StrUtil.blankToDefault(cancelReason, "V2 工作流已取消"));
        }
    }

    public Long getRunId() {
        return runId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
