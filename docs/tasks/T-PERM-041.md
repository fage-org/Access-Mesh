---
doc_type: task
id: T-PERM-041
title: 条件权限不可转授（20041 + DDL CHECK + 全形态校验 + 测试）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.5.1
  - docs/design/permission-center/core-flows.md#§6
  - docs/design/schema/permission-center.sql
  - docs/design/frontend/permission-grant.md#§4
depends_on:
  - T-PERM-034
blocks:
  - T-FE-018
acceptance:
  - "apply-grant-plan 新增不变量：conditionCode != null 时 canGrant 必须为 false——覆盖 creates[].key（主权限）、creates[].children[]（嵌套子权限）、parentPermissionId 挂已有父记录的 create、updates 应用三态变更后的**最终状态**（判定与 update 是否同时携带 conditionCode/canGrant 无关：只改 conditionCode 覆盖到当前 canGrant=true 的记录、或只改 canGrant=true 使已有条件的记录变为可转授，均触发 20041）"
  - "违反上述不变量 -> 20041 CONDITIONAL_PERMISSION_CANNOT_DELEGATE（新错误码，加入 PermissionErrorCode 枚举与 api-contract 错误码表）"
  - "schema DDL：role_resource_permission 增加 CHECK (condition_id IS NULL OR can_grant = false)（ck_role_resource_permission_condition_can_grant）——仅最终态建表 DDL（2026-08-05 二轮评审确认：项目未上线、不考虑历史数据，任务范围不含存量迁移/DBA 执行/部署责任说明）"
  - "测试矩阵：creates key 带条件+canGrant=true -> 20041 / children 带条件+canGrant=true -> 20041 / 挂父 create 带条件+canGrant=true -> 20041 / updates 同时改条件+canGrant=true -> 20041 / **updates 只改 conditionCode（当前 canGrant=true）-> 20041 / updates 只改 canGrant=true（当前已有条件）-> 20041** / updates 清条件或 canGrant=false 合法 / 条件+canGrant=false 各形态合法（不误伤）"
  - "design_writeback：api-contract §6.5.1（校验规则 + 结构约束 + 错误码枚举）、permission-center.sql（DDL CHECK）、permission-grant.md §4（前端行为）、core-flows.md §6（写链路流程）核对一致"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-06
---

# T-PERM-041 条件权限不可转授

> 状态：proposed（2026-08-05 评审确认：条件权限不可转授使用错误码 20041）
> 依赖：T-PERM-034（prevalidateGrantPlan 唯一预检入口基线）
> 前置验收：见 acceptance

## 背景

评审确认"条件权限不能转授"为既定不变量：带条件的权限（`conditionCode != null`）不得再授予他人（`canGrant` 必须 false）。此前契约未约束、数据库仅范围 CHECK。本任务落地 API 校验（20041）、DDL CHECK 与测试。

## 范围

1. `prevalidateGrantPlan` 新增条件转授不变量校验（creates 三形态 + updates 应用三态变更后的**最终状态**判定，与是否同时携带 conditionCode/canGrant 无关）。
2. `PermissionErrorCode` 新增 20041 `CONDITIONAL_PERMISSION_CANNOT_DELEGATE`。
3. `permission-center.sql` 增加 `ck_role_resource_permission_condition_can_grant` CHECK 约束（**仅最终态建表 DDL**，2026-08-05 二轮评审确认：不考虑历史数据，不含存量迁移）。
4. 测试覆盖（见 acceptance 第 4 条）。

## 完成记录

（待实现后填写）
