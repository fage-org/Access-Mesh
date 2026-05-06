# Subplan: Phase 2.1 - User Management Page

## Summary
实现用户管理完整功能,包含左侧组织树筛选、用户列表表格、搜索/分页、创建/编辑/删除用户、启用/停用、重置密码、用户组织关系配置等核心功能。

## User Story
作为系统管理员,我希望通过用户管理页面管理用户数据和配置用户组织关系,以便构建完整的用户体系并为权限配置提供基础数据。

## Problem → Solution
**当前状态**: frontend 缺少用户管理页面
**目标状态**: 完整的用户管理页面,支持 CRUD、组织筛选、状态管理、密码重置

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase2-user-org-menu-pages.plan.md`
- **Phase**: Phase 2.1 (User Management)
- **Estimated Files**: 6 files (1 page + 5 components)
- **Prerequisite**: Phase 1.1/1.2 已完成(认证流程、HTTP 拦截器)

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  用户管理                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  组织树筛选   │  │  用户列表表格                   ││
│  │              │  │                                 ││
│  │  [搜索框]    │  │  [用户名][昵称][组织][邮箱]... ││
│  │              │  │  ┌─────────────────────────────┐││
│  │  组织树      │  │  │ 用户数据行                   │││
│  │  ├─ 总部    │  │  │ [编辑][停用][重置密码][删除]│││
│  │  │  ├─研发部│  │  └─────────────────────────────┘││
│  │  │  ├─市场部│  │                                 ││
│  │              │  │  [分页组件]                     ││
│  │  [清除选择]  │  │                                 ││
│  └──────────────┘  └─────────────────────────────────┘│
│                                                       │
│  [新增用户] [批量删除]                                │
│  [用户名搜索] [昵称搜索] [手机号搜索] [搜索] [重置]   │
└───────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 组织树点击 | 筛选用户列表 | 点击组织节点后表格只显示该组织用户 |
| 清除选择 | 清除组织筛选 | 显示全部用户 |
| 用户搜索 | 多条件搜索 | 用户名/昵称/手机号支持模糊搜索 |
| 新增用户 | 弹窗表单 | 包含用户名、昵称、密码、邮箱、手机、状态、主组织 |
| 编辑用户 | 弹窗表单 | 用户名不可修改,密码不显示 |
| 启用/停用 | 切换状态 | 二次确认,调用 enableUser API |
| 重置密码 | 重置为随机密码 | 二次确认,显示新密码 |
| 删除用户 | 二次确认 | 单个删除,批量删除预留 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `frontend/src/components/RePureTableBar/src/bar.tsx` | 全文 | 表格工具栏模式,列设置/刷新/全屏 |
| P0 | `frontend/src/api/admin/user.ts` | 全文(Phase 2.1 创建) | 用户 API 接口定义 |
| P0 | `frontend/src/api/admin/org.ts` | 全文(Phase 2.1 创建) | 组织 API 接口定义 |
| P1 | `frontend/src/plugins/elementPlus.ts` | 90-92 | ElTable/ElTableColumn 全局注册 |
| P1 | `frontend/src/utils/http/index.ts` | 60-113 | HTTP 拦截器,统一响应体处理 |
| P2 | `frontend/src/constants/permission.ts` | 全文(Phase 2.1 创建) | 权限码常量定义 |

---

## Patterns to Mirror

### VUE_PAGE_PATTERN
// SOURCE: frontend/src/views/welcome/index.vue:1-10
```vue
<script setup lang="ts">
defineOptions({
  name: "Welcome"
});
</script>

<template>
  <h1>Pure-Admin-Thin（非国际化版本）</h1>
