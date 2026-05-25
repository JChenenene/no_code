# learn-claude-code Harness 改造计划与当前进度

## 背景

本项目参考 `shareAI-lab/learn-claude-code` 的 harness engineering 思路：Agent 的智能主要来自模型，工程侧要重点建设模型可用的执行环境，也就是工具、知识、上下文、任务状态、权限边界、工作目录隔离、后台任务和验证闭环。

本项目已经具备 LangChain4j、LangGraph4j、RAG、工具调用、V2 多 Agent 工作流、SSE、构建验证等基础。后续不应重写 agent 核心，而应把这些能力产品化、持久化、安全化。

## Harness 映射总览

| learn-claude-code harness 机制 | 本项目现状 | 目标落点 |
| --- | --- | --- |
| Agent loop | 已有普通生成链路和 V2 LangGraph4j 工作流 | 保持现有 Agent 编排，补执行状态和恢复能力 |
| Tools | 已有文件工具、素材工具、构建工具 | 统一工具权限、路径、结果摘要和 artifact 记录 |
| Todo / Task system | 已有 `workflow_run`、`workflow_step`、`workflow_artifact`、单机 job registry 和失败/取消全量重试 | 后续补单步恢复、超时治理、队列化和更细粒度 started 事件 |
| Skill loading | 现有 `knowledge` 和 `prompt`，但不是按需 Skill | 增加本地 Skill Registry，planner 输出 requiredSkills |
| Context compact | 主要加载最近历史原文 | 增加摘要记忆和工具结果压缩 |
| Background tasks | 阶段 G 已有 virtual thread + SSE + runId 取消令牌 | 后续增加超时、队列和跨进程恢复 |
| Worktree isolation | 阶段 B 已完成 V2 run workspace | V2 按 `appId/runId/codeGenType` 隔离；普通生成保留旧目录回退 |
| Permission governance | 阶段 C 基础治理已完成 | 已有 run workspace、路径归一化、敏感文件限制、扩展名白名单、1MB 上限和工具结果摘要 |
| Verification harness | 阶段 F 基础完成并已实测 | 已有静态引用、JS 语法、Vue 构建、产物大小、浏览器截图、首屏 DOM、console error 验证和静态预览目录回退 |

## 当前总体结论

P0 中“V2 接入主聊天链路”和“任务系统与状态持久化”已经完成阶段 A 范围；“工作目录隔离”已经完成阶段 B 范围；“文件工具权限边界”已经完成阶段 C 的基础治理。P1/P2 多数仍是基础能力存在，但未形成完整 harness。

| 优先级 | 改造项 | 当前状态 | 结论 |
| --- | --- | --- | --- |
| P0 | V2 多 Agent 工作流接入主聊天链路 | 基本完成 | 前后端已有 V2 模式、SSE 事件和时间线展示 |
| P0 | 任务系统与状态持久化 | 阶段 A 完成 | 已有 `workflow_run`、`workflow_step`、`workflow_artifact`、最新 run 聚合恢复和 runId 明细查询；已修复流式实时 step 与最终结果重复写入；重试归入后续阶段 |
| P0 | 文件工具权限边界 | 阶段 C 基础完成 | 已有 `ToolPathResolver`，V2 Vue 工具调用已绑定 run workspace；已补白名单、大小限制、敏感读写和工具结果摘要 |
| P1 | 按需 Skill / 知识加载机制 | 阶段 D 基础完成 | 已有本地 Skill Registry、`requiredSkills` 和 V2 按需加载；未做独立 load_skill 工具 |
| P1 | 上下文压缩与摘要记忆 | 阶段 I 基础完成 | 已新增 `app_chat_summary`，普通生成和 V2 检索都会加载长期摘要记忆 + 最近少量原文 |
| P1 | 工作目录隔离 | 阶段 B 完成 | V2 已按 `appId/runId/codeGenType` 输出，预览/下载/部署优先 latest succeeded run，普通生成保留旧目录回退 |
| P2 | 后台任务与队列化执行 | 阶段 G 基础完成 | 已有状态查询、取消标记、运行中保护、单机 job registry 和节点间协作式取消；未做超时和队列 |
| P2 | 验证链路提升 | 阶段 F 基础完成并已实测 | 已有静态、构建、产物大小、静态首屏非空、浏览器截图、首屏 DOM、console error、非文件链接跳过和结构化 artifact |

## 阶段 A：V2 可恢复任务图

状态：已完成。

完成时间：2026-05-24。

本阶段已经把 V2 工作流从“只靠本次 SSE 内存时间线”升级为“数据库可恢复任务图”。现在 V2 主聊天链路会创建 `workflow_run`，运行中按 `agent_completed` 事件写入 `workflow_step`，最终完成时保存完整 `WorkflowV2Response`、重建权威步骤时间线，并把 `task_spec/retrieval/asset/code/review/fix/verification/final` 等产物写入 `workflow_artifact`。前端刷新后通过 `/app/workflow/latest/detail` 恢复完整时间线，而不是只显示一条 `WorkflowV2 RESTORED`。

### 目标

把 V2 工作流从“本次 SSE 内存时间线”升级为“数据库可恢复任务图”。刷新页面后，用户应能看到最近一次 V2 的完整 agent 时间线、失败原因、验证结果和产物位置。

### 数据库规格

