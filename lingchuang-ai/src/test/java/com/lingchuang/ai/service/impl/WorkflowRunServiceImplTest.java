package com.lingchuang.ai.service.impl;

import com.lingchuang.ai.mapper.WorkflowRunMapper;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.enums.WorkflowRunStatusEnum;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunServiceImplTest {

    private WorkflowRunMapper workflowRunMapper;
    private WorkflowRunServiceImpl workflowRunService;

    @BeforeEach
    void setUp() {
        workflowRunMapper = mock(WorkflowRunMapper.class);
        workflowRunService = new WorkflowRunServiceImpl();
        setMapper(workflowRunService, workflowRunMapper);
    }

    @Test
    void shouldCreateRunningWorkflowRun() {
        when(workflowRunMapper.insert(any(WorkflowRun.class))).thenReturn(1);

        WorkflowRun workflowRun = workflowRunService.createRunningRun(1001L, 2002L, "生成官网");

        assertEquals(1001L, workflowRun.getAppId());
        assertEquals(2002L, workflowRun.getUserId());
        assertEquals("生成官网", workflowRun.getPrompt());
        assertEquals(WorkflowRunStatusEnum.RUNNING.getValue(), workflowRun.getStatus());
        assertNotNull(workflowRun.getRequestId());
        verify(workflowRunMapper).insert(any(WorkflowRun.class), eq(true));
    }

    @Test
    void shouldCreateRunningWorkflowRunWithCodeGenType() {
        when(workflowRunMapper.insert(any(WorkflowRun.class))).thenReturn(1);

        WorkflowRun workflowRun = workflowRunService.createRunningRun(1001L, 2002L, "生成官网", "vue_project");

        assertEquals("vue_project", workflowRun.getCodeGenType());
        assertNotNull(workflowRun.getId());
        assertEquals(WorkflowRunStatusEnum.RUNNING.getValue(), workflowRun.getStatus());
        verify(workflowRunMapper).insert(any(WorkflowRun.class), eq(true));
    }

    @Test
    void shouldAttachWorkspaceToWorkflowRun() {
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(1L)
                .requestId("req-1")
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        when(workflowRunMapper.update(any(WorkflowRun.class))).thenReturn(1);

        workflowRunService.attachWorkspace(workflowRun, "D:/tmp/code_output/1001/1/vue_project", "/static/1001/1/vue_project/dist/index.html");

        assertEquals("D:/tmp/code_output/1001/1/vue_project", workflowRun.getWorkspacePath());
        assertEquals("/static/1001/1/vue_project/dist/index.html", workflowRun.getPreviewUrl());
        verify(workflowRunMapper).update(any(WorkflowRun.class), eq(true));
    }

    @Test
    void shouldMarkRunSucceeded() {
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(1L)
                .requestId("req-1")
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        when(workflowRunMapper.update(any(WorkflowRun.class))).thenReturn(1);

        workflowRunService.markSucceeded(workflowRun, "{\"ok\":true}");

        assertEquals(WorkflowRunStatusEnum.SUCCEEDED.getValue(), workflowRun.getStatus());
        assertEquals("{\"ok\":true}", workflowRun.getLastResponseJson());
        assertNotNull(workflowRun.getFinishedTime());
        verify(workflowRunMapper).update(any(WorkflowRun.class), eq(true));
    }

    @Test
    void shouldQueryLatestRunByAppAndUser() {
        WorkflowRun expected = WorkflowRun.builder().requestId("req-latest").build();
        when(workflowRunMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(expected);

        WorkflowRun latestRun = workflowRunService.getLatestRun(1001L, 2002L);

        assertEquals("req-latest", latestRun.getRequestId());
        verify(workflowRunMapper).selectOneByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldQueryLatestSucceededRunByAppAndUser() {
        WorkflowRun expected = WorkflowRun.builder()
                .requestId("req-success")
                .status(WorkflowRunStatusEnum.SUCCEEDED.getValue())
                .build();
        when(workflowRunMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(expected);

        WorkflowRun latestRun = workflowRunService.getLatestSucceededRun(1001L, 2002L);

        assertEquals("req-success", latestRun.getRequestId());
        assertEquals(WorkflowRunStatusEnum.SUCCEEDED.getValue(), latestRun.getStatus());
        verify(workflowRunMapper).selectOneByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldQueryRunningRunByAppAndUser() {
        WorkflowRun expected = WorkflowRun.builder()
                .requestId("req-running")
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        when(workflowRunMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(expected);

        WorkflowRun runningRun = workflowRunService.getRunningRun(1001L, 2002L);

        assertEquals("req-running", runningRun.getRequestId());
        assertEquals(WorkflowRunStatusEnum.RUNNING.getValue(), runningRun.getStatus());
        verify(workflowRunMapper).selectOneByQuery(any(QueryWrapper.class));
    }

    @Test
    void shouldCancelRunningRun() {
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(1L)
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .build();
        when(workflowRunMapper.update(any(WorkflowRun.class), eq(true))).thenReturn(1);

        boolean cancelled = workflowRunService.cancelRun(workflowRun, "用户取消");

        assertTrue(cancelled);
        assertEquals(WorkflowRunStatusEnum.CANCELLED.getValue(), workflowRun.getStatus());
        assertEquals("用户取消", workflowRun.getErrorMessage());
        assertNotNull(workflowRun.getFinishedTime());
        verify(workflowRunMapper).update(any(WorkflowRun.class), eq(true));
    }

    @Test
    void shouldNotCancelFinishedRun() {
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(1L)
                .status(WorkflowRunStatusEnum.SUCCEEDED.getValue())
                .build();

        boolean cancelled = workflowRunService.cancelRun(workflowRun, "用户取消");

        assertFalse(cancelled);
        assertEquals(WorkflowRunStatusEnum.SUCCEEDED.getValue(), workflowRun.getStatus());
    }

    private void setMapper(WorkflowRunServiceImpl service, WorkflowRunMapper mapper) {
        try {
            Field mapperField = ServiceImpl.class.getDeclaredField("mapper");
            mapperField.setAccessible(true);
            mapperField.set(service, mapper);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("注入测试 Mapper 失败", e);
        }
    }
}
