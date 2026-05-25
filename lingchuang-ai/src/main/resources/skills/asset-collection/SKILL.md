---
name: asset-collection
description: 适用于图片、图标、Logo、插画、Mermaid 图和外部素材收集规划。
---

# asset-collection

## when_to_use

- 用户要求素材、图片、图标、Logo、插画、图表或品牌视觉资源。
- 页面效果依赖外部图片或可替代的本地 SVG/样式资源。

## instructions

- 素材不可用时必须降级，不能阻断代码生成。
- 优先选择和页面主题直接相关的素材，避免泛化占位图。
- 外部 URL 需要能被前端访问，无法确认时提供本地替代方案。
- 不把第三方素材下载到敏感目录。

## examples

- 个人介绍页可使用头像占位、技能图标和简洁背景图。
- 品牌页可优先规划 Logo 和主视觉图片。
