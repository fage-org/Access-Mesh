---
doc_type: task
id: T-PERM-092
title: （R2-T13）删除旧执行体与四旧 DTO
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §9.1/§9.4
depends_on:
  - T-PERM-089
  - T-PERM-090
  - T-PERM-091
blocks: []
acceptance:
  - "PermQuery/PermBatchQuery/PermResult/PermBatchResult 与独立编排删除；PermResultUtils 改为新结果到既有外部响应的纯转换或删除（不先重建旧 PermResult 再转换）；X04：生产引用为零（直接调用/方法引用/反射/序列化/测试/文档全查），RolePermEntry 仅作 ROLE_PERM_SNAPSHOT 缓存载荷边界例外明确"
  - "架构测试：getDenied 名称可留作薄门面，但禁止其注入权限 Mapper、解析角色或调用条件/互斥服务"
  - "R2 完成条件闭合：本卡在仍有 LEGACY_API 服务时也可完成（legacy 语义已在新 execute 内表达，无永久双执行）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-092 （R2-T13）删除旧执行体与四旧 DTO

## 背景

设计 §9.1 字段迁移不能只包门面、§9.4 删除与回退检查（报告临时编号 R2-T13）。两个完成条件之一：本卡完成=R2 入口统一（≠T-PERM-054 完成）。

## 范围

- 删除与全仓残留清扫（含 mock 层与文档现在时残留）；回写时把 R2 引擎终态章节按现行规范并入 engine/implementation.md（设计稿对应章节标注已并入；设计稿整体转 superseded 在计划完结归档时，准入面回写由 ADM 系列卡承担）。

## 非目标 / 遗留

- API 独立授权退役与 legacy 协议退役=T-ACCESS-062（第二个完成条件）。
