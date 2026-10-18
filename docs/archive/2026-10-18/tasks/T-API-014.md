---
doc_type: task
id: T-API-014
title: 注册 SDK 无宿主 JSON 对象装配
status: done
plan: docs/archive/2026-10-18/recent-change-review-remediation.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md#contract-section-19-10-1
depends_on: []
acceptance:
  - 缺少宿主 mapper 时可以装配并严格读取清单，已有宿主对象继续复用且不新增全局对象
  - 定向行为验证、本地双轨评审、计划统一回归与设计回写完成
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

承接最近变更的代码核实与用户确认范围，固定评审基线 `060500dc5..1f97aee3d`。

## 范围

缺少宿主 mapper 时可以装配并严格读取清单，已有宿主对象继续复用且不新增全局对象。

## 当前口径

2026-10-18 用户选择局部默认对象方案；不增加正式 Jackson 自动配置依赖。

## 验收对照

- [x] 实现及定向反例。
- [x] 双轨评审、统一回归和设计回写。

## 非目标 / 遗留

不扩大既有产品边界，不重新启动已暂缓事项。验证证据由所属计划统一归档。

## 完成记录

2026-10-18 完成实现、定向验证、本地双轨核对与统一完整回归，设计已回写。命令、实际计数、评审核实与验证边界见[统一验收](evidence/recent-change-review/verification.md)。
