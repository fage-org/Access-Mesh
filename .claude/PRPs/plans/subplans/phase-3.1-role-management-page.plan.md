# Subplan: Phase 3.1 - Role Management Page

## Summary
实现角色管理完整功能，包含左侧角色树展示、右侧角色详情面板、角色节点 CRUD、分组角色 extraRoles 配置、角色权限配置入口等核心功能。

## User Story
作为权限管理员，我希望通过角色管理页面管理角色结构和配置分组角色的额外角色，以便构建完整的角色体系并为权限配置提供角色基础。

## Problem → Solution
**当前状态**: frontend 缺少角色管理页面
**目标状态**: 完整的角色管理页面，支持角色树展示、节点 CRUD、角色类型区分、extraRoles 配置

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase3-permission-center-core-pages.plan.md`
- **Phase**: Phase 3.1 (Role Management)
- **Estimated Files**: 8 files (1 page + 1 subpage + 6 components)
- **Prerequisite**: Phase 2 已完成（用户/组织/菜单管理）

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  角色管理                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  角色树      │  │  角色详情面板                   ││
│  │              │  │                                 ││
│  │  [搜索框]    │  │  角色名称: 系统管理员          ││
│  │  [刷新按钮]  │  │  角色编码: sys_admin            ││
│  │              │  │  角色类型: [基本角色]           ││
│  │  角色树      │  │  业务域: 系统域                ││
│  │  ├─ 系统角色 │  │  状态: [启用]                  ││
│  │  │  ├─管理员 │  │                                 ││
│  │  │  ├─普通用户│  │  [编辑] [配置权限]             ││
│  │  │          │  │                                 ││
│  │  ├─ 业务角色 │  │  Tab 切换(分组角色专属):        ││
│  │  │  ├─销售组 │  │  [角色详情] [额外角色配置]     ││
│  │  │          │  │                                 ││
│  │              │  │  额外角色配置(Tab 页):          ││
│  │  [新增根角色]│  │  [可分配的基本角色列表]         ││
│  │              │  │  ├─ 查看权限                   ││
│  └──────────────┘  │  ├─ 编辑权限                   ││
│                    │  [添加基本角色]                 ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Role Type Distinction
| Type | Icon | Extra Roles | Description |
|------|------|-------------|-------------|
| GROUP_ROLE | 📁 folder | ✅ 可配置 | 分组角色，可配置额外基本角色 |
| BASIC_ROLE | 🎭 role | ❌ 不可配置 | 基本角色，直接授权 |

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 角色树点击 | 显示角色详情 | 根据类型显示详情面板 |
| 新增根角色 | 弹窗表单 | 创建顶级角色节点 |
| 新增子角色 | 弹窗表单 | 在选中节点下创建子角色 |
| 编辑角色 | 右侧面板编辑 | 点击节点后右侧面板可编辑 |
| 删除角色 | 二次确认 | 检查是否有子节点或用户关联 |
| 配置权限 | 跳转权限配置页 | 跳转到 role/permission 页面 |
| 额外角色配置 | Tab 页切换 | 分组角色专属，添加/移除基本角色 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `plan/permission-center/api-contract.md` | 128-164 | 角色管理 API 契约 |
| P0 | `plan/frontend-pages.md` | 596-667 | 角色管理页功能点 |
| P1 | `plan/permission-center/overview.md` | 29-38 | 角色模型定义 |
| P1 | `frontend/src/views/system/org/index.vue` | 全文(Phase 2.2 创建) | 组织管理页参考 |

---

## Patterns to Mirror

### PERMISSION_API_PATTERN
// SOURCE: plan/permission-center/api-contract.md:128-164
```typescript
POST /api/perm/abstract-role/tree     // 查询角色树
POST /api/perm/abstract-role/detail   // 查询角色详情
POST /api/perm/abstract-role/create   // 创建角色
POST /api/perm/abstract-role/update   // 更新角色
POST /api/perm/abstract-role/remove   // 删除角色
POST /api/perm/abstract-role/extra-roles/list    // 查询分组角色额外基本角色
POST /api/perm/abstract-role/extra-roles/add     // 分组角色添加基本角色
POST /api/perm/abstract-role/extra-roles/remove  // 分组角色移除基本角色
```
**模式要点**: 路径 `/api/perm/{resource}/{action}`，POST + JSON Body

### ROLE_TREE_PATTERN
// SOURCE: plan/frontend-pages.md:620-667
```
左侧角色树:
- 分组角色(GROUP_ROLE) → folder 图标
- 基本角色(BASIC_ROLE) → role 图标
- 支持树形层级

