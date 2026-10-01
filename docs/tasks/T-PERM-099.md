---
doc_type: task
id: T-PERM-099
title: 删除类型所有者角色引用守卫
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §13（type 能力：类型所有权与授权根）
  - docs/design/dependency-auto-grant.md §3.4（自动授权结果）
depends_on: []
blocks: []
acceptance:
  - "拍板删除语义（拒绝并提示先迁移 / 警告放行），按 decision-question-protocol 举例上报用户后落地"
  - "deleteRoles 对被 type_definition.extra.grantOriginRole 引用的角色按拍板处置：拒绝时错误码入契约 §13 错误族；警告放行时响应含后果提示且契约写明恢复路径（updateType 迁移所有者+重建授权根）"
  - "deleteRoles 列表入口（单条即单元素列表）同批覆盖守卫；回归锁以「删除所有者角色→类型首授/转授资格检查无人通过」场景实证旧实现无守卫"
  - "契约 §13 同步守卫口径与恢复路径"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-099 删除类型所有者角色引用守卫

## 背景

承接 [Q-023](../pending-problems.md#q-023)：删除类型所有者角色会回收其 AUTHORITY_ROOT 而类型保留，无引用守卫或提示——之后无人能通过该类型首授/转授资格检查，授权入口锁死；管理员删除时不知后果。可用 updateType 迁移所有者并重建授权根恢复，但无提示等于隐性陷阱。

## 范围

`RoleManageAppServiceImpl.deleteRoles` 补引用守卫（单条+批量）；守卫语义拍板后落契约 §13；错误码/提示按拍板形态入册。

## 当前口径

现状零守卫零提示。任务内决策点：硬守卫（拒绝+指引先迁移）或软提示（警告放行）。恢复路径（updateType 迁移所有者+重授 AUTHORITY_ROOT）两种形态下都写入契约作运维口径。

## 非目标 / 遗留

- 授权根重建的自动化（维持手工恢复路径）。
