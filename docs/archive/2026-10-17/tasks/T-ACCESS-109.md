---
doc_type: task
id: T-ACCESS-109
title: 浏览器 HttpOnly 会话认证改造
status: done
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
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-068](../../../pending-problems.md#q-068) 的 HttpOnly 子项；已确认启动独立改造。

## 当前口径

已按[认证设计](../../../design/browser-session-authentication.md)完成双身份 HttpOnly、CSRF、跨标签隔离、升级重新登录及简化注销。保留 U2，取消 W1 与 X1/X2；未启用 SSO 示例按用户确认删除，记住我分支保留。

## 验收对照

- [x] 范围、契约与设计。
- [x] 实现与安全边界。
- [x] 完整验证、双轨评审与回写。

## 非目标 / 遗留

强制下线与验证码替代仍按原决定暂缓。未确认的范围不自动纳入实施。

## 验证记录

2026-10-17：后端 `mvn test -T 1C` 共 2933 项通过，零失败、零跳过（含容器、heavy 与 E2E）；前端 604 项测试、类型、lint、45 组 DTO 对账及构建通过。浏览器使用隔离模拟后端核验 HttpOnly 可见性、退出失败、刷新、换账号与跨标签隔离，真实认证链由 PG 与 E2E 覆盖。代码/文档双轨评审及已知问题处置见[验收记录](evidence/T-ACCESS-109/verification.md)。
