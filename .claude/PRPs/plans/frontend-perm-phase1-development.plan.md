# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 1

## Summary

开发权限中心前端缺失的核心模块（Phase 1），包括权限条件管理、抽象用户管理和日志审计三个高优先级模块。

## User Story

As a 权限管理员, I want 在前端界面管理权限条件、抽象用户和查看审计日志, So that 我能够完整地配置和管理权限系统。

## Problem → Solution

后端已提供完整的权限条件、抽象用户和日志查询API，但前端缺少对应的页面和API层 → 开发缺失的API文件、页面组件和路由配置。

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 25-30
- **前置条件**: 代码规范分析完成, `frontend-perm-tree-component-abstraction.plan.md` 可选

---

## Dependencies

| 计划 | 关系 | 说明 |
|------|------|------|
| `frontend-perm-tree-component-abstraction.plan.md` | 可选依赖 | 如果Tree组件抽象完成，抽象用户和权限条件页面可使用RePermissionTree |
| `frontend-perm-completed-modules-improvement.plan.md` | 前置依赖 | 已完成模块完善应先完成 |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `.claude/rules/frontend-coding-standards.md` | 编码规范标准 |
| P0 | `permission-center/.../controller/ConditionController.java` | 权限条件后端API |
| P0 | `permission-center/.../controller/UserController.java` | 抽象用户后端API |
| P0 | `permission-center/.../controller/LogQueryController.java` | 日志查询后端API |
| P1 | `frontend/src/api/perm/role.ts` | API文件参考模板 |
| P1 | `frontend/src/views/perm/role/index.vue` | 页面参考模板 |

---

## Patterns to Mirror

### API_FILE_STRUCTURE

```typescript
// SOURCE: frontend/src/api/perm/role.ts

// 1. 类型定义（接口导出）
export interface XxxItem { ... }
export interface XxxResult { ... }
export interface XxxRequest { ... }

// 2. API函数
export const getXxxList = (data?: XxxRequest) => {
  return http.request<XxxResult>("post", "/api/perm/xxx/list", { data });
};

// 3. 工具函数
export const transformXxxResponse = (response: XxxResult) => { ... };

// 4. 常量定义
export const XXX_STATUS_TAG = { ... };
```

### PAGE_COMPONENT_STRUCTURE

```vue
<!-- SOURCE: frontend/src/views/perm/role/index.vue -->

<script setup lang="ts">
defineOptions({ name: "PermXxx" });

// 1. 权限计算
const canCreate = hasPerms(PERM_CODES.PERM_XXX_CREATE);

// 2. 数据加载
const loadData = async () => { ... };

// 3. 交互处理
const handleCreate = () => { ... };
const handleDelete = async (id: number) => { ... };

// 4. 初始化
onMounted(() => { loadData(); });
</script>

<template>
  <div class="xxx-management">
    <!-- 左侧列表/树 -->
    <!-- 右侧详情 -->
    <!-- 表单弹窗 -->
  </div>
</template>
```

---

## Files to Create

### Phase 1.1: 权限条件管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/condition.ts` | CREATE | 权限条件API层 |
| `views/perm/condition/index.vue` | CREATE | 权限条件管理页面 |
| `views/perm/condition/components/ConditionForm.vue` | CREATE | 条件表单组件 |
| `views/perm/condition/components/ConditionList.vue` | CREATE | 条件列表组件 |

### Phase 1.2: 抽象用户管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/user.ts` | CREATE | 抽象用户API层 |
| `views/perm/user/index.vue` | CREATE | 用户管理页面 |
| `views/perm/user/components/UserForm.vue` | CREATE | 用户表单组件 |
| `views/perm/user/components/UserList.vue` | CREATE | 用户列表组件 |

### Phase 1.3: 日志审计模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/log.ts` | CREATE | 日志查询API层 |
| `views/perm/log/index.vue` | CREATE | 日志审计页面 |
| `views/perm/log/components/ChangeLogTable.vue` | CREATE | 变更日志组件 |
| `views/perm/log/components/OperationLogTable.vue` | CREATE | 操作日志组件 |

### Phase 1.4: 路由和权限配置

| File | Action | Justification |
|------|--------|---------------|
| `constants/permission.ts` | UPDATE | 补充Phase 1权限码 |
| `router/modules/perm.ts` | UPDATE | 添加Phase 1路由 |

---

## Step-by-Step Tasks

### Task 1: 创建权限条件API (condition.ts)

