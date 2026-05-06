# Plan: Frontend Phase 3 - Permission Center Core Pages (Strategic Overview)

> **详细实施指南见子计划**: Phase 3.1 (角色管理) + Phase 3.2 (用户角色) + Phase 3.3 (权限配置)

## Summary
实现权限中心核心管理页面：角色管理、用户角色分配、角色权限配置。

## Problem → Solution
- **当前**: frontend 缺少权限中心管理页面
- **目标**: 完整的权限中心模块（角色 CRUD + 用户角色分配 + 三段式授权）

## Metadata
- **Complexity**: Large
- **Estimated Files**: 20 files
- **Subplans**: Phase 3.1 (角色) + Phase 3.2 (用户角色) + Phase 3.3 (权限配置)

---

## Strategic Architecture

### Permission Center Core
```
Phase 3.1: 角色管理页面
├─ 左侧角色树 + 右侧详情面板
├─ 角色类型区分（GROUP_ROLE vs BASIC_ROLE）
├─ 角色 CRUD
├─ 分组角色 extraRoles 配置
├─ 权限配置入口（跳转到 Phase 3.3）
└─ 7 个任务，8 个文件

Phase 3.2: 用户角色分配页面
├─ 左侧用户选择 + 右侧角色分配树
├─ 组织上下文选择（角色生效范围）
├─ 角色树 checkbox 多选
├─ 批量分配/回收
└─ 5 个任务，5 个文件

Phase 3.3: 角色权限配置页面
├─ 左侧资源树选择 + 右侧操作配置
├─ 三段式授权（角色 + 资源 + 操作）
├─ 操作配置（VIEW/MANAGE/CREATE 等）
├─ 条件配置（时间范围/IP 白名单）
└─ 7 个任务，8 个文件
```

### Core Concepts

**角色类型**:
- **GROUP_ROLE**: 分组角色，可配置 extraRoles（额外基本角色）
- **BASIC_ROLE**: 基本角色，直接授权

**三段式授权**:
```
角色
+ 资源
+ 操作
= 权限
```

**组织上下文**:
- 用户角色分配需指定组织上下文（角色生效范围）

---

## NOT Building (Scope Boundaries)

Phase 3 **不实现**（后续 Phase 实现）：
- 业务域管理完整功能 → Phase 4
- 类型定义管理完整功能 → Phase 4
- 权限条件完整配置 → Phase 4
- 批量导入角色 → 后续 Phase

---

## Related Subplans

| Subplan | File | Tasks | Description |
|---------|------|-------|-------------|
| Phase 3.1 | `subplans/phase-3.1-role-management-page.plan.md` | 7 | **角色管理**: 树展示、CRUD、extraRoles 配置 |
| Phase 3.2 | `subplans/phase-3.2-user-role-assignment-page.plan.md` | 5 | **用户角色**: 用户选择、组织上下文、批量操作 |
| Phase 3.3 | `subplans/phase-3.3-role-permission-config-page.plan.md` | 7 | **权限配置**: 三段式授权（角色+资源+操作） |

**实施顺序**: Phase 3.1 → Phase 3.2 → Phase 3.3

**实施建议**:
1. Phase 3.1 建立角色管理基础（角色树 + 类型区分）
2. Phase 3.2 建立用户角色分配（组织上下文概念）
3. Phase 3.3 实现核心三段式授权逻辑
4. Phase 3.3 是权限中心的核心功能，需严格按 API 契约实现

---

**Generated**: 2026-05-06
**Plan Status**: Strategic Overview - See Subplans for Implementation Details