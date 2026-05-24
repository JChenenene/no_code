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
 * V2 工作流运行记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_run")
public class WorkflowRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("requestId")
    private String requestId;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    private String prompt;

    @Column("codeGenType")
    private String codeGenType;

    @Column("workspacePath")
    private String workspacePath;

    @Column("previewUrl")
    private String previewUrl;

    private String status;

    @Column("lastResponseJson")
    private String lastResponseJson;

    @Column("errorMessage")
    private String errorMessage;

    @Column("startedTime")
    private LocalDateTime startedTime;

    @Column("finishedTime")
    private LocalDateTime finishedTime;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