继续保留 `workflow_run`，但要修正语义：

- `status` 扩展为 `pending/running/succeeded/failed/cancelled`。
- `lastResponseJson` 保存完整 `WorkflowV2Response` JSON，不再只保存摘要。
- `codeGenType`、`workspacePath`、`previewUrl` 暂不在阶段 A 改表，归入阶段 B 的运行目录隔离和产物预览恢复。

新增 `workflow_step`：

| 字段 | 说明 |
| --- | --- |
| `id` | 步骤 ID |
| `runId` | 关联 `workflow_run.id` |
| `requestId` | 工作流请求 ID，便于日志关联 |
| `stepNumber` | 执行顺序 |
| `agentName` | Agent 名称，例如 `RequirementPlannerAgent` |
| `stage` | 阶段，例如 `PLANNING/RETRIEVAL/VERIFYING` |
| `status` | `running/succeeded/failed/skipped/degraded` |
| `inputSummary` | 输入摘要 |
| `outputSummary` | 输出摘要 |
| `errorMessage` | 错误信息 |
| `startedTime` / `finishedTime` | 开始和结束时间 |
| `durationMs` | 耗时 |
| `isDelete` | 逻辑删除 |

新增 `workflow_artifact`：

| 字段 | 说明 |
| --- | --- |
| `id` | 产物 ID |
| `runId` | 关联 `workflow_run.id` |
| `artifactType` | `task_spec/retrieval/asset/code/review/fix/verification/final` |
| `summary` | 产物摘要 |
| `path` | 本地路径，例如代码目录、构建目录 |
| `url` | 预览或部署 URL |
| `jsonContent` | 结构化 JSON |
| `createTime` / `updateTime` | 时间 |
| `isDelete` | 逻辑删除 |

### 后端接口

保留现有：

- `GET /app/chat/gen/code/v2`
- `GET /app/workflow/latest?appId=...`

已新增或升级：

- `GET /app/workflow/{runId}`：返回 run + steps + artifacts 聚合视图。
- `GET /app/workflow/{runId}/steps`：返回步骤时间线。
- `GET /app/workflow/{runId}/artifacts`：返回产物列表。
- `GET /app/workflow/latest/detail?appId=...`：返回 run + steps + artifacts 聚合视图，供前端刷新恢复。

### SSE 事件规格

现有事件继续兼容：

- `workflow_start`
- `agent_started`
- `agent_completed`
- `route_decision`
- `step_completed`
- `workflow_completed`
- `workflow_error`

已补充：

- 每个事件都带 `runId` 和 `requestId`。
- `agent_completed` 在 agent 执行后写入 step，状态归一化为 `succeeded/failed/skipped/degraded/running`。
- `workflow_completed` 携带完整 `WorkflowV2Response`，后端落库为完整 response JSON，并生成结构化 artifact。

### 实现说明

- `AppServiceImpl.chatToGenCodeV2` 负责创建 `workflow_run`，并把同一个 `requestId` 传入 `CodeGenWorkflowV2`。
- V2 SSE 事件在主聊天链路统一补齐 `runId/requestId`。
- 运行中 `agent_completed` 先写入 `workflow_step`，便于中途刷新恢复已完成步骤。
- `workflow_completed` 后调用 `WorkflowPersistenceService.saveWorkflowResult`，删除同 run 的临时 step/artifact 后按完整 response 重建最终数据。
- `GET /app/workflow/latest/detail` 和 `GET /app/workflow/{runId}` 都返回 `WorkflowRunDetailVO`。
- `agent_started` 仍由当前内存执行记录派生，尚未做到真正“执行前落库 running”；这不影响阶段 A 的完成后恢复，后续可在后台任务阶段继续细化为实时状态机。

### 验收结果

- V2 执行一轮后，数据库能查到 1 条 run、多条 step、多条 artifact。
- 刷新聊天页面后，工作流面板恢复完整步骤，不再只显示 `WorkflowV2 RESTORED`。
- 如果最终 `finalStatus` 不是 `SUCCESS`，run 会标记为 `failed`，失败 step 会记录 `errorMessage`。
- 本地库 `lingchuang_ai` 已建好 `workflow_run`、`workflow_step`、`workflow_artifact`。
- 2026-05-25 复测发现流式 `agent_completed` 实时写入 step 后，最终 `saveWorkflowResult` 再逻辑删除并重写会留下 `isDelete=1` 历史副本；已改为已有实时 step 时只补缺，不再重复写入。最新 run `2058848503709122560` 验证为 9 条可见 step、0 条逻辑删除副本。

已执行验证：

- `./mvnw "-Dtest=AppServiceImplRagTest,WorkflowPersistenceServiceImplTest,WorkflowRunServiceImplTest,WorkflowRuntimeServiceTest,CodeGenWorkflowV2FlowTest,WorkflowV2ResponseMapperTest" test`
- `./mvnw test`
- `npm run type-check`
- `npm run build`
- `./mvnw "-Dtest=WorkflowPersistenceServiceImplTest" test`

## 阶段 B：运行目录隔离

状态：已完成。

完成时间：2026-05-24。

### 目标

避免同一个 appId 多次生成、修复、验证互相覆盖。每次 V2 run 都有独立 workspace。

### 目录规范

V2 产物目录统一为：

