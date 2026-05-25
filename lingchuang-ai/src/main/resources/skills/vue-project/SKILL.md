---
name: vue-project
description: 适用于 Vue 工程生成、组件拆分、入口文件、构建配置和运行验证。
---

# vue-project

## when_to_use

- 用户要求 Vue 项目、组件化页面、管理端或需要构建验证的工程。
- 输出模式是 `vue_project`。

## instructions

- 必须保证 `package.json`、入口文件和主页面组件完整。
- 组件拆分要服务于可读性，不为简单页面过度拆分。
- 避免引入未声明依赖，优先使用项目已有依赖。
- 生成后应能通过构建验证。

## examples

- Vue 单页项目至少包含 `package.json`、`index.html`、`src/main.js` 或 `src/main.ts`、`src/App.vue`。
