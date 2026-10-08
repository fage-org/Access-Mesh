---
doc_type: task
id: T-ACCESS-093
title: 独立平台身份、账号与审计
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md#platform-accounts
  - docs/design/tenant-lifecycle.md#platform-audit
  - docs/design/schema/access-service.sql
  - docs/design/access-service-api-contract.md
depends_on: []
acceptance:
  - 平台身份独立于租户，首次部署只创建平台账号，平台令牌不能访问租户业务端点
  - 多账号同权管理、随机密码交付、改密、停用恢复及最后可用管理员保护符合设计
  - 平台审计独立落表，关键数据库变更与审计同事务且不记录凭据
  - 身份混用、并发最后管理员保护与审计失败回滚具备行为证据
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

承接 [Q-063](../../../pending-problems.md#q-063) 已确认的独立平台身份、同权账号管理及强事务审计。

## 范围

平台账号、认证、可信上下文、初始化、账号管理与独立审计后端；同步更新对应契约和 schema。前端与 Gateway 分别由所属任务承接。

## 当前口径

按[平台账号](../../../design/tenant-lifecycle.md#platform-accounts)和[平台审计](../../../design/tenant-lifecycle.md#platform-audit)设计执行；不为平台账号伪造租户或租户主体。

## 验收对照

- [x] 平台认证与租户身份严格隔离。
- [x] 初始化、账号生命周期、密码交付与最后管理员保护。
- [x] 独立审计和强事务回滚证据。
- [x] 契约、schema 与运行说明回写。

## 非目标 / 遗留

不提供平台角色与细分权限管理；不以本卡解决租户内 Q-059 的管理员治理。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
