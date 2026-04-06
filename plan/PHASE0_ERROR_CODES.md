# Phase 0 错误码表

本文档用于冻结 Phase 0 已存在的拒绝原因与通用错误语义。错误码格式按本轮约定统一为“字母-数字”，模块标识采用 `PERM`。

## 1. 编码规则

- 格式：`PERM-XXX`
- `PERM` 表示权限中心模块
- `XXX` 为三位数字
- 本文档仅为现有语义分配编码，不新增错误场景

## 2. 拒绝原因编码

以下错误码与 `PermissionCheckResult.denyReason` 建立映射关系：

| 错误码 | 拒绝原因 | 说明 |
|--------|----------|------|
| `PERM-001` | `NO_ROLE` | 用户在指定域下无角色 |
| `PERM-002` | `NO_PERMISSION` | 无匹配的授权记录 |
| `PERM-003` | `CONDITION_FAIL` | 条件评估不通过 |
| `PERM-004` | `CONFLICT` | 命中冲突规则导致权限失效 |
| `PERM-005` | `DEPENDENCY_FAIL` | 依赖链不完整 |

## 3. 通用错误语义编码

以下编码对应现有执行计划中已冻结的通用错误语义：

| 错误码 | 错误语义 | 说明 |
|--------|----------|------|
| `PERM-101` | 请求参数非法 | 请求体字段缺失、格式非法或枚举值非法 |
| `PERM-102` | 资源不存在 | `resource_entity` 不存在或已删除 |
| `PERM-103` | 操作不存在 | `operation_permission` 不存在或已删除 |
| `PERM-104` | 资源类型与操作类型不匹配 | `operation_permission.resource_type` 与资源类型不兼容 |
| `PERM-105` | 域配置不允许 | 不满足 `domain_scope_config` 或 `domain_relation_config` 约束 |
| `PERM-106` | 条件不可用 | `permission_condition.status` 非 `APPROVED` 或 `permission_condition.enabled = false` |

## 4. 使用约束

- `denyReason` 字段继续使用既有枚举值，不替换为错误码
- 错误码用于接口错误语义、日志、审计与后续实现时的稳定映射
- 同一语义在 Phase 0 内只能对应一个固定编码
- 未在本文档出现的错误场景，不得在 Phase 0 中自行新增编码