- **ACTION**: 创建权限条件管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/condition.ts
  import { http } from "@/utils/http";

  // 类型定义
  export interface ConditionItem {
    id: number;
    tenantId: number;
    conditionType: string;
    name: string;
    config: string; // JSON配置
    description: string | null;
    status: number;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface ConditionListResult {
    success: boolean;
    data: {
      items: Array<ConditionItem>;
    };
  }

  export interface ConditionCreateRequest {
    conditionType: string;
    name: string;
    config: string;
    description?: string;
    status?: number;
  }

  export interface ConditionUpdateRequest {
    id: number;
    conditionType?: string;
    name?: string;
    config?: string;
    description?: string;
    status?: number;
  }

  // API函数
  export const getConditionList = () => {
    return http.request<ConditionListResult>(
      "post",
      "/api/perm/permission-condition/list",
      { data: {} }
    );
  };

  export const getConditionDetail = (data: { id: number }) => {
    return http.request<ConditionDetailResult>(
      "post",
      "/api/perm/permission-condition/detail",
      { data }
    );
  };

  export const createCondition = (data: ConditionCreateRequest) => {
    return http.request<ConditionActionResult>(
      "post",
      "/api/perm/permission-condition/create",
      { data }
    );
  };

  export const updateCondition = (data: ConditionUpdateRequest) => {
    return http.request<ConditionActionResult>(
      "post",
      "/api/perm/permission-condition/update",
      { data }
    );
  };

  export const deleteCondition = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/permission-condition/remove",
      { data }
    );
  };

  // 常量定义
  export const CONDITION_TYPE_OPTIONS = [
    { label: "时间范围", value: "TIME_RANGE" },
    { label: "地域限制", value: "GEO_LOCATION" },
    { label: "组织归属", value: "ORGANIZATION" },
    { label: "自定义", value: "CUSTOM" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 2: 创建权限条件管理页面

- **ACTION**: 创建权限条件管理页面
- **IMPLEMENT**:
  ```vue
  <!-- views/perm/condition/index.vue -->
  <script setup lang="ts">
  defineOptions({ name: "PermCondition" });
  
  import { ref, onMounted } from "vue";
  import { ElMessage, ElMessageBox } from "element-plus";
  import {
    getConditionList,
    deleteCondition,
    type ConditionItem
  } from "@/api/perm/condition";
  import { PERM_CODES } from "@/constants/permission";
  import { hasPerms } from "@/utils/auth";
  import ConditionForm from "./components/ConditionForm.vue";
  import ConditionList from "./components/ConditionList.vue";

  const conditions = ref<Array<ConditionItem>>([]);
  const loading = ref(false);
  const selectedCondition = ref<ConditionItem | null>(null);
  const formRef = ref();

  const canCreate = hasPerms(PERM_CODES.PERM_CONDITION_CREATE);
  const canUpdate = hasPerms(PERM_CODES.PERM_CONDITION_UPDATE);
  const canDelete = hasPerms(PERM_CODES.PERM_CONDITION_DELETE);

  const loadConditions = async () => {
    loading.value = true;
    try {
      const res = await getConditionList();
      if (res.success) {
        conditions.value = res.data.items;
      }
    } catch {
      ElMessage.error("加载权限条件失败");
    } finally {
      loading.value = false;
    }
  };

  const handleCreate = () => {
    formRef.value.openDialog();
  };

  const handleEdit = (condition: ConditionItem) => {
    formRef.value.openDialog(condition);
  };

  const handleDelete = async (id: number) => {
    try {
      await ElMessageBox.confirm("确认删除该权限条件？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });
      const res = await deleteCondition({ ids: [id] });
      if (res.success) {
        ElMessage.success("删除成功");
        await loadConditions();
      }
    } catch (error) {
      if (error !== "cancel") {
        ElMessage.error("删除失败");
      }
    }
  };

  onMounted(() => {
    loadConditions();
  });
  </script>

  <template>
    <div class="condition-management">
      <div class="flex h-full">
        <!-- 左侧条件列表 -->
        <div class="w-[350px] border-r flex flex-col">
          <div class="p-4 border-b">
            <el-button
              type="primary"
              size="small"
              :disabled="!canCreate"
              @click="handleCreate"
            >
              新增条件
            </el-button>
          </div>
          <ConditionList
            :data="conditions"
            :loading="loading"
            :can-update="canUpdate"
            :can-delete="canDelete"
            @edit="handleEdit"
            @delete="handleDelete"
          />
        </div>

        <!-- 右侧详情/说明 -->
        <div class="flex-1 p-4">
          <div v-if="selectedCondition" class="condition-detail">
            <!-- 条件详情展示 -->
          </div>
          <div v-else class="text-center py-10 text-gray-500">
            请选择权限条件或创建新条件
          </div>
        </div>

        <!-- 条件表单弹窗 -->
        <ConditionForm ref="formRef" @success="loadConditions" />
      </div>
    </div>
  </template>
  ```
- **CHECKLIST**:
  - [ ] 使用 `<script setup lang="ts">` + `defineOptions`
  - [ ] 权限计算使用 `hasPerms`
  - [ ] 数据加载错误处理
  - [ ] 删除确认对话框

### Task 3: 创建抽象用户API (user.ts)

- **ACTION**: 创建抽象用户管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/user.ts
  import { http } from "@/utils/http";

  // 类型定义
  export interface UserItem {
    id: number;
    tenantId: number;
    subjectTypeCode: string;
    externalId: string;
    username: string;
    status: number;
    extra: string | null;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface UserListResult {
    success: boolean;
    data: {
      items: Array<UserItem>;
      total: number;
      pageNum: number;
      pageSize: number;
      hasNext: boolean;
    };
  }

  export interface UserSyncRequest {
    subjectTypeCode: string;
    externalId: string;
    username: string;
    status?: number;
    extra?: string;
  }

  export interface UserCreateRequest {
    subjectTypeCode: string;
    externalId: string;
    username: string;
    status?: number;
    extra?: string;
  }

  export interface UserUpdateRequest {
    id: number;
    username?: string;
    status?: number;
    extra?: string;
  }

  // API函数
  export const getUserList = (data: { 
    subjectTypeCode?: string; 
    domainCode?: string; 
    keyword?: string;
    pageNum?: number;
    pageSize?: number;
  }) => {
    return http.request<UserListResult>(
      "post",
      "/api/perm/abstract-user/list",
      { data }
    );
  };

  export const getUserDetail = (data: { id: number }) => {
    return http.request<UserDetailResult>(
      "post",
      "/api/perm/abstract-user/detail",
      { data }
    );
  };

  export const syncUser = (data: UserSyncRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/sync",
      { data }
    );
  };

  export const createUser = (data: UserCreateRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/create",
      { data }
    );
  };

  export const updateUser = (data: UserUpdateRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/update",
      { data }
    );
  };

  export const deleteUser = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/abstract-user/remove",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 4: 创建权限排查页面（完整诊断系统）

