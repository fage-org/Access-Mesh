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
  - "执行前确认：D1 type_value 自动分配语义已定稿，且 api-contract/core-flows/schema 对外 typeCode、内部 type_value、软删除不复用的边界一致"
  - "设计回写明确 type_value 在 tenant_id + type_key 内自动分配、全局唯一、软删除不复用；外部 API 不要求调用方传 typeValue；当前仅 delete_flag=0 的唯一索引不足以单独保证不复用，需明确 allocator/墓碑策略"
  - "按归档评审默认方案引入 perm-common.BusinessKeys + BusinessKeysParityTest，覆盖 grant/role/resource/interface mapping/sync 常见业务键，禁止散落字符串拼接；如改用等价方案需执行前确认"
  - "@AppliesTo 或等价注册模型能表达资源类型专属操作与全局操作，常量、种子、校验路径一致"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-PERM-019 工作单 D：防呆机制

> 状态：proposed
> 执行门禁：进入 `in-progress` 前必须再次确认 D1 语义和设计回写范围。

## 背景

D 来自归档设计评审 §11 的暂缓项，目标是减少权限中心实现阶段的隐式约定和易错点。当前核对结论是：D 的方向与现行设计基本兼容，但 `type_value` 外部入参和软删除不复用保证方式存在文档漂移，必须先收敛设计再实现。

> **重基线（T-ACCESS-012，2026-08-22）**：原 D4「SyncHandler 版本声明」已移除——归并后内部 admin→permission 同步链已删除（T-ACCESS-005），仅剩外部业务服务 sync 摄入面（`/api/perm/**/sync`），跨服务协议演进防护价值大幅下降（用户决策移除）；外部 sync 的 `sync_metadata` 版本校验按现行契约（api-contract §6.2）继续有效，不依赖本任务。落点为 access-service permission 域；schema 权威为 `access-service.sql`。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| D1 | `type_value` 自动分配器 | `api-contract.md` 与 schema 强调外部使用 `typeCode`、内部使用 `type_value`，但 `core-flows.md` 仍有 `type-definition/create` 输入 `typeValue` 的旧描述；schema 当前唯一索引只约束 `delete_flag=0`，不足以单独保证“软删不复用” | `DESIGN_DRIFT` |
| D2 | BusinessKeys 封装 | 归档评审已明确默认方案为 `perm-common.BusinessKeys` + `BusinessKeysParityTest`；方向与当前业务键规范兼容，替代方案需另行确认 | `NO_HARD_CONFLICT` |
| D3 | `@AppliesTo` | 与资源类型专属操作、全局操作模型兼容；需核对 OperationCode 常量、种子与校验路径 | `NO_HARD_CONFLICT` |

## 执行前确认

1. 是否确认外部 `type-definition/create` 不再接收 `typeValue`，由服务端在 `tenant_id + type_key` 内自动分配。
2. 是否通过 allocator/墓碑机制保证软删除 `type_value` 不复用，而不是仅依赖当前 `delete_flag=0` 唯一索引。
3. 是否允许先做设计回写，再做代码防呆实现。
4. 是否按默认方案使用 `perm-common.BusinessKeys` + `BusinessKeysParityTest`，并把 `@AppliesTo` 定位为内部实现约束，不暴露为 API 契约。

## 验收标准

- 冲突设计先被修订，任务完成前 `design_writeback.status` 必须为 `done`。
- 不引入 RESTful 路径参数或 `@RequestParam`。
- 不在 AppService 重写 DomainService 已有领域逻辑。
- 涉及批量解析时不得引入 N+1 查询。