节点操作:
- 编辑(修改名称、状态)
- 删除
- 配置权限 → 跳转权限配置页
- 配置子角色(分组角色专属)
```
**模式要点**: 区分角色类型，树形展示，操作按钮自定义

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/perm/role.ts` | CREATE | 角色 API 接口定义 |
| `frontend/src/views/perm/role/index.vue` | CREATE | 角色管理主页面 |
| `frontend/src/views/perm/role/permission.vue` | CREATE | 角色权限配置页面(Phase 3.3 详细实现) |
| `frontend/src/views/perm/role/components/RoleTree.vue` | CREATE | 角色树组件 |
| `frontend/src/views/perm/role/components/RoleForm.vue` | CREATE | 角色创建/编辑表单 |
| `frontend/src/views/perm/role/components/RoleDetail.vue` | CREATE | 角色详情面板 |
| `frontend/src/views/perm/role/components/ExtraRolesPanel.vue` | CREATE | 分组角色额外角色配置 |

## NOT Building

- 角色树拖拽排序 - Phase 3.1 先实现基础树
- 角色权限配置完整功能 - 仅提供入口，Phase 3.3 详细实现
- 批量导入角色 - 后续 Phase 实现
- 角色层级限制 - Phase 3.1 不限制层级

---

## Step-by-Step Tasks

