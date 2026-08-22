---
doc_type: task
id: T-PERM-033
title: 权限排查后端门禁统一 + DTO 扩展（直连 /api/perm/*，无聚合层）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.6
  - docs/design/permission-center/api-contract.md#§6.7
  - docs/design/permission-center/api-contract.md#§6.8
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/permission-query.md
depends_on:
  - T-FE-013
blocks: []
acceptance:
  - "统一门禁 PERMISSION_QUERY:VIEW 全链路：资源类型常量 + 类型/操作种子 + 默认角色授权 + 权限码下发白名单追加（常量现位于 access/application/query/impl/UserMenuQueryServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES）"
  - "explain 门禁从 SYSTEM_CONFIG:VIEW 切换为统一门禁；门禁方案任务中定稿（A=PERMISSION_QUERY:VIEW 即全租户排查能力；B=PERMISSION_QUERY:VIEW + 被查目标 USER:VIEW/ROLE:VIEW）"
  - "explain 响应扩展命中条件/条件评估过程/冲突详情 + 评估上下文来源（管理员输入 vs 当前请求）+ IP/时间条件评估 + 敏感条件值脱敏"
  - "recentChanges 按完整权限键 6 字段过滤（domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode；当前实现只按用户/角色取 50 条）"
  - "ADMIN_USER/USER 主体语义核对（subjectTypeCode 来源与候选查询方式、TypeResolutionService.resolveUserId 解析路径）"
  - "query-resources / permission-view/* 契约核对完成、差异登记（effective-roles/resource-users/role-permissions/effective-permission-codes/resource-tree + treeMode TODO）"
  - "前端联调路径为既有契约端点（/api/perm/permission-view/effective-permissions、/api/perm/permission-view/explain、/api/perm/auth/query-scopes）；无新增 Gateway 路由（3 路由契约不动）；前端 perms.ts 常量 SYSTEM_CONFIG:VIEW 切换为 PERMISSION_QUERY:VIEW"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-PERM-033 权限排查后端门禁统一 + DTO 扩展

> 状态：proposed
> 复杂度：🟡 中（单模块：access-service permission 域，跨域只读经 application.query）
> 前端任务：T-FE-013（本任务 depends_on 前端，前端 Phase 1 mock 已完成）

## 背景

T-FE-013 权限排查页前端已实现（Phase 1 mock 驱动，mock 路径 `/permission-query/*`）。

> **重基线（T-ACCESS-012，2026-08-22，用户决策：取消聚合层）**：原「admin 域聚合层 `PermissionQueryController`（`/permission-query/*`）+ OpenFeign 调 permission-center」方案取消——归并后 `/perm/**` 与 `/admin/**` 同路由到 access-service，聚合层前提（前端不直连权限服务）不再成立；三个端点的契约路径已存在（见 acceptance），页面直连使用，不新增 Controller/聚合 DTO/Gateway 路由（维持 T-ACCESS-010 固化的 3 路由契约）。原「跨模块 admin-service + permission-center」复杂度与「AuthServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES L122-137」锚点失效：常量已迁 `access/application/query/impl/UserMenuQueryServiceImpl.java`；explain 若需 admin 域数据由 `application.query` 查询服务承接（依赖白名单见 access-service-architecture.md §3）。

前端核实发现的后端现状问题（详见 `docs/design/frontend/permission-query.md` §8-9）：

- explain 门禁 SYSTEM_CONFIG:VIEW（PermissionViewAppServiceImpl）
- effective-permissions 门禁目标实例 USER:VIEW/ROLE:VIEW
- query-resources/query-scopes 运行时接口无排查门禁
- explain 响应无命中条件/条件评估/冲突详情
- recentChanges 只按用户/角色取 50 条，未按权限键过滤
- ADMIN_USER/USER 主体类型语义待核对

## 范围

1. **统一门禁模型**：新增资源类型 `ResourceTypeCode.PERMISSION_QUERY`；类型/操作种子 + 默认角色授权；权限码下发白名单追加（`UserMenuQueryServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES`）。门禁方案任务中用权限矩阵明确两种权限仅有其一的结果后定稿（A/B 见 acceptance）。
2. **explain DTO 扩展**：命中条件/条件评估过程/冲突详情；评估上下文来源（管理员输入 vs 当前请求）；IP/时间等条件如何评估；敏感条件值脱敏。
3. **recentChanges 按完整权限键过滤**：按 6 字段过滤，只返回与目标权限键相关的事件（契约 §6.8）。
4. **ADMIN_USER/USER 主体语义核对**：`ADMIN_USER` 是 AccessMesh 管理端用户主要真实类型、`USER` 为通用类型；核对 `subjectTypeCode` 来源、候选查询方式与 `resolveUserId` 解析路径。
5. **query-resources API 核对**（§6.6 运行时 SDK 视角，前端不做 UI；treeMode TODO 一并核对）。
6. **permission-view/* 契约差异核对**（§6.8 字段一致性）。
7. **前端联调切换**：mock 路径 `/permission-query/*` 切换为契约路径 `/api/perm/*`（实际切换在 T-FE-019 联调执行）。

## 非目标

- 不新增聚合层、聚合 Controller/DTO 或 Gateway 路由（T-ACCESS-012 决策）。
- 不改变三端点既有契约路径与请求/响应结构（§6.6-§6.8）。

## 验收对照

见 frontmatter acceptance；逐项满足后转 done，设计回写目标为 api-contract §6.6-§6.8 门禁/DTO 描述与 implementation.md 对应链路。
