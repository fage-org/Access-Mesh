---
doc_type: task
id: T-PERM-041
title: 主权限条件不变量（20041 不可转授 + 20042 启用状态 + 主权限 DDL CHECK + 全形态校验 + 测试）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.5.1
  - docs/design/permission-center/core-flows.md#§6
  - docs/design/schema/access-service.sql
  - docs/design/frontend/permission-grant.md#§4
depends_on:
  - T-PERM-034
blocks:
  - T-FE-018
acceptance:
  - "apply-grant-plan 新增不变量（**2026-08-08 修订：仅主权限**）：**主权限** `conditionCode != null` 时 `canGrant` 必须为 false——覆盖 `creates[].key`（主权限）、updates（目标为主权限）应用三态变更后的**最终状态**（判定与 update 是否同时携带 conditionCode/canGrant 无关：只改 conditionCode 覆盖到当前 canGrant=true 的记录、或只改 canGrant=true 使已有条件的记录变为可转授，均触发 20041）；**子权限不适用本不变量**（子权限 create 非 null/false -> 20043，由 T-PERM-034 管辖；本任务不处理子权限属性）"
  - "违反上述不变量 -> 20041 CONDITIONAL_PERMISSION_CANNOT_DELEGATE（新错误码，加入 PermissionErrorCode 枚举与 api-contract 错误码表）"
  - "schema DDL：role_resource_permission 增加 **CHECK (condition_id IS NULL OR can_grant = false)**（ck_role_resource_permission_condition_can_grant，**主权限**；**子权限 CHECK (depend_on IS NULL OR (condition_id IS NULL AND can_grant = false)) 归 T-PERM-034，职责拆分**）——仅最终态建表 DDL（2026-08-05 评审确认：项目未上线、不考虑历史数据，任务范围不含存量迁移/DBA 执行/部署责任说明）"
  - "**条件启用状态（2026-08-08 产品确认，新增 20042 CONDITION_DISABLED，加入 PermissionErrorCode 枚举与 api-contract 错误码表）**：**主权限** `conditionCode` 新写入或变更时必须 enabled=true（creates 主权限与 updates 中 conditionCode 变化均适用，按最终状态判定）；存量绑定（update 未变更 conditionCode）允许保留并回显标注；违反 -> 20042；与前端选择器/复制同规则（新选限启用、源条件停用时复制入口禁用）；子权限不承载条件（20043），不受本不变量约束"
  - "测试矩阵：**主权限** creates key 带条件+canGrant=true -> 20041 / **主权限** updates 同时改条件+canGrant=true -> 20041 / updates 只改 conditionCode（当前 canGrant=true）-> 20041 / updates 只改 canGrant=true（当前已有条件）-> 20041 / updates 清条件或 canGrant=false 合法 / 主权限条件+canGrant=false 合法（不误伤）/ **主权限 create 或 update 写入/变更到停用条件 -> 20042** / 存量停用绑定未改 conditionCode 的 update 合法 / **子权限相关不变量由 T-PERM-034 管辖（children/挂父 create 带条件 -> 20043，本任务不再覆盖）**"
  - "design_writeback：api-contract §6.5.1（校验规则 + 结构约束 + 错误码枚举）、access-service.sql（role_resource_permission DDL CHECK）、permission-grant.md §4（前端行为）、core-flows.md §6（写链路流程）核对一致"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-PERM-041 主权限条件不变量（20041 不可转授 + 20042 启用状态）

> 状态：proposed（2026-08-05 评审确认：条件权限不可转授使用错误码 20041；**2026-08-08 补充：20042 条件启用状态并入本任务**）
> 依赖：T-PERM-034（`prevalidate` 唯一预检入口基线；流程名 prevalidateGrantPlan，Java 方法名 = `PermissionGrantPlanDomainService.prevalidate`）
> 前置验收：见 acceptance

## 背景

评审确认"条件权限不能转授"为既定不变量：带条件的权限（`conditionCode != null`）不得再授予他人（`canGrant` 必须 false）。**2026-08-08 产品确认追加"条件启用状态"不变量：主权限新写入/变更的 conditionCode 必须为启用状态（20042）**——停用条件不得新建绑定或改绑，存量绑定未变更 conditionCode 允许保留。此前契约未约束、数据库仅范围 CHECK。本任务落地主权限两项 API 校验（20041/20042）、主权限 DDL CHECK 与测试（子权限不变量 20043 归 T-PERM-034，职责拆分确认）。

> **重基线（T-ACCESS-012，2026-08-22）**：落点为 access-service permission 域；schema 权威为 `access-service.sql`。

## 范围

1. `prevalidate`（流程名 prevalidateGrantPlan）新增**主权限**条件转授不变量校验（creates 主权限 + updates 目标为主权限，应用三态变更后的**最终状态**判定，与是否同时携带 conditionCode/canGrant 无关；**仅主权限，子权限由 T-PERM-034 20043 管辖**）。
2. `PermissionErrorCode` 新增 20041 `CONDITIONAL_PERMISSION_CANNOT_DELEGATE` 与 20042 `CONDITION_DISABLED`。
3. `prevalidate`（流程名 prevalidateGrantPlan）新增**主权限**条件启用状态校验：`conditionCode` 新写入或变更时必须 `enabled=true`（creates 主权限与 updates 中 conditionCode 变化均适用，按最终状态判定；存量绑定未变更 conditionCode 允许保留），违反 -> 20042。
4. `access-service.sql` 增加 `ck_role_resource_permission_condition_can_grant` CHECK 约束（主权限 `condition_id IS NULL OR can_grant = false`；**子权限 CHECK 归 T-PERM-034，职责拆分**；仅最终态建表 DDL，2026-08-05 评审确认：不考虑历史数据，不含存量迁移）。
5. 测试覆盖（见 acceptance 第 4/5 条）。

## 完成记录

（待实现后填写）