- **ACTION**: 实现 `/perm/explain` 完整的权限解释和诊断页面
- **IMPLEMENT**:
  - 查询模式选择（用户/角色）
  - 完整的权限解释分析
  - 来源角色追溯（via继承链）
  - 近期变更历史展示
  - **权限可视化图表**（资源-操作矩阵）
  - **权限对比功能**（对比两个主体/角色的权限差异）
- **CHECKLIST**:
  - [ ] 支持用户/角色两种查询模式
  - [ ] 完整的权限解释分析（判定结果、原因、来源）
  - [ ] 来源角色追溯（via继承链）
  - [ ] 近期变更历史展示
  - [ ] **权限可视化图表（资源-操作权限矩阵）**
  - [ ] **权限对比功能（支持两个主体/角色对比）**
- **GOTCHA**: 需要结合 `permissionView.ts` 中的 `explainPermission` API

### Task 5: 创建抽象用户管理页面

- **ACTION**: 创建抽象用户管理页面
- **IMPLEMENT**: 参照角色管理页面结构
- **CHECKLIST**:
  - [ ] 支持分页查询
  - [ ] 支持关键字搜索
  - [ ] 支持用户同步功能
  - [ ] 支持CRUD操作

### Task 6: 创建日志查询API (log.ts)

- **ACTION**: 创建日志查询API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/log.ts
  import { http } from "@/utils/http";

  // 变更日志
  export interface ChangeLogItem {
    id: number;
    tenantId: number;
    entityType: string;
    entityId: number;
    eventType: string;
    changeType: string;
    diffSnapshot: string;
    operatorId: number;
    source: string;
    createdAt: string;
  }

  // 操作日志
  export interface OperationLogItem {
    id: number;
    tenantId: number;
    module: string;
    action: string;
    targetType: string;
    targetId: string;
    summary: string;
    operatorId: number;
    createdAt: string;
  }

  export interface ChangeLogListRequest {
    entityType?: string;
    entityId?: number;
    pageNum?: number;
    pageSize?: number;
  }

  export interface OperationLogListRequest {
    module?: string;
    action?: string;
    pageNum?: number;
    pageSize?: number;
  }

  // API函数
  export const getChangeLogs = (data: ChangeLogListRequest) => {
    return http.request<ChangeLogListResult>(
      "post",
      "/api/perm/log/change/list",
      { data }
    );
  };

  export const getOperationLogs = (data: OperationLogListRequest) => {
    return http.request<OperationLogListResult>(
      "post",
      "/api/perm/log/operation/list",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 7: 创建日志审计页面

- **ACTION**: 创建日志审计页面
- **IMPLEMENT**:
  - Tab切换变更日志和操作日志
  - 支持分页查询
  - 支持按条件筛选
- **CHECKLIST**:
  - [ ] 使用Tabs组件切换日志类型
  - [ ] 支持按实体类型/ID筛选变更日志
  - [ ] 支持按模块/操作类型筛选操作日志
  - [ ] 表格展示完整的日志信息

### Task 8: 补充权限码

- **ACTION**: 在 `constants/permission.ts` 中补充Phase 1权限码
- **IMPLEMENT**:
  ```typescript
  // 权限条件管理
  export const PERM_CONDITION_VIEW = "perm:condition:view";
  export const PERM_CONDITION_CREATE = "perm:condition:create";
  export const PERM_CONDITION_UPDATE = "perm:condition:update";
  export const PERM_CONDITION_DELETE = "perm:condition:delete";

  // 抽象用户管理
  export const PERM_USER_VIEW = "perm:user:view";
  export const PERM_USER_CREATE = "perm:user:create";
  export const PERM_USER_UPDATE = "perm:user:update";
  export const PERM_USER_DELETE = "perm:user:delete";
  export const PERM_USER_SYNC = "perm:user:sync";

  // 日志审计
  export const PERM_LOG_VIEW = "perm:log:view";
  ```
- **VALIDATE**: 添加到 `PERM_CODES` 对象

### Task 9: 更新路由配置

- **ACTION**: 更新 `router/modules/perm.ts`
- **IMPLEMENT**:
  ```typescript
  {
    path: "/perm/condition",
    name: "PermCondition",
    component: () => import("@/views/perm/condition/index.vue"),
    meta: {
      title: "权限条件",
      auths: [PERM_CODES.PERM_CONDITION_VIEW]
    }
  },
  {
    path: "/perm/user",
    name: "PermUser",
    component: () => import("@/views/perm/user/index.vue"),
    meta: {
      title: "抽象用户",
      auths: [PERM_CODES.PERM_USER_VIEW]
    }
  },
  {
    path: "/perm/log",
    name: "PermLog",
    component: () => import("@/views/perm/log/index.vue"),
    meta: {
      title: "日志审计",
      auths: [PERM_CODES.PERM_LOG_VIEW]
    }
  }
  ```
- **VALIDATE**: 路由编译通过

---

## Testing Strategy

### Static Analysis

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm lint:prettier
```

### Functional Testing

| Test | Input | Expected Output |
|------|-------|-----------------|
| 权限条件列表 | 访问 `/perm/condition` | 显示权限条件列表 |
| 权限条件创建 | 填写表单提交 | 创建成功，列表刷新 |
| 权限条件删除 | 点击删除按钮 | 确认后删除成功 |
| 用户列表 | 访问 `/perm/user` | 显示用户分页列表 |
| 用户同步 | 填写同步信息提交 | 同步成功 |
| 变更日志 | 访问 `/perm/log` | 显示变更日志列表 |
| 操作日志 | 切换Tab | 显示操作日志列表 |

---

## Validation Commands

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm build
```

EXPECT: 零错误，构建成功

---

## Acceptance Criteria

- [ ] `api/perm/condition.ts` 创建完成，包含完整CRUD
- [ ] `views/perm/condition/` 页面组件创建完成
- [ ] `api/perm/user.ts` 创建完成，包含完整CRUD
- [ ] `views/perm/user/` 页面组件创建完成
- [ ] `api/perm/log.ts` 创建完成，包含两种日志查询
- [ ] `views/perm/log/` 页面组件创建完成
- [ ] 权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 后端API变更 | Low | High | 开发前确认API稳定性 |
| 页面复杂度超预期 | Medium | Medium | 分Task执行，及时反馈 |
| 权限码命名冲突 | Low | Medium | 统一命名规范 |
