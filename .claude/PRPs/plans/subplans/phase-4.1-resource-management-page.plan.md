# Subplan: Phase 4.1 - Resource Management Page

## Summary
实现资源管理完整功能，包含左侧资源树展示、右侧资源详情表单、资源节点 CRUD、资源依赖配置等核心功能。

## User Story
作为权限管理员，我希望通过资源管理页面管理资源结构和配置资源依赖关系，以便构建完整的资源体系并为权限配置提供资源基础。

## Problem → Solution
**当前状态**: frontend 缺少资源管理页面
**目标状态**: 完整的资源管理页面，支持资源树展示、节点 CRUD、资源类型筛选、资源依赖配置

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase4-resource-advanced-config.plan.md`
- **Phase**: Phase 4.1 (Resource Management)
- **Estimated Files**: 6 files (1 page + 5 components)
- **Prerequisite**: Phase 3.3 已完成（角色权限配置）

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  资源管理                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  资源树      │  │  资源详情表单                   ││
│  │              │  │                                 ││
│  │  [业务域选择]│  │  资源名称: [_______________]   ││
│  │  [系统域]   │  │  资源编码: [_______________]   ││
│  │              │  │  资源类型: [下拉选择]          ││
│  │  [资源类型筛选]│  │  业务域: [下拉选择]            ││
│  │  [MENU][API]│  │  状态: [启用/停用]             ││
│  │              │  │                                 ││
│  │  资源树      │  │  [保存] [取消]                  ││
│  │  ├─ 系统管理 │  │                                 ││
│  │  │  ├─用户管│  │  Tab 切换:                      ││
│  │  │  ├─组织管│  │  [资源详情] [资源依赖]         ││
│  │              │  │                                 ││
│  │  [新增根资源]│  │  资源依赖(Tab 页):              ││
│  │              │  │  [源资源]授权触发 [目标资源]补全││
│  └──────────────┘  │  ├─ 用户授权 → 补全查看权限    ││
│                    │  [添加依赖]                     ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 业务域选择 | 下拉选择 | 切换业务域显示不同资源树 |
| 资源类型筛选 | 多选筛选 | 按资源类型(MENU/API/BUTTON/DATA)筛选 |
| 资源树点击 | 显示资源详情 | 右侧显示详情表单 |
| 新增根资源 | 弹窗表单 | 创建顶级资源节点 |
| 新增子资源 | 弹窗表单 | 在选中节点下创建子资源 |
| 编辑资源 | 右侧表单编辑 | 点击节点后右侧表单可编辑 |
| 删除资源 | 二次确认 | 检查是否有子节点或权限关联 |
| 资源依赖配置 | Tab 页切换 | 配置源资源授权触发目标资源补全 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `plan/permission-center/api-contract.md` | 165-182 | 资源 API 契约 |
| P0 | `plan/permission-center/overview.md` | 49-58 | 资源依赖模型 |
| P1 | `frontend/src/views/perm/role/components/ResourceTree.vue` | 全文(Phase 3.3 创建) | 资源树参考 |

---

## Patterns to Mirror

### RESOURCE_DEPENDENCY_PATTERN
// SOURCE: plan/permission-center/overview.md:49-58
```text
资源依赖配置:
- resource_entity_id: 源资源(被授权后触发补全)
- depends_on_resource_entity_id: 目标资源(需要补全)
- source_operation_bits: 源资源哪些操作触发补全
- required_operation_bits: 目标资源需要补全的操作
```
**模式要点**: 配置资源依赖触发权限补全

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/perm/resource.ts` | UPDATE | 补充资源 CRUD API |
| `frontend/src/api/perm/resourceDependency.ts` | CREATE | 资源依赖 API 接口 |
| `frontend/src/views/perm/resource/index.vue` | CREATE | 资源管理主页面 |
| `frontend/src/views/perm/resource/components/ResourceTree.vue` | CREATE | 资源树组件 |
| `frontend/src/views/perm/resource/components/ResourceForm.vue` | CREATE | 资源详情表单 |
| `frontend/src/views/perm/resource/components/DependencyConfig.vue` | CREATE | 资源依赖配置组件 |