### Task 1: 创建角色 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/role.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface RoleNode {
    id: number;
    parentId: number | null;
    name: string;
    code: string;
    type: "GROUP_ROLE" | "BASIC_ROLE";
    typeDesc: string;
    domainCode: string;
    domainName: string;
    status: number;
    children?: Array<RoleNode>;
  }

  export interface RoleTreeResult {
    code: number;
    message: string;
    data: Array<RoleNode>;
  }

  export interface RoleDetailResult {
    code: number;
    message: string;
    data: RoleNode;
  }

  export interface RoleCreateRequest {
    parentId?: number;
    name: string;
    code: string;
    type: "GROUP_ROLE" | "BASIC_ROLE";
    domainCode: string;
    status?: number;
  }

  export interface RoleUpdateRequest {
    id: number;
    name?: string;
    status?: number;
  }

  export interface ExtraRoleItem {
    id: number;
    roleTypeCode: string;
    roleExternalId: string;
    roleName: string;
  }

  export interface ExtraRolesResult {
    code: number;
    message: string;
    data: Array<ExtraRoleItem>;
  }

  // ========== API 函数 ==========

  /** 角色树 */
  export const getRoleTree = (data?: { domainCode?: string }) => {
    return http.request<RoleTreeResult>("post", "/api/perm/abstract-role/tree", { data });
  };

  /** 角色详情 */
  export const getRoleDetail = (data: { id: number }) => {
    return http.request<RoleDetailResult>("post", "/api/perm/abstract-role/detail", { data });
  };

  /** 创建角色 */
  export const createRole = (data: RoleCreateRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/abstract-role/create", { data });
  };

  /** 更新角色 */
  export const updateRole = (data: RoleUpdateRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/abstract-role/update", { data });
  };

  /** 删除角色 */
  export const deleteRole = (data: { id: number }) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/abstract-role/remove", { data });
  };

  /** 查询分组角色额外基本角色 */
  export const getExtraRoles = (data: { roleId: number }) => {
    return http.request<ExtraRolesResult>("post", "/api/perm/abstract-role/extra-roles/list", { data });
  };

  /** 分组角色添加基本角色 */
  export const addExtraRole = (data: { roleId: number; basicRoleId: number }) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/abstract-role/extra-roles/add", { data });
  };

  /** 分组角色移除基本角色 */
  export const removeExtraRole = (data: { roleId: number; basicRoleId: number }) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/abstract-role/extra-roles/remove", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 
  - 角色类型使用字符串 "GROUP_ROLE" | "BASIC_ROLE"，不是数字
  - extraRoles API 仅适用于 GROUP_ROLE 类型
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 2: 创建角色管理主页面
- **ACTION**: 新建 `frontend/src/views/perm/role/index.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { useRouter } from "vue-router";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getRoleTree, getRoleDetail, deleteRole, type RoleNode } from "@/api/perm/role";
  import RoleTree from "./components/RoleTree.vue";
  import RoleDetail from "./components/RoleDetail.vue";
  import RoleForm from "./components/RoleForm.vue";

  defineOptions({
    name: "PermRole"
  });

  const router = useRouter();
  const treeData = ref<Array<RoleNode>>([]);
  const selectedRoleId = ref<number | null>(null);
  const selectedRoleDetail = ref<RoleNode | null>(null);
  const loading = ref(false);
  const roleFormRef = ref();

  const loadRoleTree = async () => {
    loading.value = true;
    try {
      const res = await getRoleTree();
      if (res.success) {
        treeData.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载角色树失败");
    } finally {
      loading.value = false;
    }
  };

  const handleRoleNodeClick = async (roleId: number) => {
    selectedRoleId.value = roleId;
    try {
      const res = await getRoleDetail({ id: roleId });
      if (res.success) {
        selectedRoleDetail.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载角色详情失败");
    }
  };

  const handleCreateRoot = () => {
    roleFormRef.value.openDialog(null);
  };

  const handleCreateChild = (parentId: number) => {
    roleFormRef.value.openDialog(parentId);
  };

  const handleEdit = () => {
    if (selectedRoleDetail.value) {
      roleFormRef.value.openDialog(selectedRoleDetail.value.parentId, selectedRoleDetail.value);
    }
  };

  const handleDelete = async (roleId: number) => {
    try {
      await ElMessageBox.confirm("确认删除该角色?删除后子角色将一并删除", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await deleteRole({ id: roleId });
      if (res.success) {
        ElMessage.success("删除成功");
        loadRoleTree();
        selectedRoleId.value = null;
        selectedRoleDetail.value = null;
      }
    } catch (error) {
      // 用户取消不处理
    }
  };

  const handleConfigPermission = () => {
    if (selectedRoleId.value) {
      router.push({
        path: "/perm/role/permission",
        query: { roleId: selectedRoleId.value }
      });
    }
  };

  const handleFormSuccess = () => {
    loadRoleTree();
  };

  onMounted(() => {
    loadRoleTree();
  });
  </script>

  <template>
    <div class="flex h-full">
      <!-- 左侧角色树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button type="primary" size="small" @click="handleCreateRoot">
            新增根角色
          </el-button>
        </div>
        <RoleTree
          :data="treeData"
          :loading="loading"
          @node-click="handleRoleNodeClick"
          @create-child="handleCreateChild"
          @delete="handleDelete"
        />
      </div>

      <!-- 右侧详情区域 -->
      <div class="flex-1 p-4">
        <RoleDetail
          :role-detail="selectedRoleDetail"
          :role-id="selectedRoleId"
          @edit="handleEdit"
          @config-permission="handleConfigPermission"
        />
      </div>

      <!-- 角色表单弹窗 -->
      <RoleForm
        ref="roleFormRef"
        @success="handleFormSuccess"
      />
    </div>
  </template>
  ```
- **MIRROR**: VUE_PAGE_PATTERN
- **IMPORTS**: vue-router, role API, components
- **GOTCHA**: 
  - 配置权限按钮跳转到 role/permission 页面
  - 新增子角色时传递 parentId
  - 编辑时传递 roleDetail 数据
