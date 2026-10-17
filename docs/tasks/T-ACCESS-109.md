---
doc_type: task
id: T-ACCESS-109
title: 浏览器 HttpOnly 会话认证改造
status: in-progress
plan: —
domain: cross-service
design_refs:
  - docs/design/browser-session-authentication.md
  - docs/design/access-service-api-contract.md
  - docs/design/frontend/login.md
  - docs/design/services/gateway.md
depends_on: [T-FE-071, T-FE-072]
acceptance:
  - 范围与兼容边界由用户确认并落入设计
  - 已确认范围的浏览器认证与 CSRF 保护整体实现
  - 保持双身份隔离、跨标签处置及现有权限安全边界
  - 完成对应前后端验证、双轨评审及设计回写
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-17
---

## 背景

承接 [Q-068](../pending-problems.md#q-068) 的 HttpOnly 子项；已确认启动独立改造。

## 当前口径

当前处于[方案定案](../design/browser-session-authentication.md)阶段，已确认租户与平台网页登录一起覆盖（R1）、新增网页专用 Cookie 入口并保留原 Bearer 登录（S1）、拒绝对应身份域 Cookie 与 Bearer 混用（T1）；退出确认与重新登录时序等剩余取舍待决定；不得先行修改既有认证通道。

## 验收对照

- [ ] 范围、契约与设计。
- [ ] 实现与安全边界。
- [ ] 完整验证、双轨评审与回写。

## 非目标 / 遗留

强制下线与验证码替代仍按原决定暂缓。未确认的范围不自动纳入实施。
