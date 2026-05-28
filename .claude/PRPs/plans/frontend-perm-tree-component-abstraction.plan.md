# Plan: Permission Tree Component Abstraction

## Summary

将 RoleTree 和 ResourceTree 组件抽象为可复用的通用权限树组件 `RePermissionTree`，支持角色树和资源树两种模式，提高代码复用性和维护性。

## User Story

As a 前端开发者, I want 一个通用的权限树组件, So that 我能够在角色管理和资源管理中复用相同的树形交互逻辑，减少重复代码。

## Problem → Solution

RoleTree.vue 和 ResourceTree.vue 结构高度相似（Props、Emits、筛选逻辑、操作按钮），但分别维护两套几乎相同的代码 → 抽象为通用组件 `RePermissionTree`，通过配置参数支持不同模式。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 5-7
- **前置条件**: Phase 0 分析完成

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `frontend/src/views/perm/role/components/RoleTree.vue` | 源组件参考 |
| P0 | `frontend/src/views/perm/resource/components/ResourceTree.vue` | 源组件参考 |
| P0 | `.claude/rules/frontend-coding-standards.md` | 组件开发规范 |
| P1 | `frontend/src/components/` | 通用组件目录结构 |

---

## Analysis: RoleTree vs ResourceTree

### 相似点（可抽象）

| 特性 | RoleTree | ResourceTree | 抽象方案 |
|------|----------|--------------|----------|
| Props结构 | `data`, `loading`, `selectedXxxId`, `canCreate`, `canDelete` | 相同 | 统一Props接口 |
| Emits事件 | `nodeClick`, `createChild`, `delete` | 相同 | 统一Emits定义 |
| 搜索过滤 | debounce 300ms + filterNode | 相同 | 内置Hook |
| 操作按钮 | 新增/删除按钮 + hover显示 | 相同 | 模板内置 |
| 节点模板 | 名称 + 类型Tag + 状态Tag + 操作按钮 | 相同 | slot配置 |

### 差异点（需配置化）

| 差异 | RoleTree | ResourceTree | 配置方案 |
|------|----------|--------------|----------|
| 数据类型 | `RoleTreeNode` | `ResourceTreeNode` | 泛型 `<T extends TreeNode>` |
| Tag渲染 | `getRoleTypeTag` + `getStatusTag` | `getResourceTypeTag` + `getResourceStatusTag` | slot自定义 |
| 搜索字段 | `name` | `name` + `code` | 配置 `searchFields` |
| 树节点key | `id` | `id` | 统一 |

---

## Component Design

### Props Interface

```typescript
// components/RePermissionTree/types.ts

export interface TreeNode {
  id: number;
  name: string;
  children?: Array<TreeNode>;
  [key: string]: any;
}

export interface PermissionTreeProps<T extends TreeNode> {
  // 数据
  data: Array<T>;
  loading: boolean;
  selectedId: number | null;
  
  // 权限
  canCreate: boolean;
  canDelete: boolean;
  
  // 配置
  mode: "role" | "resource"; // 影响默认渲染
  searchPlaceholder?: string;
  searchFields?: Array<string>; // 默认 ["name"]
  emptyText?: string;
}

export interface PermissionTreeEmits {
  nodeClick: [id: number];
  createChild: [parentId: number];
  delete: [id: number];
}
```

### Slot Configuration

```vue
<!-- 使用示例 -->
<RePermissionTree
  :data="roleTreeData"
  :loading="loading"
  :selected-id="selectedRoleId"
  mode="role"
  @node-click="handleNodeClick"
>
  <!-- 自定义节点内容 -->
  <template #node-content="{ data }">
    <span>{{ data.name }}</span>
    <el-tag :type="getRoleTypeTag(data.roleTypeCode).type">
      {{ getRoleTypeTag(data.roleTypeCode).text }}
    </el-tag>
  </template>
</RePermissionTree>
```

---

## Files to Create

| File | Action | Justification |
|------|--------|---------------|
| `components/RePermissionTree/index.ts` | CREATE | Barrel文件导出 |
| `components/RePermissionTree/RePermissionTree.vue` | CREATE | 主组件 |
| `components/RePermissionTree/types.ts` | CREATE | 类型定义 |
| `components/RePermissionTree/useTreeFilter.ts` | CREATE | 筛选逻辑Hook |
| `views/perm/role/components/RoleTree.vue` | UPDATE | 改用RePermissionTree |
| `views/perm/resource/components/ResourceTree.vue` | UPDATE | 改用RePermissionTree |

---

## Step-by-Step Tasks

### Task 1: Create Type Definitions