```text
tmp/code_output/{appId}/{runId}/{codeGenType}/
```

示例：

```text
tmp/code_output/416069017569198082/187000000000000001/vue_project/
tmp/code_output/416069017569198082/187000000000000002/html/
```

普通生成链路继续保留 `codeGenType_appId`。V2 已优先完成 run workspace，旧目录作为预览、下载、部署的回退。

### 需要改造的能力

- `WorkflowRun` 已新增 `codeGenType/workspacePath/previewUrl`，创建后用 runId 绑定工作目录。
- `GeneratedArtifactSupport` 已支持 `resolveRunWorkspaceDir`、`resolveLegacyGeneratedCodeDir` 和 run 级预览 URL。
- `CodeGenWorkflowV2` / `WorkflowRuntimeService` 已支持传入 `workflowRunId/workspacePath`。
- `CodeAuthorAgent` / `PatchAuthorAgent` 已优先写入 `sessionState.workspacePath`。
- `AiCodeGeneratorFacade` 已支持 HTML/MULTI_FILE 指定输出目录；Vue 项目通过 `ToolPathResolver` 注册 run workspace，避免固定到 `vue_project_{appId}`。
- 下载、聊天页预览、编辑页预览、部署默认使用 latest succeeded run；如果没有 V2 succeeded run，则回退到旧目录。
- 前端刷新恢复 V2 run 时优先使用 latest succeeded run 的 `previewUrl`；V2 完成时也会基于 `runId` 切到 run 级预览 URL；编辑页“查看预览”也会优先打开 latest succeeded run 的 `previewUrl`。

### 验收标准

- 同一个 appId 连续执行两次 V2，两个 run 的文件目录互不覆盖。
- 前端预览显示 latest succeeded run 的产物。
- 部署和下载指向同一份 latest succeeded run 产物。
- 没有 successful V2 run 时，下载、预览、部署仍回退到旧目录，不影响普通生成。

已执行验证：

- `./mvnw "-Dtest=ToolPathResolverTest,WorkflowRunServiceImplTest,WorkflowRuntimeServiceTest,AppServiceImplRagTest" test`

## 阶段 C：权限治理与工具结果治理

状态：基础完成。

完成时间：2026-05-24。

### 目标

把文件工具从“能用”升级为“可控、可审计、可恢复”。这对应 learn-claude-code 中 permission governance 和 tool harness 的能力。

### 已有能力

- `ToolPathResolver` 已禁止空路径、绝对路径和越界路径。
- 读写模式均禁止隐藏文件和 `.env/application.yml` 等敏感文件。
- V2 Vue 工具调用已通过 `workflowRunId` 绑定当前 run workspace。
- 写入已限制扩展名白名单：`.html/.css/.js/.ts/.vue/.json/.md/.svg/.png/.jpg/.jpeg/.webp`。
- `FileWriteTool`、`FileModifyTool`、`FileReadTool` 均限制单文件内容不超过 1MB。
- `FileWriteTool`、`FileModifyTool` 的工具执行展示会截断长内容，避免把超长内容塞进聊天历史。

### 需要补强

- 工具执行结果进一步结构化写入 `workflow_artifact` 或 `workflow_step.outputSummary`，目前已先做聊天历史截断摘要。
- 后续如需要支持更多二进制资源类型，应通过白名单显式扩展，不能回退成任意文件写入。

### 验收标准

- `../application.yml`、绝对路径、`.env`、隐藏文件写入全部拒绝。
- 合法相对路径能写入当前 run workspace。
- 大文件写入被拒绝并返回清晰错误。
- 工具结果不会把超长文件内容直接塞入聊天上下文。

已执行验证：

- `./mvnw "-Dtest=ToolPathResolverTest" test`
- `./mvnw "-Dtest=ToolPathResolverTest,PatchAuthorAgentTest,WorkflowRuntimeServiceTest,AppServiceImplRagTest" test`
- `SPRING_DATASOURCE_PASSWORD=123456 ./mvnw test`
- `npm run type-check`

## 阶段 D：Skill Registry 与上下文压缩

状态：基础完成。

完成时间：2026-05-24。

### 目标

参考 learn-claude-code 的 on-demand skill loading 和 context compact：不要把所有知识一次性塞进上下文，而是在 planner 判断需要时加载最小必要技能；长会话用摘要替代旧原文。

### Skill 目录规范

新增本地目录：

```text
src/main/resources/skills/
  design-ui/SKILL.md
  vue-project/SKILL.md
  deployment/SKILL.md
  code-review/SKILL.md
  asset-collection/SKILL.md
```

每个 `SKILL.md` 至少包含：

- `name`
- `description`
- `when_to_use`
- `instructions`
- `examples`

### 已完成能力

