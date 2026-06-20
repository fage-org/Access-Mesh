---
doc_type: task
id: T-PERM-009
title: 定义 scopeMode 枚举 + 数据权限响应结构改造
status: proposed
plan: docs/plans/scope-mode-migration-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§3-数据权限契约
  - docs/design/permission-center/api-contract.md
depends_on: []
blocks: [T-PERM-010, T-PERM-011, T-PERM-012, T-PERM-013, T-PERM-014, T-PERM-015]
acceptance:
  - "定义 scopeMode 三值枚举 INSTANCE | ALL | NONE"
  - "数据权限响应体由 {allowed, items[], scopeAll} 改为 {allowed, scopeMode, items[], scopeTypeCodes[]}"
  - "强制调用方按枚举三分支编程（编译期/类型系统暴露遗漏分支）"
  - "不做新老兼容（项目未上线，直接换）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-20
---

# T-PERM-009 scopeMode 枚举 + 响应结构改造

> 来源：[scope-mode-migration-plan](../plans/scope-mode-migration-plan.md) 任务 B-1（工作单 B，方案 B2）

## 背景

数据权限"空集即全部"是 P0 静默安全风险：业务方看到 `items: []` 常误读为"不加过滤"→ 数据泄露。`scopeAll` boolean 把"空 ≠ 全量"语义责任甩给调用方。B2 用显式枚举 `scopeMode` 三分支，编译期暴露遗漏。

## 方案要点

- `scopeMode`：`INSTANCE`（用 items[] 加 IN 过滤）/ `ALL`（不加范围过滤）/ `NONE`（直接返回空，不发 SQL）
- 响应：`{allowed, scopeMode, items[], scopeTypeCodes[]}`（仅 INSTANCE 时 items 非空）
- 兼容：直接换，不做新老并存（评审基准声明项目未上线）

## 阻塞下游

B-2~B-7 全部依赖本任务的枚举与响应结构定义。

## 设计回写

- `docs/design/permission-center-v3.5-design.md §3`：核对二层权限模型 L2 与 scopeMode 表述一致
- `docs/design/permission-center/api-contract.md`：scopeMode 作为正式定义（与 T-PERM-014 协同移除迁移注记）
