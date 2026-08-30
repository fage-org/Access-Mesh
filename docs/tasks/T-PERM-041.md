---
doc_type: task
id: T-PERM-041
title: 主权限条件不变量（20041 不可转授 + 20042 启用状态 + 主权限 DDL CHECK + 全形态校验 + 测试）
status: done
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
  status: done
last_updated: 2026-08-30
---

# T-PERM-041 主权限条件不变量（20041 不可转授 + 20042 启用状态）

> 状态：done（2026-08-30 收口；2026-08-05 评审确认：条件权限不可转授使用错误码 20041；**2026-08-08 补充：20042 条件启用状态并入本任务**）
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

**2026-08-30 收口**。调研核实：acceptance 五项中 20041（枚举 + `validateGrantAttributes` creates/updates 最终态判定）、主权限 DDL CHECK（`ck_role_resource_permission_condition_can_grant`）与 api-contract/core-flows/permission-grant.md 契约文字均**先行存在**（设计先行回写），实际缺口为 20042 后端校验与计划级测试矩阵：

- **枚举**：`PermissionErrorCode` 新增 `CONDITION_DISABLED(20042, "权限条件已停用")`（沿用枚举预留编号与 api-contract §6.5.1 既定登记；LOCAL_PROJECTION_IMMUTABLE javadoc 的预留说明同步收口为"已由 CONDITION_DISABLED 承载"）。
- **20042 校验**（`prevalidateGrantPlan` 两处，均紧随 20041 之后，符合 §6.5.1 错误优先级 20041 → 20042 → 20033 → 其他）：
  - creates 主权限：`conditionCode != null` 时目标条件必须 enabled=true（子权限带条件已被 20043 先行拦截，能携带条件到判定处的均为主权限）；
  - updates：`conditionCode` 非空且**变更**时（新条件 id ≠ 改前 `conditionId`，**2026-08-30 设计定案：按解析后条件 id 比对，同 id 重写视同存量保留豁免**，与前端 v3.1「未修改 conditionCode 允许保留」同口径）目标条件必须 enabled=true；清除（`""`）与缺省（`null`）不触发。
- **20006 存在性批量预检先于 20041/20042 维持既有顺序**（2026-08-30 设计定案：极端组合下与 mock 演练顺序错误码不同但均为拒绝，正常 UI 不可达该组合，不重排 prevalidate 结构）。
- **测试**：`PermissionGrantPlanDomainServiceImplTest` 新增 `MainPermissionConditionInvariants` 13 用例（20041：create 组合违反/updates 三态合并/只改 conditionCode/只改 canGrant=true/优先级 20041→20042；合法：清条件、canGrant=false、create 条件+canGrant=false；20042：create 写入停用条件/update 变更到停用条件（旧实现下必败，锁住修复）/同 id 重写豁免/存量停用绑定不动合法/停用绑定改绑到启用条件放行（复评补正的正向锁，防豁免条件写反）——计划级经 `doCallRealMethod` 执行真实 `validateGrantAttributes`）；`AccessServiceSchemaH2Test` 补 `ck_role_resource_permission_condition_can_grant` CHECK 拒绝用例（15/15）。
- **文档回写核对**：api-contract §6.5.1 共用不变量块与 20042 规则标已落地 + "未变更"比对口径成文；permission-grant.md §4 停用条件规则补后端落地与比对口径、§11 S11/§12 决策 15 标后端已落地；core-flows §6 L127 20042 时限与同 id 豁免限定经复评补正（首版"无需改"结论有误）、implementation/access-service.sql 表述已是事实性陈述无需改；前端无代码改动（20042 错误码映射与停用条件交互 T-FE-040 先行落地）。
- **回归**：access-service `mvn test` 全量通过（surefire 双轨：单元轨 978 + 容器轨 115，0 失败 0 错误）。
