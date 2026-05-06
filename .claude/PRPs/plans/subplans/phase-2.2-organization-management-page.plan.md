# Subplan: Phase 2.2 - Organization Management Page

## Summary
实现组织管理完整功能,包含左侧组织树展示、右侧组织详情表单、组织节点 CRUD、组织成员查看、组织树配置基础框架等核心功能。

## User Story
作为系统管理员,我希望通过组织管理页面管理组织结构和查看组织成员,以便构建完整的组织体系并为用户管理提供组织基础。

## Problem → Solution
**当前状态**: frontend 缺少组织管理页面
**目标状态**: 完整的组织管理页面,支持树形展示、节点 CRUD、成员查看、树配置预留

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase2-user-org-menu-pages.plan.md`
- **Phase**: Phase 2.2 (Organization Management)
- **Estimated Files**: 6 files (1 page + 5 components)
- **Prerequisite**: Phase 2.1 已完成(用户管理、组织 API)

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  组织管理                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  组织树      │  │  组织详情表单                   ││
│  │              │  │                                 ││
│  │  [搜索框]    │  │  组织名称: [_______________]   ││
│  │  [刷新按钮]  │  │  组织编码: [_______________]   ││
│  │              │  │  组织类型: [下拉选择]          ││
│  │  组织树      │  │  组织负责人: [用户选择]        ││
│  │  ├─ 总部    │  │  排序: [数字输入]              ││
│  │  │  ├─研发部│  │  状态: [启用/停用]             ││
│  │  │  ├─市场部│  │                                 ││
│  │  │          │  │  [保存] [取消]                  ││
│  │              │  │                                 ││
│  │  [新增根节点]│  │  Tab 切换:                      ││
│  │              │  │  [组织详情] [组织成员] [树配置]││
│  └──────────────┘  │                                 ││
│                    │  组织成员列表(Tab 页)           ││
│                    │  [用户表格...]                  ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 组织树点击 | 显示组织详情 | 点击节点后在右侧显示详情表单 |
| 新增根节点 | 弹窗表单 | 创建顶级组织节点 |
| 新增子节点 | 弹窗表单 | 在选中节点下创建子组织 |
| 编辑组织 | 右侧表单编辑 | 点击节点后右侧表单可编辑 |
| 删除组织 | 二次确认 | 检查是否有子节点或成员 |
| 组织成员查看 | Tab 页切换 | 显示该组织下的用户列表 |
| 树配置 | Tab 页预留 | Phase 2.2 只预留框架,后续实现 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `frontend/src/api/admin/org.ts` | 全文(Phase 2.1 创建) | 组织 API 接口定义 |
| P0 | `frontend/src/api/admin/user.ts` | 全文(Phase 2.1 创建) | 用户 API(查询组织成员) |
| P1 | Element Plus Tree 文档 | 全文 | el-tree 配置,拖拽,右键菜单 |
| P1 | `frontend/src/views/system/user/components/OrgTreePanel.vue` | 全文(Phase 2.1 创建) | 组织树组件参考 |

---

## Patterns to Mirror

### TREE_COMPONENT_PATTERN
// SOURCE: frontend/src/views/system/user/components/OrgTreePanel.vue
```vue
<el-tree
  :data="treeData"
  :props="defaultProps"
  node-key="id"
  highlight-current
  default-expand-all
  :expand-on-click-node="false"
  @node-click="handleNodeClick"
>
  <template #default="{ node, data }">
    <span class="flex items-center justify-between w-full">
      <span>{{ node.label }}</span>
      <el-tag v-if="data.id === selectedNodeId" type="success" size="small">已选</el-tag>
    </span>
  </template>
</el-tree>
```
**模式要点**: 自定义节点内容,highlight-current,expand-on-click-node=false

### ORG_DETAIL_FORM_PATTERN
// SOURCE: 组织详情表单设计
```vue
<el-form :model="form" label-width="100px">
  <el-form-item label="组织名称">
    <el-input v-model="form.name" />
  </el-form-item>
  <el-form-item label="组织编码">
    <el-input v-model="form.code" />
  </el-form-item>
  <el-form-item label="组织类型">
    <el-select v-model="form.type">
      <el-option label="公司" :value="1" />
      <el-option label="部门" :value="2" />
    </el-select>
  </el-form-item>
