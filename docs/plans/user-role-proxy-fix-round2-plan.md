---
doc_type: plan
title: 用户角色代理修复（第二轮审查发现）
status: completed
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/permission-center/api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/cross-service/admin-permission-sync.md
tasks:
  - T-PERM-016
  - T-ADMIN-017
  - T-ADMIN-018
  - T-ADMIN-019
acceptance: "4 项修复完成 + 测试通过 + 设计回写；P1-1 删除重复预检、P1-2 getUser 可见性裁剪、P2-1 relationOrgName 业务键解析、P2-2 deleteUser 批量加载消除 N+1"
last_updated: 2026-06-20
---

# 用户角色代理修复（第二轮审查发现）

> 状态：进行中
> 来源：2026-06-20 第二轮代码审查，4 项 P1/P2 发现
> 前置：[../archive/2026-06-20/user-role-proxy-fix-plan.md](../archive/2026-06-20/user-role-proxy-fix-plan.md)（第一轮 M1-M13+S1-S3 已归档）

## 背景

第一轮修复归档后，第二轮审查发现 4 项残留问题，经代码级核实全部成立：

| ID | 严重度 | 问题 | 核实 |
|---|---|---|---|
| P1-1 | P1 | assign/revoke 预检把 roleExternalId 当 ROLE resource_entity.code 传 auth/check，而 ROLE 权限实际挂 abstract_role.id 维度，预检语义错位且重复（perm 已兜底）| ✅ 成立 |
| P1-2 | P1 | getUser 仅类型级 VIEW 门禁，未做组织可见性裁剪，知道 ID 可读列表不可见用户 | ✅ 成立 |
| P2-1 | P2 | listUserRoles 用 relationId（abstract_role.id）查 sys_org，主键空间错配，relationOrgName 错配/为空 | ✅ 成立 |
| P2-2 | P2 | deleteUser 循环内逐条 selectValidById 查组织，违反 N+1 约束 | ✅ 成立 |

## 决策（用户已确认）

- **P1-1**：方案 A — 删除 admin 层重复预检，交给 permission-center 兜底（perm UserManageAppServiceImpl.assignRole 已用正确 abstract_role.id 做 ROLE:MANAGE 校验）
- **P1-2**：拒绝读取无组织关系的用户（正常不会有此类用户），复用 `validateUsersInDefaultTreeScope`
- **P2-1**：方案 A — permission-center 在 UserRolesResp.RoleSummary 增返回关联组织业务键（relationExternalId），admin 据此查 sys_org
- **P2-2**：循环前批量加载 orgMap，缺失组织显式处理

## 任务清单（引用 [../tasks/README.md](../tasks/README.md) 看板）

| 任务 ID | 标题 | 关联发现 | 状态 |
|---|---|---|---|
| T-PERM-016 | UserRolesResp.RoleSummary 增 relationExternalId + getUserRoles 批量解析 | P2-1 | ⚙️ |
| T-ADMIN-017 | 删除 assign/revoke 重复 ROLE:MANAGE 预检 | P1-1 | ⚙️ |
| T-ADMIN-018 | getUser 加组织可见性裁剪 | P1-2 | ⚙️ |
| T-ADMIN-019 | deleteUser 批量加载 orgMap 消除 N+1 | P2-2 | ⚙️ |

> 依赖：T-ADMIN-017（删预检）独立；T-ADMIN-018 独立；T-ADMIN-019 独立；T-PERM-016 先行，T-ADMIN 的 listUserRoles 改动依赖 T-PERM-016 字段落地（但 listUserRoles 改动并入 T-PERM-016 的 admin 侧适配，不单列）。

## 非目标

- 不改 permission-center ROLE 权限挂载维度（abstract_role.id vs resource_entity）的内部一致性——那是更深层问题，超出本轮范围
- 不为功能角色补同步 ROLE resource_entity（P1-1 用删预检规避，不触及同步契约）

## 归档条件

- 4 项修复完成 + `mvn test` 通过 + 设计回写完成
- 移入 `docs/archive/`
