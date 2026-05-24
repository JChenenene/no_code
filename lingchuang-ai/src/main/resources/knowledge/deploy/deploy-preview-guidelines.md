---
title: 部署与预览约束
codeGenTypes: vue_project, multi_file, html
sourceType: deploy
priority: medium
tags: deploy, preview, build
---
# 部署约束

- HTML 与多文件页面输出后应直接可访问
- Vue 工程需要完成构建，确保产物目录为 dist
- 资源引用优先使用相对路径，避免部署后静态资源失效

## 预览约束

- 预览页面加载时不得依赖未配置的后端接口
- 图片、字体、脚本路径要适配静态部署
