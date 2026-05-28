# Plan: Permission Condition Management Module

## Summary

Develop the permission condition management module for the frontend, including the API layer and complete CRUD page components.

## User Story

As a permission administrator, I want to manage permission conditions through the frontend interface, so that I can configure time ranges, geographic restrictions, and custom conditions for fine-grained access control.

## Problem → Solution

Backend provides complete permission condition APIs (list, create, update, delete), but frontend lacks corresponding API files and management pages → Develop the missing API layer and page components.

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-phase1-development.plan.md`
- **Estimated Files**: 4
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/ConditionController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/role.ts` | API file template |
| P1 | `frontend/src/views/perm/role/index.vue` | Page template reference |

---

## Patterns to Mirror

### API_FILE_STRUCTURE

```typescript
// Source: frontend/src/api/perm/role.ts

// 1. Type definitions (exported interfaces)
export interface XxxItem { ... }
export interface XxxResult { ... }
export interface XxxRequest { ... }

// 2. API functions
export const getXxxList = (data?: XxxRequest) => {
  return http.request<XxxResult>("post", "/api/perm/xxx/list", { data });
};

// 3. Utility functions
export const transformXxxResponse = (response: XxxResult) => { ... };

// 4. Constants
export const XXX_STATUS_TAG = { ... };
```

---

## Files to Create

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/condition.ts` | CREATE | Permission condition API layer |
| `views/perm/condition/index.vue` | CREATE | Permission condition management page |
| `views/perm/condition/components/ConditionForm.vue` | CREATE | Condition form component |
| `views/perm/condition/components/ConditionList.vue` | CREATE | Condition list component |

---

## Step-by-Step Tasks

### Task 1: Create Permission Condition API (condition.ts)

- **ACTION**: Create permission condition management API file
- **IMPLEMENT**:
  ```typescript
  // api/perm/condition.ts
  import { http } from "@/utils/http";

  // Type definitions
  export interface ConditionItem {
    id: number;
    tenantId: number;
    conditionType: string;
    name: string;
    config: string; // JSON configuration
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

  export interface ConditionDetailResult {
    success: boolean;
    data: ConditionItem;
  }

  export interface ConditionActionResult {
    success: boolean;
    data: { id: number };
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

  // API functions
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

  // Constants
  export const CONDITION_TYPE_OPTIONS = [
    { label: "时间范围", value: "TIME_RANGE" },
    { label: "地域限制", value: "GEO_LOCATION" },
    { label: "组织归属", value: "ORGANIZATION" },
    { label: "自定义", value: "CUSTOM" }
  ];

  export const CONDITION_STATUS_TAG = {
    0: { label: "禁用", type: "danger" },
    1: { label: "启用", type: "success" }
  };
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Condition List Component

- **ACTION**: Create condition list component
- **IMPLEMENT**:
  ```vue
  <!-- views/perm/condition/components/ConditionList.vue -->
  <script setup lang="ts">
  defineOptions({ name: "ConditionList" });

  import { type ConditionItem } from "@/api/perm/condition";

  interface Props {
    data: Array<ConditionItem>;
    loading: boolean;
    canUpdate: boolean;
    canDelete: boolean;
  }

  const props = defineProps<Props>();
  const emit = defineEmits<{
    edit: [condition: ConditionItem];
    delete: [id: number];
  }>();

  const handleEdit = (condition: ConditionItem) => {
    emit("edit", condition);
  };

  const handleDelete = (id: number) => {
    emit("delete", id);
  };
  </script>

  <template>
    <div class="condition-list">
      <el-scrollbar v-loading="props.loading">
        <div
          v-for="item in props.data"
          :key="item.id"
          class="condition-item p-3 border-b hover:bg-gray-50 cursor-pointer"
        >
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="font-medium">{{ item.name }}</div>
              <div class="text-sm text-gray-500 mt-1">
                {{ item.conditionType }} | {{ item.description || "No description" }}
              </div>
            </div>
            <div class="flex gap-2">
              <el-button
                v-if="props.canUpdate"
                type="primary"
                size="small"
                @click.stop="handleEdit(item)"
              >
                Edit
              </el-button>
              <el-button
                v-if="props.canDelete"
                type="danger"
                size="small"
                @click.stop="handleDelete(item.id)"
              >
                Delete
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!props.data.length && !props.loading" description="No conditions" />
      </el-scrollbar>
    </div>
  </template>
  ```

### Task 3: Create Condition Form Component

- **ACTION**: Create condition form dialog component
- **IMPLEMENT**:
  - Support create and edit modes
  - JSON config editor (textarea or json editor)
  - Condition type selection
  - Form validation

### Task 4: Create Condition Management Page

- **ACTION**: Create permission condition management page
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
      ElMessage.error("Failed to load conditions");
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
      await ElMessageBox.confirm("Confirm delete this condition?", "Warning", {
        confirmButtonText: "Confirm",
        cancelButtonText: "Cancel",
        type: "warning"
      });
      const res = await deleteCondition({ ids: [id] });
      if (res.success) {
        ElMessage.success("Deleted successfully");
        await loadConditions();
      }
    } catch (error) {
      if (error !== "cancel") {
        ElMessage.error("Delete failed");
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
        <!-- Left condition list -->
        <div class="w-[350px] border-r flex flex-col">
          <div class="p-4 border-b">
            <el-button
              type="primary"
              size="small"
              :disabled="!canCreate"
              @click="handleCreate"
            >
              Add Condition
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

        <!-- Right detail/description -->
        <div class="flex-1 p-4">
          <div v-if="selectedCondition" class="condition-detail">
            <!-- Condition detail display -->
          </div>
          <div v-else class="text-center py-10 text-gray-500">
            Select a condition or create a new one
          </div>
        </div>

        <!-- Condition form dialog -->
        <ConditionForm ref="formRef" @success="loadConditions" />
      </div>
    </div>
  </template>
  ```
- **CHECKLIST**:
  - [ ] Use `<script setup lang="ts">` + `defineOptions`
  - [ ] Permission check using `hasPerms`
  - [ ] Error handling for data loading
  - [ ] Delete confirmation dialog

### Task 5: Update Constants and Router

- **ACTION**: Add permission codes and routes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_CONDITION_VIEW = "perm:condition:view";
  export const PERM_CONDITION_CREATE = "perm:condition:create";
  export const PERM_CONDITION_UPDATE = "perm:condition:update";
  export const PERM_CONDITION_DELETE = "perm:condition:delete";

  // router/modules/perm.ts
  {
    path: "/perm/condition",
    name: "PermCondition",
    component: () => import("@/views/perm/condition/index.vue"),
    meta: {
      title: "Permission Condition",
      auths: [PERM_CODES.PERM_CONDITION_VIEW]
    }
  }
  ```

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
| Condition list | Visit `/perm/condition` | Display condition list |
| Condition create | Submit form | Create success, list refresh |
| Condition delete | Click delete | Confirm then delete success |

---

## Validation Commands

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm build
```

EXPECT: Zero errors, build success

---

## Acceptance Criteria

- [ ] `api/perm/condition.ts` created with complete CRUD
- [ ] `views/perm/condition/` page components created
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Backend API changes | Low | High | Confirm API stability before development |
| JSON editor complexity | Low | Medium | Use textarea or simple JSON editor |
