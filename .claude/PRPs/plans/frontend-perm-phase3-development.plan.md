# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 3

## Summary

开发权限中心前端缺失的高级功能模块（Phase 3），包括权限版本管理、系统配置和权限视图完善三个模块。

## User Story

As a 系统管理员, I want 在前端界面查看权限版本快照、配置系统参数和查看完整的权限视图, So that 我能够审计权限变更和进行系统级配置。

## Problem → Solution

后端已提供权限版本、系统配置和高级权限视图的完整API，但前端缺少对应页面 → 开发缺失的API文件、页面组件和路由配置。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 15-20
- **前置条件**: Phase 2完成, `frontend-perm-completed-modules-improvement.plan.md` 中的权限视图完善

---

## Dependencies

| 计划 | 关系 | 说明 |
|------|------|------|
| `frontend-perm-phase2-development.plan.md` | 前置依赖 | Phase 2应先完成 |
| `frontend-perm-completed-modules-improvement.plan.md` | 部分重叠 | 权限视图API完善应在本计划前完成 |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/PermissionVersionController.java` | 版本管理后端API |
| P0 | `permission-center/.../controller/SystemConfigController.java` | 系统配置后端API |
| P0 | `permission-center/.../controller/PermissionViewController.java` | 权限视图后端API |
| P1 | `frontend/src/api/perm/permissionView.ts` | 现有权限视图API |
| P1 | `frontend/src/views/perm/view/index.vue` | 现有权限视图页面 |

---

## Files to Create

### Phase 3.1: 权限版本管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/version.ts` | CREATE | 版本管理API层 |
| `views/perm/version/index.vue` | CREATE | 版本管理页面 |
| `views/perm/version/components/VersionComparePanel.vue` | CREATE | 版本对比组件 |
| `views/perm/version/components/VersionRollbackPanel.vue` | CREATE | 版本回滚组件 |

### Phase 3.2: 系统配置管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/systemConfig.ts` | CREATE | 系统配置API层 |
| `views/perm/system-config/index.vue` | CREATE | 系统配置页面 |

### Phase 3.3: 权限视图完善

| File | Action | Justification |
|------|--------|---------------|
| `views/perm/view/components/EffectiveRolesPanel.vue` | CREATE | 有效角色视图 |
| `views/perm/view/components/ResourcePermissionsPanel.vue` | CREATE | 资源权限分布视图 |
| `views/perm/view/components/RecentChangesPanel.vue` | CREATE | 近期变更视图 |
| `views/perm/view/components/UserResourceTreePanel.vue` | CREATE | 用户资源树视图 |

### Phase 3.4: 路由和权限配置

| File | Action | Justification |
|------|--------|---------------|
| `constants/permission.ts` | UPDATE | 补充Phase 3权限码 |
| `router/modules/perm.ts` | UPDATE | 添加Phase 3路由 |

---

## Step-by-Step Tasks

### Task 1: 创建权限版本API (version.ts)