</el-form>
```
**模式要点**: 右侧详情表单,动态显示选中节点信息

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/views/system/org/index.vue` | CREATE | 组织管理主页面 |
| `frontend/src/views/system/org/components/OrgTree.vue` | CREATE | 组织树组件 |
| `frontend/src/views/system/org/components/OrgForm.vue` | CREATE | 组织详情表单 |
| `frontend/src/views/system/org/components/OrgMembers.vue` | CREATE | 组织成员列表 |
| `frontend/src/views/system/org/components/OrgTreeConfig.vue` | CREATE | 组织树配置 Tab(预留) |

## NOT Building

- 组织树拖拽排序 - Phase 2.2 先实现基础树,拖拽在后续优化
- 组织树配置完整功能 - 预留 Tab 框架,后续实现
- 组织负责人选择器 - Phase 2.2 使用简单下拉,Phase 2.3 实现用户选择器
- 批量导入组织 - 后续 Phase 实现
- 组织层级限制 - Phase 2.2 不限制层级

---

## Step-by-Step Tasks

### Task 1: 创建组织管理主页面
- **ACTION**: 新建 `frontend/src/views/system/org/index.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, onMounted } from "vue";
  import { ElMessage, ElMessageBox } from "element-plus";
  import { getOrgTree, getOrgDetail, createOrg, updateOrg, deleteOrg, type OrgNode } from "@/api/admin/org";
  import OrgTree from "./components/OrgTree.vue";
  import OrgForm from "./components/OrgForm.vue";
  import OrgMembers from "./components/OrgMembers.vue";
  import OrgTreeConfig from "./components/OrgTreeConfig.vue";
  import { PERM_CODES } from "@/constants/permission";

  defineOptions({
    name: "SystemOrg"
  });

  const treeData = ref<Array<OrgNode>>([]);
  const selectedOrgId = ref<number | null>(null);
  const selectedOrgDetail = ref<OrgNode | null>(null);
  const loading = ref(false);

  const activeTab = ref("detail");

  // 加载组织树
  const loadOrgTree = async () => {
    loading.value = true;
    try {
      const res = await getOrgTree();
      if (res.success) {
        treeData.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载组织树失败");
    } finally {
      loading.value = false;
    }
  };

  // 组织树节点点击
  const handleOrgNodeClick = async (orgId: number) => {
    selectedOrgId.value = orgId;
    activeTab.value = "detail";

    // 加载组织详情
    try {
      const res = await getOrgDetail({ id: orgId });
      if (res.success) {
        selectedOrgDetail.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载组织详情失败");
    }
  };

  // 新增根节点
  const handleCreateRoot = () => {
    selectedOrgDetail.value = null;
    activeTab.value = "detail";
  };

  // 新增子节点
  const handleCreateChild = () => {
    if (!selectedOrgId.value) {
      ElMessage.warning("请先选择父组织节点");
      return;
    }
    // 将父节点信息传递给表单
  };

  // 删除组织
  const handleDeleteOrg = async (orgId: number) => {
    try {
      await ElMessageBox.confirm("确认删除该组织?删除后子组织将一并删除", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      });

      const res = await deleteOrg({ id: orgId });
      if (res.success) {
        ElMessage.success("删除成功");
        loadOrgTree();
        selectedOrgId.value = null;
        selectedOrgDetail.value = null;
      }
    } catch (error) {
      // 用户取消不处理
    }
  };

  // 表单保存成功
  const handleFormSuccess = () => {
    loadOrgTree();
  };

  onMounted(() => {
    loadOrgTree();
  });
  </script>

  <template>
    <div class="flex h-full">
      <!-- 左侧组织树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            v-perm="[PERM_CODES.SYS_ORG_CREATE]"
            @click="handleCreateRoot"
          >
            新增根节点
          </el-button>
        </div>
        <OrgTree
          :data="treeData"
          :loading="loading"
          @node-click="handleOrgNodeClick"
          @create-child="handleCreateChild"
          @delete="handleDeleteOrg"
        />
      </div>

      <!-- 右侧详情区域 -->
      <div class="flex-1 p-4">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="组织详情" name="detail">
            <OrgForm
              :org-detail="selectedOrgDetail"
              :parent-id="selectedOrgId"
              @success="handleFormSuccess"
            />
          </el-tab-pane>

          <el-tab-pane label="组织成员" name="members" :disabled="!selectedOrgId">
            <OrgMembers :org-id="selectedOrgId" />
          </el-tab-pane>

          <el-tab-pane label="树配置" name="config" :disabled="!selectedOrgId">
            <OrgTreeConfig :org-id="selectedOrgId" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </template>
  ```
