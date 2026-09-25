---
doc_type: task
id: T-PERM-080
title: （R2-T01）全仓调用与语义清点
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.5/§9.1
depends_on: []
blocks: []
acceptance:
  - "全部旧执行体消费点（query/queryBatch、四便捷入口、computeInstanceDenied/passesScopeAll 的直接调用、方法引用、反射、缓存序列化、测试夹具、文档引用）清点成册，区分「旧执行体消费点」与「LEGACY_API 业务模式消费点」两类"
  - "每个实际调用点有唯一迁移目标（新 Selection/ResultForm 组合，或显式退役）"
  - "外部 batch 上限与内部 getDenied 容量分别盘点落账；清点结论回写设计 §6.5 迁移矩阵增补"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-080 （R2-T01）全仓调用与语义清点

## 背景

统一设计稿 §6.5 消费者迁移矩阵只列主要消费者，不代替完整引用清单（设计 §6.5 末句）；实施前必须先成册。报告临时编号 R2-T01（映射见计划卡）。

## 范围

- rg 全仓盘点旧执行体符号（PermQuery/PermBatchQuery/PermResult/PermBatchResult、query/queryBatch、hasPermissionByCode/getDeniedResourceCodes/hasPermissionByEntityId/getDeniedEntityIds、computeInstanceDenied/passesScopeAll），含 mock 层、测试夹具、文档引用（`-g '!docs/archive/**'`）；清点维度六面：调用/语义/输出形态/事务边界/缓存序列化/协议（设计 §9.2 R2-T01 口径）。
- 按消费语义分两类登记：旧执行体消费点（迁新 execute）与 LEGACY_API 业务模式消费点（保持共同集合语义迁入新 execute）。
- 盘点外部 batch 上限与内部 getDenied 容量（分属不同约束面）。

## 非目标 / 遗留

- 不改任何实现代码；本卡是清点与迁移目标表产出。
