---
doc_type: task
id: T-ACCESS-110
title: 内置管理服务登记保护
status: done
plan: docs/archive/2026-10-17/permission-lifecycle-boundaries-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/access-service-architecture.md
  - docs/design/frontend/service-interface-mapping.md
depends_on: []
acceptance:
  - 单独或混合批量删除内置登记均整体拒绝，无部分变更
  - 停用内置服务拒绝，其他字段编辑与普通服务启停删除保持
  - 前后端保护一致且说明原因，设计回写
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-075](../../../pending-problems.md#q-075)，实施范围已于 2026-10-17 确认。

## 范围

租户管理 API 与页面禁止停用或删除内置 access-service；普通服务照常维护，内置服务其他字段仍按原规则维护；不新增平台关闭接口。

## 当前口径

以 design_refs 中的现行契约及本任务范围为准；新增设计取舍必须由用户决策。

## 验收对照

- [x] 单独或混合批量删除内置登记均整体拒绝，无部分变更
- [x] 停用内置服务拒绝，其他字段编辑与普通服务启停删除保持
- [x] 前后端保护一致且说明原因，设计回写

## 非目标 / 遗留

本卡不扩大到其他待解决问题。


## 完成记录

2026-10-17：后端根命令 `mvn test -T 1C` 2944 项通过，零失败/错误/跳过（含 E2E/heavy）；前端 611 项与类型、lint、45 组 DTO 对账、构建通过。双轨评审及定向证据见[验收记录](evidence/lifecycle-boundaries/verification.md)。