- **VALIDATE**: 页面布局正确，角色树渲染正常

### Task 3: 创建角色树组件
- **ACTION**: 新建 `frontend/src/views/perm/role/components/RoleTree.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";
  import { type RoleNode } from "@/api/perm/role";

  defineOptions({
    name: "RoleTree"
  });

  const props = defineProps<{
    data: Array<RoleNode>;
    loading: boolean;
  }>();

  const emit = defineEmits<{
    nodeClick: [roleId: number];
    createChild: [parentId: number];
    delete: [roleId: number];
  }>();

  const selectedNodeId = ref<number | null>(null);
  const filterText = ref("");

  const defaultProps = {
    children: "children",
    label: "name"
  };

  const handleNodeClick = (data: RoleNode) => {
    selectedNodeId.value = data.id;
    emit("nodeClick", data.id);
  };

  const handleCreateChild = (data: RoleNode) => {
    emit("createChild", data.id);
  };

  const handleDelete = (data: RoleNode) => {
    emit("delete", data.id);
  };

  const getRoleTypeIcon = (type: string) => {
    return type === "GROUP_ROLE" ? "Folder" : "User";
  };

  const getRoleTypeTag = (type: string) => {
    return type === "GROUP_ROLE" 
      ? { text: "分组角色", type: "primary" } 
      : { text: "基本角色", type: "success" };
  };
  </script>

  <template>
    <div class="role-tree-container">
      <el-input
        v-model="filterText"
        placeholder="搜索角色名称"
        clearable
        class="mb-2"
      />

      <el-scrollbar class="flex-1">
        <el-tree
          :data="props.data"
          :props="defaultProps"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          v-loading="props.loading"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <div class="flex items-center justify-between w-full pr-2">
              <div class="flex items-center gap-2">
                <IconifyIconOffline :icon="getRoleTypeIcon(data.type)" />
                <span class="truncate">{{ node.label }}</span>
                <el-tag :type="getRoleTypeTag(data.type).type" size="small">
                  {{ getRoleTypeTag(data.type).text }}
                </el-tag>
              </div>
              <div class="flex gap-1 opacity-0 group-hover:opacity-100">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click.stop="handleCreateChild(data)"
                >
                  新增
                </el-button>
                <el-button
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
      </el-scrollbar>
    </div>
  </template>

  <style scoped lang="scss">
  .role-tree-container {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  </style>
  ```
- **MIRROR**: TREE_COMPONENT_PATTERN
- **IMPORTS**: ref from vue, RoleNode type, IconifyIconOffline
- **GOTCHA**: 
  - 角色类型图标区分(Folder vs User)
  - 角色类型标签显示(分组角色 vs 基本角色)
  - 操作按钮 hover 时显示
- **VALIDATE**: 角色树正确渲染，类型图标和标签正确显示

