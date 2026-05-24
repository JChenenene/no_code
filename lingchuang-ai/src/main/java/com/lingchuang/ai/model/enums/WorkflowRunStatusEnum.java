package com.lingchuang.ai.model.enums;

import lombok.Getter;

@Getter
public enum WorkflowRunStatusEnum {

    PENDING("待运行", "pending"),
    RUNNING("运行中", "running"),
    SUCCEEDED("已成功", "succeeded"),
    FAILED("已失败", "failed"),
    CANCELLED("已取消", "cancelled");

    private final String text;
    private final String value;

    WorkflowRunStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}