- 新增 `SkillRegistryService`，扫描 `classpath*:skills/*/SKILL.md`，解析 front matter 中的 `name/description`，提供 Skill 目录和按需正文加载。
- 新增默认 Skill：`design-ui`、`vue-project`、`deployment`、`code-review`、`asset-collection`。
- `TaskSpec` 已新增 `requiredSkills`，planner prompt 已要求模型只选择必要 Skill。
- `RequirementPlannerAgent` 会保留模型输出的 `requiredSkills`，并根据输出类型和关键词补充明显需要的技能，例如 `vue_project` 自动补 `vue-project`，素材类需求补 `asset-collection`，页面/自我介绍类需求补 `design-ui`。
- `ContextRetrievalAgent` 会根据 `requiredSkills` 加载 Skill 正文，并写入 `RetrievalBundle.loadedSkills/missingSkills/skillContents`；未知 Skill 只记录缺失，不阻断主流程。
- `WorkflowV2PromptComposer` 只把已加载的 Skill 正文注入 Author Prompt，不把全部 Skill 固定塞入上下文。
- `RagPromptSupport.buildHistorySummary` 已做确定性压缩：较早历史压成 `[历史摘要]`，最近 4 条保留原文且单条截断，避免长会话把旧内容无限塞入 RAG/普通生成 prompt。

### 加载策略

- planner system prompt 中只描述可选 skill 名称和 description。
- `RequirementPlannerAgent` 输出并归一化 `requiredSkills`。
- `ContextRetrievalAgent` 根据 `requiredSkills` 加载对应 `SKILL.md`，再结合现有 RAG 检索。
- 如果 skill 不存在，记录 missing/degraded，不阻断主流程。

### 摘要记忆规格

本阶段未新增数据库表。原因：`app_memory_summary` 属于 schema 变更，应单独确认后执行。阶段 D 先落地不改表的上下文压缩，作为 s06 micro-compact 的项目内版本。

后续可新增 `app_memory_summary`：

新增 `app_memory_summary`：

| 字段 | 说明 |
| --- | --- |
| `id` | 摘要 ID |
| `appId` | 应用 ID |
| `userId` | 用户 ID |
| `summaryType` | `conversation/workflow/tool/decision` |
| `summary` | 摘要内容 |
| `sourceStartTime` / `sourceEndTime` | 摘要覆盖范围 |
| `createTime` / `updateTime` | 时间 |
| `isDelete` | 逻辑删除 |

上下文组装改为：

```text
长期摘要 + 最近 4-8 条原文 + 当前用户请求 + 必要 skill + RAG snippets
```

当前已实现的无表版本：

```text
[历史摘要] 较早对话压缩说明 + 最近 4 条原文 + 当前用户请求 + 必要 skill + RAG snippets
```

### 验收标准

- planner 能输出并归一化 `requiredSkills`。
- V2 执行时只加载相关 skill，不加载全部知识。
- 长会话继续生成时，prompt 中包含旧历史压缩摘要和最近原文，不再原样拼接全部历史。

已执行验证：

- `./mvnw "-Dtest=SkillRegistryServiceTest,ContextRetrievalAgentTest,WorkflowV2PromptComposerTest,RagPromptSupportTest" test`

## 阶段 E：后台任务、验证增强和素材治理

状态：基础完成。

完成时间：2026-05-25。

### 后台任务

目标状态：

- `pending`
- `running`
- `succeeded`
- `failed`
- `cancelled`

接口：

- `POST /app/workflow/{runId}/cancel`
- `GET /app/workflow/{runId}/status`

已完成能力：

- 新增 `GET /app/workflow/{runId}/status`，返回当前用户可访问的 `workflow_run` 状态。
- 新增 `POST /app/workflow/{runId}/cancel`，运行中 run 标记为 `cancelled`，写入 finishedTime 和取消原因。
- `WorkflowRunService` 支持 `getRunningRun`、`cancelRun`、`isCancelled`。
- V2 启动前检查同一用户同一 app 是否已有 `running` run；存在则拒绝再次启动，避免前端一直叠加“AI 正在思考”。
- V2 Flux 完成或异常时会检查 run 是否已取消；如果已取消，不再覆盖为 `succeeded/failed`，也不再写入完成后的 AI 聊天摘要。

治理规则：

- 同一用户同一 app 当前最多 1 个 V2 running run。
- 单次 V2 Flux/SSE 仍沿用当前执行模型，SSE emitter 超时 30 分钟。
- 阶段 G 已引入单机可取消 job registry 和执行令牌；本阶段保留状态取消语义，取消后不会覆盖最终完成态。

### 验证增强

保留现有静态验证：

- 入口文件检查。
- 静态资源引用检查。
- JS 语法检查。
- Vue 构建检查。

后续浏览器验证目标：

- 打开预览 URL。
- 截图并写入 artifact。
- 检查首屏 DOM 文本非空。
- 采集 console error。

已完成能力：

- 静态项目和 Vue 项目验证都会记录产物目录大小到 `VerificationArtifact.details`。
- 验证前新增产物目录大小上限，当前限制为 2MB；超过限制时 `failureType=artifact_size`，`canFix=true`。
- 大小统计跳过 `node_modules` 和 `.git`，避免依赖目录污染生成产物判断。
- 静态 HTML / multi-file 项目会检查 `index.html` 可见文本；只有脚本、样式、标签或空白内容时判定为 `first_screen_empty`。
- 浏览器截图、真实 DOM 首屏非空和 console error 采集尚未接入，仍保留为后续增强。

### 素材治理

当前素材代码路径存在，环境依赖缺失时会在执行外部工具前降级并说明原因：

- `PEXELS_API_KEY`：内容图片搜索。
- `DASHSCOPE_API_KEY`：Logo 或模型相关能力。
- `COS_SECRET_ID`、`COS_SECRET_KEY`：素材上传。
- `mmdc.cmd`：Mermaid 渲染。
- `UndrawIllustrationTool` 当前依赖易变 `_next/data` 地址，需要替换或默认降级。