</template>
```
**模式要点**: `<script setup lang="ts">` + `defineOptions` 定义组件名

### TABLE_BAR_PATTERN
// SOURCE: frontend/src/components/RePureTableBar/src/bar.tsx:53-392
```tsx
export default defineComponent({
  name: "PureTableBar",
  props,
  emits: ["refresh", "fullscreen"],
  setup(props, { emit, slots, attrs }) {
    const size = ref("default");
    const loading = ref(false);
    const dynamicColumns = ref(cloneDeep(props?.columns));

    function onReFresh() {
      loading.value = true;
      emit("refresh");
      delay(500).then(() => (loading.value = false));
    }

    return () => (
      <>
        <div class="w-full px-2 pb-2 bg-bg_color">
          <div class="flex justify-between w-full h-[60px] p-4">
            <p class="font-bold truncate">{props.title}</p>
            <div class="flex items-center justify-around">
              {/* 工具栏按钮 */}
              <RefreshIcon onClick={() => onReFresh()} />
            </div>
          </div>
          {slots.default({ size: size.value, dynamicColumns: dynamicColumns.value })}
        </div>
      </>
    );
  }
});
```
**模式要点**: JSX 组件,slots 传递 size 和 dynamicColumns,工具栏集成

### TREE_COMPONENT_PATTERN
// SOURCE: Element Plus 官方示例
```vue
<el-tree
  :data="treeData"
  :props="defaultProps"
  node-key="id"
  default-expand-all
  :expand-on-click-node="false"
  @node-click="handleNodeClick"
>
  <template #default="{ node, data }">
    <span class="custom-tree-node">
      <span>{{ node.label }}</span>
      <span>
        <el-button type="primary" link size="small">操作</el-button>
      </span>
    </span>
  </template>
</el-tree>
```
**模式要点**: 自定义节点内容,template slot,expand-on-click-node=false

### API_TYPE_DEFINITION_PATTERN
// SOURCE: frontend/src/api/user.ts:3-23
```typescript
export type UserResult = {
  success: boolean;
  data: {
    avatar: string;
    username: string;
  };
};

export const getLogin = (data?: object) => {
  return http.request<UserResult>("post", "/login", { data });
};
```
**模式要点**: 类型定义前置导出,`http.request<T>` 泛型

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/admin/user.ts` | CREATE | 用户 API 接口定义(已在主计划详细说明) |
| `frontend/src/api/admin/org.ts` | CREATE | 组织 API 接口定义(已在主计划详细说明) |
| `frontend/src/constants/permission.ts` | CREATE | 权限码常量定义(已在主计划详细说明) |
| `frontend/src/views/system/user/index.vue` | CREATE | 用户管理主页面(已在主计划详细说明) |
| `frontend/src/views/system/user/components/UserForm.vue` | CREATE | 用户表单弹窗(已在主计划详细说明) |
| `frontend/src/views/system/user/components/OrgTreePanel.vue` | CREATE | 组织树筛选面板(已在主计划详细说明) |
| `frontend/src/views/system/user/components/UserOrgDialog.vue` | CREATE | 用户组织关系配置弹窗 |
| `frontend/src/views/system/user/components/UserRoleDialog.vue` | CREATE | 用户角色分配弹窗(预留) |

## NOT Building

- 用户角色分配完整功能 - 预留弹窗框架,Phase 3 完整实现
- 用户组织关系多组织配置 - Phase 2.1 只支持主组织配置
- 批量删除完整功能 - 预留按钮,功能在后续实现
- 数据导入导出 - 后续 Phase 实现
- 表格高级筛选(多条件组合) - Phase 2.1 只支持基础搜索

---

## Step-by-Step Tasks

### Task 1: 创建用户 API 接口
- **ACTION**: 新建 `frontend/src/api/admin/user.ts`
- **STATUS**: ✅ 已在主计划详细说明(Task 1)
- **CODE**: 见主计划 lines 280-386
- **VALIDATE**: 类型定义完整,接口路径正确

### Task 2: 创建组织 API 接口
- **ACTION**: 新建 `frontend/src/api/admin/org.ts`
- **STATUS**: ✅ 已在主计划详细说明(Task 2)
- **CODE**: 见主计划 lines 392-476
- **VALIDATE**: OrgNode 包含 children 字段支持树形结构

### Task 3: 创建权限码常量定义
- **ACTION**: 新建 `frontend/src/constants/permission.ts`
- **STATUS**: ✅ 已在主计划详细说明(Task 4)
- **CODE**: 见主计划 lines 572-600
- **VALIDATE**: 权限码格式 `{模块}:{资源}:{操作}`

