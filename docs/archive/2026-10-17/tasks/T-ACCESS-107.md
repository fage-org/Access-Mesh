---
doc_type: task
id: T-ACCESS-107
title: 有界审计 CSV 导出与登录日志字段清理
status: done
plan: docs/archive/2026-10-17/audit-log-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md#contract-section-16-7
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
depends_on: []
acceptance:
  - 范围内行为符合契约，必要回归通过
  - 本地双轨评审与设计回写完成
  - 随计划统一完成完整回归及前端工程门禁
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-061](../../../pending-problems.md#q-061)，已采纳规则见[契约 §16.7](../../../design/access-service-api-contract.md#contract-section-16-7)。

## 范围

三类 CSV 导出端点、同源权限与脱敏、行数/字节限额、固定查询快照、location 退出、路由登记及契约。

## 当前口径

执行契约 §16.7；实际出现新的产品取舍时提交用户决定。

## 验收对照

- [x] 行为与回归。
- [x] 双轨评审与设计回写。
- [x] 计划统一验收。

## 非目标 / 遗留

遵循所属计划非目标；不擅自扩大保留或删除范围。

## 验证记录

2026-10-17 完成，实际命令、计数、复验与验证边界见[统一验收](evidence/audit-log-lifecycle/verification.md)。