已完成能力：

- `AssetPlanningAgent` 会根据模型规划出的任务类型做依赖预检。
- 如果计划包含内容图片任务但缺 `PEXELS_API_KEY`，直接返回 degraded，避免空跑 Pexels。
- 如果计划包含 Logo 任务但缺 `DASHSCOPE_API_KEY`，直接返回 degraded，避免空跑 DashScope。
- 如果计划包含 Mermaid 图任务但缺 Mermaid CLI 或 COS 配置，直接返回 degraded，避免生成后上传失败。
- unDraw 插画任务暂不做启动前阻断，工具调用失败时沿用原有“部分素材任务失败，降级继续”。

### 验收标准

- 缺素材依赖时，V2 不失败，只记录 degraded。
- 取消任务后 run 状态变为 `cancelled`，后续完成回调不会覆盖成 succeeded/failed。
- 同一用户同一 app 已有 running run 时，新的 V2 请求会被拒绝。
- 产物目录超过大小限制时验证失败，并给出结构化 `artifact_size` 问题。
- 静态 HTML / multi-file 的 `index.html` 没有可见文本时验证失败，并给出结构化 `first_screen_empty` 问题。
- 素材计划依赖缺失时不会调用外部工具，直接记录 `AssetPlan.degraded=true` 和缺失依赖说明。
- 浏览器验证失败时展示 console error 和截图 artifact 已在阶段 F 接入。

已执行验证：

- `./mvnw "-Dtest=WorkflowRunServiceImplTest,AppServiceImplRagTest" test`
- `./mvnw "-Dtest=BuildVerifyAgentTest" test`
- `./mvnw "-Dtest=BuildVerifyAgentTest,AssetPlanningAgentTest" test`

## 前端展示方案

`AppChatPage.vue` 中 V2 面板从简单列表升级为三层：

- 顶部：run 状态、耗时、最终结果、预览入口。
- 中部：agent timeline，展示 planner/retrieval/asset/author/review/fix/verify/final。
- 底部：可展开 artifact，包括验证报告、素材、代码目录、截图。

刷新恢复逻辑：

1. 页面加载 app 信息。
2. 调用 `GET /app/workflow/latest/detail?appId=...`。
3. 如果 latest run 是 `running`，展示运行中状态并提示可继续等待。
4. 如果 latest run 是 `succeeded/failed/cancelled`，展示完整历史状态。

## 后续总计划

当前 A-H 已经完成基础 harness：V2 可恢复任务图、运行目录隔离、工具权限治理、Skill Registry、上下文压缩、状态查询/取消、协作式真取消、失败/取消全量重试、基础验证、素材依赖预检和浏览器级验证闭环。后续不再做“大改一锅端”，按阶段 I-J 和 G+ 逐步补齐产品化能力。

### 阶段 F：浏览器级验证闭环

状态：基础完成。

完成时间：2026-05-25。

目标：

- 把当前“静态文件验证”升级为“真实预览验证”。
- 对 HTML、multi-file、Vue 构建产物统一做浏览器打开、首屏非空、console error 和截图 artifact。

后端改造：

- 已新增 `BrowserVerificationService`，输入 run 上下文、本地验证结果和 preview URL，输出结构化 `BrowserVerificationResult`。
- 已新增可替换的 `BrowserPreviewProbe`，默认实现为 `SeleniumBrowserPreviewProbe`，单元测试可 mock 浏览器环境。
- 已对静态项目使用 run 级 preview URL；Vue 项目使用 `dist/index.html` 的 run 级 preview URL。
- 已修复 run 级静态预览 URL 访问目录时不返回深层 `index.html` 的问题；`/static/{appId}/{runId}/{codeGenType}/` 现在会回退到该目录下 `index.html`。
- 已修复静态引用检查把 `javascript:`、`mailto:`、`tel:`、锚点和其他 URI scheme 当本地文件路径解析的问题；非法本地路径会转成验证 issue，不再中断整个工作流。
- 已采集：
  - HTTP 可访问状态。
  - 首屏 DOM 文本长度。
  - console error 列表。
  - screenshot 本地路径或 COS URL。
- 已将截图和浏览器报告写入 `workflow_artifact`，artifactType 为 `verification_screenshot`、`browser_verification`。
- `BuildVerifyAgent` 保持本地静态验证职责，并在本地验证通过、有 V2 run 上下文时追加浏览器验证；浏览器验证失败会进入现有修复闭环。

前端改造：

- V2 面板 artifact 区已展示浏览器验证报告。
- 有截图 URL 时展示“查看截图”入口。
- console error 先展示数量；详细错误保留在 artifact JSON 中，后续可展开展示。

验收标准：

- 生成空白 HTML 时，静态验证或浏览器验证能失败并明确 `first_screen_empty`。
- 生成有 JS 运行时报错的页面时，浏览器验证能采集 console error。
- 成功生成时能看到截图 artifact。
- 浏览器验证失败不会导致 run 丢失，失败信息能刷新恢复。

测试计划：

