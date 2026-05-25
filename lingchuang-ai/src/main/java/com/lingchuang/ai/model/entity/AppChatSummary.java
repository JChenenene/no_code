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
 * 应用对话摘要记忆。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_chat_summary")
public class AppChatSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    @Column("summaryType")
    private String summaryType;

    @Column("summaryText")
    private String summaryText;

    @Column("coveredMessageIds")
    private String coveredMessageIds;

    @Column("tokenEstimate")
    private Integer tokenEstimate;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
