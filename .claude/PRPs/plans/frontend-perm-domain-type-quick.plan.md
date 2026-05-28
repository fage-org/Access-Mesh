# Plan: Permission Center Frontend 快速任务 - 业务域与类型定义管理

## Summary

快速开发业务域管理独立页面（`/perm/domain`）和类型定义管理独立页面（`/perm/type`），包含完整的CRUD功能和关联管理。

## User Story

As a 权限管理员, I want 独立的业务域和类型定义管理页面, So that 我能够方便地管理业务域配置和类型定义。

## Problem → Solution

后端API已存在（`domain.ts`, `type.ts`），但前端只有API层没有管理页面 → 快速开发两个独立的管理页面。

## Metadata

- **Complexity**: Medium
- **Source PRD**: 用户决策（问题3和问题4）
- **Estimated Files**: 12-15
- **前置条件**: Phase 1 部分完成
- **预计工时**: 2-3天

---

## Scope

### 业务域管理页面 (`/perm/domain`)

**功能范围：**
- 业务域CRUD管理
- 业务域配置管理（集成域配置）
- 业务域与资源类型关联（可选Phase 2）

### 类型定义管理页面 (`/perm/type`)

**功能范围：**
- 类型定义CRUD管理（资源类型、主体类型、角色类型）
- 操作权限管理集成（在同一页面管理）
- 类型与操作权限关联

---

## Files to Create

### 业务域模块

| File | Action | Justification |
|------|--------|---------------|
| `views/perm/domain/index.vue` | CREATE | 业务域管理主页面 |
| `views/perm/domain/components/DomainForm.vue` | CREATE | 业务域表单组件 |
| `views/perm/domain/components/DomainList.vue` | CREATE | 业务域列表组件 |
| `views/perm/domain/components/DomainConfigPanel.vue` | CREATE | 域配置面板组件 |

### 类型定义模块

| File | Action | Justification |
|------|--------|---------------|
| `views/perm/type/index.vue` | CREATE | 类型定义管理主页面 |
| `views/perm/type/components/TypeForm.vue` | CREATE | 类型表单组件 |
| `views/perm/type/components/TypeList.vue` | CREATE | 类型列表组件 |
| `views/perm/type/components/OperationConfigPanel.vue` | CREATE | 操作权限配置面板 |

### 配置更新

| File | Action | Justification |
|------|--------|---------------|
| `constants/permission.ts` | UPDATE | 补充权限码 |
| `router/modules/perm.ts` | UPDATE | 添加新路由 |

---

## Step-by-Step Tasks

### Task 1: Create Domain Management Page

- **ACTION**: 创建业务域管理页面
- **IMPLEMENT**:
  - 左侧列表 + 右侧详情的布局
  - 业务域CRUD功能
  - 域配置管理Tab
- **CHECKLIST**:
  - [ ] 业务域列表展示
  - [ ] 业务域创建/编辑表单
  - [ ] 业务域删除功能
  - [ ] 域配置面板（JSON编辑器）

### Task 2: Create Type Definition Page

- **ACTION**: 创建类型定义管理页面
- **IMPLEMENT**:
  - 分类筛选（资源类型、主体类型、角色类型）
  - 类型定义CRUD功能
  - 操作权限配置集成
- **CHECKLIST**:
  - [ ] 类型定义列表（支持分类筛选）
  - [ ] 类型定义创建/编辑表单
  - [ ] 类型定义删除功能
  - [ ] 操作权限配置面板

### Task 3: Update API Files

- **ACTION**: 补充域配置API方法到domain.ts
- **IMPLEMENT**:
  ```typescript
  // 添加到 api/perm/domain.ts
  
  export const getDomainConfigList = (data: { domainCode: string }) => {
    return http.request<DomainConfigListResult>(
      "post",
      "/api/perm/domain-config/list",
      { data }
    );
  };

  export const saveDomainConfig = (data: DomainConfigSaveRequest) => {
    return http.request<DomainConfigActionResult>(
      "post",
      "/api/perm/domain-config/save",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 4: Update Permissions

- **ACTION**: 补充权限码常量
- **IMPLEMENT**:
  ```typescript
  // 业务域管理
  export const PERM_DOMAIN_VIEW = "perm:domain:view";
  export const PERM_DOMAIN_CREATE = "perm:domain:create";
  export const PERM_DOMAIN_UPDATE = "perm:domain:update";
  export const PERM_DOMAIN_DELETE = "perm:domain:delete";
  export const PERM_DOMAIN_CONFIG_VIEW = "perm:domain-config:view";
  export const PERM_DOMAIN_CONFIG_UPDATE = "perm:domain-config:update";

  // 类型定义管理
  export const PERM_TYPE_VIEW = "perm:type:view";
  export const PERM_TYPE_CREATE = "perm:type:create";
  export const PERM_TYPE_UPDATE = "perm:type:update";
  export const PERM_TYPE_DELETE = "perm:type:delete";
  export const PERM_OPERATION_VIEW = "perm:operation:view";
  export const PERM_OPERATION_UPDATE = "perm:operation:update";
  ```

### Task 5: Update Router

- **ACTION**: 添加新路由配置
- **IMPLEMENT**:
  ```typescript
  // 添加到 router/modules/perm.ts
  {
    path: "/perm/domain",
    name: "PermDomain",
    component: () => import("@/views/perm/domain/index.vue"),
    meta: {
      title: "业务域管理",
      auths: [PERM_CODES.PERM_DOMAIN_VIEW]
    }
  },
  {
    path: "/perm/type",
    name: "PermType",
    component: () => import("@/views/perm/type/index.vue"),
    meta: {
      title: "类型定义",
      auths: [PERM_CODES.PERM_TYPE_VIEW]
    }
  }
  ```

---

## Acceptance Criteria

- [ ] `/perm/domain` 业务域管理页面功能完整
- [ ] `/perm/type` 类型定义管理页面功能完整
- [ ] 权限码已补充
- [ ] 路由配置已更新
- [ ] 全量编译通过

---

## Dependencies

| 依赖 | 说明 |
|------|------|
| `domain.ts` | API文件已存在，需补充域配置方法 |
| `type.ts` | API文件已存在，需补充操作权限管理 |
| `operation.ts` | 已存在，类型定义页面需复用 |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| JSON编辑器组件选择 | Low | Low | 使用vue-json-viewer或monaco-editor |
| 域配置API变更 | Low | Medium | 开发前确认API稳定性 |