- `DefaultBrowserVerificationServiceTest`：覆盖本地验证失败跳过浏览器、preview URL 和截图 URL 生成。
- `BuildVerifyAgentTest`：覆盖浏览器 console error 合并到 `VerificationArtifact` 并标记 `console_error`。
- `WorkflowPersistenceServiceImplTest`：覆盖 `browser_verification` 和 `verification_screenshot` artifact 落库。
- 前端 `npm run type-check`。
- 手动 V2 生成用例仍需在本地后端运行状态下执行，确认 Selenium/Chrome 环境可用。

实测结果：

- 2026-05-25：重启前后端后，用临时测试账号执行“小范自我介绍页面”V2 流程，runId `2058848503709122560`，最终 `finalStatus=SUCCESS`。
- 预览 URL `http://127.0.0.1:8123/api/static/416456794957819904/2058848503709122560/html/` 返回 200，内容包含“小范”。
- 截图 URL `http://127.0.0.1:8123/api/static/416456794957819904/2058848503709122560/html/verification/browser-screenshot.jpg` 返回 200，类型为 `image/jpeg`。
- 数据库状态：`workflow_run.status=succeeded`，`workflow_step` 可见记录 9 条且无 `isDelete=1` 副本，`workflow_artifact` 包含 `browser_verification` 和 `verification_screenshot`。
- 回归验证：`./mvnw test` 通过，结果为 110 tests、0 failures、19 skipped；`npm run type-check` 通过。

不做：

- 不在本阶段做视觉美学自动评分。
- 不引入复杂云端浏览器服务，先使用本地 Playwright/Selenium 能力。
- 不把截图上传 COS 作为强依赖；当前优先保存到 run workspace 并通过静态资源 URL 访问。

### 阶段 G：真正的后台任务与取消执行

状态：基础完成。

完成时间：2026-05-25。

目标：

- 从“状态取消”升级为“可取消、可查询、可超时”的后台执行治理。
- 解决用户看到一直思考、重复启动、任务卡住时只能等的问题。

后端改造：

- 已新增运行时 `WorkflowJobRegistry`，维护 runId 到 `WorkflowCancelToken`、requestId 和开始时间。
- `AppServiceImpl.chatToGenCodeV2` 创建 run 后会注册 job，Flux 完成、错误或取消后通过 `doFinally` 清理 job。
- `AppServiceImpl.cancelWorkflowRun` 会同时触发运行时取消令牌和数据库 `workflow_run.status=cancelled`。
- `WorkflowRuntimeService` 在调用 V2 时按 runId 获取取消令牌并传入 `CodeGenWorkflowV2`。
- `CodeGenWorkflowV2` 在 LangGraph 节点边界检查取消令牌，取消后抛出 `WorkflowCancelledException`，停止后续节点。
- Flux/SSE 模式取消时发送 `workflow_cancelled` 事件并正常 complete，避免前端一直卡在 loading。
- `workflow_run` 状态继续保留 `pending/running/succeeded/failed/cancelled`，本阶段未改表。
- SSE 断开时不立即取消后台任务，前端仍可通过 `GET /app/workflow/{runId}/status` 和 latest detail 恢复。

仍未完成：

- 未做自动超时扫描；超时任务还不能自动标记 `timeout`。
- 未做队列化 pending 调度；同一 app 仍采用运行中保护和拒绝重复启动。
- 未做跨进程恢复；应用重启后内存 job registry 会丢失，但数据库 run 状态仍可查询。

前端改造：

- V2 面板已增加“取消”按钮，只有 latest/current run 为 running 时展示。
- 已新增 `cancelWorkflowRun` API 封装，调用 `POST /app/workflow/{runId}/cancel`。
- 前端接收 `workflow_cancelled` 事件后会把消息 loading 置 false，并刷新 latest workflow detail。
- running 状态展示 runId、开始时间、耗时仍未完成。
- 如果 SSE 断开，展示“任务仍在后台运行，可刷新恢复”。

验收标准：

- 点击取消后，run 变为 `cancelled`，后续 agent 不继续写入成功结果。
- 同一个 app 仍只允许一个 running run。
- 超时任务能自动结束，前端能看到超时原因。（未完成，归入阶段 H 前置或后续 G+）
- 刷新页面能恢复 running/cancelled/failed 状态。

测试计划：

- `WorkflowJobRegistryTest`：注册、取消、清理、缺失 job 取消返回 false。
- `WorkflowRuntimeServiceTest`：运行时注册、取消、向 V2 传递取消令牌。
- `CodeGenWorkflowV2FlowTest`：取消令牌能阻断后续 agent。
- `AppServiceImplRagTest`：取消接口触发运行时取消；取消后完成回调不覆盖 run 状态。

已执行验证：

- `./mvnw "-Dtest=WorkflowJobRegistryTest,WorkflowRuntimeServiceTest,CodeGenWorkflowV2FlowTest,AppServiceImplRagTest" test`

不做：

- 不上完整 MQ。
- 不做跨进程分布式任务恢复；单机内存 job registry 先满足当前应用。

### 阶段 H：失败重试与单步恢复

状态：基础完成。

完成时间：2026-05-25。

目标：

- 用户可以对失败或已取消的 V2 run 发起全量重试。
- 每次重试都创建新的 runId 和新的 workspace，不覆盖原 run 的 steps/artifacts。
- 单步恢复和从验证/修复阶段继续仍保留为后续 H+，本阶段不做图级 checkpoint。

