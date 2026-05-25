---
name: code-review
description: 适用于代码审查、修复建议、阻塞问题识别和最小变更原则。
---

# code-review

## when_to_use

- 工作流进入 review、fix 或 patch 阶段。
- 用户要求检查质量、修复问题或避免回归。

## instructions

- 先找会导致运行失败、构建失败、空白页或安全风险的问题。
- 修复建议必须可执行，避免空泛评价。
- patch 阶段只改必要文件，不能扩大需求范围。
- 验证通过前不要宣称完成。

## examples

- 缺入口文件、引用不存在资源、JS 语法错误属于 blocker。
- 视觉细节优化不是 blocker，除非用户明确要求。