- **ACTION**: 创建类型定义文件
- **IMPLEMENT**:
  ```typescript
  // components/RePermissionTree/types.ts
  export interface TreeNode {
    id: number;
    name: string;
    children?: Array<TreeNode>;
    [key: string]: unknown;
  }

  export interface PermissionTreeProps<T extends TreeNode = TreeNode> {
    data: Array<T>;
    loading: boolean;
    selectedId: number | null;
    canCreate: boolean;
    canDelete: boolean;
    mode?: "role" | "resource";
    searchPlaceholder?: string;
    searchFields?: Array<string>;
    emptyText?: string;
  }

  export type PermissionTreeEmits = {
    nodeClick: [id: number];
    createChild: [parentId: number];
    delete: [id: number];
  };
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 2: Create useTreeFilter Hook

- **ACTION**: 抽取筛选逻辑为Hook
- **IMPLEMENT**:
  ```typescript
  // components/RePermissionTree/useTreeFilter.ts
  import { ref, watch, onUnmounted } from "vue";

  export function useTreeFilter<T extends { id: number }>(
    searchFields: Array<string> = ["name"]
  ) {
    const filterText = ref("");
    const treeRef = ref();
    let filterTimer: ReturnType<typeof setTimeout> | null = null;

    const filterNode = (value: string, data: T): boolean => {
      if (!value) return true;
      const lowerValue = value.toLowerCase();
      return searchFields.some(field => {
        const fieldValue = (data as Record<string, unknown>)[field];
        return String(fieldValue).toLowerCase().includes(lowerValue);
      });
    };

    watch(filterText, val => {
      if (filterTimer) clearTimeout(filterTimer);
      filterTimer = setTimeout(() => {
        treeRef.value?.filter(val);
      }, 300);
    });

    onUnmounted(() => {
      if (filterTimer) {
        clearTimeout(filterTimer);
        filterTimer = null;
      }
    });

    return {
      filterText,
      treeRef,
      filterNode
    };
  }
  ```
- **VALIDATE**: Hook可正常工作

### Task 3: Create RePermissionTree Component

- **ACTION**: 创建主组件
- **IMPLEMENT**:
  ```vue
  <!-- components/RePermissionTree/RePermissionTree.vue -->
  <script setup lang="ts" generic="T extends TreeNode">
  import { useTreeFilter } from "./useTreeFilter";
  import { type TreeNode, type PermissionTreeProps } from "./types";

  defineOptions({
    name: "RePermissionTree"
  });

  const props = withDefaults(
    defineProps<PermissionTreeProps<T>>(),
    {
      mode: "role",
      searchPlaceholder: "搜索名称",
      searchFields: () => ["name"],
      emptyText: "暂无数据"
    }
  );

  const emit = defineEmits<{
    nodeClick: [id: number];
    createChild: [parentId: number];
    delete: [id: number];
  }>();

  const { filterText, treeRef, filterNode } = useTreeFilter<T>(
    props.searchFields
  );

  const defaultProps = {
    children: "children",
    label: "name",
    value: "id"
  };

  const handleNodeClick = (data: T) => {
    emit("nodeClick", data.id);
  };

  const handleCreateChild = (data: T) => {
    emit("createChild", data.id);
  };

  const handleDelete = (data: T) => {
    emit("delete", data.id);
  };
  </script>

  <template>
    <div class="re-permission-tree">
      <!-- 搜索栏 -->
      <div class="p-2 border-b">
        <el-input
          v-model="filterText"
          :placeholder="searchPlaceholder"
          clearable
          size="small"
        />
      </div>

      <!-- 树形组件 -->
      <el-scrollbar class="flex-1">
        <el-tree
          ref="treeRef"
          v-loading="loading"
          :data="data"
          :props="defaultProps"
          :filter-node-method="filterNode"
          node-key="id"
          :current-node-key="selectedId"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="flex items-center justify-between w-full pr-2 group">
              <!-- 节点内容 - 通过slot自定义 -->
              <div class="flex items-center gap-2 overflow-hidden flex-1">
                <slot name="node-content" :data="data">
                  <!-- 默认渲染 -->
                  <span class="truncate">{{ data.name }}</span>
                </slot>
              </div>
              
              <!-- 操作按钮 -->
              <div
                class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
              >
                <el-button
                  v-if="canCreate"
                  type="primary"
                  link
                  size="small"
                  @click.stop="handleCreateChild(data)"
                >
                  新增
                </el-button>
                <el-button
                  v-if="canDelete"
                  type="danger"
                  link
                  size="small"
                  @click.stop="handleDelete(data)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </template>
        </el-tree>

        <div
          v-if="data.length === 0 && !loading"
          class="text-center py-10 text-gray-500"
        >
          {{ emptyText }}
        </div>
      </el-scrollbar>
    </div>
  </template>

  <style scoped lang="scss">
  .re-permission-tree {
    display: flex;
    flex-direction: column;
    height: 100%;

    :deep(.el-tree-node__content) {
      height: 36px;
    }
  }
  </style>
  ```
- **VALIDATE**: 组件可正常渲染

### Task 4: Create Barrel File

- **ACTION**: 创建barrel文件
- **IMPLEMENT**:
  ```typescript
  // components/RePermissionTree/index.ts
  import RePermissionTree from "./RePermissionTree.vue";
  import { useTreeFilter } from "./useTreeFilter";
  import type { TreeNode, PermissionTreeProps } from "./types";

  export { RePermissionTree, useTreeFilter };
  export type { TreeNode, PermissionTreeProps };

  // 简化导入
  const PermissionTree = RePermissionTree;
  export default PermissionTree;
  ```
- **VALIDATE**: Barrel模式符合规范

### Task 5: Refactor RoleTree Component

- **ACTION**: 重构角色树组件使用RePermissionTree
- **IMPLEMENT**:
  ```vue
  <!-- views/perm/role/components/RoleTree.vue -->
  <script setup lang="ts">
  import { RePermissionTree } from "@/components/RePermissionTree";
  import {
    type RoleTreeNode,
    getRoleTypeTag,
    getStatusTag
  } from "@/api/perm/role";

  defineOptions({ name: "RoleTree" });

  const props = defineProps<{
    data: Array<RoleTreeNode>;
    loading: boolean;
    selectedRoleId: number | null;
    canCreate: boolean;
    canDelete: boolean;
  }>();

  const emit = defineEmits<{
    nodeClick: [roleId: number];
    createChild: [parentId: number];
    delete: [roleId: number];
  }>();
  </script>

  <template>
    <RePermissionTree
      :data="data"
      :loading="loading"
      :selected-id="selectedRoleId"
      :can-create="canCreate"
      :can-delete="canDelete"
      mode="role"
      search-placeholder="搜索角色名称"
      empty-text="暂无角色数据"
      @node-click="$emit('nodeClick', $event)"
      @create-child="$emit('createChild', $event)"
      @delete="$emit('delete', $event)"
    >
      <template #node-content="{ data }">
        <span class="truncate">{{ data.name }}</span>
        <el-tag
          :type="getRoleTypeTag(data.roleTypeCode).type"
          size="small"
        >
          {{ getRoleTypeTag(data.roleTypeCode).text }}
        </el-tag>
        <el-tag
          v-if="data.status === 0"
          :type="getStatusTag(data.status).type"
          size="small"
        >
          {{ getStatusTag(data.status).text }}
        </el-tag>
      </template>
    </RePermissionTree>
  </template>
  ```
- **VALIDATE**: 角色管理功能正常

### Task 6: Refactor ResourceTree Component

- **ACTION**: 重构资源树组件使用RePermissionTree
- **IMPLEMENT**: 类似RoleTree重构方式
- **CHECKLIST**:
  - [ ] 使用RePermissionTree组件
  - [ ] 自定义node-content slot
  - [ ] 资源类型Tag和状态Tag正确渲染
  - [ ] 搜索支持名称和编码

### Task 7: Verify Both Components Work

- **ACTION**: 验证重构后功能正常
- **IMPLEMENT**:
  - 角色管理页面功能测试
  - 资源管理页面功能测试
  - 搜索过滤功能测试
  - 操作按钮功能测试
- **CHECKLIST**:
  - [ ] 角色树正常渲染
  - [ ] 资源树正常渲染
  - [ ] 搜索过滤功能正常
  - [ ] 新增/删除操作正常
  - [ ] 节点点击正常

---

## Testing Strategy

### Unit Testing

| Test | Input | Expected Output |
|------|-------|-----------------|
| 组件渲染 | props.data | 树形结构渲染 |
| 搜索过滤 | 输入关键字 | 筛选后节点显示 |
| 节点点击 | 点击节点 | emit nodeClick事件 |
| 操作按钮 | hover节点 | 显示新增/删除按钮 |

### Integration Testing

| Test | Input | Expected Output |
|------|-------|-----------------|
| 角色管理集成 | 访问/perm/role | 角色树正常显示 |
| 资源管理集成 | 访问/perm/resource | 资源树正常显示 |

---

## Validation Commands

```bash
cd frontend

# 类型检查
pnpm typecheck

# ESLint检查
pnpm lint:eslint

# 构建检查
pnpm build
```

EXPECT: 零错误

---

## Acceptance Criteria

- [ ] `RePermissionTree` 组件创建完成
- [ ] 支持 `role` 和 `resource` 两种模式
- [ ] `useTreeFilter` Hook抽取完成
- [ ] `RoleTree.vue` 重构完成，使用RePermissionTree
- [ ] `ResourceTree.vue` 重构完成，使用RePermissionTree
- [ ] 角色管理页面功能正常
- [ ] 资源管理页面功能正常
- [ ] 全量编译通过
- [ ] ESLint检查通过

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 泛型使用复杂 | Medium | Low | 提供完整类型示例 |
| Slot渲染性能 | Low | Low | 使用v-slot优化 |
| 兼容性问题 | Low | Medium | 充分测试后替换 |
