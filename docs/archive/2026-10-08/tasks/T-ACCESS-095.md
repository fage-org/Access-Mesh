---
doc_type: task
id: T-ACCESS-095
title: 租户生命周期与认证任务入口接线
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md#tenant-suspension
  - docs/design/tenant-lifecycle.md#redis-gate
  - docs/design/tenant-lifecycle.md#tenant-login
  - docs/design/service-authentication.md
  - docs/design/access-service-api-contract.md
depends_on:
  - T-ACCESS-092
  - T-ACCESS-094
acceptance:
  - Redis 预阻断、数据库事务和令牌发布按设计串联，故障拒绝并自动修复
  - 普通登录切换 tenantCode 并与 OAuth2 客户端解耦
  - 会话、M2M、OAuth2 和新启动任务执行租户门禁，恢复后旧用户令牌不复活
  - 并发停用恢复、重启旧状态、故障中断及修复具备确定性行为证据
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

租户停用必须覆盖已认证请求、服务凭证和任务，不能仅在登录时判断状态。

## 范围

状态变更事务、门禁修复、登录切换及服务端认证和任务入口接线。

## 当前口径

按[停用恢复](../../../design/tenant-lifecycle.md#tenant-suspension)及[Redis 协议](../../../design/tenant-lifecycle.md#redis-gate)执行；外部离线 JWT 访问不承诺即时撤回。

## 验收对照

- [x] 状态事务与自动修复。
- [x] tenantCode 登录切换。
- [x] 认证和任务入口无遗漏。
- [x] 并发与故障行为证据及契约回写。

## 非目标 / 遗留

网关与前端分别由 T-GW-013、T-FE-067 承接；不支持 Redis 自动主从切换。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
