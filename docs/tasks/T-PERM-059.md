---
doc_type: task
id: T-PERM-059
title: 权限视图/排查删除重设计——范围圈定与新设计方向（范围待定）
status: proposed
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.8
  - docs/design/access-service-architecture.md#§14
  - docs/design/permission-center/implementation.md#§3
depends_on: []
blocks: []
acceptance:
  - "范围圈定定案：七端点（permission-view 的 effective-permissions/resource-users/role-permissions/effective-roles/resource-tree/explain/recent-changes）+ 前端排查页（permission-query）+ /query-permission-tree（零外部消费端点，2026-09-09 D2 定案并入评估）的删除边界；effective-permission-codes（登录串端点）确认排除"
  - "连动面处置定案：①Gateway bootstrap 固定图（BootstrapGraphDefinition 注册的 effective-permissions/explain 两行）随删调整 + architecture §14.3/§14.4 回写 + runbook 固定图升级 FAQ；②T-FE-043（排查页暂停重做登记卡）absorb/cancel 二选一处置"
  - "新设计方向产出（可与删除分期）：基于统一引擎结果模型（T-PERM-057 落地后）的视图/排查新形态设计草案"
  - "e2e 核对：两垂直切片不触删除面端点（已核实），回归确认零影响"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-059 权限视图/排查删除重设计

> 状态：proposed（2026-09-09 grill Q14 定案任务化：删除重设计，具体范围待定）
> 依赖：无（独立范围决策任务；新设计产出建议在 T-PERM-057 落地后）

## 背景

权限视图/排查系（permission-view 七端点 + 前端排查页）拟整体删除重新设计（2026-09-09 用户定案）。前端排查页本已暂停待重做（T-FE-043，2026-09-06 登记）；/query-permission-tree 经核实为零外部消费端点（前端授权页实际消费 role-resource-permission/list + resource-entity/tree），一并并入评估。

## 范围

- 删除边界圈定与连动面处置（固定图/前端登记卡/文档锚点）；新设计方向草案。

## 非目标 / 遗留

- 登录权限串链路（effective-permission-codes + UserMenuQueryService）不动。
- 管理面写门禁与授权页消费的端点（role-resource-permission/list、resource-entity/tree 等）不在删除面。
