---
doc_type: task
id: T-PERM-033
title: 权限排查后端聚合层 + 门禁 + DTO 扩展
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: backend
depends_on:
  - T-FE-013
blocks: []
last_updated: 2026-07-11
---

# T-PERM-033 权限排查后端聚合层 + 门禁 + DTO 扩展

> 状态：proposed
> 复杂度：🔴 高（跨模块：admin-service + permission-center）
> 前端任务：T-FE-013（本任务 depends_on 前端，前端 Phase 1 mock 已完成）

## 背景

T-FE-013 权限排查页前端已实现（Phase 1 mock 驱动），采用未来 admin-service 聚合路径 `/permission-query/*`。
本任务实现真实后端聚合层 + 统一门禁 + DTO 扩展，使前端从 mock 切换到真实接口。

前端核实发现的后端现状问题（详见 `docs/design/frontend/permission-query.md` §8-9）：
- 前端不直连 permission-center（architecture.md §1.5 L89），需 admin-service 聚合层
- explain 门禁 SYSTEM_CONFIG:VIEW（PermissionViewAppServiceImpl:665）
- effective-permissions 门禁目标实例 USER:VIEW/ROLE:VIEW（:134/153）
- query-resources/query-scopes 运行时接口无排查门禁
- explain 响应无命中条件/条件评估/冲突详情
- recentChanges 只按用户/角色取 50 条，未按权限键过滤（:735-748）
- ADMIN_USER/USER 主体类型语义待核对

## 范围（7 项）

### 1. admin-service 聚合入口
- 新增 `PermissionQueryController`（`@RequestMapping("/permission-query")`）
- 三个端点：`effective-permissions` / `query-scopes` / `explain`
- 聚合 DTO（对齐 api-contract.md §6.6-6.8）
- 内部通过 OpenFeign 调 permission-center（AuthController + PermissionViewController）
- Phase 1 mock 已模拟该路径，前端无需改路径

### 2. 统一门禁模型
- 新增资源类型 `ResourceTypeCode.PERMISSION_QUERY`
- 类型/操作种子 + 默认角色授权
- admin-service 权限码下发白名单（`AuthServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES` L122-137 当前不含 PERMISSION_QUERY，需追加）
- 门禁方案（任务中用权限矩阵明确两种权限仅有其一的结果，再定稿）：
  - 方案 A：`PERMISSION_QUERY:VIEW` 即全租户排查能力
  - 方案 B：`PERMISSION_QUERY:VIEW` + 被查目标 `USER:VIEW`/`ROLE:VIEW`

当前后端门禁现状（核实）：
- explain：`SYSTEM_CONFIG:VIEW`（PermissionViewAppServiceImpl:665）
- effective-permissions：目标实例 `USER:VIEW`/`ROLE:VIEW`（:134/153）
- query-resources/query-scopes：运行时接口，无排查门禁

### 3. explain DTO 扩展
- 命中条件 / 条件评估过程 / 冲突详情（当前响应只有 allowed/reason/sourceRoles/recentChanges）
- 评估上下文来源（管理员输入 vs 当前请求）
- IP / 时间等条件如何评估
- 敏感条件值脱敏

### 4. recentChanges 按完整权限键过滤
- 当前实现（`PermissionViewAppServiceImpl:735-748`）只按用户/角色取最近 50 条，未按资源操作过滤
- 契约要求（§6.8 L1583）：只返回与目标权限键相关的事件
- 修复：按 `domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode` 完整 6 字段过滤

### 5. ADMIN_USER/USER 主体语义核对
- `ADMIN_USER` 是 AccessMesh 管理端用户主要真实类型
- `USER` 为通用用户类型
- 核对 `subjectTypeCode` 的来源与候选查询方式
- 确认 `TypeResolutionService.resolveUserId` 对两者的解析路径

### 6. query-resources API 核对（保留原计划）
- §6.6 运行时 SDK 视角，前端不做 UI（仅 API 核对）
- treeMode TODO（§6.6 L1249 + `PermissionQueryAppServiceImpl.java:162`）
- 核对后端 DTO vs 契约字段一致性

### 7. permission-view/* 契约差异核对（保留原计划）
- `effective-roles` / `resource-users` / `role-permissions` / `effective-permission-codes`
- `resource-tree`
- 核对后端 DTO vs 契约 §6.8 字段一致性

## 跨模块交付

本任务同时修改 admin-service（聚合入口 + 门禁白名单）和 permission-center（门禁 + DTO 扩展 + recentChanges 过滤）。
若需拆分，建议拆 admin-service 聚合子任务（依赖 permission-center 门禁 + DTO 扩展完成）。

## 验收

- admin-service `PermissionQueryController` 三端点可用，聚合 DTO 对齐契约
- `PERMISSION_QUERY:VIEW` 全链路（资源类型常量 + 类型/操作种子 + 默认角色授权 + admin-service 白名单）
- explain 响应含条件评估 / 冲突详情
- recentChanges 按完整权限键 6 字段过滤
- ADMIN_USER/USER 主体语义确认
- query-resources / permission-view/* API 核对完成，差异登记
- 前端从 mock 切换到真实接口，路径不变（`/permission-query/*`）
- 前端 `perms.ts` 常量值 `SYSTEM_CONFIG:VIEW` 切换为 `PERMISSION_QUERY:VIEW`