### Task 4: 创建角色详情面板
- **ACTION**: 新建 `frontend/src/views/perm/role/components/RoleDetail.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";
  import { type RoleNode } from "@/api/perm/role";
  import ExtraRolesPanel from "./ExtraRolesPanel.vue";

  defineOptions({
    name: "RoleDetail"
  });

  const props = defineProps<{
    roleDetail: RoleNode | null;
    roleId: number | null;
  }>();

  const emit = defineEmits<{
    edit: [];
    configPermission: [];
  }>();

  const activeTab = ref("detail");

  const handleEdit = () => {
    emit("edit");
  };

  const handleConfigPermission = () => {
    emit("configPermission");
  };
  </script>

  <template>
    <div class="role-detail">
      <div v-if="!props.roleDetail" class="text-center py-10 text-gray-500">
        请选择角色节点
      </div>

      <div v-else>
        <!-- 操作按钮 -->
        <div class="flex gap-2 mb-4">
          <el-button type="primary" size="small" @click="handleEdit">编辑</el-button>
          <el-button type="success" size="small" @click="handleConfigPermission">配置权限</el-button>
        </div>

        <!-- Tab 切换(分组角色专属) -->
        <el-tabs v-model="activeTab" v-if="props.roleDetail.type === 'GROUP_ROLE'">
          <el-tab-pane label="角色详情" name="detail">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="角色名称">{{ props.roleDetail.name }}</el-descriptions-item>
              <el-descriptions-item label="角色编码">{{ props.roleDetail.code }}</el-descriptions-item>
              <el-descriptions-item label="角色类型">{{ props.roleDetail.typeDesc }}</el-descriptions-item>
              <el-descriptions-item label="业务域">{{ props.roleDetail.domainName }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="props.roleDetail.status === 1 ? 'success' : 'danger'">
                  {{ props.roleDetail.status === 1 ? "启用" : "停用" }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="额外角色配置" name="extraRoles">
            <ExtraRolesPanel :role-id="props.roleId" />
          </el-tab-pane>
        </el-tabs>

        <!-- 基本角色只显示详情 -->
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="角色名称">{{ props.roleDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="角色编码">{{ props.roleDetail.code }}</el-descriptions-item>
          <el-descriptions-item label="角色类型">{{ props.roleDetail.typeDesc }}</el-descriptions-item>
          <el-descriptions-item label="业务域">{{ props.roleDetail.domainName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="props.roleDetail.status === 1 ? 'success' : 'danger'">
              {{ props.roleDetail.status === 1 ? "启用" : "停用" }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Panel Pattern
- **IMPORTS**: ref from vue, RoleNode type, ExtraRolesPanel component
- **GOTCHA**: 
  - 分组角色显示 Tab 切换（角色详情 + 额外角色配置）
  - 基本角色只显示详情 Descriptions
  - 编辑和配置权限按钮触发事件
- **VALIDATE**: 角色详情正确显示，Tab 切换正常

### Task 5: 创建分组角色额外角色配置面板
- **ACTION**: 新建 `frontend/src/views/perm/role/components/ExtraRolesPanel.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted, watch } from "vue";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getRoleTree, getExtraRoles, addExtraRole, removeExtraRole, type RoleNode, type ExtraRoleItem } from "@/api/perm/role";

  defineOptions({
    name: "ExtraRolesPanel"
  });

  const props = defineProps<{
    roleId: number | null;
  }>();

  const loading = ref(false);
  const extraRolesList = ref<Array<ExtraRoleItem>>([]);
  const allBasicRoles = ref<Array<RoleNode>>([]);
  const selectedBasicRoleId = ref<number | null>(null);

  // 加载所有基本角色(用于添加选择)
  const loadAllBasicRoles = async () => {
    try {
      const res = await getRoleTree();
      if (res.success) {
        // 过滤出基本角色
        allBasicRoles.value = flattenRoles(res.data).filter(r => r.type === "BASIC_ROLE");
      }
    } catch (error) {
      ElMessage.error("加载基本角色列表失败");
    }
  };

  // 加载分组角色已配置的额外角色
  const loadExtraRoles = async () => {
    if (!props.roleId) return;

    loading.value = true;
    try {
      const res = await getExtraRoles({ roleId: props.roleId });
      if (res.success) {
        extraRolesList.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载额外角色配置失败");
    } finally {
      loading.value = false;
    }
  };

  // 添加基本角色
  const handleAddExtraRole = async () => {
    if (!selectedBasicRoleId.value) {
      ElMessage.warning("请选择要添加的基本角色");
      return;
    }

    try {
      const res = await addExtraRole({
        roleId: props.roleId!,
        basicRoleId: selectedBasicRoleId.value
      });
      if (res.success) {
        ElMessage.success("添加成功");
        selectedBasicRoleId.value = null;
        loadExtraRoles();
      }
    } catch (error) {
      ElMessage.error("添加失败");
    }
  };

  // 移除基本角色
  const handleRemoveExtraRole = async (basicRoleId: number) => {
    try {
      await ElMessageBox.confirm("确认移除该基本角色?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await removeExtraRole({
        roleId: props.roleId!,
        basicRoleId: basicRoleId
      });
      if (res.success) {
        ElMessage.success("移除成功");
        loadExtraRoles();
      }
    } catch (error) {
      // 用户取消不处理
    }
  };

  // 扁平化角色树(用于下拉选择)
  const flattenRoles = (tree: Array<RoleNode>): Array<RoleNode> => {
    const result: Array<RoleNode> = [];
    const traverse = (nodes: Array<RoleNode>) => {
      nodes.forEach(node => {
        result.push(node);
        if (node.children) {
          traverse(node.children);
        }
      });
    };
    traverse(tree);
    return result;
  };

  watch(() => props.roleId, () => {
    loadExtraRoles();
    loadAllBasicRoles();
  }, { immediate: true });
  </script>

  <template>
    <div class="extra-roles-panel">
      <!-- 添加基本角色 -->
      <div class="flex gap-2 mb-4">
        <el-select
          v-model="selectedBasicRoleId"
          placeholder="选择基本角色"
          clearable
          filterable
          class="w-[300px]"
        >
          <el-option
            v-for="role in allBasicRoles"
            :key="role.id"
            :label="role.name"
            :value="role.id"
          />
        </el-select>
        <el-button type="primary" size="small" @click="handleAddExtraRole">添加</el-button>
      </div>

      <!-- 已配置的额外角色列表 -->
      <el-table :data="extraRolesList" v-loading="loading" border stripe>
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="roleTypeCode" label="角色编码" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              @click="handleRemoveExtraRole(row.id)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="extraRolesList.length === 0 && !loading" class="text-center py-10 text-gray-500">
        暂未配置额外基本角色
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Table Pattern
- **IMPORTS**: ref, onMounted, watch from vue, role API
- **GOTCHA**: 
  - 只加载 BASIC_ROLE 类型角色用于添加选择
  - 需要扁平化角色树（flattenRoles）用于下拉选择
  - 移除时二次确认