### Task 4: 创建用户管理主页面
- **ACTION**: 新建 `frontend/src/views/system/user/index.vue`
- **STATUS**: ✅ 已在主计划详细说明(Task 5)
- **CODE**: 见主计划 lines 602-944
- **VALIDATE**: 页面结构正确,表格渲染正常

### Task 5: 创建用户表单弹窗
- **ACTION**: 新建 `frontend/src/views/system/user/components/UserForm.vue`
- **STATUS**: ✅ 已在主计划详细说明(Task 6)
- **CODE**: 见主计划 lines 950-1130
- **VALIDATE**: 表单验证规则正确,提交逻辑完整

### Task 6: 创建组织树筛选面板
- **ACTION**: 新建 `frontend/src/views/system/user/components/OrgTreePanel.vue`
- **STATUS**: ✅ 已在主计划详细说明(Task 7)
- **CODE**: 见主计划 lines 1137-1239
- **VALIDATE**: 组织树正确渲染,点击节点触发 select 事件

### Task 7: 创建用户组织关系配置弹窗
- **ACTION**: 新建 `frontend/src/views/system/user/components/UserOrgDialog.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, onMounted } from "vue";
  import { ElMessage, type FormInstance } from "element-plus";
  import { getOrgTree, type OrgNode } from "@/api/admin/org";
  import { updateUser, getUserDetail } from "@/api/admin/user";

  defineOptions({
    name: "UserOrgDialog"
  });

  const props = defineProps<{
    userId: number;
  }>();

  const emit = defineEmits<{
    success: [];
  }>();

  const dialogVisible = ref(false);
  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const orgTreeData = ref<Array<OrgNode>>([]);

  const form = reactive({
    primaryOrgId: null as number | null
  });

  const defaultProps = {
    children: "children",
    label: "name"
  };

  // 加载组织树
  const loadOrgTree = async () => {
    try {
      const res = await getOrgTree();
      if (res.success) {
        orgTreeData.value = res.data;
      }
    } catch (error) {
      ElMessage.error("加载组织树失败");
    }
  };

  // 加载用户当前组织
  const loadUserOrg = async () => {
    loading.value = true;
    try {
      const res = await getUserDetail({ id: props.userId });
      if (res.success) {
        form.primaryOrgId = res.data.primaryOrgId;
      }
    } catch (error) {
      ElMessage.error("加载用户信息失败");
    } finally {
      loading.value = false;
    }
  };

  // 打开弹窗
  const openDialog = () => {
    dialogVisible.value = true;
    loadOrgTree();
    loadUserOrg();
  };

  // 提交
  const handleSubmit = async () => {
    loading.value = true;
    try {
      const res = await updateUser({
        id: props.userId,
        primaryOrgId: form.primaryOrgId
      });
      if (res.success) {
        ElMessage.success("组织配置成功");
        dialogVisible.value = false;
        emit("success");
      }
    } catch (error) {
      ElMessage.error("组织配置失败");
    } finally {
      loading.value = false;
    }
  };

  defineExpose({
    openDialog
  });
  </script>

  <template>
    <el-dialog
      v-model="dialogVisible"
      title="配置用户组织"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="主组织">
          <el-tree-select
            v-model="form.primaryOrgId"
            :data="orgTreeData"
            :props="defaultProps"
            node-key="id"
            check-strictly
            placeholder="请选择主组织"
            clearable
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </template>
  ```
- **MIRROR**: Vue Dialog Pattern
- **IMPORTS**: ref, reactive, onMounted from vue, ElMessage, FormInstance from element-plus, org API, user API
- **GOTCHA**: 
  - 使用 el-tree-select 选择组织(比 el-select + el-tree 组合更简洁)
  - check-strictly 属性允许选择任意节点(不强制父子关联)
  - clearable 允许清空选择(表示无主组织)
- **VALIDATE**: 
  - 组织树正确加载
  - 用户当前组织正确显示
  - 提交成功后更新用户主组织