后端改造：

- 已新增 `GET /app/workflow/{runId}/retry`，返回 SSE 流并复用现有 V2 事件处理；使用 GET 是因为浏览器原生 `EventSource` 不能发 POST。
- 已在 `AppService.retryWorkflowRun` 中限制只有 `failed/cancelled` run 可以重试。
- 已复用原 run 的 `appId`、`prompt` 和当前 app 的 `codeGenType` 创建新 run。
- 已沿用阶段 B 的 run workspace 规则，重试会写入新的 `tmp/code_output/{appId}/{newRunId}/{codeGenType}/`。
- 已通过 `workflow_artifact` 写入 `retry_parent`，记录 parent runId、requestId、status、prompt 和 workspace，暂不改表新增 `parentRunId` 字段。
- 未新增 `POST /app/workflow/{runId}/retry-from?stage=...`；从验证/修复阶段继续需要 artifact 复制/复用和图节点入口控制，后续单独做。

前端改造：

- V2 面板在 `failed/cancelled` 状态展示“重试”按钮。
- 已抽出 `attachWorkflowEventSource`，普通 V2 生成和 retry 共用 SSE 事件处理。
- 重试开始后清空当前 timeline/artifacts，进入 running 状态；完成后通过 latest detail 恢复新 run 明细。
- 暂未展示 parent run 跳转入口；parent 信息已在 `retry_parent` artifact 中留存。

验收标准：

- 失败或已取消 run 能重试并生成新的 runId。
- 原 run 的 steps/artifacts 不被覆盖。
- 新 run 记录 `retry_parent` artifact，方便追溯来源。
- 同一 app 已有 running run 时仍拒绝重试，避免并发覆盖。
- 从修复继续时复用旧代码目录或复制到新 workspace 后修改尚未完成，归入 H+。

测试计划：

- `AppServiceImplRagTest`：失败 run 可全量重试、running run 禁止重试、成功 run 禁止重试。
- `WorkflowPersistenceServiceImplTest`：`retry_parent` artifact 落库。
- `CodeGenWorkflowV2FlowTest`：继续覆盖 V2 基础执行和取消边界。
- 前端 `npm run type-check` 覆盖 retry API 和 V2 面板类型。

已执行验证：

- `./mvnw "-Dtest=AppServiceImplRagTest,WorkflowPersistenceServiceImplTest" test`
- `./mvnw "-Dtest=WorkflowJobRegistryTest,WorkflowRuntimeServiceTest,CodeGenWorkflowV2FlowTest,AppServiceImplRagTest,WorkflowPersistenceServiceImplTest" test`
- 2026-05-25 重启前后端后，点击失败 run 的“重试”按钮，确认原 `POST` SSE 入口会被浏览器 `EventSource` 以 `GET` 调用导致 405；已改为 `GET /app/workflow/{runId}/retry` 并复测通过。
- 复测 runId `2058858718651277312`：页面进入 running、最终 `succeeded`，浏览器验证通过，iframe 指向 `/api/static/416069017569198082/2058858718651277312/html/`。
- 预览 URL `http://127.0.0.1:8123/api/static/416069017569198082/2058858718651277312/html/` 返回 200，内容包含“小范”。
- `./mvnw test`
- `npm run type-check`

不做：

- 不做任意 agent 节点的图级 checkpoint 恢复；先做产品上最有价值的失败/取消全量重试。
- 不做 `retry-from` 单步入口。
- 不复制旧 workspace 的 code/review/verification artifact 到新 workspace；后续做 H+ 时再补。

### 阶段 I：摘要记忆持久化与上下文压缩升级

状态：基础完成。

完成时间：2026-05-25。

目标：

- 从当前“无表确定性压缩”升级为可持久化、可复用的摘要记忆。
- 长会话继续生成时，不再依赖最近 20 条原文或临时压缩说明。

数据库改造：

- 已新增 `app_chat_summary`：
  - `id`
  - `appId`
  - `userId`
  - `summaryType`
  - `summaryText`
  - `coveredMessageIds`
  - `tokenEstimate`
  - `createTime`
  - `updateTime`
  - `isDelete`
  - 唯一键：`uk_app_user_type (appId, userId, summaryType)`

后端改造：

- 已新增 `AppChatSummary`、`AppChatSummaryMapper`、`AppChatSummaryService`、`AppChatSummaryServiceImpl`。
- 摘要生成采用确定性压缩：从最近对话历史构造 `USER/AI` 摘要，不调用大模型，不新增 key。
- `ChatHistoryService.addChatMessage` 保存成功后刷新 conversation 摘要；摘要刷新失败只记录 warn，不影响主聊天流程。
- `ChatHistoryService.loadChatHistoryToMemory` 现在加载“长期摘要 system message + 最近少量原文”，避免继续把最近 20 条全量塞入 memory。
- 普通生成 `RagInvocationContext` 已带 `memorySummary`，`RagPromptSupport`、`RagPromptAugmentor` 和 `HybridKnowledgeContentInjector` 会把摘要注入 retrieval query / augmented prompt。
- V2 `ContextRetrievalAgent` 会读取持久化摘要，把它拼入检索输入，并写入 `RetrievalBundle.memorySummary`。
- `WorkflowV2PromptComposer` 会把 `RetrievalBundle.memorySummary` 注入 Author Prompt 的“长期对话摘要记忆”章节。
- 本地库 `lingchuang_ai` 已创建 `app_chat_summary` 表。

