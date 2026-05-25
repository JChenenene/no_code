# 数据库初始化
# @author 零创AI
# @from <a href="https://codefather.cn">编程导航学习圈</a>

-- 创建库
create database if not exists lingchuang_ai;

-- 切换库
use lingchuang_ai;

-- 用户表
-- 以下是建表语句

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 应用表
create table app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;

-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;

-- 应用对话摘要记忆表
create table if not exists app_chat_summary
(
    id                bigint auto_increment comment 'id' primary key,
    appId             bigint                             not null comment '应用 id',
    userId            bigint                             not null comment '创建用户 id',
    summaryType       varchar(32)                        not null comment '摘要类型：conversation/tool/decision',
    summaryText       mediumtext                         not null comment '摘要内容',
    coveredMessageIds text                               null comment '摘要覆盖的对话消息 id',
    tokenEstimate     int      default 0                 not null comment '摘要 token 估算值',
    createTime        datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime        datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete          tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_app_user_type (appId, userId, summaryType),
    INDEX idx_appId_userId_updateTime (appId, userId, updateTime)
) comment '应用对话摘要记忆' collate = utf8mb4_unicode_ci;

-- V2 工作流运行记录表
create table if not exists workflow_run
(
    id               bigint auto_increment comment 'id' primary key,
    requestId        varchar(64)                        not null comment '工作流请求 ID',
    appId            bigint                             not null comment '应用 id',
    userId           bigint                             not null comment '创建用户 id',
    prompt           text                               not null comment '用户提示词',
    codeGenType      varchar(32)                        null comment '代码生成类型',
    workspacePath    varchar(1024)                      null comment '运行工作目录',
    previewUrl       varchar(1024)                      null comment '预览地址',
    status           varchar(32)                        not null comment 'running/succeeded/failed',
    lastResponseJson mediumtext                         null comment '最后一次完整 SSE 响应',
    errorMessage     text                               null comment '错误信息',
    startedTime      datetime default CURRENT_TIMESTAMP not null comment '开始时间',
    finishedTime     datetime                           null comment '结束时间',
    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete         tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_requestId (requestId),
    INDEX idx_appId_userId_createTime (appId, userId, createTime),
    INDEX idx_status (status)
) comment 'V2 工作流运行记录' collate = utf8mb4_unicode_ci;

-- V2 工作流步骤记录表
create table if not exists workflow_step
(
    id            bigint auto_increment comment 'id' primary key,
    runId         bigint                             not null comment '工作流运行 id',
    requestId     varchar(64)                        not null comment '工作流请求 ID',
    stepNumber    int                                not null comment '步骤序号',
    agentName     varchar(128)                       null comment 'Agent 名称',
    stage         varchar(64)                        null comment '工作流阶段',
    status        varchar(32)                        not null comment 'running/succeeded/failed/skipped/degraded',
    inputSummary  text                               null comment '输入摘要',
    outputSummary text                               null comment '输出摘要',
    errorMessage  text                               null comment '错误信息',
    startedTime   datetime                           null comment '开始时间',
    finishedTime  datetime                           null comment '结束时间',
    durationMs    bigint                             null comment '耗时毫秒',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    INDEX idx_runId_stepNumber (runId, stepNumber),
    INDEX idx_requestId (requestId),
    INDEX idx_status (status)
) comment 'V2 工作流步骤记录' collate = utf8mb4_unicode_ci;

-- V2 工作流产物记录表
create table if not exists workflow_artifact
(
    id           bigint auto_increment comment 'id' primary key,
    runId        bigint                             not null comment '工作流运行 id',
    artifactType varchar(64)                        not null comment '产物类型',
    summary      text                               null comment '产物摘要',
    path         varchar(1024)                      null comment '本地路径',
    url          varchar(1024)                      null comment '访问地址',
    jsonContent  mediumtext                         null comment '结构化 JSON 内容',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_runId_type (runId, artifactType),
    INDEX idx_artifactType (artifactType)
) comment 'V2 工作流产物记录' collate = utf8mb4_unicode_ci;
