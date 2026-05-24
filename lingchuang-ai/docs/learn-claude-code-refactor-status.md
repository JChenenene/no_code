# learn-claude-code Harness 改造计划与当前进度

## 背景

本项目参考 `shareAI-lab/learn-claude-code` 的 harness engineering 思路：Agent 的智能主要来自模型，工程侧要重点建设模型可用的执行环境，也就是工具、知识、上下文、任务状态、权限边界、工作目录隔离、后台任务和验证闭环。

本项目已经具备 LangChain4j、LangGraph4j、RAG、工具调用、V2 多 Agent 工作流、SSE、构建验证等基础。后续不应重写 agent 核心，而应把这些能力产品化、持久化、安全化。

## Harness 映射总览

| learn-claude-code harness 机制 | 本项目现状 | 目标落点 |
| --- | --- | --- |
| Agent loop | 已有普通生成链路和 V2 LangGraph4j 工作流 | 保持现有 Agent 编排，补执行状态和恢复能力 |
| Tools | 已有文件工具、素材工具、构建工具 | 统一工具权限、路径、结果摘要和 artifact 记录 |
| Todo / Task system | 已有 `workflow_run`、`workflow_step`、`workflow_artifact` | 后续补重试、取消、队列化和更细粒度 started 事件 |
| Skill loading | 现有 `knowledge` 和 `prompt`，但不是按需 Skill | 增加本地 Skill Registry，planner 输出 requiredSkills |
| Context compact | 主要加载最近历史原文 | 增加摘要记忆和工具结果压缩 |
| Background tasks | V2 使用 virtual thread + SSE | 增加 job 状态、取消、超时、查询 |
| Worktree isolation | 阶段 B 已完成 V2 run workspace | V2 按 `appId/runId/codeGenType` 隔离；普通生成保留旧目录回退 |
| Permission governance | 已有 `ToolPathResolver` 基础权限，V2 Vue 工具调用已可绑定 run workspace | 补扩展名白名单、大小限制、敏感读写策略和工具结果摘要 |
| Verification harness | 已有静态引用、JS 语法、Vue 构建检查 | 增加浏览器截图、首屏非空、console error、结构化报告 |

## 当前总体结论

P0 中“V2 接入主聊天链路”和“任务系统与状态持久化”已经完成阶段 A 范围；“工作目录隔离”已经完成阶段 B 范围；“文件工具权限边界”已具备基础路径治理和 V2 run workspace 绑定，但扩展名白名单、大小限制、敏感读写策略仍归入阶段 C。P1/P2 多数仍是基础能力存在，但未形成完整 harness。

| 优先级 | 改造项 | 当前状态 | 结论 |
| --- | --- | --- | --- |
| P0 | V2 多 Agent 工作流接入主聊天链路 | 基本完成 | 前后端已有 V2 模式、SSE 事件和时间线展示 |
| P0 | 任务系统与状态持久化 | 阶段 A 完成 | 已有 `workflow_run`、`workflow_step`、`workflow_artifact`、最新 run 聚合恢复和 runId 明细查询；重试归入后续阶段 |
| P0 | 文件工具权限边界 | 基本完成 | 已有 `ToolPathResolver`，V2 Vue 工具调用已绑定 run workspace；白名单、大小限制和敏感读写策略归入阶段 C |
| P1 | 按需 Skill / 知识加载机制 | 未完成 | 仍是 RAG/knowledge/prompt，没有 Skill Registry |
| P1 | 上下文压缩与摘要记忆 | 未完成 | 仍主要加载最近历史，没有摘要记忆入库 |
| P1 | 工作目录隔离 | 阶段 B 完成 | V2 已按 `appId/runId/codeGenType` 输出，预览/下载/部署优先 latest succeeded run，普通生成保留旧目录回退 |
| P2 | 后台任务与队列化执行 | 未完成 | 有 virtual thread + SSE，没有 job 状态机、取消和查询 |
| P2 | 验证链路提升 | 部分完成 | 已有静态和构建验证，缺浏览器级验证 |

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

已执行验证：

- `./mvnw "-Dtest=AppServiceImplRagTest,WorkflowPersistenceServiceImplTest,WorkflowRunServiceImplTest,WorkflowRuntimeServiceTest,CodeGenWorkflowV2FlowTest,WorkflowV2ResponseMapperTest" test`
- `./mvnw test`
- `npm run type-check`
- `npm run build`

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

### 目标

把文件工具从“能用”升级为“可控、可审计、可恢复”。这对应 learn-claude-code 中 permission governance 和 tool harness 的能力。

### 已有能力

- `ToolPathResolver` 已禁止空路径、绝对路径和越界路径。
- 写模式已禁止隐藏文件和 `.env/application.yml` 等敏感文件。
- `FileWriteTool` 和 `FileModifyTool` 已使用 `ToolPathResolver.resolveForWrite`。

