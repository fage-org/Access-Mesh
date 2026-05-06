# Subplan: Phase 2.3 - Menu Management Page

## Summary
实现菜单管理完整功能,包含左侧菜单树展示、右侧菜单详情表单、菜单节点 CRUD、菜单类型区分(目录/菜单/按钮)、路由配置等核心功能。

## User Story
作为系统管理员,我希望通过菜单管理页面管理菜单结构和配置路由信息,以便构建完整的菜单体系并为权限配置提供菜单基础。

## Problem → Solution
**当前状态**: frontend 缺少菜单管理页面
**目标状态**: 完整的菜单管理页面,支持树形展示、节点 CRUD、类型区分、路由配置

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase2-user-org-menu-pages.plan.md`
- **Phase**: Phase 2.3 (Menu Management)
- **Estimated Files**: 4 files (1 page + 3 components)
- **Prerequisite**: Phase 2.2 已完成(组织管理、菜单 API)

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  菜单管理                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  菜单树      │  │  菜单详情表单                   ││
│  │              │  │                                 ││
│  │  [搜索框]    │  │  菜单名称: [_______________]   ││
│  │              │  │  菜单类型: [目录/菜单/按钮]    ││
│  │  菜单树      │  │                                 ││
│  │  ├─ 系统管理│  │  路由路径: (菜单类型才有)      ││
│  │  │  ├─用户管│  │  [_______________]             ││
│  │  │  ├─组织管│  │                                 ││
│  │  │  ├─菜单管│  │  组件路径: (菜单类型才有)      ││
│  │  │          │  │  [_______________]             ││
│  │  │          │  │                                 ││
│  │              │  │  权限标识: (按钮类型才有)      ││
│  │  [新增根菜单]│  │  [_______________]             ││
│  │              │  │                                 ││
│  │              │  │  图标: [图标选择器]            ││
│  │              │  │  排序: [数字输入]              ││
│  │              │  │  是否显示: [开关]              ││
│  │              │  │  状态: [启用/停用]             ││
│  │              │  │                                 ││
│  │              │  │  [保存] [取消]                  ││
│  └──────────────┘  └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Menu Type Distinction
| Type | Fields | Description |
|------|--------|-------------|
| 目录 (type=0) | name, icon, sort, visible, status | 目录节点,不对应路由 |
| 菜单 (type=1) | name, path, component, icon, sort, visible, status | 菜单节点,对应路由 |
| 按钮 (type=2) | name, permCode, sort, status | 按钮权限,不对应路由 |

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 菜单树点击 | 显示菜单详情 | 根据类型显示不同表单字段 |
| 新增根菜单 | 弹窗表单 | 创建顶级菜单节点(默认目录类型) |
| 新增子菜单 | 弹窗表单 | 在选中节点下创建子菜单 |
| 编辑菜单 | 右侧表单编辑 | 类型不可修改 |
| 删除菜单 | 二次确认 | 检查是否有子节点 |
| 菜单类型切换 | 表单字段动态显示 | 不同类型显示不同字段 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `frontend/src/api/admin/menu.ts` | 全文(Phase 2.1 创建) | 菜单 API 接口定义 |
| P1 | Element Plus Tree 文档 | 全文 | el-tree 配置 |
| P1 | `frontend/src/views/system/org/components/OrgTree.vue` | 全文(Phase 2.2 创建) | 组织树参考 |

---

## Patterns to Mirror

### MENU_TYPE_FIELDS_PATTERN
// SOURCE: 菜单类型字段设计
```vue
<!-- 目录类型字段 -->
<el-form-item label="菜单名称">
  <el-input v-model="form.name" />
</el-form-item>
<el-form-item label="图标">
  <IconifyIconOffline :icon="form.icon" />
</el-form-item>

<!-- 菜单类型额外字段 -->
<el-form-item label="路由路径" v-if="form.type === 1">
  <el-input v-model="form.path" />
</el-form-item>
<el-form-item label="组件路径" v-if="form.type === 1">
  <el-input v-model="form.component" />
</el-form-item>

<!-- 按钮类型额外字段 -->
<el-form-item label="权限标识" v-if="form.type === 2">
  <el-input v-model="form.permCode" />
</el-form-item>
```
**模式要点**: 根据菜单类型动态显示表单字段

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/views/system/menu/index.vue` | CREATE | 菜单管理主页面 |
| `frontend/src/views/system/menu/components/MenuTree.vue` | CREATE | 菜单树组件 |
| `frontend/src/views/system/menu/components/MenuForm.vue` | CREATE | 菜单详情表单 |

## NOT Building

- 菜单树拖拽排序 - Phase 2.3 先实现基础树
- 图标选择器完整功能 - 使用简单输入,后续优化
- 菜单权限按钮配置 - Phase 2.3 只做基础 CRUD
- 菜单路由动态生成 - Phase 7 实现
- 批量导入菜单 - 后续 Phase 实现