- **VALIDATE**: 额外角色列表正确显示，添加/移除功能正常

### Task 6: 创建角色表单弹窗
- **ACTION**: 新建 `frontend/src/views/perm/role/components/RoleForm.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, watch } from "vue";
  import { ElMessage, type FormInstance, type FormRules } from "element-plus";
  import { createRole, updateRole, type RoleNode, type RoleCreateRequest, type RoleUpdateRequest } from "@/api/perm/role";

  defineOptions({
    name: "RoleForm"
  });

  const emit = defineEmits<{
    success: [];
  }>();

  const dialogVisible = ref(false);
  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const isEdit = ref(false);
  const parentId = ref<number | null>(null);

  const form = reactive<RoleCreateRequest | RoleUpdateRequest>({
    parentId: undefined,
    name: "",
    code: "",
    type: "BASIC_ROLE",
    domainCode: "",
    status: 1
  });

  const rules: FormRules = {
    name: [
      { required: true, message: "请输入角色名称", trigger: "blur" }
    ],
    code: [
      { required: true, message: "请输入角色编码", trigger: "blur" },
      { pattern: /^[A-Z_]+$/, message: "编码只能包含大写字母和下划线", trigger: "blur" }
    ],
    type: [
      { required: true, message: "请选择角色类型", trigger: "change" }
    ],
    domainCode: [
      { required: true, message: "请选择业务域", trigger: "change" }
    ]
  };

  // 打开弹窗
  const openDialog = (parent: number | null, editData?: RoleNode) => {
    dialogVisible.value = true;
    parentId.value = parent;

    if (editData) {
      isEdit.value = true;
      Object.assign(form, editData);
    } else {
      isEdit.value = false;
      resetForm();
      form.parentId = parent;
    }
  };

  const resetForm = () => {
    form.parentId = undefined;
    form.name = "";
    form.code = "";
    form.type = "BASIC_ROLE";
    form.domainCode = "";
    form.status = 1;
  };

  const handleSubmit = async () => {
    if (!formRef.value) return;

    await formRef.value.validate(async (valid) => {
      if (!valid) return;

      loading.value = true;
      try {
        if (isEdit.value) {
          const res = await updateRole({ id: form.id!, name: form.name, status: form.status });
          if (res.success) {
            ElMessage.success("更新成功");
            dialogVisible.value = false;
            emit("success");
          }
        } else {
          const res = await createRole(form as RoleCreateRequest);
          if (res.success) {
            ElMessage.success("创建成功");
            dialogVisible.value = false;
            emit("success");
          }
        }
      } catch (error) {
        ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
      } finally {
        loading.value = false;
      }
    });
  };

  watch(dialogVisible, (val) => {
    if (!val) {
      resetForm();
      formRef.value?.resetFields();
    }
  });

  defineExpose({
    openDialog
  });
  </script>

  <template>
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑角色' : '新增角色'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="上级角色" v-if="!isEdit">
          <el-input :value="parentId ? '已选择父节点' : '根角色'" disabled />
        </el-form-item>

        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入角色编码(大写字母+下划线)" :disabled="isEdit" />
        </el-form-item>

        <el-form-item label="角色类型" prop="type" v-if="!isEdit">
          <el-radio-group v-model="form.type">
            <el-radio value="GROUP_ROLE">分组角色</el-radio>
            <el-radio value="BASIC_ROLE">基本角色</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="业务域" prop="domainCode" v-if="!isEdit">
          <el-input v-model="form.domainCode" placeholder="Phase 3.1 使用简单输入,Phase 3.2 实现业务域选择器" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </template>
  ```
