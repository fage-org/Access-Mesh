# Subplan: Phase 3.2 - User-Role Assignment Page

## Summary
实现用户角色分配完整功能，包含用户选择、组织上下文选择、角色树多选、批量分配/回收等核心功能。

## User Story
作为权限管理员，我希望通过用户角色分配页面为用户分配角色并指定组织上下文，以便构建完整的用户-角色-组织关系并为权限生效提供基础。

## Problem → Solution
**当前状态**: frontend 缺少用户角色分配页面
**目标状态**: 完整的用户角色分配页面，支持用户选择、组织上下文、角色树多选、批量操作

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase3-permission-center-core-pages.plan.md`
- **Phase**: Phase 3.2 (User-Role Assignment)
- **Estimated Files**: 5 files (1 page + 4 components)
- **Prerequisite**: Phase 3.1 已完成（角色管理）

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  用户角色分配                                          │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  用户选择    │  │  角色分配树                     ││
│  │              │  │                                 ││
│  │  [搜索框]    │  │  [组织上下文选择]               ││
│  │  [用户列表]  │  │                                 ││
│  │  ├─ 张三    │  │  角色树(checkbox 多选)          ││
│  │  ├─ 李四    │  │  ├─ 系统角色                    ││
│  │  ├─ 王五    │  │  │  ├─ ☑ 系统管理员             ││
│  │              │  │  │  ├─ ☐ 普通用户               ││
│  │  [当前选中]  │  │  ├─ 业务角色                    ││
│  │  张三(已选)  │  │  │  ├─ ☑ 销售组长               ││
│  └──────────────┘  │                                 ││
│                    │  [已分配角色列表]                ││
│                    │  ├─ 系统管理员 [组织:总部]      ││
│                    │  ├─ 销售组长 [组织:销售部]      ││
│                    │  [移除]                          ││
│                    │                                 ││
│                    │  [批量分配] [批量回收]           ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 用户选择 | 左侧用户列表 | 点击选择用户，支持搜索 |
| 组织上下文选择 | 下拉选择 | 角色分配需在组织上下文生效 |
| 角色树多选 | checkbox 多选 | 显示所有可选角色，已分配角色勾选 |
| 批量分配 | 批量勾选角色 | 分配多个角色到同一用户 |
| 批量回收 | 已分配列表操作 | 移除已分配角色 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `plan/permission-center/api-contract.md` | 198-212 | 用户角色 API 契约 |
| P0 | `plan/frontend-pages.md` | 161-179 | 用户角色分配流程 |
| P0 | `plan/permission-center/core-flows.md` | 83-104 | 场景三:用户角色分配流程 |
| P1 | `frontend/src/views/system/user/index.vue` | 全文(Phase 2.1 创建) | 用户管理页参考 |

---

## Patterns to Mirror

### USER_ROLE_ASSIGN_PATTERN
// SOURCE: plan/frontend-pages.md:161-179
```
用户角色分配流程:
1. 选择用户
2. 选择组织上下文(角色分配需在组织上下文)
3. 展示角色树(checkbox 多选)
4. 批量分配/回收
```
**模式要点**: 用户选择 + 组织上下文 + 角色树多选

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/perm/userRole.ts` | CREATE | 用户角色 API 接口 |
| `frontend/src/views/perm/user-role/index.vue` | CREATE | 用户角色分配主页面 |
| `frontend/src/views/perm/user-role/components/UserSelector.vue` | CREATE | 用户选择组件 |
| `frontend/src/views/perm/user-role/components/OrgContextSelector.vue` | CREATE | 组织上下文选择 |
| `frontend/src/views/perm/user-role/components/RoleAssignTree.vue` | CREATE | 角色分配树组件 |

---

## Step-by-Step Tasks

