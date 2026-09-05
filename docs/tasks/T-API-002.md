---
doc_type: task
id: T-API-002
title: perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口
status: proposed
plan: docs/plans/design-audit-followup-plan.md
domain: cross-service
design_refs:
  - docs/design/permission-center/core-flows.md#§15
  - docs/design/permission-center/api-contract.md
depends_on: []
blocks: []
acceptance:
  - "DTO 公共化与内部 id 全族裁剪（2026-09-05 定案全裁）：QueryResourcesReq/Resp、QueryScopesReq/Resp 的稳定部分迁入 perm-common（access-service 改 import，HTTP JSON 面同步收窄）；内部数据库 id 字段族全数从对外响应裁剪——QueryScopesResp.parentPermissionIds、ScopeGroup.matchedRoleIds/matchedPermissionIds/dependOnPermissionIds、ResourceEntry.matchedRoleIds/matchedPermissionIds（均为 role/role_resource_permission 内部行 id，与 core-flows §15「不要求/不泄漏内部数据库 ID」口径对齐）；前端排查页同端点复用（api-contract §6.7），frontend perm-scope.ts 类型与排查页展示消费面**同批改造**；api-contract 契约同步回写"
  - "PermissionFeignClient 增加两个 POST 方法（照 check/batch-check 现成样式）：/api/perm/auth/query-resources、/api/perm/auth/query-scopes，沿用既有服务身份拦截器，不新增 SDK 抽象层"
  - "PermissionFeignClientContractTest 端点清单同步：contractPathsAreFrozen 方法计数 16→18 一并更新 + 两个新端点回归锁（路径存在性 + DTO 字段快照防漂移）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-05
---

# T-API-002 perm-sdk 补齐 auth/query-resources 与 auth/query-scopes 调用入口

> 状态：proposed（2026-09-05 设计体检 P2-2；同日复评审定案内部 id 全族裁剪）
> 依赖：无；SNAPSHOT 链注意 perm-common 改动须先 install 再编译下游（AGENTS.md 构建陷阱）

## 背景

core-flows §15「SDK 可接入」检查点承诺四件套（check/batch-check/query-resources/query-scopes），服务端端点齐全（PermAuthController），但 SDK `PermissionFeignClient` 只有前两个，且四个 Query* DTO 锁在 access-service 内部包。接入方拉取「用户可见资源集合」（query-resources）与「数据范围四态」（query-scopes，DENIED/INSTANCE/ALL/EMPTY）是数据权限的运行时消费端——SDK 缺口等于业务服务接不上数据权限，只能手写 HTTP + 自造 DTO 副本（制造漂移面）。

## 设计口径（2026-09-05 定案，复评审补强）

- 最小补齐：DTO 稳定部分公共化 + 两个 Feign 方法 + 契约测试，无新抽象层。
- **内部 id 字段族全裁**（复评审决策）：同端点被管理排查页复用（§6.7），前端 perm-scope.ts 已消费 matched* 三字段——裁剪须前后端同批，排查页展示面改为业务键（角色编码/权限键）或去掉内部 id 展示，具体形态执行时按排查页现状定。
- `effective-permissions` 是管理端排查视图（分页、给人看），不替代上述运行时能力。
- 不含 example-service 演示改造（如需另开小项）。

## 范围

- perm-common DTO 迁移 + 内部 id 字段族裁剪 + 前端排查页同批改造；
- PermissionFeignClient 两方法 + 契约测试（计数 16→18）+ 回归锁；
- api-contract 回写（端点入 SDK 清单、裁字段口径与前端改造注记）。