验收标准：

- 长会话多轮后，prompt 中包含持久化摘要。
- 摘要生成失败时，仍能使用最近原文回退。
- 普通 RAG 和 V2 RAG 都能把摘要作为上下文输入。

测试计划：

- `AppChatSummaryServiceImplTest`：创建、更新、读取摘要。
- `ChatHistoryServiceImplTest`：保存聊天后刷新摘要、加载 memory 包含摘要。
- `ContextRetrievalAgentTest`：摘要与 skill/RAG 组合。
- `RagPromptSupportTest`：retrieval query 包含长期摘要记忆。
- `AppServiceImplRagTest`：普通生成会把摘要放入 `RagInvocationContext`。

已执行验证：

- `./mvnw "-Dtest=AppChatSummaryServiceImplTest,ChatHistoryServiceImplTest,ContextRetrievalAgentTest" test`
- `./mvnw "-Dtest=AppChatSummaryServiceImplTest,ChatHistoryServiceImplTest,ContextRetrievalAgentTest,RagPromptSupportTest,AppServiceImplRagTest" test`
- `./mvnw "-Dtest=AppChatSummaryServiceImplTest,ChatHistoryServiceImplTest,ContextRetrievalAgentTest,RagPromptSupportTest,AppServiceImplRagTest,WorkflowV2PromptComposerTest,CodeGenWorkflowV2FlowTest,WorkflowRuntimeServiceTest" test`
- `./mvnw test`，结果为 124 tests、0 failures、19 skipped。

不做：

- 不做向量化长期记忆；先做结构化摘要。
- 不在本阶段引入模型摘要调用；先保证可持久化、可回退、可验证的基础闭环。
- 不做工具结果独立摘要表；当前继续沿用阶段 C 的工具结果截断摘要。

### 阶段 J：Skill 工具化与素材能力完善

状态：待做。

目标：

- 从“planner 按需加载本地 Skill 文本”升级为更接近 learn-claude-code 的 skill/tool harness。
- 素材收集从“可降级”升级为“可配置、可观察、可替换”。

Skill 改造：

- 增加 `SkillManifest` 字段：
  - name
  - version
  - description
  - when_to_use
  - required_env
  - tool_permissions
  - output_contract
- 给 V2 增加内部 `load_skill` 能力或 service API，显式记录加载过哪些 skill。
- skill 加载结果写入 `workflow_artifact`，便于回放和调试。

素材改造：

- 替换 `UndrawIllustrationTool` 对 `_next/data` 易变地址的依赖，改成稳定来源或默认关闭。
- 内容图、Logo、Mermaid 分别记录子任务状态：
  - `planned`
  - `skipped_missing_dependency`
  - `succeeded`
  - `failed_degraded`
- 素材结果写入 artifact，前端可展开查看。
- 增加配置开关：
  - `workflow.asset.enabled`
  - `workflow.asset.content-image.enabled`
  - `workflow.asset.logo.enabled`
  - `workflow.asset.diagram.enabled`
  - `workflow.asset.illustration.enabled`

验收标准：

- 每次 V2 run 能看到加载了哪些 skill。
- 缺素材依赖时能看到具体哪类素材被跳过。
- 补齐依赖后，素材工具能实际返回资源并写入 artifact。
- 素材失败不影响主代码生成。

测试计划：

- `SkillRegistryServiceTest`：manifest 解析和 required_env。
- `AssetPlanningAgentTest`：不同素材类型的跳过、成功、失败降级。
- 集成手测：补齐 `PEXELS_API_KEY` 后跑一轮内容图片收集。

不做：

- 不让模型任意读取本地 skill 目录；仍由后端 registry 白名单控制。

## 后续推荐执行顺序

1. 阶段 I：做摘要记忆持久化。原因是它需要稳定的 run/artifact 数据作为上下文来源。
2. 阶段 J：做 Skill 工具化和素材完善。原因是素材依赖外部服务，最后做更稳。
3. 阶段 G+：补超时扫描和轻量队列。原因是协作式取消已完成，剩余是执行治理增强。
4. 阶段 H+：补 `retry-from` 和单步恢复。原因是它需要更明确的 artifact 复制/复用契约，不能和全量重试混在一起做。

## 后续统一测试策略

每个阶段至少执行：

- 本阶段新增单元测试。
- V2 流程相关组合测试：`CodeGenWorkflowV2FlowTest`、`WorkflowRuntimeServiceTest`、`WorkflowV2ResponseMapperTest`。
- 后端关键回归：`./mvnw test`。
- 前端类型检查：`npm run type-check`。

阶段 F/J 涉及浏览器或外部素材时，额外做手动集成验证：

- `html`：简单自我介绍页。
- `multi_file`：带 CSS 和 JS 的多文件页面。
- `vue_project`：构建、预览、截图、下载、部署。

## 明确不做

- 不复制 learn-claude-code 的 Python agent 示例。
- 不重写现有 LangGraph4j V2 编排。
- 不一次性引入真实 git worktree；先用 run workspace 完成隔离。
- 不把所有 knowledge 直接塞进 prompt；改为 Skill + RAG 按需加载。
