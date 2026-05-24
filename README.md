# 零创 AI

零创 AI 是一个零代码应用开发平台。用户可以通过自然语言描述需求，创建应用、与 AI 持续对话迭代页面，并查看或部署生成结果。

## 主要能力

- 自然语言创建应用：输入应用目标和页面需求后生成初版应用。
- AI 对话式迭代：在应用对话页继续细化需求，实时查看生成效果。
- 应用管理：支持个人应用列表、应用详情、应用信息编辑和删除。
- 应用部署：生成完成后可部署为可访问作品。
- 精选展示与后台管理：支持精选应用展示，管理员可管理应用、用户和对话记录。
- RAG 知识库：内置页面模板、交互模式、部署规范和视觉规范等知识材料，用于辅助生成质量。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、MyBatis-Flex、Redis、LangChain4j、LangGraph4j。
- 前端：Vue 3、TypeScript、Ant Design Vue、Pinia、Vue Router、Vite。
- 存储与集成：MySQL、Redis、腾讯云 COS、Elasticsearch、DeepSeek、DashScope、SiliconFlow、Pexels。
- 工程能力：SSE 流式生成、应用部署、代码下载、截图服务、监控指标和 OpenAPI 接口文档。

## 目录结构

```text
.
├── lingchuang-ai/                  # 主项目目录
│   ├── src/                        # Spring Boot 单体后端
│   ├── lingchuang-ai-frontend/     # Vue 3 前端
│   └── lingchuang-ai-microservice/ # 微服务拆分模块
└── README.md
```

`lingchuang-ai-runtime/` 和 `nginx-1.29.1/` 是本地运行工具或缓存目录，不提交到远程仓库。

## 本地运行

### 后端

```bash
cd lingchuang-ai
./mvnw spring-boot:run
```

默认接口地址为 `http://localhost:8123/api`。运行前需要准备 MySQL、Redis，并按需配置 `DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、`COS_SECRET_ID`、`COS_SECRET_KEY`、`PEXELS_API_KEY`、`SILICONFLOW_API_KEY` 等环境变量。

### 前端

```bash
cd lingchuang-ai/lingchuang-ai-frontend
npm install
npm run dev
```

常用命令：

```bash
npm run build
npm run lint
npm run openapi2ts
```

## 配置说明

- 开发配置入口：`lingchuang-ai/src/main/resources/application.yml`
- 生产配置模板：`lingchuang-ai/src/main/resources/application-prod-sample.yml`
- 前端环境配置：`lingchuang-ai/lingchuang-ai-frontend/src/config/env.ts`

敏感信息应通过环境变量注入，不应提交真实密钥或生产凭据。
