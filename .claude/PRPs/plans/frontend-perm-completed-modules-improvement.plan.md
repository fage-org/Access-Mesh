# Plan: Permission Center Frontend 已完成模块完善计划

## Summary

基于代码规范审查结果，对已完成的7个前端模块进行完善和优化，包括代码质量提升、功能补充和API对齐。

## User Story

As a 前端开发者, I want 已完成的模块符合团队编码规范且功能完整, So that 我能够维护高质量、一致性好的代码库。

## Problem → Solution

已完成的模块存在规范偏差（如类型导入语法不一致）、权限码缺失、功能不完整（如权限视图页面仅部分实现）→ 通过系统性的完善计划修复规范问题、补充缺失功能、提升代码质量。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 15-20
- **前置条件**: 代码规范分析完成

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `.claude/rules/frontend-coding-standards.md` | 编码规范标准 |
| P0 | `frontend/src/api/perm/permissionView.ts` | 权限视图API（需完善） |
| P0 | `frontend/src/views/perm/view/index.vue` | 权限视图页面（需完善） |
| P1 | `frontend/src/views/perm/explain/index.vue` | 权限排查页面（需实现） |
| P1 | `frontend/src/constants/permission.ts` | 权限码常量（需补充） |

---

## Patterns to Mirror

### TYPE_IMPORT_PATTERN

```typescript
// ✅ 正确 — 内联类型导入
import { type AxiosRequestConfig } from "axios";
import { type FormInstance } from "element-plus";

// ❌ 禁止 — 单独导入类型
import type { AxiosRequestConfig } from "axios";
```

### API_DEFINITION_PATTERN

```typescript
// SOURCE: frontend/src/api/perm/role.ts

// 导出类型定义 + API函数
export interface RoleTreeNode {
  id: number;
  name: string;
  // ...
}

export const getRoleTree = (data?: { domainCode?: string }) => {
  return http.request<RoleTreeResult>("post", "/api/perm/abstract-role/tree", {
    data: data || {}
  });
};

// 配套工具函数
export const transformRoleTreeResponse = (response: RoleTreeResult): Array<RoleTreeNode> => {
  // ...
};
```

### COMPONENT_PATTERN

```vue
<!-- SOURCE: frontend/src/views/perm/role/index.vue -->

<script setup lang="ts">
defineOptions({
  name: "PermRole"
});

// Props/Emits类型定义
const props = defineProps<{
  roleDetail: RoleDetail | null;
}>();

const emit = defineEmits<{
  edit: [];
}>();
</script>
```

---