- **ACTION**: 创建权限版本管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/version.ts
  import { http } from "@/utils/http";

  export interface PermissionVersionItem {
    id: number;
    tenantId: number;
    version: string;
    description: string | null;
    snapshot: string; // JSON快照
    createdBy: number;
    createdAt: string;
  }

  export interface VersionCompareRequest {
    sourceVersion: string;
    targetVersion: string;
  }

  export interface VersionCompareResult {
    success: boolean;
    data: {
      additions: Array<{
        resourceType: string;
        resourceCode: string;
        operationCode: string;
        roles: Array<string>;
      }>;
      deletions: Array<{
        resourceType: string;
        resourceCode: string;
        operationCode: string;
        roles: Array<string>;
      }>;
      modifications: Array<{
        resourceType: string;
        resourceCode: string;
        operationCode: string;
        beforeRoles: Array<string>;
        afterRoles: Array<string>;
      }>;
    };
  }

  // API函数
  export const getVersionList = () => {
    return http.request<VersionListResult>(
      "post",
      "/api/perm/permission-version/list",
      { data: {} }
    );
  };

  export const getVersionDetail = (data: { id: number }) => {
    return http.request<VersionDetailResult>(
      "post",
      "/api/perm/permission-version/detail",
      { data }
    );
  };

  export const createVersion = (data: { 
    version: string; 
    description?: string 
  }) => {
    return http.request<VersionActionResult>(
      "post",
      "/api/perm/permission-version/create",
      { data }
    );
  };

  export const compareVersions = (data: VersionCompareRequest) => {
    return http.request<VersionCompareResult>(
      "post",
      "/api/perm/permission-version/compare",
      { data }
    );
  };

  export const rollbackToVersion = (data: { version: string }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/permission-version/rollback",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 2: 创建权限版本管理页面

- **ACTION**: 创建权限版本管理页面
- **IMPLEMENT**:
  - 版本列表展示
  - 版本对比功能（可视化展示差异）
  - 版本回滚功能（带确认对话框）
- **CHECKLIST**:
  - [ ] 版本列表支持分页
  - [ ] 版本对比可视化（增删改用不同颜色）
  - [ ] 回滚操作二次确认

### Task 3: 创建系统配置API (systemConfig.ts)

- **ACTION**: 创建系统配置管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/systemConfig.ts
  import { http } from "@/utils/http";

  export interface SystemConfigItem {
    id: number;
    tenantId: number;
    configKey: string;
    configValue: string;
    description: string | null;
    category: string;
    editable: boolean;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface SystemConfigListRequest {
    category?: string;
    keyword?: string;
  }

  export interface SystemConfigUpdateRequest {
    id: number;
    configValue: string;
    description?: string;
  }

  // API函数
  export const getSystemConfigList = (data: SystemConfigListRequest) => {
    return http.request<SystemConfigListResult>(
      "post",
      "/api/perm/system-config/list",
      { data }
    );
  };

  export const getSystemConfigDetail = (data: { id: number }) => {
    return http.request<SystemConfigDetailResult>(
      "post",
      "/api/perm/system-config/detail",
      { data }
    );
  };

  export const updateSystemConfig = (data: SystemConfigUpdateRequest) => {
    return http.request<SystemConfigActionResult>(
      "post",
      "/api/perm/system-config/update",
      { data }
    );
  };

  // 配置分类常量
  export const SYSTEM_CONFIG_CATEGORIES = [
    { label: "基础配置", value: "BASIC" },
    { label: "缓存配置", value: "CACHE" },
    { label: "安全配置", value: "SECURITY" },
    { label: "审计配置", value: "AUDIT" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 4: 创建系统配置管理页面

- **ACTION**: 创建系统配置管理页面
- **IMPLEMENT**:
  - 配置列表展示（按分类分组）
  - 配置值编辑（支持JSON格式）
  - 只读配置禁止编辑
- **CHECKLIST**:
  - [ ] 配置按分类分组展示
  - [ ] 支持配置值编辑和保存
  - [ ] 只读配置有明确标识

### Task 5: 完善权限视图API

- **ACTION**: 补充 `permissionView.ts` 缺失的方法
- **IMPLEMENT**:
  ```typescript
  // 添加到 api/perm/permissionView.ts

  // 查询有效角色
  export interface UserEffectiveRolesRequest {
    subjectTypeCode?: string;
    subjectExternalId?: string;
    domainCode?: string;
    pageNum?: number;
    pageSize?: number;
  }

  export interface EffectiveRoleItem {
    roleTypeCode: string;
    roleExternalId: string;
    roleName: string;
    via: Array<string>;
    source: string;
  }

  export const getEffectiveRoles = (data: UserEffectiveRolesRequest) => {
    return http.request<EffectiveRolesResult>(
      "post",
      "/api/perm/permission-view/effective-roles",
      { data }
    );
  };

  // 查询资源权限分布
  export interface ResourcePermissionViewRequest {
    domainCode?: string;
    resourceTypeCode: string;
    resourceCode: string;
    codeType?: string;
  }

  export interface ResourcePermissionItem {
    resourceTypeCode: string;
    resourceCode: string;
    resourceName: string;
    users: Array<{
      subjectTypeCode: string;
      subjectExternalId: string;
      username: string;
      operationCodes: Array<string>;
    }>;
  }

  export const getResourcePermissions = (data: ResourcePermissionViewRequest) => {
    return http.request<ResourcePermissionsResult>(
      "post",
      "/api/perm/permission-view/resource-users",
      { data }
    );
  };

  // 查询用户资源树
  export interface UserResourceTreeRequest {
    subjectTypeCode: string;
    subjectExternalId: string;
    domainCode?: string;
    resourceTypeCode?: string;
  }

  export interface UserResourceTreeItem {
    resourceTypeCode: string;
    resourceCode: string;
    resourceName: string;
    operationCodes: Array<string>;
    children?: Array<UserResourceTreeItem>;
  }

  export const getUserResourceTree = (data: UserResourceTreeRequest) => {
    return http.request<UserResourceTreeResult>(
      "post",
      "/api/perm/permission-view/resource-tree",
      { data }
    );
  };

  // 查询近期变更
  export interface RecentChangesRequest {
    days?: number;
    resourceTypeCode?: string;
  }

  export const getRecentChanges = (data: RecentChangesRequest) => {
    return http.request<RecentChangesResult>(
      "post",
      "/api/perm/permission-view/recent-changes",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 6: 完善权限视图页面

- **ACTION**: 完善权限视图页面，添加多个子视图
- **IMPLEMENT**:
  - 有效角色查询视图（按用户查询）
  - 资源权限分布视图（按资源查询有哪些用户有权限）
  - 近期变更视图（展示权限变更历史）
  - 用户资源树视图（用户的权限资源树）
- **CHECKLIST**:
  - [ ] 使用Tabs组件组织多个视图
  - [ ] 有效角色视图支持分页
  - [ ] 资源权限分布支持筛选
  - [ ] 近期变更支持按天数筛选
  - [ ] 用户资源树支持展开折叠

### Task 7: 补充Phase 3权限码

- **ACTION**: 补充Phase 3的权限码
- **IMPLEMENT**:
  ```typescript
  // 权限版本管理
  export const PERM_VERSION_VIEW = "perm:version:view";
  export const PERM_VERSION_CREATE = "perm:version:create";
  export const PERM_VERSION_COMPARE = "perm:version:compare";
  export const PERM_VERSION_ROLLBACK = "perm:version:rollback";

  // 系统配置管理
  export const PERM_SYSTEM_CONFIG_VIEW = "perm:system-config:view";
  export const PERM_SYSTEM_CONFIG_UPDATE = "perm:system-config:update";

  // 权限视图（补充细化权限）
  export const PERM_VIEW_EFFECTIVE_ROLES = "perm:view:effective-roles";
  export const PERM_VIEW_RESOURCE_PERMISSIONS = "perm:view:resource-permissions";
  export const PERM_VIEW_RECENT_CHANGES = "perm:view:recent-changes";
  export const PERM_VIEW_USER_RESOURCE_TREE = "perm:view:user-resource-tree";
  ```
- **VALIDATE**: 添加到 `PERM_CODES` 对象

### Task 8: 更新路由配置

- **ACTION**: 更新路由配置
- **IMPLEMENT**:
  ```typescript
  {
    path: "/perm/version",
    name: "PermVersion",
    component: () => import("@/views/perm/version/index.vue"),
    meta: {
      title: "权限版本",
      auths: [PERM_CODES.PERM_VERSION_VIEW]
    }
  },
  {
    path: "/perm/system-config",
    name: "PermSystemConfig",
    component: () => import("@/views/perm/system-config/index.vue"),
    meta: {
      title: "系统配置",
      auths: [PERM_CODES.PERM_SYSTEM_CONFIG_VIEW]
    }
  }
  ```
- **VALIDATE**: 路由编译通过

---

## Acceptance Criteria

- [ ] `api/perm/version.ts` 创建完成
- [ ] `views/perm/version/` 页面组件创建完成
- [ ] `api/perm/systemConfig.ts` 创建完成
- [ ] `views/perm/system-config/` 页面组件创建完成
- [ ] `permissionView.ts` 补充完整所有API方法
- [ ] 权限视图页面包含所有子视图
- [ ] Phase 3权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 版本对比可视化复杂 | Medium | Low | 使用diff组件库 |
| 权限视图数据量大 | Medium | Medium | 支持分页和懒加载 |