- **MIRROR**: VUE_PAGE_PATTERN
- **IMPORTS**: org API, components, PERM_CODES
- **GOTCHA**: 
  - 左侧组织树 + 右侧 Tab 切换布局
  - 点击节点后加载详情并切换到详情 Tab
  - 删除组织时提示子组织一并删除
- **VALIDATE**: 页面布局正确,Tab 切换正常

### Task 2: 创建组织树组件
- **ACTION**: 新建 `frontend/src/views/system/org/components/OrgTree.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";
  import { type OrgNode } from "@/api/admin/org";

  defineOptions({
    name: "OrgTree"
  });

  const props = defineProps<{
    data: Array<OrgNode>;
    loading: boolean;
  }>();

  const emit = defineEmits<{
    nodeClick: [orgId: number];
    createChild: [parentId: number];
    delete: [orgId: number];
  }>();

  const selectedNodeId = ref<number | null>(null);
  const filterText = ref("");

  const defaultProps = {
    children: "children",
    label: "name"
  };

  const handleNodeClick = (data: OrgNode) => {
    selectedNodeId.value = data.id;
    emit("nodeClick", data.id);
  };

  const handleCreateChild = (data: OrgNode) => {
    emit("createChild", data.id);
  };

  const handleDelete = (data: OrgNode) => {
    emit("delete", data.id);
  };
  </script>

  <template>
    <div class="org-tree-container">
      <el-input
        v-model="filterText"
        placeholder="搜索组织名称"
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
              <span class="truncate">{{ node.label }}</span>
              <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
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
  .org-tree-container {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  </style>
  ```
- **MIRROR**: TREE_COMPONENT_PATTERN
- **IMPORTS**: ref from vue, OrgNode type
- **GOTCHA**: 
  - 使用 filter-node-method 支持搜索过滤
  - 操作按钮在 hover 时显示(opacity-0 → opacity-100)
  - @click.stop 阻止事件冒泡(避免触发 node-click)
- **VALIDATE**: 组织树正确渲染,搜索过滤正常

