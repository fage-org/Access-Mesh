# Subplan: Phase 3.3 - Role Permission Configuration Page (Three-Segment Authorization)

## Summary
实现角色权限配置完整功能，包含左侧资源树选择、右侧操作配置、条件配置、三段式授权保存等核心功能。

## User Story
作为权限管理员，我希望通过角色权限配置页面为角色配置资源访问权限和操作权限，以便实现三段式授权（角色 + 资源 + 操作）并控制角色访问范围。

## Problem → Solution
**当前状态**: frontend 缺少角色权限配置页面
**目标状态**: 完整的角色权限配置页面，支持资源树选择、操作配置、条件配置、三段式授权保存

## Metadata
- **Complexity**: Large
- **Parent Plan**: `frontend-phase3-permission-center-core-pages.plan.md`
- **Phase**: Phase 3.3 (Role Permission Configuration)
- **Estimated Files**: 8 files (1 page + 7 components)
- **Prerequisite**: Phase 3.1 已完成（角色管理）

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  角色权限配置 - 系统管理员                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  资源树选择  │  │  权限配置                       ││
│  │              │  │                                 ││
│  │  [业务域选择]│  │  资源详情:                      ││
│  │  [系统域]   │  │  名称: 用户管理                 ││
│  │              │  │  类型: MENU                     ││
│  │  [资源类型筛选]│  │  编码: sys:user                ││
│  │  [MENU] [API]│  │                                 ││
│  │              │  │  操作配置:                      ││
│  │  资源树      │  │  ☑ VIEW (查看)                  ││
│  │  ├─ 系统管理 │  │  ☑ MANAGE (管理)                ││
│  │  │  ├─ ☑ 用户│  │  ☐ CREATE (创建)                ││
│  │  │  ├─ ☐ 组织│  │  ☐ EDIT (编辑)                  ││
│  │  │  ├─ ☐ 菜单│  │  ☐ DELETE (删除)                ││
│  │              │  │                                 ││
│  │              │  │  条件配置(可选):                ││
│  │              │  │  [时间范围] [IP白名单]          ││
│  │              │  │                                 ││
│  │              │  │  [保存授权] [取消]               ││
│  └──────────────┘  │                                 ││
│                    │  已配置权限列表:                 ││
│                    │  ├─ 用户管理 [VIEW/MANAGE]      ││
│                    │  ├─ 组织管理 [VIEW]             ││
│                    │  [移除]                          ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Three-Segment Authorization Model
```
角色
+ 资源
+ 操作
= 权限
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 业务域选择 | 下拉选择 | 切换业务域显示不同资源树 |
| 资源类型筛选 | 多选筛选 | 按资源类型(MENU/API/BUTTON)筛选 |
| 资源树选择 | checkbox 多选 | 选择要授权的资源 |
| 操作配置 | checkbox 多选 | 配置资源可执行的操作 |
| 条件配置 | 可选配置 | 时间范围、IP白名单等 |
| 保存授权 | 批量保存 | 保存三段式授权关系 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `plan/permission-center/api-contract.md` | 165-182 | 资源与操作 API 契约 |
| P0 | `plan/permission-center/api-contract.md` | 206-211 | 角色权限配置 API 契约 |
| P0 | `plan/frontend-pages.md` | 684-700 | 角色权限配置页功能点 |
| P1 | `plan/permission-center/overview.md` | 39-48 | 资源与操作模型 |

---

## Patterns to Mirror

### PERMISSION_CONFIG_PATTERN
// SOURCE: plan/frontend-pages.md:684-700
```
角色权限配置(三段式):
左侧:资源树选择
- 业务域选择
- 资源类型筛选
- 资源树 checkbox 多选

右侧:权限配置
- 资源详情
- 操作配置(VIEW/MANAGE/EDIT等)
- 条件配置(可选)
```
**模式要点**: 资源树选择 + 操作配置 + 条件配置

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/perm/resource.ts` | CREATE | 资源 API 接口定义 |
| `frontend/src/api/perm/operation.ts` | CREATE | 操作权限 API 接口定义 |
| `frontend/src/api/perm/rolePermission.ts` | CREATE | 角色权限配置 API 接口 |
| `frontend/src/views/perm/role/permission.vue` | UPDATE | 角色权限配置页面完整实现 |
| `frontend/src/views/perm/role/components/ResourceTree.vue` | CREATE | 资源树选择组件 |
| `frontend/src/views/perm/role/components/OperationConfig.vue` | CREATE | 操作配置组件 |
| `frontend/src/views/perm/role/components/ConditionConfig.vue` | CREATE | 条件配置组件 |

