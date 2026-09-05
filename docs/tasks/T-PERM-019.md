---
doc_type: task
id: T-PERM-019
title: 工作单 D：防呆机制（type_value 自动分配、业务键封装、AppliesTo）
status: proposed
plan: docs/plans/design-review-def-followup-plan.md
domain: permission-center
design_refs:
  - docs/archive/2026-06-17/design-review.md#§11
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "D1 已落地收口（2026-09-05 核实，卡面标完成、无代码工作）：createType 服务端分配=全量行（含软删行）max+1、软删不复用为分配语义本身；并发撞值/显式码抢占映射 20049 可重试 + uk_type_definition_* 兜底；api-contract §5.1 与 core-flows §3 已于 2026-08-28 收口成文（本卡 2026-08-22 所记 DESIGN_DRIFT 已不存在）"
  - "D3 注解方案废弃（2026-09-05 定案）：@AppliesTo 预设「表达全局操作」，该概念已随 T-PERM-049 整体退役；类型专属操作现由 DDL 种子/预置组 + 操作位按类型隔离 + 授权侧适用性校验（20008/20005）完整表达，注解化属多余元数据；残余=OperationCodeConstants/ResourceTypeCode 常量、DDL 种子、校验路径三方一致性核对，并入 D2 交付"
  - "D2 BusinessKeys 收敛（唯一实质交付，2026-09-05 定案保留）：后端业务键拼接点盘点（TYPEKEY_<typeValue> 生成码、relationKey `TYPE:externalId` 解析、bootstrap `typeCode:operationCode` 拼接等，SyncKeyCodec 已集中作范本）收敛到 perm-common 统一封装 + BusinessKeysParityTest 锁格式；范围限后端（前端 TS 类型辅助不动）；T-PERM-051 新增 `{typeKey}:{typeCode}` 键族直接经本模块构造，不得再裸拼"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-05
---

# T-PERM-019 工作单 D：防呆机制

> 状态：proposed（2026-09-05 设计体检重基线：D1 标完成、D3 废注解收窄、D2 收敛为唯一实质交付；定案来源 [design-audit-followup](../plans/design-audit-followup-plan.md)，本卡 plan 字段保留原始溯源）

## 背景

D 来自归档设计评审 §11 的暂缓项，目标是减少权限中心实现阶段的隐式约定和易错点。当前核对结论是：D 的方向与现行设计基本兼容，但 `type_value` 外部入参和软删除不复用保证方式存在文档漂移，必须先收敛设计再实现。

> **重基线（T-ACCESS-012，2026-08-22）**：原 D4「SyncHandler 版本声明」已移除——归并后内部 admin→permission 同步链已删除（T-ACCESS-005），仅剩外部业务服务 sync 摄入面（`/api/perm/**/sync`），跨服务协议演进防护价值大幅下降（T-ACCESS-012 重基线移除）；外部 sync 的 `sync_metadata` 版本校验按现行契约（api-contract §6.2）继续有效，不依赖本任务。落点为 access-service permission 域；schema 权威为 `access-service.sql`。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| D1 | `type_value` 自动分配器 | 已随 T-PERM-023 落地：服务端 max+1（含软删行）+ 20049 并发兜底 + 文档 2026-08-28 收口（2026-09-05 复核确认） | ✅ 完成 |
| D2 | BusinessKeys 封装 | 唯一实质交付：拼接点盘点收敛到 perm-common + BusinessKeysParityTest；范围限后端 | 待执行 |
| D3 | `@AppliesTo` | 注解方案废弃（2026-09-05：全局操作概念已退役，类型专属操作由种子/位段/授权校验完整表达）；残余一致性核对并入 D2 | 已重基线 |

## 执行前确认（2026-09-05 重基线后已全部有答案）

1. ✅ 外部 `type-definition/create` 不接收 `typeValue`，服务端在 `tenant_id + type_key` 内自动分配——**已实现即终态**。
2. ✅ 软删除 `type_value` 不复用由分配语义（全量行含软删 max+1）保证，无需墓碑表——**已实现即终态**。
3. ✅ 设计回写随 D2 收口一并完成（D1 相关文档已收口，无遗留）。
4. ✅ 采用 `perm-common.BusinessKeys` + `BusinessKeysParityTest` 默认方案；`@AppliesTo` 不做（废弃，见子项核对表）。

## 验收标准

- 冲突设计先被修订，任务完成前 `design_writeback.status` 必须为 `done`。
- 不引入 RESTful 路径参数或 `@RequestParam`。
- 不在 AppService 重写 DomainService 已有领域逻辑。
- 涉及批量解析时不得引入 N+1 查询。