### 需要补强

- 读写都绑定 run workspace。
- 写工具增加文件大小限制，建议单文件默认不超过 1MB。
- 写工具增加扩展名白名单，默认允许 `.html/.css/.js/.ts/.vue/.json/.md/.svg/.png/.jpg/.jpeg/.webp`。
- 读工具默认禁止读取隐藏文件和敏感配置文件；如确需读取，只允许读取生成目录内普通源码文件。
- 工具执行结果写入 `workflow_artifact` 或 `workflow_step.outputSummary`，长内容只保存摘要和 artifact 引用。

### 验收标准

- `../application.yml`、绝对路径、`.env`、隐藏文件写入全部拒绝。
- 合法相对路径能写入当前 run workspace。
- 大文件写入被拒绝并返回清晰错误。
- 工具结果不会把超长文件内容直接塞入聊天上下文。

## 阶段 D：Skill Registry 与上下文压缩

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

### 加载策略

- 系统提示中只放 skill 名称和 description。
- `RequirementPlannerAgent` 输出 `requiredSkills`。
- `ContextRetrievalAgent` 根据 `requiredSkills` 加载对应 `SKILL.md`，再结合现有 RAG 检索。
- 如果 skill 不存在，记录 degraded，不阻断主流程。

### 摘要记忆规格

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

### 验收标准

- planner 能输出 `requiredSkills`。
- V2 执行时只加载相关 skill，不加载全部知识。
- 长会话继续生成时，prompt 中包含历史摘要，而不是仅依赖最近 20 条原文。

## 阶段 E：后台任务、验证增强和素材治理

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

治理规则：

- 同一用户最多同时运行 1-2 个 V2 任务。
- 单次 V2 默认超时 30 分钟。
- 取消后停止继续发送 SSE，run 和未完成 step 标记为 `cancelled`。

### 验证增强

保留现有静态验证：

- 入口文件检查。
- 静态资源引用检查。
- JS 语法检查。
- Vue 构建检查。

新增浏览器验证：

- 打开预览 URL。
- 截图并写入 artifact。
- 检查首屏 DOM 文本非空。
- 采集 console error。
- 检查产物目录大小。

### 素材治理

当前素材代码路径存在，但环境依赖缺失时会降级。文档中应明确：

- `PEXELS_API_KEY`：内容图片搜索。
- `DASHSCOPE_API_KEY`：Logo 或模型相关能力。
- `COS_SECRET_ID`、`COS_SECRET_KEY`：素材上传。
- `mmdc.cmd`：Mermaid 渲染。
- `UndrawIllustrationTool` 当前依赖易变 `_next/data` 地址，需要替换或默认降级。

### 验收标准

- 缺素材依赖时，V2 不失败，只记录 degraded。
- 浏览器验证失败时，前端展示 console error 和截图 artifact。
- 取消任务后前端状态变为 cancelled，不再显示“AI 正在思考”。

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

## 交付顺序

1. 先补 `workflow_step` 和 `workflow_artifact`，让 V2 过程可恢复。
2. 再做 run workspace，解决覆盖问题。
3. 再完善工具权限和工具结果摘要。
4. 然后做 Skill Registry 和摘要记忆。
5. 最后做后台任务、浏览器验证和素材治理。

## 测试计划

后端：

- `WorkflowRunServiceTest`：覆盖 running/succeeded/failed/cancelled。
- `WorkflowStepServiceTest`：覆盖 agent step 创建、完成、失败。
- `WorkflowArtifactServiceTest`：覆盖 artifact 写入和查询。
- `ToolPathResolverTest`：覆盖路径穿越、绝对路径、隐藏文件、敏感文件、超大文件。
- `CodeGenWorkflowV2FlowTest`：覆盖成功生成、验证失败、修复循环、失败落库。

前端：

- 普通生成和 V2 工作流切换。
- V2 SSE 正常完成。
- SSE 中断后刷新恢复 timeline。
- 失败 run 展示失败 agent 和错误原因。
- artifact 展开显示验证报告和截图。

集成：

- 跑通 `html`、`multi_file`、`vue_project` 三类生成。
- 同一 appId 连续生成两次，产物不互相覆盖。
- latest succeeded run 可预览、下载、部署。
- 缺素材依赖时降级继续；补齐依赖后素材收集可用。

## 明确不做

- 不复制 learn-claude-code 的 Python agent 示例。
- 不重写现有 LangGraph4j V2 编排。
- 不一次性引入真实 git worktree；先用 run workspace 完成隔离。
- 不把所有 knowledge 直接塞进 prompt；改为 Skill + RAG 按需加载。