### Task 8: 创建用户角色分配弹窗(预留)
- **ACTION**: 新建 `frontend/src/views/system/user/components/UserRoleDialog.vue`
- **IMPLEMENT**: 预留弹窗框架,Phase 3 完整实现
  ```vue
  <script setup lang="ts">
  import { ref } from "vue";

  defineOptions({
    name: "UserRoleDialog"
  });

  const dialogVisible = ref(false);

  // 打开弹窗(预留,Phase 3 完整实现)
  const openDialog = () => {
    dialogVisible.value = true;
    // TODO: Phase 3 实现角色分配逻辑
  };

  defineExpose({
    openDialog
  });
  </script>

  <template>
    <el-dialog
      v-model="dialogVisible"
      title="分配角色"
      width="600px"
      :close-on-click-modal="false"
    >
      <div class="text-center py-10 text-gray-500">
        角色分配功能将在 Phase 3 实现
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </template>
  ```
- **MIRROR**: Vue Dialog Pattern(简化版)
- **IMPORTS**: ref from vue
- **GOTCHA**: Phase 3 将完整实现角色分配逻辑,Phase 2.1 只预留弹窗框架
- **VALIDATE**: 弹窗正确显示预留提示

### Task 9: 在主页面集成组织配置和角色分配按钮
- **ACTION**: 更新 `frontend/src/views/system/user/index.vue`
- **IMPLEMENT**: 在操作列添加组织配置和角色分配按钮
  ```vue
  <!-- 操作列扩展(在 Task 5 的基础上增加) -->
  <template #action="{ row }">
    <el-button
      type="primary"
      link
      size="small"
      v-perm="[PERM_CODES.SYS_USER_UPDATE]"
      @click="handleEdit(row)"
    >
      编辑
    </el-button>
    <el-button
      type="success"
      link
      size="small"
      @click="handleConfigOrg(row)"
    >
      配置组织
    </el-button>
    <el-button
      type="info"
      link
      size="small"
      @click="handleAssignRole(row)"
    >
      分配角色
    </el-button>
    <el-button
      type="primary"
      link
      size="small"
      @click="handleToggleStatus(row)"
    >
      {{ row.status === 1 ? "停用" : "启用" }}
    </el-button>
    <el-button
      type="warning"
      link
      size="small"
      @click="handleResetPassword(row)"
    >
      重置密码
    </el-button>
    <el-button
      type="danger"
      link
      size="small"
      v-perm="[PERM_CODES.SYS_USER_DELETE]"
      @click="handleDelete(row)"
    >
      删除
    </el-button>
  </template>
  ```
- **SCRIPT SETUP 扩展**:
  ```typescript
  import UserOrgDialog from "./components/UserOrgDialog.vue";
  import UserRoleDialog from "./components/UserRoleDialog.vue";

  const userOrgDialogRef = ref();
  const userRoleDialogRef = ref();

  // 配置组织
  const handleConfigOrg = (row: UserItem) => {
    userOrgDialogRef.value.openDialog(row.id);
  };

  // 分配角色
  const handleAssignRole = (row: UserItem) => {
    userRoleDialogRef.value.openDialog(row.id);
  };
  ```
- **TEMPLATE 扩展**:
  ```vue
  <!-- 用户表单弹窗(已有) -->
  <UserForm
    ref="userFormRef"
    :edit-id="currentEditId"
    @success="handleFormSuccess"
  />

  <!-- 用户组织配置弹窗 -->
  <UserOrgDialog
    ref="userOrgDialogRef"
    @success="handleFormSuccess"
  />

  <!-- 用户角色分配弹窗 -->
  <UserRoleDialog
    ref="userRoleDialogRef"
    @success="handleFormSuccess"
  />
  ```
- **MIRROR**: Vue Composition API Pattern
- **IMPORTS**: UserOrgDialog, UserRoleDialog 组件
- **GOTCHA**: 
  - 配置组织按钮使用 handleConfigOrg 方法
  - 分配角色按钮使用 handleAssignRole 方法(预留)
  - 组织配置成功后刷新列表(显示新的主组织名称)