## Files to Change

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/permissionView.ts` | UPDATE | 补充缺失的API方法 |
| `api/perm/resourceDependency.ts` | UPDATE | 补充批量同步和循环检测方法 |
| `views/perm/view/index.vue` | UPDATE | 完善权限视图功能 |
| `views/perm/explain/index.vue` | UPDATE | 实现权限排查功能 |
| `constants/permission.ts` | UPDATE | 补充缺失的权限码 |
| `router/modules/perm.ts` | UPDATE | 补充缺失路由和权限绑定 |

---

## Step-by-Step Tasks

### Task 1: 修复类型导入语法

- **ACTION**: 将所有 `import type { ... }` 改为 `import { type ... }`
- **IMPLEMENT**:
  - 扫描所有 `api/perm/*.ts` 文件
  - 替换类型导入语法
- **CHECKLIST**:
  - [ ] `domain.ts` 使用内联类型导入
  - [ ] `operation.ts` 使用内联类型导入
  - [ ] `permissionView.ts` 使用内联类型导入
  - [ ] `resourceDependency.ts` 使用内联类型导入
  - [ ] `resource.ts` 使用内联类型导入
  - [ ] `rolePermission.ts` 使用内联类型导入
  - [ ] `role.ts` 使用内联类型导入
  - [ ] `service.ts` 使用内联类型导入
  - [ ] `type.ts` 使用内联类型导入
  - [ ] `userRole.ts` 使用内联类型导入
- **VALIDATE**: `pnpm lint:eslint` 通过

### Task 2: 完善 permissionView.ts API

- **ACTION**: 补充权限视图页面缺失的API方法
- **IMPLEMENT**:
  ```typescript
  // 查询有效角色
  export const getEffectiveRoles = (data: UserEffectiveRolesRequest) => {
    return http.request<EffectiveRolesResult>(
      "post",
      "/api/perm/permission-view/effective-roles",
      { data }
    );
  };

  // 查询资源权限分布
  export const getResourcePermissions = (data: ResourcePermissionViewRequest) => {
    return http.request<ResourcePermissionsResult>(
      "post",
      "/api/perm/permission-view/resource-users",
      { data }
    );
  };

  // 查询角色权限配置
  export const getRolePermissionsView = (data: RolePermissionViewRequest) => {
    return http.request<RolePermissionsViewResult>(
      "post",
      "/api/perm/permission-view/role-permissions",
      { data }
    );
  };

  // 查询用户资源树
  export const getUserResourceTree = (data: UserResourceTreeRequest) => {
    return http.request<UserResourceTreeResult>(
      "post",
      "/api/perm/permission-view/resource-tree",
      { data }
    );
  };

  // 查询近期变更
  export const getRecentChanges = (data: RecentChangesRequest) => {
    return http.request<RecentChangesResult>(
      "post",
      "/api/perm/permission-view/recent-changes",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 3: 完善权限视图页面

- **ACTION**: 完善 `/perm/view` 权限视图页面功能
- **IMPLEMENT**:
  - 添加有效角色查询视图
  - 添加资源权限分布视图
  - 添加权限变更历史视图
  - 添加用户资源树视图
- **CHECKLIST**:
  - [ ] 页面包含多个Tab切换视图
  - [ ] 有效角色查询功能完整
  - [ ] 资源权限分布功能完整
  - [ ] 权限变更历史功能完整
  - [ ] 用户资源树功能完整
- **GOTCHA**: 每个视图独立组件，避免单个文件过大

### Task 4: 实现权限排查页面

- **ACTION**: 实现 `/perm/explain` 权限排查页面
- **IMPLEMENT**:
  ```vue
  <!-- 权限排查功能 -->
  <template>
    <div class="permission-explain">
      <!-- 查询条件 -->
      <el-form :model="queryForm">
        <el-form-item label="主体类型">
          <el-select v-model="queryForm.targetType">
            <el-option label="用户" value="USER" />
            <el-option label="角色" value="ROLE" />
          </el-select>
        </el-form-item>
        <!-- 其他查询条件... -->
      </el-form>
      
      <!-- 权限解释结果 -->
      <div v-if="explainResult" class="explain-result">
        <!-- 权限判定结果、来源角色、变更历史等 -->
      </div>
    </div>
  </template>
  ```
- **CHECKLIST**:
  - [ ] 支持用户/角色两种查询模式
  - [ ] 显示权限判定结果
  - [ ] 显示权限来源角色
  - [ ] 显示近期变更历史
- **GOTCHA**: 需要结合 `permissionView.ts` 中的 `explainPermission` API

### Task 5: 补充缺失的权限码

- **ACTION**: 在 `constants/permission.ts` 中补充缺失的权限码
- **IMPLEMENT**:
  ```typescript
  // 权限视图权限码
  export const PERM_USER_ROLE_VIEW = "perm:user-role:view";
  export const PERM_USER_ROLE_ASSIGN = "perm:user-role:assign";
  export const PERM_USER_ROLE_REVOKE = "perm:user-role:revoke";
  
  // 权限视图权限码
  export const PERM_VIEW_EFFECTIVE_ROLES = "perm:view:effective-roles";
  export const PERM_VIEW_RESOURCE_PERMISSIONS = "perm:view:resource-permissions";
  export const PERM_VIEW_CHANGE_LOG = "perm:view:change-log";
  
  // 权限排查权限码
  export const PERM_EXPLAIN_QUERY = "perm:explain:query";
  export const PERM_EXPLAIN_DIAGNOSE = "perm:explain:diagnose";
  ```
- **VALIDATE**: 所有权限码格式符合 `{module}:{resource}:{action}`

### Task 6: 更新路由配置

- **ACTION**: 更新 `router/modules/perm.ts` 补充权限绑定
- **IMPLEMENT**:
  - 为所有路由添加 `meta.auths` 配置
  - 确认隐藏页面 `showLink: false` 配置
- **CHECKLIST**:
  - [ ] `/perm/role` 绑定 `PERM_CODES.SYS_ROLE_VIEW`
  - [ ] `/perm/user-role` 绑定 `PERM_CODES.PERM_USER_ROLE_VIEW`
  - [ ] `/perm/resource` 绑定 `PERM_CODES.PERM_RESOURCE_VIEW`
  - [ ] `/perm/view` 绑定 `PERM_CODES.PERM_VIEW`
  - [ ] `/perm/explain` 绑定 `PERM_CODES.PERM_EXPLAIN`

### Task 7: 修复资源依赖API

- **ACTION**: 补充 `resourceDependency.ts` 缺失的方法
- **IMPLEMENT**:
  ```typescript
  // 批量同步资源依赖
  export const batchSyncDependencies = (data: DependencyBatchSyncRequest) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/resource-dependency/batch-sync",
      { data }
    );
  };

  // 检测依赖循环
  export const checkDependencyCycle = (data: DependencyCycleCheckRequest) => {
    return http.request<DependencyCycleCheckResult>(
      "post",
      "/api/perm/resource-dependency/check",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 8: 代码质量检查

- **ACTION**: 运行完整的代码质量检查
- **IMPLEMENT**:
  ```bash
  cd frontend
  pnpm typecheck
  pnpm lint:eslint
  pnpm lint:prettier
  pnpm build
  ```
- **VALIDATE**: 无错误，警告数 < 10

---

## Testing Strategy

### Static Analysis

```bash
cd frontend

# 类型检查
pnpm typecheck

# ESLint检查
pnpm lint:eslint

# Prettier检查
pnpm lint:prettier

# 构建检查
pnpm build
```

### Functional Testing

| Test | Input | Expected Output |
|------|-------|-----------------|
| 权限视图页面 | 访问 `/perm/view` | 页面正常渲染，多Tab切换正常 |
| 权限排查页面 | 访问 `/perm/explain` | 页面正常渲染，查询功能正常 |
| 资源依赖批量同步 | 调用 batchSyncDependencies | API调用成功 |
| 依赖循环检测 | 调用 checkDependencyCycle | 返回检测结果 |

---

## Validation Commands

### Static Analysis

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm lint:prettier
```

EXPECT: 零编译错误，ESLint警告 < 10

### Full Compile

```bash
pnpm build
```

EXPECT: 构建成功

---

## Acceptance Criteria

- [ ] 所有API文件使用内联类型导入语法
- [ ] `permissionView.ts` 包含完整的权限视图API
- [ ] 权限视图页面功能完整（多Tab视图）
- [ ] 权限排查页面功能完整
- [ ] `constants/permission.ts` 包含所有需要的权限码
- [ ] 路由配置包含完整的权限绑定
- [ ] `resourceDependency.ts` 包含批量同步和循环检测方法
- [ ] 全量编译通过，ESLint警告 < 10

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| API变更导致不兼容 | Low | High | 与后端API文档对齐 |
| 权限码命名冲突 | Low | Medium | 统一命名规范 |
| 类型检查失败 | Medium | Low | 逐步修复类型问题 |
