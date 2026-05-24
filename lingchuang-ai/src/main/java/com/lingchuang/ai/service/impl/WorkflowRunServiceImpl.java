package com.lingchuang.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.mapper.WorkflowRunMapper;
import com.lingchuang.ai.model.entity.WorkflowRun;
import com.lingchuang.ai.model.enums.WorkflowRunStatusEnum;
import com.lingchuang.ai.service.WorkflowRunService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * V2 工作流运行记录服务。
 */
@Service
public class WorkflowRunServiceImpl extends ServiceImpl<WorkflowRunMapper, WorkflowRun> implements WorkflowRunService {

    @Override
    public WorkflowRun createRunningRun(Long appId, Long userId, String prompt) {
        return createRunningRun(appId, userId, prompt, null);
    }

    @Override
    public WorkflowRun createRunningRun(Long appId, Long userId, String prompt, String codeGenType) {
        WorkflowRun workflowRun = WorkflowRun.builder()
                .id(IdUtil.getSnowflakeNextId())
                .requestId(IdUtil.fastSimpleUUID())
                .appId(appId)
                .userId(userId)
                .prompt(prompt)
                .codeGenType(codeGenType)
                .status(WorkflowRunStatusEnum.RUNNING.getValue())
                .startedTime(LocalDateTime.now())
                .build();
        this.save(workflowRun);
        return workflowRun;
    }

    @Override
    public void attachWorkspace(WorkflowRun workflowRun, String workspacePath, String previewUrl) {
        if (workflowRun == null) {
            return;
        }
        workflowRun.setWorkspacePath(workspacePath);
        workflowRun.setPreviewUrl(previewUrl);
        this.updateById(workflowRun);
    }

    @Override
    public void markSucceeded(WorkflowRun workflowRun, String responseJson) {
        if (workflowRun == null) {
            return;
        }
        workflowRun.setStatus(WorkflowRunStatusEnum.SUCCEEDED.getValue());
        workflowRun.setLastResponseJson(responseJson);
        workflowRun.setFinishedTime(LocalDateTime.now());
        this.updateById(workflowRun);
    }

    @Override
    public void markFailed(WorkflowRun workflowRun, String errorMessage) {
        if (workflowRun == null) {
            return;
        }
        workflowRun.setStatus(WorkflowRunStatusEnum.FAILED.getValue());
        workflowRun.setErrorMessage(StrUtil.blankToDefault(errorMessage, "V2 工作流执行失败"));
        workflowRun.setFinishedTime(LocalDateTime.now());
        this.updateById(workflowRun);
    }

    @Override
    public WorkflowRun getLatestRun(Long appId, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .orderBy("createTime", false)
                .limit(1);
        return this.getOne(queryWrapper);
    }

    @Override
    public WorkflowRun getLatestSucceededRun(Long appId, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("status", WorkflowRunStatusEnum.SUCCEEDED.getValue())
                .orderBy("createTime", false)
                .limit(1);
        return this.getOne(queryWrapper);
    }
}