### Task 3: 创建组织详情表单
- **ACTION**: 新建 `frontend/src/views/system/org/components/OrgForm.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, watch } from "vue";
  import { ElMessage, type FormInstance, type FormRules } from "element-plus";
  import { createOrg, updateOrg, type OrgNode, type OrgCreateRequest, type OrgUpdateRequest } from "@/api/admin/org";

  defineOptions({
    name: "OrgForm"
  });

  const props = defineProps<{
    orgDetail: OrgNode | null;
    parentId: number | null;
  }>();

  const emit = defineEmits<{
    success: [];
  }>();

  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const isEdit = ref(false);

  const form = reactive<OrgCreateRequest | OrgUpdateRequest>({
    parentId: undefined,
    name: "",
    code: "",
    type: 1,
    leaderId: undefined,
    sort: 0,
    status: 1
  });

  const rules: FormRules = {
    name: [
      { required: true, message: "请输入组织名称", trigger: "blur" }
    ],
    code: [
      { required: true, message: "请输入组织编码", trigger: "blur" },
      { pattern: /^[A-Z_]+$/, message: "编码只能包含大写字母和下划线", trigger: "blur" }
    ],
    type: [
      { required: true, message: "请选择组织类型", trigger: "change" }
    ]
  };

  // 监听 orgDetail 变化(节点点击)
  watch(() => props.orgDetail, (detail) => {
    if (detail) {
      isEdit.value = true;
      Object.assign(form, detail);
    } else {
      isEdit.value = false;
      resetForm();
      form.parentId = props.parentId;
    }
  }, { immediate: true });

  // 重置表单
  const resetForm = () => {
    form.parentId = undefined;
    form.name = "";
    form.code = "";
    form.type = 1;
    form.leaderId = undefined;
    form.sort = 0;
    form.status = 1;
  };

  // 提交表单
  const handleSubmit = async () => {
    if (!formRef.value) return;

    await formRef.value.validate(async (valid) => {
      if (!valid) return;

      loading.value = true;
      try {
        if (isEdit.value && props.orgDetail) {
          const res = await updateOrg({ id: props.orgDetail.id, ...form });
          if (res.success) {
            ElMessage.success("更新成功");
            emit("success");
          }
        } else {
          const res = await createOrg(form as OrgCreateRequest);
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

  const handleCancel = () => {
    resetForm();
  };
  </script>

  <template>
    <div class="org-form">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="上级组织" v-if="!isEdit">
          <el-input :value="props.parentId ? '已选择父节点' : '根节点'" disabled />
        </el-form-item>

        <el-form-item label="组织名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入组织名称" />
        </el-form-item>

        <el-form-item label="组织编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入组织编码(大写字母+下划线)" :disabled="isEdit" />
        </el-form-item>

        <el-form-item label="组织类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择组织类型">
            <el-option label="公司" :value="1" />
            <el-option label="部门" :value="2" />
            <el-option label="小组" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="组织负责人">
          <el-input v-model="form.leaderId" placeholder="Phase 2.3 实现用户选择器" disabled />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </template>
  ```
- **MIRROR**: Vue Form Pattern
- **IMPORTS**: ref, reactive, watch from vue, FormInstance, FormRules from element-plus, org API
- **GOTCHA**: 
  - watch 监听 orgDetail 变化,动态切换编辑/新增模式
  - 编码只能包含大写字母和下划线
  - 编辑模式下编码不可修改
  - 组织负责人使用简单输入,Phase 2.3 实现用户选择器
- **VALIDATE**: 表单验证正确,提交逻辑完整

### Task 4: 创建组织成员列表
- **ACTION**: 新建 `frontend/src/views/system/org/components/OrgMembers.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, watch } from "vue";
  import { getUserPage, type UserItem } from "@/api/admin/user";

  defineOptions({
    name: "OrgMembers"
  });

  const props = defineProps<{
    orgId: number | null;
  }>();

  const loading = ref(false);
  const tableData = ref<Array<UserItem>>([]);
  const total = ref(0);

  // 加载组织成员
  const loadMembers = async () => {
    if (!props.orgId) return;

    loading.value = true;
    try {
      const res = await getUserPage({
        pageNum: 1,
        pageSize: 100,
        orgId: props.orgId
      });
      if (res.success) {
        tableData.value = res.data.items;
        total.value = res.data.total;
      }
    } catch (error) {
      console.error("加载组织成员失败");
    } finally {
      loading.value = false;
    }
  };

  // 监听 orgId 变化
  watch(() => props.orgId, () => {
    loadMembers();
  }, { immediate: true });
  </script>

  <template>
    <div class="org-members">
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
      >
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total === 0 && !loading" class="text-center py-10 text-gray-500">
        该组织暂无成员
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Table Pattern
- **IMPORTS**: ref, watch from vue, user API
- **GOTCHA**: 
  - 使用 getUserPage 查询组织成员(orgId 参数)
  - pageSize=100 显示所有成员(不分页)
  - 无成员时显示提示
- **VALIDATE**: 组织成员列表正确显示

### Task 5: 创建组织树配置 Tab(预留)
- **ACTION**: 新建 `frontend/src/views/system/org/components/OrgTreeConfig.vue`
- **IMPLEMENT**: 预留 Tab 框架
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";

  defineOptions({
    name: "OrgTreeConfig"
  });

  const props = defineProps<{
    orgId: number | null;
  }>();
  </script>

  <template>
    <div class="org-tree-config">
      <div class="text-center py-10 text-gray-500">
        组织树配置功能将在后续 Phase 实现
      </div>
    </div>
  </template>
  ```