---

## Step-by-Step Tasks

### Task 1: 创建资源 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/resource.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface ResourceNode {
    id: number;
    parentId: number | null;
    name: string;
    code: string;
    type: "MENU" | "API" | "BUTTON" | "DATA";
    typeDesc: string;
    domainCode: string;
    domainName: string;
    status: number;
    children?: Array<ResourceNode>;
  }

  export interface ResourceTreeResult {
    code: number;
    message: string;
    data: Array<ResourceNode>;
  }

  export interface ResourceDetailResult {
    code: number;
    message: string;
    data: ResourceNode;
  }

  // ========== API 函数 ==========

  /** 资源树(按业务域和资源类型筛选) */
  export const getResourceTree = (data: {
    domainCode?: string;
    resourceTypeCodes?: Array<string>;
  }) => {
    return http.request<ResourceTreeResult>("post", "/api/perm/resource-entity/tree", { data });
  };

  /** 资源详情 */
  export const getResourceDetail = (data: { id: number }) => {
    return http.request<ResourceDetailResult>("post", "/api/perm/resource-entity/detail", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 
  - 资源类型使用字符串 "MENU" | "API" | "BUTTON" | "DATA"
  - 资源树支持按业务域和资源类型筛选
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 2: 创建操作权限 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/operation.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface OperationItem {
    id: number;
    code: string;
    name: string;
    description: string;
    resourceTypeCode: string;
    bitPosition: number; // 位运算位置
    status: number;
  }

  export interface OperationListResult {
    code: number;
    message: string;
    data: Array<OperationItem>;
  }

  // ========== API 函数 ==========

  /** 操作权限列表(按资源类型查询) */
  export const getOperationList = (data: { resourceTypeCode: string }) => {
    return http.request<OperationListResult>("post", "/api/perm/operation-permission/list", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 操作权限按资源类型查询(不同资源类型有不同操作集)
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 3: 创建角色权限配置 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/rolePermission.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface RolePermissionItem {
    id: number;
    roleId: number;
    resourceId: number;
    resourceName: string;
    operationBits: number; // 位运算组合
    operations: Array<string>; // 操作码列表
    conditionId?: number;
    conditionName?: string;
    createTime: string;
  }

  export interface RolePermissionListResult {
    code: number;
    message: string;
    data: Array<RolePermissionItem>;
  }

  export interface RolePermissionSaveRequest {
    roleId: number;
    permissions: Array<{
      resourceId: number;
      operationCodes: Array<string>;
      conditionId?: number;
    }>;
  }

  export interface RolePermissionRevokeRequest {
    ids: Array<number>; // 角色权限关系 ID
  }

  // ========== API 函数 ==========

  /** 查询角色权限配置 */
  export const getRolePermissionList = (data: { roleId: number }) => {
    return http.request<RolePermissionListResult>("post", "/api/perm/role-resource-permission/list", { data });
  };

  /** 三段式批量保存授权 */
  export const saveRolePermissions = (data: RolePermissionSaveRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/role-resource-permission/save", { data });
  };

  /** 批量回收授权 */
  export const revokeRolePermissions = (data: RolePermissionRevokeRequest) => {
    return http.request<{ code: number; message: string }>("post", "/api/perm/role-resource-permission/revoke", { data });
  };
  ```
- **MIRROR**: PERMISSION_API_PATTERN
- **IMPORTS**: `http` from "@/utils/http"
- **GOTCHA**: 
  - 三段式保存授权传递 roleId + permissions 数组
  - permissions 包含 resourceId + operationCodes + conditionId
  - revoke 使用角色权限关系 ID
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 4: 完善角色权限配置页面
- **ACTION**: 更新 `frontend/src/views/perm/role/permission.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { useRoute } from "vue-router";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getRoleDetail, type RoleNode } from "@/api/perm/role";
  import { getRolePermissionList, saveRolePermissions, revokeRolePermissions, type RolePermissionItem } from "@/api/perm/rolePermission";
  import ResourceTree from "../components/ResourceTree.vue";
  import OperationConfig from "../components/OperationConfig.vue";
  import ConditionConfig from "../components/ConditionConfig.vue";

  defineOptions({
    name: "PermRolePermission"
  });

  const route = useRoute();
  const roleId = ref<number | null>(null);
  const roleDetail = ref<RoleNode | null>(null);
  const configuredPermissions = ref<Array<RolePermissionItem>>([]);
  const selectedResourceId = ref<number | null>(null);
  const selectedResourceDetail = ref<ResourceNode | null>(null);
  const selectedOperationCodes = ref<Array<string>>([]);
  const selectedConditionId = ref<number | null>(null);
  const loading = ref(false);

  const domainCode = ref<string>("");

  onMounted(async () => {
    const id = route.query.roleId as string;
    if (id) {
      roleId.value = Number(id);
      try {
        const res = await getRoleDetail({ id: roleId.value });
        if (res.success) {
          roleDetail.value = res.data;
          domainCode.value = res.data.domainCode;
        }
        loadConfiguredPermissions();
      } catch (error) {
        ElMessage.error("加载角色详情失败");
      }
    }
  });

  const loadConfiguredPermissions = async () => {
    if (!roleId.value) return;

    loading.value = true;
    try {
      const res = await getRolePermissionList({ roleId: roleId.value });
      if (res.success) {
        configuredPermissions.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载已配置权限失败");
    } finally {
      loading.value = false;
    }
  };

  const handleResourceSelect = (resourceId: number, detail: ResourceNode) => {
    selectedResourceId.value = resourceId;
    selectedResourceDetail.value = detail;
  };

  const handleOperationChange = (codes: Array<string>) => {
    selectedOperationCodes.value = codes;
  };

  const handleConditionChange = (conditionId: number | null) => {
    selectedConditionId.value = conditionId;
  };

  const handleSavePermission = async () => {
    if (!selectedResourceId.value || selectedOperationCodes.value.length === 0) {
      ElMessage.warning("请先选择资源和操作");
      return;
    }

    loading.value = true;
    try {
      const res = await saveRolePermissions({
        roleId: roleId.value!,
        permissions: [{
          resourceId: selectedResourceId.value,
          operationCodes: selectedOperationCodes.value,
          conditionId: selectedConditionId.value
        }]
      });
      if (res.success) {
        ElMessage.success("保存成功");
        loadConfiguredPermissions();
        // 清空选择
        selectedResourceId.value = null;
        selectedResourceDetail.value = null;
        selectedOperationCodes.value = [];
        selectedConditionId.value = null;
      }
    } catch (error) {
      ElMessage.error("保存失败");
    } finally {
      loading.value = false;
    }
  };

  const handleRevokePermission = async (permissionId: number) => {
    try {
      await ElMessageBox.confirm("确认移除该权限?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await revokeRolePermissions({ ids: [permissionId] });
      if (res.success) {
        ElMessage.success("移除成功");
        loadConfiguredPermissions();
      }
    } catch (error) {
      // 用户取消不处理
    }
  };
  </script>

  <template>
    <div class="role-permission">
      <div v-if="!roleDetail" class="text-center py-10 text-gray-500">
        请选择角色
      </div>

      <div v-else>
        <h3 class="mb-4">配置角色权限: {{ roleDetail.name }}</h3>

        <div class="flex h-[600px]">
          <!-- 左侧资源树选择 -->
          <ResourceTree
            :domain-code="domainCode"
            :configured-permissions="configuredPermissions"
            @select="handleResourceSelect"
            class="w-[300px] border-r"
          />

          <!-- 右侧权限配置 -->
          <div class="flex-1 p-4">
            <!-- 资源详情 -->
            <div v-if="selectedResourceDetail" class="mb-4">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="资源名称">{{ selectedResourceDetail.name }}</el-descriptions-item>
                <el-descriptions-item label="资源类型">{{ selectedResourceDetail.typeDesc }}</el-descriptions-item>
                <el-descriptions-item label="资源编码">{{ selectedResourceDetail.code }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <!-- 操作配置 -->
            <OperationConfig
              :resource-type="selectedResourceDetail?.type"
              :selected-codes="selectedOperationCodes"
              @change="handleOperationChange"
              class="mb-4"
            />

            <!-- 条件配置 -->
            <ConditionConfig
              :selected-condition-id="selectedConditionId"
              @change="handleConditionChange"
              class="mb-4"
            />

            <!-- 保存按钮 -->
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!selectedResourceId || selectedOperationCodes.length === 0"
              @click="handleSavePermission"
            >
              保存授权
            </el-button>

            <!-- 已配置权限列表 -->
            <div class="mt-4">
              <h4 class="mb-2">已配置权限</h4>
              <el-table :data="configuredPermissions" v-loading="loading" border stripe>
                <el-table-column prop="resourceName" label="资源名称" />
                <el-table-column label="操作权限">
                  <template #default="{ row }">
                    <el-tag v-for="op in row.operations" :key="op" class="mr-1">
                      {{ op }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="conditionName" label="条件" />
                <el-table-column label="操作">
                  <template #default="{ row }">
                    <el-button
                      type="danger"
                      link
                      size="small"
                      @click="handleRevokePermission(row.id)"
                    >
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: VUE_PAGE_PATTERN
- **IMPORTS**: useRoute, role API, rolePermission API, components
- **GOTCHA**: 
  - domainCode 从角色详情获取，用于资源树筛选
  - 三段式授权: resourceId + operationCodes + conditionId
  - 保存后清空选择状态
- **VALIDATE**: 页面布局正确，三段式授权流程正常

### Task 5: 创建资源树选择组件
- **ACTION**: 新建 `frontend/src/views/perm/role/components/ResourceTree.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, watch, onMounted } from "vue";
  import { getResourceTree, getResourceDetail, type ResourceNode } from "@/api/perm/resource";
  import { type RolePermissionItem } from "@/api/perm/rolePermission";

  defineOptions({
    name: "ResourceTree"
  });

  const props = defineProps<{
    domainCode: string;
    configuredPermissions: Array<RolePermissionItem>;
  }>();

  const emit = defineEmits<{
    select: [resourceId: number, detail: ResourceNode];
  }>();

  const resourceTreeData = ref<Array<ResourceNode>>([]);
  const loading = ref(false);
  const selectedNodeId = ref<number | null>(null);
  const filterForm = reactive({
    domainCode: "",
    resourceTypeCodes: ["MENU", "API"]
  });

  const defaultProps = {
    children: "children",
    label: "name"
  };

  const loadResourceTree = async () => {
    loading.value = true;
    try {
      const res = await getResourceTree({
        domainCode: filterForm.domainCode || props.domainCode,
        resourceTypeCodes: filterForm.resourceTypeCodes
      });
      if (res.success) {
        resourceTreeData.value = res.data;
      }
    } catch (error) {
      console.error("加载资源树失败");
    } finally {
      loading.value = false;
    }
  };

  const handleNodeClick = async (data: ResourceNode) => {
    selectedNodeId.value = data.id;
    try {
      const res = await getResourceDetail({ id: data.id });
      if (res.success) {
        emit("select", data.id, res.data);
      }
    } catch (error) {
      console.error("加载资源详情失败");
    }
  };

  const handleFilterChange = () => {
    loadResourceTree();
  };

  const isConfigured = (resourceId: number) => {
    return props.configuredPermissions.some(p => p.resourceId === resourceId);
  };

  watch(() => props.domainCode, () => {
    filterForm.domainCode = props.domainCode;
    loadResourceTree();
  }, { immediate: true });

  onMounted(() => {
    loadResourceTree();
  });
  </script>

  <template>
    <div class="resource-tree">
      <!-- 筛选栏 -->
      <div class="p-4 border-b">
        <el-checkbox-group v-model="filterForm.resourceTypeCodes" @change="handleFilterChange">
          <el-checkbox value="MENU">菜单</el-checkbox>
          <el-checkbox value="API">接口</el-checkbox>
          <el-checkbox value="BUTTON">按钮</el-checkbox>
          <el-checkbox value="DATA">数据</el-checkbox>
        </el-checkbox-group>
      </div>

      <!-- 资源树 -->
      <el-scrollbar class="flex-1">
        <el-tree
          :data="resourceTreeData"
          :props="defaultProps"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          v-loading="loading"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <div class="flex items-center justify-between w-full pr-2">
              <div class="flex items-center gap-2">
                <span class="truncate">{{ node.label }}</span>
                <el-tag :type="data.type === 'MENU' ? 'primary' : data.type === 'API' ? 'success' : 'warning'" size="small">
                  {{ data.type }}
                </el-tag>
              </div>
              <el-tag v-if="isConfigured(data.id)" type="info" size="small">已配置</el-tag>
            </div>
          </template>
        </el-tree>
      </el-scrollbar>
    </div>
  </template>

  <style scoped lang="scss">
  .resource-tree {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  </style>
  ```
- **MIRROR**: TREE_COMPONENT_PATTERN
- **IMPORTS**: ref, reactive, watch, onMounted from vue, resource API
- **GOTCHA**: 
  - 资源类型筛选使用 checkbox-group
  - 已配置资源显示"已配置"标签
  - 点击节点后加载详情并触发 select 事件
- **VALIDATE**: 资源树正确显示，筛选功能正常

### Task 6: 创建操作配置组件
- **ACTION**: 新建 `frontend/src/views/perm/role/components/OperationConfig.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, watch, onMounted } from "vue";
  import { getOperationList, type OperationItem } from "@/api/perm/operation";

  defineOptions({
    name: "OperationConfig"
  });

  const props = defineProps<{
    resourceType: "MENU" | "API" | "BUTTON" | "DATA" | null;
    selectedCodes: Array<string>;
  }>();

  const emit = defineEmits<{
    change: [codes: Array<string>];
  }>();

  const operationList = ref<Array<OperationItem>>([]);
  const checkedCodes = ref<Array<string>>([]);
  const loading = ref(false);

  const loadOperations = async () => {
    if (!props.resourceType) {
      operationList.value = [];
      return;
    }

    loading.value = true;
    try {
      const res = await getOperationList({ resourceTypeCode: props.resourceType });
      if (res.success) {
        operationList.value = res.data;
        // 初始化勾选状态
        checkedCodes.value = props.selectedCodes;
      }
    } catch (error) {
      console.error("加载操作列表失败");
    } finally {
      loading.value = false;
    }
  };

  const handleCheckChange = () => {
    emit("change", checkedCodes.value);
  };

  watch(() => props.resourceType, () => {
    loadOperations();
    checkedCodes.value = [];
  }, { immediate: true });

  watch(() => props.selectedCodes, () => {
    checkedCodes.value = props.selectedCodes;
  });
  </script>

  <template>
    <div class="operation-config">
      <h4 class="mb-2">操作配置</h4>
      <div v-if="!props.resourceType" class="text-gray-500">
        请先选择资源
      </div>
      <div v-else v-loading="loading">
        <el-checkbox-group v-model="checkedCodes" @change="handleCheckChange">
          <el-checkbox
            v-for="op in operationList"
            :key="op.code"
            :value="op.code"
          >
            {{ op.name }} ({{ op.code }})
          </el-checkbox>
        </el-checkbox-group>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Component Pattern
- **IMPORTS**: ref, watch, onMounted from vue, operation API
- **GOTCHA**: 
  - 操作列表按资源类型查询
  - 切换资源类型时清空勾选状态
  - checkbox-group 多选操作
- **VALIDATE**: 操作列表正确显示，勾选功能正常

### Task 7: 创建条件配置组件
- **ACTION**: 新建 `frontend/src/views/perm/role/components/ConditionConfig.vue`
- **IMPLEMENT**: Phase 3.3 先实现基础框架
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";

  defineOptions({
    name: "ConditionConfig"
  });

  const props = defineProps<{
    selectedConditionId: number | null;
  }>();

  const emit = defineEmits<{
    change: [conditionId: number | null];
  }>();

  const selectedCondition = ref<number | null>(null);

  const handleConditionChange = () => {
    emit("change", selectedCondition.value);
  };
  </script>

  <template>
    <div class="condition-config">
      <h4 class="mb-2">条件配置(可选)</h4>
      <div class="text-gray-500 mb-2">
        Phase 3.3 使用简单下拉,Phase 4 实现完整条件配置
      </div>
      <el-select
        v-model="selectedCondition"
        placeholder="选择权限条件"
        clearable
        @change="handleConditionChange"
      >
        <!-- Phase 4 将实现完整条件列表 -->
        <el-option label="时间范围限制" value="1" />
        <el-option label="IP白名单" value="2" />
      </el-select>
    </div>
  </template>
  ```
- **MIRROR**: Vue Component Pattern(基础版)
- **IMPORTS**: ref from vue
- **GOTCHA**: Phase 3.3 只实现基础框架，Phase 4 详细实现
- **VALIDATE**: 条件选择正确显示

---

## Acceptance Criteria
- [ ] 所有 7 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 资源树正确显示和筛选
- [ ] 操作配置正确显示(按资源类型)
- [ ] 条件配置基础框架正常
- [ ] 三段式授权保存功能正常
- [ ] 已配置权限列表正确显示
- [ ] 移除权限功能正常

## Completion Checklist
- [ ] 使用 `<script setup lang="ts">` + `defineOptions`
- [ ] API 类型定义前置导出
- [ ] 使用 `@/` 别名导入
- [ ] 无硬编码字符串
- [ ] 无相对路径导入

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 三段式授权逻辑复杂 | Medium | High | 严格按 API 契约实现 |
| 操作配置按资源类型动态显示 | Medium | Medium | 使用 watch 监听 resourceType 变化 |
| 条件配置预留框架 | Low | Low | Phase 4 详细实现 |

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase3-permission-center-core-pages.plan.md`
**Confidence Score**: 7/10 — 三段式授权逻辑复杂，条件配置预留