---

## Step-by-Step Tasks

### Task 1: 创建菜单管理主页面
- **ACTION**: 新建 `frontend/src/views/system/menu/index.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getMenuTree, getMenuDetail, deleteMenu, type MenuNode } from "@/api/admin/menu";
  import MenuTree from "./components/MenuTree.vue";
  import MenuForm from "./components/MenuForm.vue";
  import { PERM_CODES } from "@/constants/permission";

  defineOptions({
    name: "SystemMenu"
  });

  const treeData = ref<Array<MenuNode>>([]);
  const selectedMenuId = ref<number | null>(null);
  const selectedMenuDetail = ref<MenuNode | null>(null);
  const loading = ref(false);

  const loadMenuTree = async () => {
    loading.value = true;
    try {
      const res = await getMenuTree();
      if (res.success) {
        treeData.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载菜单树失败");
    } finally {
      loading.value = false;
    }
  };

  const handleMenuNodeClick = async (menuId: number) => {
    selectedMenuId.value = menuId;
    try {
      const res = await getMenuDetail({ id: menuId });
      if (res.success) {
        selectedMenuDetail.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载菜单详情失败");
    }
  };

  const handleCreateRoot = () => {
    selectedMenuDetail.value = null;
    selectedMenuId.value = null;
  };

  const handleDeleteMenu = async (menuId: number) => {
    try {
      await ElMessageBox.confirm("确认删除该菜单?删除后子菜单将一并删除", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await deleteMenu({ id: menuId });
      if (res.success) {
        ElMessage.success("删除成功");
        loadMenuTree();
        selectedMenuId.value = null;
        selectedMenuDetail.value = null;
      }
    } catch (error) {
      // 用户取消不处理
    }
  };

  const handleFormSuccess = () => {
    loadMenuTree();
  };

  onMounted(() => {
    loadMenuTree();
  });
  </script>

  <template>
    <div class="flex h-full">
      <!-- 左侧菜单树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            v-perm="[PERM_CODES.SYS_MENU_CREATE]"
            @click="handleCreateRoot"
          >
            新增根菜单
          </el-button>
        </div>
        <MenuTree
          :data="treeData"
          :loading="loading"
          @node-click="handleMenuNodeClick"
          @delete="handleDeleteMenu"
        />
      </div>

      <!-- 右侧详情表单 -->
      <div class="flex-1 p-4">
        <MenuForm
          :menu-detail="selectedMenuDetail"
          :parent-id="selectedMenuId"
          @success="handleFormSuccess"
        />
      </div>
    </div>
  </template>
  ```
- **MIRROR**: VUE_PAGE_PATTERN
- **IMPORTS**: menu API, components, PERM_CODES
- **GOTCHA**: 删除菜单时提示子菜单一并删除
- **VALIDATE**: 页面布局正确,菜单树渲染正常

### Task 2: 创建菜单树组件
- **ACTION**: 新建 `frontend/src/views/system/menu/components/MenuTree.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";
  import { type MenuNode } from "@/api/admin/menu";

  defineOptions({
    name: "MenuTree"
  });

  const props = defineProps<{
    data: Array<MenuNode>;
    loading: boolean;
  }>();

  const emit = defineEmits<{
    nodeClick: [menuId: number];
    createChild: [parentId: number];
    delete: [menuId: number];
  }>();

  const selectedNodeId = ref<number | null>(null);
  const filterText = ref("");

  const defaultProps = {
    children: "children",
    label: "name"
  };

  const handleNodeClick = (data: MenuNode) => {
    selectedNodeId.value = data.id;
    emit("nodeClick", data.id);
  };

  const handleCreateChild = (data: MenuNode) => {
    emit("createChild", data.id);
  };

  const handleDelete = (data: MenuNode) => {
    emit("delete", data.id);
  };

  const getTypeTag = (type: number) => {
    const tags = {
      0: { text: "目录", type: "primary" },
      1: { text: "菜单", type: "success" },
      2: { text: "按钮", type: "warning" }
    };
    return tags[type];
  };
  </script>

  <template>
    <div class="menu-tree-container">
      <el-input
        v-model="filterText"
        placeholder="搜索菜单名称"
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
                <span class="truncate">{{ node.label }}</span>
                <el-tag :type="getTypeTag(data.type).type" size="small">
                  {{ getTypeTag(data.type).text }}
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
  .menu-tree-container {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  </style>
  ```
- **MIRROR**: TREE_COMPONENT_PATTERN
- **IMPORTS**: ref from vue, MenuNode type
- **GOTCHA**: 节点显示菜单类型标签(目录/菜单/按钮)
- **VALIDATE**: 菜单树正确渲染,类型标签正确显示