---

## Step-by-Step Tasks

### Task 1: 补充资源 API 接口(CRUD)
- **ACTION**: 更新 `frontend/src/api/perm/resource.ts`
- **IMPLEMENT**: 补充 CRUD API
  ```typescript
  // 补充类型定义
  export interface ResourceCreateRequest {
    parentId?: number;
    name: string;
    code: string;
    type: "MENU" | "API" | "BUTTON" | "DATA";
    domainCode: string;
    status?: number;
  }

  export interface ResourceUpdateRequest {
    id: number;
    name?: string;
    status?: number;
  }

  // 补充 API 函数
  /** 创建资源 */
  export const createResource = (data: ResourceCreateRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/resource-entity/create", { data });
  };

  /** 更新资源 */
  export const updateResource = (data: ResourceUpdateRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/resource-entity/update", { data });
  };

  /** 删除资源 */
  export const deleteResource = (data: { id: number }) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/resource-entity/remove", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 资源类型使用字符串 "MENU" | "API" | "BUTTON" | "DATA"
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 2: 创建资源依赖 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/resourceDependency.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface DependencyItem {
    id: number;
    resourceEntityId: number;
    resourceName: string;
    dependsOnResourceEntityId: number;
    dependsOnResourceName: string;
    sourceOperationBits: number;
    requiredOperationBits: number;
  }

  export interface DependencyListResult {
    code: number;
    message: string;
    data: Array<DependencyItem>;
  }

  export interface DependencyCreateRequest {
    resourceEntityId: number;
    dependsOnResourceEntityId: number;
    sourceOperationBits: number;
    requiredOperationBits: number;
  }

  // ========== API 函数 ==========

  /** 查询资源依赖 */
  export const getDependencyList = (data: { resourceEntityId: number }) => {
    return http.request<DependencyListResult>("post", "/api/perm/resource-dependency/list", { data });
  };

  /** 创建资源依赖 */
  export const createDependency = (data: DependencyCreateRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/resource-dependency/create", { data });
  };

  /** 删除资源依赖 */
  export const deleteDependency = (data: { id: number }) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/resource-dependency/remove", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 
  - resourceEntityId 是源资源（被授权后触发补全）
  - dependsOnResourceEntityId 是目标资源（需要补全）
  - sourceOperationBits 和 requiredOperationBits 使用位运算
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 3-6: 页面和组件实现(参考 Phase 3.3 资源树和 Phase 2.2 组织管理)

由于资源管理页面的逻辑与组织管理页和角色权限配置页的资源树类似，这里提供实现要点：

**Task 3: 资源管理主页面**
- 左侧资源树 + 右侧详情表单布局
- 业务域和资源类型筛选
- 资源节点 CRUD（新增根资源/子资源、编辑、删除）
- Tab 切换（资源详情 + 资源依赖）

**Task 4: 资源树组件**
- 参考 Phase 3.3 的 ResourceTree.vue
- 支持业务域和资源类型筛选
- 显示资源类型标签（MENU/API/BUTTON/DATA）

**Task 5: 资源详情表单**
- 参考 Phase 2.2 的 OrgForm.vue
- 资源名称、编码、类型、业务域、状态
- 编辑模式下资源编码和类型不可修改

**Task 6: 资源依赖配置组件**
- 显示已配置的依赖关系
- 添加依赖：选择源资源 + 目标资源 + 操作位配置
- 移除依赖

---

## Acceptance Criteria
- [ ] 所有 6 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 资源树正确显示和筛选
- [ ] 资源类型标签正确显示
- [ ] 资源详情表单正确显示
- [ ] 资源节点 CRUD 功能正常
- [ ] 资源依赖配置功能正常

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase4-resource-advanced-config.plan.md`
**Confidence Score**: 8/10 — 资源管理逻辑清晰，依赖配置相对独立