- **MIRROR**: Vue Form Pattern
- **IMPORTS**: ref, reactive, watch from vue, FormInstance, FormRules from element-plus, role API
- **GOTCHA**: 
  - 编辑模式下角色编码和类型不可修改
  - 角色类型使用字符串 "GROUP_ROLE" | "BASIC_ROLE"
  - 业务域使用简单输入，Phase 3.2 实现选择器
- **VALIDATE**: 表单验证正确，提交逻辑完整

### Task 7: 创建角色权限配置页面(框架)
- **ACTION**: 新建 `frontend/src/views/perm/role/permission.vue`
- **IMPLEMENT**: Phase 3.1 只创建页面框架，Phase 3.3 详细实现
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { useRoute } from "vue-router";
  import { ElMessage } from "element-plus";
  import { getRoleDetail, type RoleNode } from "@/api/perm/role";

  defineOptions({
    name: "PermRolePermission"
  });

  const route = useRoute();
  const roleId = ref<number | null>(null);
  const roleDetail = ref<RoleNode | null>(null);
  const loading = ref(false);

  onMounted(async () => {
    const id = route.query.roleId as string;
    if (id) {
      roleId.value = Number(id);
      try {
        const res = await getRoleDetail({ id: roleId.value });
        if (res.success) {
          roleDetail.value = res.data;
        }
      } catch (error) {
        ElMessage.error("加载角色详情失败");
      }
    }
  });
  </script>

  <template>
    <div class="role-permission">
      <div v-if="!roleDetail" class="text-center py-10 text-gray-500">
        请选择角色
      </div>

      <div v-else>
        <h3 class="mb-4">配置角色权限: {{ roleDetail.name }}</h3>
        <div class="text-center py-10 text-gray-500">
          角色权限配置功能将在 Phase 3.3 详细实现
        </div>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Page Pattern(框架版)
- **IMPORTS**: useRoute, role API
- **GOTCHA**: Phase 3.1 只创建框架，Phase 3.3 实现完整功能
- **VALIDATE**: 页面框架正确显示

---

## Acceptance Criteria
- [ ] 所有 7 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 角色树正确显示和搜索
- [ ] 角色类型标签正确显示(分组角色 vs 基本角色)
- [ ] 角色详情面板正确显示
- [ ] 分组角色额外角色配置功能正常
- [ ] 角色 CRUD 功能正常
- [ ] 配置权限按钮跳转正常

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
**Confidence Score**: 8/10 — 功能明确，extraRoles 配置复杂