- **VALIDATE**: 
  - 配置组织按钮正确触发弹窗
  - 组织配置成功后列表刷新

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| 用户列表查询 | pageNum=1, pageSize=10 | 返回用户数据和总数 | 组织筛选为空 |
| 用户搜索 | username="admin" | 返回匹配用户 | 模糊搜索 |
| 用户创建 | UserCreateRequest | 创建成功 | 密码长度不足 |
| 用户编辑 | UserUpdateRequest | 更新成功 | 用户名不可修改 |
| 用户删除 | ids=[1] | 删除成功 | 用户不存在 |
| 用户启用/停用 | ids=[1], status=0 | 状态更新 | 批量操作 |
| 密码重置 | id=1 | 返回新密码 | 用户不存在 |
| 组织配置 | primaryOrgId=10 | 更新用户主组织 | 组织不存在 |
| 组织树筛选 | orgId=5 | 筛选该组织用户 | 清除筛选 |

### Edge Cases Checklist
- [ ] 用户名已存在(创建时)
- [ ] 用户不存在(编辑/删除/重置密码时)
- [ ] 组织不存在(组织配置时)
- [ ] 密码格式不符合要求
- [ ] 邮箱/手机号格式错误
- [ ] 组织树为空(加载失败)
- [ ] 用户列表为空(无数据)
- [ ] 分页参数错误(pageNum < 1)
- [ ] 搜索条件全为空

---

## Validation Commands

### Static Analysis
```bash
cd frontend
pnpm typecheck
```
EXPECT: Zero type errors

### Lint Check
```bash
cd frontend
pnpm lint:eslint
```
EXPECT: All lint checks pass

### Dev Server
```bash
cd frontend
pnpm dev
```
EXPECT:
1. 用户管理页面正确渲染
2. 组织树正确显示并可点击筛选
3. 用户列表表格正确显示
4. 搜索/分页功能正常
5. 新增/编辑用户弹窗正常
6. 配置组织弹窗正常
7. 分配角色弹窗显示预留提示
8. 启用/停用/重置密码/删除功能正常

### Manual Validation
- [ ] 启动前端开发服务器
- [ ] 检查用户管理页面是否正确渲染
- [ ] 检查组织树是否正确显示和筛选
- [ ] 检查用户列表表格是否正确渲染
- [ ] 测试用户搜索功能
- [ ] 测试分页功能
- [ ] 点击新增用户,填写表单并提交
- [ ] 点击编辑用户,修改表单并提交
- [ ] 点击配置组织,选择组织并提交
- [ ] 点击分配角色,查看预留提示
- [ ] 点击启用/停用按钮,查看状态切换
- [ ] 点击重置密码,查看新密码显示
- [ ] 点击删除用户,确认删除

---

## Acceptance Criteria
- [ ] 所有 8 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 用户列表正确渲染
- [ ] 组织树筛选功能正常
- [ ] 用户搜索/分页功能正常
- [ ] 用户创建/编辑功能正常
- [ ] 组织配置功能正常
- [ ] 角色分配弹窗显示预留提示
- [ ] 启用/停用/重置密码/删除功能正常
- [ ] API 接口正确对接

## Completion Checklist
- [ ] 使用 `<script setup lang="ts">` + `defineOptions`
- [ ] API 类型定义前置导出
- [ ] 使用内联类型导入 `{ type X }`
- [ ] 使用 `http.request<T>` 泛型
- [ ] 使用 `@/` 别名导入
- [ ] HTML 标签自闭合
- [ ] 无硬编码字符串
- [ ] 无相对路径导入

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 后端 API 未就绪 | High | High | 使用 mock 数据先实现前端逻辑 |
| el-tree-select 兼容性 | Low | Medium | Element Plus 2.4+ 已支持 |
| 组织配置逻辑复杂 | Medium | Medium | Phase 2.1 只支持主组织,多组织后续实现 |
| 角色分配预留冲突 | Low | Low | 预留弹窗框架,Phase 3 完整实现 |

## Notes
1. **Phase 2.1 重点**: 完成用户管理完整功能,包含组织配置预留
2. **简化策略**: 角色分配功能预留框架,Phase 3 完整实现
3. **依赖**: 需要 RePureTableBar 组件、Element Plus Tree/Table/TreeSelect 组件
4. **Mock 数据**: 如果后端未就绪,先用 mock 数据实现前端页面逻辑
5. **主组织配置**: Phase 2.1 只支持配置主组织,多组织关系在后续 Phase 实现

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase2-user-org-menu-pages.plan.md`
**Confidence Score**: 9/10 — 功能明确,主计划已有详细说明