- **MIRROR**: Vue Component Pattern(预留版)
- **IMPORTS**: ref from vue
- **GOTCHA**: Phase 2.2 只预留框架,后续实现完整配置功能
- **VALIDATE**: Tab 页正确显示预留提示

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| 组织树查询 | 无参数 | 返回组织树数据 | 组织树为空 |
| 组织详情查询 | id=1 | 返回组织详情 | 组织不存在 |
| 组织创建 | OrgCreateRequest | 创建成功 | 编码已存在 |
| 组织更新 | OrgUpdateRequest | 更新成功 | 组织不存在 |
| 组织删除 | id=1 | 删除成功 | 有子节点/成员 |
| 组织成员查询 | orgId=1 | 返回成员列表 | 无成员 |
| 组织树搜索 | filterText="研发" | 过滤显示匹配节点 | 无匹配 |

### Edge Cases Checklist
- [ ] 组织编码已存在(创建时)
- [ ] 组织不存在(编辑/删除时)
- [ ] 删除有子节点的组织
- [ ] 删除有成员的组织
- [ ] 组织树为空(无数据)
- [ ] 组织成员为空
- [ ] 组织负责人不存在
- [ ] 编码格式错误(非大写字母+下划线)
- [ ] 组织层级过深(无限层级问题)

---

## Validation Commands

### Static Analysis
```bash
cd frontend
pnpm typecheck
```
EXPECT: Zero type errors

### Dev Server
```bash
cd frontend
pnpm dev
```
EXPECT:
1. 组织管理页面正确渲染
2. 组织树正确显示
3. 点击节点显示详情表单
4. 新增根节点/子节点功能正常
5. 编辑/删除组织功能正常
6. 组织成员 Tab 正确显示成员列表
7. 树配置 Tab 显示预留提示

### Manual Validation
- [ ] 启动前端开发服务器
- [ ] 检查组织管理页面是否正确渲染
- [ ] 检查组织树是否正确显示
- [ ] 测试组织树搜索功能
- [ ] 点击新增根节点,填写表单并提交
- [ ] 点击组织节点,查看详情表单
- [ ] 编辑组织信息并提交
- [ ] 点击新增子节点按钮,创建子组织
- [ ] 点击删除按钮,确认删除
- [ ] 切换到组织成员 Tab,查看成员列表
- [ ] 切换到树配置 Tab,查看预留提示

---

## Acceptance Criteria
- [ ] 所有 5 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 组织树正确显示和搜索
- [ ] 组织详情表单正确显示
- [ ] 组织节点 CRUD 功能正常
- [ ] 组织成员查看功能正常
- [ ] 树配置 Tab 显示预留提示

## Completion Checklist
- [ ] 使用 `<script setup lang="ts">` + `defineOptions`
- [ ] API 类型定义前置导出
- [ ] 使用 `@/` 别名导入
- [ ] 无硬编码字符串
- [ ] 无相对路径导入

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 后端 API 未就绪 | Medium | High | 使用 mock 数据 |
| 组织树拖拽实现复杂 | Low | Medium | Phase 2.2 不实现拖拽 |
| 删除有子节点组织处理 | Medium | Medium | 提示用户子组织一并删除 |
| 组织负责人选择器复杂 | Low | Low | Phase 2.2 使用简单输入,Phase 2.3 实现 |

## Notes
1. **Phase 2.2 重点**: 完成组织管理基础功能,树配置预留框架
2. **简化策略**: 组织负责人选择器、树拖拽在后续 Phase 实现
3. **依赖**: Phase 2.1 已完成组织 API 和用户 API
4. **Mock 数据**: 如果后端未就绪,先用 mock 数据实现前端页面逻辑

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase2-user-org-menu-pages.plan.md`
**Confidence Score**: 8/10 — 功能明确,树配置预留