### Task 1: 创建用户角色 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/userRole.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface UserRoleItem {
    id: number;
    userId: number;
    username: string;
    roleId: number;
    roleName: string;
    roleTypeCode: string;
    orgId: number;
    orgName: string;
    createTime: string;
  }

  export interface UserRoleListResult {
    code: number;
    message: string;
    data: Array<UserRoleItem>;
  }

  export interface UserRoleAssignRequest {
    userId: number;
    roleIds: Array<number>;
    orgId: number; // 组织上下文必填
  }

  export interface UserRoleRevokeRequest {
    ids: Array<number>; // 用户角色关系 ID
  }

  // ========== API 函数 ==========

  /** 查询用户角色关系 */
  export const getUserRoleList = (data: { userId: number }) => {
    return http.request<UserRoleListResult>("post", "/api/perm/user-role/list", { data });
  };

  /** 批量分配角色 */
  export const assignRoles = (data: UserRoleAssignRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/user-role/assign", { data });
  };

  /** 批量回收角色 */
  export const revokeRoles = (data: UserRoleRevokeRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/user-role/revoke", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 
  - 角色分配必须指定 orgId（组织上下文）
  - revoke 使用用户角色关系 ID，不是 roleId
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 2: 创建用户角色分配主页面
- **ACTION**: 新建 `frontend/src/views/perm/user-role/index.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getUserRoleList, assignRoles, revokeRoles, type UserRoleItem } from "@/api/perm/userRole";
  import UserSelector from "./components/UserSelector.vue";
  import OrgContextSelector from "./components/OrgContextSelector.vue";
  import RoleAssignTree from "./components/RoleAssignTree.vue";

  defineOptions({
    name: "PermUserRole"
  });

  const selectedUserId = ref<number | null>(null);
  const selectedOrgId = ref<number | null>(null);
  const assignedRoles = ref<Array<UserRoleItem>>([]);
  const loading = ref(false);

  const handleUserSelect = async (userId: number) => {
    selectedUserId.value = userId;
    loadAssignedRoles();
  };

  const handleOrgSelect = (orgId: number) => {
    selectedOrgId.value = orgId;
  };

  const loadAssignedRoles = async () => {
    if (!selectedUserId.value) return;

    loading.value = true;
    try {
      const res = await getUserRoleList({ userId: selectedUserId.value });
      if (res.success) {
        assignedRoles.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载已分配角色失败");
    } finally {
      loading.value = false;
    }
  };

  const handleAssignRoles = async (roleIds: Array<number>) => {
    if (!selectedUserId.value || !selectedOrgId.value) {
      ElMessage.warning("请先选择用户和组织上下文");
      return;
    }

    try {
      const res = await assignRoles({
        userId: selectedUserId.value,
        roleIds: roleIds,
        orgId: selectedOrgId.value
      });
      if (res.success) {
        ElMessage.success("分配成功");
        loadAssignedRoles();
      }
    } catch (error) {
      ElMessage.error("分配失败");
    }
  };

  const handleRevokeRole = async (userRoleId: number) => {
    try {
      await ElMessageBox.confirm("确认回收该角色?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await revokeRoles({ ids: [userRoleId] });
      if (res.success) {
        ElMessage.success("回收成功");
        loadAssignedRoles();
      }
    } catch (error) {
      // 用户取消不处理
    }
  };

  onMounted(() => {
    // 初始化加载用户列表
  });
  </script>

  <template>
    <div class="flex h-full">
      <!-- 左侧用户选择 -->
      <UserSelector class="w-[260px] border-r" @select="handleUserSelect" />

      <!-- 右侧角色分配 -->
      <div class="flex-1 p-4">
        <!-- 组织上下文选择 -->
        <OrgContextSelector
          :selected-org-id="selectedOrgId"
          @select="handleOrgSelect"
          class="mb-4"
        />

        <!-- 角色分配树 -->
        <RoleAssignTree
          :assigned-roles="assignedRoles"
          :selected-org-id="selectedOrgId"
          @assign="handleAssignRoles"
        />

        <!-- 已分配角色列表 -->
        <div class="mt-4">
          <h4 class="mb-2">已分配角色</h4>
          <el-table :data="assignedRoles" v-loading="loading" border stripe>
            <el-table-column prop="roleName" label="角色名称" />
            <el-table-column prop="orgName" label="组织上下文" />
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleRevokeRole(row.id)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: VUE_PAGE_PATTERN
- **IMPORTS**: userRole API, components
- **GOTCHA**: 
  - 用户选择和组织上下文选择是前置条件
  - 分配角色需同时传递 userId + roleIds + orgId
  - 移除角色使用用户角色关系 ID
- **VALIDATE**: 页面布局正确，角色分配流程正常

### Task 3: 创建用户选择组件
- **ACTION**: 新建 `frontend/src/views/perm/user-role/components/UserSelector.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, onMounted } from "vue";
  import { getUserPage, type UserItem } from "@/api/admin/user";

  defineOptions({
    name: "UserSelector"
  });

  const emit = defineEmits<{
    select: [userId: number];
  }>();

  const loading = ref(false);
  const userList = ref<Array<UserItem>>([]);
  const selectedUserId = ref<number | null>(null);
  const searchForm = reactive({
    username: "",
    nickname: ""
  });

  const loadUsers = async () => {
    loading.value = true;
    try {
      const res = await getUserPage({
        pageNum: 1,
        pageSize: 100,
        username: searchForm.username,
        nickname: searchForm.nickname
      });
      if (res.success) {
        userList.value = res.data.items;
      }
    } catch (error) {
      console.error("加载用户列表失败");
    } finally {
      loading.value = false;
    }
  };

  const handleUserClick = (user: UserItem) => {
    selectedUserId.value = user.id;
    emit("select", user.id);
  };

  const handleSearch = () => {
    loadUsers();
  };

  onMounted(() => {
    loadUsers();
  });
  </script>

  <template>
    <div class="user-selector">
      <div class="p-4 border-b">
        <el-input
          v-model="searchForm.username"
          placeholder="搜索用户名"
          clearable
          class="mb-2"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" size="small" @click="handleSearch">搜索</el-button>
      </div>

      <el-scrollbar class="flex-1">
        <div v-loading="loading">
          <div
            v-for="user in userList"
            :key="user.id"
            class="p-3 border-b cursor-pointer hover:bg-gray-50"
            :class="{ 'bg-blue-50': user.id === selectedUserId }"
            @click="handleUserClick(user)"
          >
            <div class="flex items-center gap-2">
              <el-avatar :src="user.avatar" :size="32" />
              <div>
                <div class="font-medium">{{ user.nickname }}</div>
                <div class="text-gray-500 text-sm">{{ user.username }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div v-if="userList.length === 0 && !loading" class="text-center py-10 text-gray-500">
        暂无用户
      </div>
    </div>
  </template>

  <style scoped lang="scss">
  .user-selector {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  </style>
  ```
- **MIRROR**: Vue Component Pattern
- **IMPORTS**: ref, reactive, onMounted from vue, user API
- **GOTCHA**: 
  - 使用 getUserPage 加载用户列表
  - 选中用户高亮显示
  - pageSize=100 显示所有用户（不分页）
- **VALIDATE**: 用户列表正确显示，搜索功能正常

### Task 4: 创建组织上下文选择组件
- **ACTION**: 新建 `frontend/src/views/perm/user-role/components/OrgContextSelector.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { getOrgTree, type OrgNode } from "@/api/admin/org";

  defineOptions({
    name: "OrgContextSelector"
  });

  const props = defineProps<{
    selectedOrgId: number | null;
  }>();

  const emit = defineEmits<{
    select: [orgId: number];
  }>();

  const orgTreeData = ref<Array<OrgNode>>([]);
  const loading = ref(false);

  const defaultProps = {
    children: "children",
    label: "name"
  };

  const loadOrgTree = async () => {
    loading.value = true;
    try {
      const res = await getOrgTree();
      if (res.success) {
        orgTreeData.value = res.data;
      }
    } catch (error) {
      console.error("加载组织树失败");
    } finally {
      loading.value = false;
    }
  };

  const handleOrgSelect = (orgId: number) => {
    emit("select", orgId);
  };

  onMounted(() => {
    loadOrgTree();
  });
  </script>

  <template>
    <div class="org-context-selector">
      <div class="flex items-center gap-2">
        <span class="text-gray-600">组织上下文:</span>
        <el-tree-select
          v-model="props.selectedOrgId"
          :data="orgTreeData"
          :props="defaultProps"
          node-key="id"
          check-strictly
          placeholder="请选择组织上下文"
          clearable
          @change="handleOrgSelect"
        />
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Component Pattern
- **IMPORTS**: ref, onMounted from vue, org API
- **GOTCHA**: 
  - 使用 el-tree-select 选择组织
  - check-strictly 允许选择任意节点
  - 组织上下文必填（角色生效范围）
- **VALIDATE**: 组织树选择正确显示

### Task 5: 创建角色分配树组件
- **ACTION**: 新建 `frontend/src/views/perm/user-role/components/RoleAssignTree.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, computed, onMounted, watch } from "vue";
  import { ElMessage } from "element-plus";
  import { getRoleTree, type RoleNode } from "@/api/perm/role";
  import { type UserRoleItem } from "@/api/perm/userRole";

  defineOptions({
    name: "RoleAssignTree"
  });

  const props = defineProps<{
    assignedRoles: Array<UserRoleItem>;
    selectedOrgId: number | null;
  }>();

  const emit = defineEmits<{
    assign: [roleIds: Array<number>];
  }>();

  const roleTreeData = ref<Array<RoleNode>>([]);
  const checkedRoleIds = ref<Array<number>>([]);
  const loading = ref(false);

  const defaultProps = {
    children: "children",
    label: "name"
  };

  // 已分配角色 ID 列表
  const assignedRoleIds = computed(() => {
    return props.assignedRoles.map(r => r.roleId);
  });

  const loadRoleTree = async () => {
    loading.value = true;
    try {
      const res = await getRoleTree();
      if (res.success) {
        roleTreeData.value = res.data;
        // 初始化已分配角色勾选状态
        checkedRoleIds.value = assignedRoleIds.value;
      }
    } catch (error) {
      ElMessage.error("加载角色树失败");
    } finally {
      loading.value = false;
    }
  };

  const handleCheckChange = () => {
    // 获取新勾选的角色 ID（排除已分配的）
    const newCheckedIds = checkedRoleIds.value.filter(id => !assignedRoleIds.value.includes(id));

    if (newCheckedIds.length === 0) {
      ElMessage.warning("请勾选要分配的角色");
      return;
    }

    emit("assign", newCheckedIds);
  };

  watch(() => props.assignedRoles, () => {
    checkedRoleIds.value = assignedRoleIds.value;
  }, { immediate: true });

  onMounted(() => {
    loadRoleTree();
  });
  </script>

  <template>
    <div class="role-assign-tree">
      <h4 class="mb-2">角色选择</h4>
      <el-tree
        :data="roleTreeData"
        :props="defaultProps"
        node-key="id"
        show-checkbox
        default-expand-all
        :default-checked-keys="checkedRoleIds"
        :filter-node-method="filterNode"
        v-loading="loading"
        @check-change="handleCheckChange"
      >
        <template #default="{ node, data }">
          <div class="flex items-center gap-2">
            <span>{{ node.label }}</span>
            <el-tag :type="data.type === 'GROUP_ROLE' ? 'primary' : 'success'" size="small">
              {{ data.type === "GROUP_ROLE" ? "分组角色" : "基本角色" }}
            </el-tag>
          </div>
        </template>
      </el-tree>

      <el-button type="primary" class="mt-4" @click="handleCheckChange">批量分配勾选角色</el-button>
    </div>
  </template>
  ```
- **MIRROR**: TREE_COMPONENT_PATTERN
- **IMPORTS**: ref, computed, onMounted, watch from vue, role API, userRole API
- **GOTCHA**: 
  - 使用 show-checkbox 支持多选
  - default-checked-keys 初始化已分配角色勾选状态
  - 只分配新勾选的角色（排除已分配的）
- **VALIDATE**: 角色树正确显示，勾选状态正确，批量分配功能正常

---

## Acceptance Criteria
- [ ] 所有 5 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 用户选择功能正常
- [ ] 组织上下文选择功能正常
- [ ] 角色树多选功能正常
- [ ] 已分配角色列表正确显示
- [ ] 批量分配/回收功能正常

## Completion Checklist
- [ ] 使用 `<script setup lang="ts">` + `defineOptions`
- [ ] API 类型定义前置导出
- [ ] 使用 `@/` 别名导入
- [ ] 无硬编码字符串
- [ ] 无相对路径导入

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase3-permission-center-core-pages.plan.md`
**Confidence Score**: 8/10 — 功能明确，组织上下文逻辑复杂