### Task 3: 创建菜单详情表单
- **ACTION**: 新建 `frontend/src/views/system/menu/components/MenuForm.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, watch } from "vue";
  import { ElMessage, type FormInstance, type FormRules } from "element-plus";
  import { createMenu, updateMenu, type MenuNode, type MenuCreateRequest, type MenuUpdateRequest } from "@/api/admin/menu";

  defineOptions({
    name: "MenuForm"
  });

  const props = defineProps<{
    menuDetail: MenuNode | null;
    parentId: number | null;
  }>();

  const emit = defineEmits<{
    success: [];
  }>();

  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const isEdit = ref(false);

  const form = reactive<MenuCreateRequest | MenuUpdateRequest>({
    parentId: undefined,
    name: "",
    type: 0,
    path: undefined,
    component: undefined,
    permCode: undefined,
    icon: undefined,
    sort: 0,
    visible: 1,
    status: 1
  });

  const rules: FormRules = {
    name: [
      { required: true, message: "请输入菜单名称", trigger: "blur" }
    ],
    type: [
      { required: true, message: "请选择菜单类型", trigger: "change" }
    ],
    path: [
      { required: true, message: "请输入路由路径", trigger: "blur", conditions: [{ field: "type", value: 1 }] }
    ],
    component: [
      { required: true, message: "请输入组件路径", trigger: "blur", conditions: [{ field: "type", value: 1 }] }
    ],
    permCode: [
      { required: true, message: "请输入权限标识", trigger: "blur", conditions: [{ field: "type", value: 2 }] }
    ]
  };

  watch(() => props.menuDetail, (detail) => {
    if (detail) {
      isEdit.value = true;
      Object.assign(form, detail);
    } else {
      isEdit.value = false;
      resetForm();
      form.parentId = props.parentId;
    }
  }, { immediate: true });

  const resetForm = () => {
    form.parentId = undefined;
    form.name = "";
    form.type = 0;
    form.path = undefined;
    form.component = undefined;
    form.permCode = undefined;
    form.icon = undefined;
    form.sort = 0;
    form.visible = 1;
    form.status = 1;
  };

  const handleSubmit = async () => {
    if (!formRef.value) return;

    await formRef.value.validate(async (valid) => {
      if (!valid) return;

      loading.value = true;
      try {
        if (isEdit.value && props.menuDetail) {
          const res = await updateMenu({ id: props.menuDetail.id, ...form });
          if (res.success) {
            ElMessage.success("更新成功");
            emit("success");
          }
        } else {
          const res = await createMenu(form as MenuCreateRequest);
          if (res.success) {
            ElMessage.success("创建成功");
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
  </script>

  <template>
    <div class="menu-form">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="上级菜单" v-if="!isEdit">
          <el-input :value="props.parentId ? '已选择父节点' : '根菜单'" disabled />
        </el-form-item>

        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入菜单名称" />
        </el-form-item>

        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="isEdit">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 菜单类型特有字段 -->
        <el-form-item label="路由路径" prop="path" v-if="form.type === 1">
          <el-input v-model="form.path" placeholder="请输入路由路径(如 /system/user)" />
        </el-form-item>

        <el-form-item label="组件路径" prop="component" v-if="form.type === 1">
          <el-input v-model="form.component" placeholder="请输入组件路径(如 system/user/index)" />
        </el-form-item>

        <!-- 按钮类型特有字段 -->
        <el-form-item label="权限标识" prop="permCode" v-if="form.type === 2">
          <el-input v-model="form.permCode" placeholder="请输入权限标识(如 sys:user:add)" />
        </el-form-item>

        <el-form-item label="图标" v-if="form.type !== 2">
          <el-input v-model="form.icon" placeholder="请输入图标名称(Phase 2.3 使用简单输入)" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>

        <el-form-item label="是否显示" v-if="form.type !== 2">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
          <el-button @click="resetForm">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </template>
  ```
- **MIRROR**: Vue Form Pattern + MENU_TYPE_FIELDS_PATTERN
- **IMPORTS**: ref, reactive, watch from vue, FormInstance, FormRules from element-plus, menu API
- **GOTCHA**: 
  - 菜单类型决定表单字段显示(v-if="form.type === X")
  - 编辑模式下类型不可修改
  - 按钮类型无路由/组件/图标/显示字段
  - 图标使用简单输入,后续实现图标选择器
- **VALIDATE**: 表单验证正确,类型字段动态显示

---

## Acceptance Criteria
- [ ] 所有 3 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 菜单树正确显示和搜索
- [ ] 菜单类型标签正确显示(目录/菜单/按钮)
- [ ] 菜单详情表单根据类型显示字段
- [ ] 菜单节点 CRUD 功能正常

## Completion Checklist
- [ ] 使用 `<script setup lang="ts">` + `defineOptions`
- [ ] API 类型定义前置导出
- [ ] 使用 `@/` 别名导入
- [ ] 无硬编码字符串
- [ ] 无相对路径导入

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase2-user-org-menu-pages.plan.md`
**Confidence Score**: 8/10 — 功能明确,类型字段动态显示