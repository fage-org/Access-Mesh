---
doc_type: task
id: T-PERM-097
title: 新增分组角色互斥写守卫子树展开
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §15（rule 能力：互斥规则）
  - docs/design/engine/core-flows.md §7（权限查询引擎评估口径）
  - docs/design/engine/implementation.md §2.4（PermissionConflictDomainService）
depends_on: []
blocks: []
acceptance:
  - "写守卫新增侧把分组角色（GROUP_ROLE）目标展开子树后再入互斥 postState：已有角色 Y、规则互斥 X/Y 时，绑定子树含 X 的组 G 不再被放行（回归锁以该场景实证旧实现失败）"
  - "持有侧既有展开语义不变；窗口（validFrom/validTo）继承与持有侧对齐，口径写入契约 §15"
  - "assign/batch-assign/sync/full-sync 四入口同批核对覆盖（sync BIND 逐条守卫既有形态复用）"
  - "运行时 fail-closed 兜底语义不因写守卫增强而弱化（评估口径回归面不受影响）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-097 新增分组角色互斥写守卫子树展开

## 背景

承接 [Q-027](../pending-problems.md#q-027)：互斥写守卫（`UserManageAppServiceImpl.rejectRoleMutexOnAssign` 与 sync/full-sync BIND）中，持有侧组展开已覆盖，新增侧只把组 G 本身入 postState、未展开其子树成员 X——规则互斥 X/Y 时保存无提示放行，运行时展开后 X/Y 同场被双删并使原有 Y 失效。运行时 fail-closed 兜底仍在，不构成放行越权，但写时静默破坏既有授权。

## 范围

新增侧组目标展开子树并继承窗口；四入口（assign/batch-assign/sync/full-sync）同步核对；互斥计算复用 T-PERM-083 纯互斥计算既有设施。

## 当前口径

写守卫应与运行时评估对同一「有效持有 + 新增目标」全集做互斥预判；剪枝语义差异（写守卫要原始持有、运行时要有效持有）维持 Q-028 登记的边界，不在本任务合并遍历。

## 非目标 / 遗留

- 共享遍历参数化（Q-028 → T-PERM-101）。
- 互斥计算本身的语义变更（S/H/D 已于 T-PERM-083 定案）。
