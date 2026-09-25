---
doc_type: task
id: T-PERM-089
title: （R2-T10）迁移 check/batch/管理门禁/getDenied
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.5/§9.1
depends_on:
  - T-PERM-085
  - T-PERM-088
blocks: []
acceptance:
  - "PermissionCheckAppServiceImpl.check/batchCheck、AdminPermissionValidatorImpl、ResourceManage/TypeDefinition 等直接门禁、getDeniedResourceCodes/getDeniedEntityIds 全部经新 execute；batchCheck 禁止循环 N 次公开 execute；外层职责保留（主体业务键解析、SELF 缺省、原序/重复项、请求级父上下文）"
  - "语义变化四消费面（资源树、API 映射、资源依赖、权限树 ID 轨）逐面确认「跨 item 冲突从全拒变各自判」可接受并留差异记录（设计 §6.5 getDenied 行）"
  - "X03 等价差分：除已登记预期修复（PQ-01/06、FACTS 完整性、空角色契约、同源首次读取复用）外全部保持——差分锚来自 T-PERM-081 基线"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-089 （R2-T10）迁移 check/batch/管理门禁/getDenied

## 背景

设计 §6.5 消费者迁移矩阵前三类 + getDenied（报告临时编号 R2-T10）。迁移在明确的调用方边界选择新/旧一次，不让真实请求完整跑两次有副作用鉴权再比较。

## 范围

- check/batchCheck/getDenied 双轨、AdminPermissionValidatorImpl（当前操作者、SecurityException 与技术错误分界）、资源/类型直接门禁（CODE/ENTITY_ID 分型）。
- ID 轨批量管理门禁按原下标映射回输入 ID，不把 ID 转业务码。

## 非目标 / 遗留

- 范围四态与 LEGACY_API 集合在 T-PERM-090；旧快照/转授/视图在 T-PERM-091。
