---
doc_type: task
id: T-ADMIN-030
title: 角色重新指派对既有绑定的语义收口
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §10（role 能力：user-role 分配）
depends_on: []
blocks: []
acceptance:
  - "拍板重指派语义：相同绑定重试幂等跳过维持；窗口（validFrom/validTo）变更与 relationId 变更不再被静默吞（更新旧行 / 拒绝并报错二选一），按 decision-question-protocol 举例上报用户后落地"
  - "assignRole 接收窗口参数、assignRolesBatch 新建固定无限期两形态同批覆盖；装载既有绑定按有效期口径与拍板一致"
  - "回归锁：过期绑定重新指派在旧实现下「返回成功但仍失效」、新实现下按拍板语义处理（实证旧失败）"
  - "契约 §10 写明重指派语义（幂等边界 + 窗口/关系变更处置），先撤销再分配的恢复路径作为对照写明"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ADMIN-030 角色重新指派对既有绑定的语义收口

## 背景

承接 [Q-047](../pending-problems.md#q-047)：`UserManageAppServiceImpl.assignRole/assignRolesBatch` 按 userId+roleId 内存去重命中即跳过；`UserRoleMapper.xml` 装载既有绑定不滤有效期，uk_user_role 含 relationId 但内存去重未区分。后果：过期绑定重新指派返回成功但仍失效；未来窗口及 relationId 变更也可能被吞。先撤销再分配可恢复，但用户以为授上了实际没授上。

## 范围

两入口的既有绑定装载与去重判定；语义拍板（更新 or 拒绝）后实施；契约 §10 口径落账。不能据 sync 多重集推导管理面必须支持多窗口——语义按管理面自身拍板。

## 当前口径

现状：内存去重键 = userId+roleId，不区分有效期与 relationId。任务内决策点：重试幂等（保留）与变更（窗口/关系）的边界——变更走更新旧行还是显式拒绝报错。

## 非目标 / 遗留

- sync/full-sync BIND 的多重集窗口语义（另一契约面，维持现状）。
