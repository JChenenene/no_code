package com.lingchuang.ai.langgraph4j.v2.runtime;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前 JVM 内运行中的 V2 工作流注册表。
 */
@Component
public class WorkflowJobRegistry {

    private final Map<Long, WorkflowJob> jobs = new ConcurrentHashMap<>();

    public WorkflowCancelToken register(Long runId, String requestId) {
        if (runId == null || runId <= 0) {
            return null;
        }
        WorkflowCancelToken cancelToken = new WorkflowCancelToken(runId, requestId);
        jobs.put(runId, new WorkflowJob(runId, requestId, cancelToken, LocalDateTime.now()));
        return cancelToken;
    }

    public WorkflowCancelToken getCancelToken(Long runId) {
        WorkflowJob job = jobs.get(runId);
        return job == null ? null : job.cancelToken();
    }

    public boolean cancel(Long runId, String reason) {
        WorkflowCancelToken cancelToken = getCancelToken(runId);
        return cancelToken != null && cancelToken.cancel(reason);
    }

    public void remove(Long runId) {
        if (runId != null) {
            jobs.remove(runId);
        }
    }

    public boolean isRunning(Long runId) {
        return runId != null && jobs.containsKey(runId);
    }

    private record WorkflowJob(
            Long runId,
            String requestId,
            WorkflowCancelToken cancelToken,
            LocalDateTime startedAt
    ) {
    }
}
