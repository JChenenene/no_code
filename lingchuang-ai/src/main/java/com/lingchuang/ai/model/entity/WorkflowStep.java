package com.lingchuang.ai.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * V2 工作流步骤记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_step")
public class WorkflowStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("runId")
    private Long runId;

    @Column("requestId")
    private String requestId;

    @Column("stepNumber")
    private Integer stepNumber;

    @Column("agentName")
    private String agentName;

    private String stage;

    private String status;

    @Column("inputSummary")
    private String inputSummary;

    @Column("outputSummary")
    private String outputSummary;

    @Column("errorMessage")
    private String errorMessage;

    @Column("startedTime")
    private LocalDateTime startedTime;

    @Column("finishedTime")
    private LocalDateTime finishedTime;

    @Column("durationMs")
    private Long durationMs;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
