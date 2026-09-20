<script setup lang="ts">
import { ref, computed } from "vue";
import { h } from "vue";
import { useRouter } from "vue-router";
import { useRoleManage } from "./utils/hook";
import RoleForm from "./components/RoleForm.vue";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { ROLE_MANAGE_PERMS } from "./utils/perms";
import { PERMISSION_GRANT_PERMS } from "@/views/perm/grant/utils/perms";
import { resolveGrantEntryLabel } from "@/views/perm/grant/utils/grant-entry";
import {
  ROLE_TYPE_LABEL,
  ROLE_TYPE_CODE,
  MANAGEABLE_ROLE_TYPES,
  getRoleDetail,
  type RoleTreeNode,
  type RoleTypeCode
} from "@/api/role-manage";
import { message } from "@/utils/message";
import { isReadonlyRoleType } from "./utils/types";
import { Plus, Edit, Delete, Key } from "@element-plus/icons-vue";

defineOptions({
  name: "SystemRole"
});

const {
  roleTree,
  loading,
  selectedRole,
  filterText,
  treeProps,
  loadTree,
  filterNode,
  handleNodeClick,
  handleSubmitForm,
  handleToggleStatus,
  handleDelete,
  handleNodeDrop
} = useRoleManage();

// 树组件引用（用于过滤）
const treeRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/role-manage.md §权限接线 对齐。
const canView = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_VIEW));
const canAdd = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_ADD));
const canEdit = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_EDIT));
const canDelete = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_DELETE));

// 监听过滤文本
function onFilterInput(val: string) {
  treeRef.value?.filter(val);
}

// ========== 新建/编辑弹窗 ==========

/**
 * 编辑回显数据：树节点契约（RoleTreeNode）不含 extra 字段，编辑表单须按业务键拉
 * detail 回填（role-manage.md §8 既定路径，T-FE-016 接线）——否则 extra 回显空白，
 * 旧值不可见也无法从界面清空（后端 update null=不更新，不会误删但界面误导）。
 * detail 失败/未命中时回落树节点数据（extra 不回显，同历史行为）；无 externalId
 * 的脏数据角色无业务键可查，直接回落（表单本就强制 externalId 必填，属边缘）。
 */
async function resolveEditInitialData(
  node: RoleTreeNode
): Promise<RoleTreeNode> {
  if (!node.externalId) return node;
  try {
    const detail = await getRoleDetail({
      roleTypeCode: node.roleTypeCode,
      roleExternalId: node.externalId
    });
    return detail ? { ...node, ...detail } : node;
  } catch (error: any) {
    message(error.message || "角色详情加载失败，扩展属性未回显", {
      type: "warning"
    });
    return node;
  }
}

async function openRoleForm(
  mode: "create" | "edit",
  node?: RoleTreeNode | null,
  defaultRoleType?: RoleTypeCode
) {
  const isEdit = mode === "edit";
  // 新建：优先外部传入类型（下拉），其次节点类型；编辑：沿用节点类型。
  // RoleTreeNode.roleTypeCode 为 string，显式断言为 RoleTypeCode。
  const resolvedType: RoleTypeCode = (defaultRoleType ??
    (node?.roleTypeCode as RoleTypeCode) ??
    ROLE_TYPE_CODE.BASIC_ROLE) as RoleTypeCode;

  const initialData =
    isEdit && node ? await resolveEditInitialData(node) : null;

  let formRef: any = null;

  addDialog({
    title: isEdit ? "编辑角色" : "新增角色",
    width: "480px",
    contentRenderer: () =>
      h(RoleForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        initialData,
        defaultRoleTypeCode: resolvedType,
        parentNode: !isEdit ? node : null,
        roleTree: roleTree.value
      }),
    beforeSure: async (done: Function) => {
      if (!formRef) {
        done();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) return;
      const formData = formRef.getFormData();
      const ok = await handleSubmitForm(
        mode,
        formData,
        isEdit ? node?.id : undefined,
        isEdit ? initialData?.extra : undefined
      );
      if (ok) done();
    }
  });
}

// ========== 新增按钮：按类型下拉 ==========

const addTypeMenu = computed(() =>
  MANAGEABLE_ROLE_TYPES.map(type => ({
    label: ROLE_TYPE_LABEL[type],
    type
  }))
);

function onAddByType(type: RoleTypeCode) {
  // C2 后无类型虚拟根：新建顶层角色，parentId=null（顶层森林），类型由下拉决定。
  openRoleForm("create", null, type);
}

// ========== 树节点操作权限 ==========

/** 节点是否可编辑（C2 后树根是真实角色，仅排除只读类型） */
function isNodeEditable(node: RoleTreeNode) {
  return !isReadonlyRoleType(node.roleTypeCode);
}

/** 拖拽落点校验：跨类型禁止（前端拦截），handleNodeDrop 兜底回滚。
 *  type 取 el-tree AllowDropType（'prev'|'inner'|'next'），宽松类型避免与模板绑定冲突。 */
function allowDrop(draggingNode: any, targetNode: any, type: string): boolean {
  const dragging: RoleTreeNode = draggingNode?.data;
  const target: RoleTreeNode = targetNode?.data;
  if (!dragging || !target) return true;
  // 只读类型不可拖动
  if (isReadonlyRoleType(dragging.roleTypeCode)) return false;
  // 跨类型移动非法：inner 时目标是父须同类型，prev/next 时是兄弟须同类型
  if (target.roleTypeCode !== dragging.roleTypeCode) return false;
  // inner 到只读类型目标禁止
  if (type === "inner" && isReadonlyRoleType(target.roleTypeCode)) {
    return false;
  }
  return true;
}

// ========== 权限授予入口（4.1 v3，T-FE-036；跳转 /perm/grant 并预选角色） ==========

const router = useRouter();

/** 权限授予入口可用：ROLE:VIEW（授予页矩阵查看门禁，permission-grant.md §10 轨道 2）；BASIC_ROLE 预选（授予页 T-PERM-043 后仅展示 BASIC_ROLE） */
const canGrant = computed(() => hasPerms(PERMISSION_GRANT_PERMS.ROLE_VIEW));

/** 入口文案按 ROLE:MANAGE 二分（T-FE-055）：仅 VIEW 用户进授予页为只读矩阵，文案不承诺授予 */
const grantEntryLabel = computed(() =>
  resolveGrantEntryLabel(hasPerms(PERMISSION_GRANT_PERMS.ROLE_MANAGE))
);

function goPermissionGrant(node: RoleTreeNode) {
  router.push({
    path: "/perm/grant",
    query: {
      subjectType: "ROLE",
      ...(node.roleTypeCode === ROLE_TYPE_CODE.BASIC_ROLE && node.externalId
        ? { roleExternalId: node.externalId }
        : {})
    }
  });
}

/** 节点状态标签类型 */
function statusTagType(status: number) {
  return status === 1 ? "success" : "danger";
}
</script>

<template>
  <div class="role-page">
    <!-- 左侧角色树 -->
    <div class="tree-panel">
      <div class="tree-header">
        <span class="tree-title">角色树</span>
        <el-dropdown
          v-if="canAdd"
          trigger="click"
          @command="(cmd: RoleTypeCode) => onAddByType(cmd)"
        >
          <el-button type="primary" size="small" :icon="Plus">
            新增角色
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in addTypeMenu"
                :key="item.type"
                :command="item.type"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-input
        v-model="filterText"
        size="small"
        placeholder="搜索角色名称..."
        clearable
        class="tree-filter"
        @input="onFilterInput"
      />

      <el-scrollbar class="tree-scroll">
        <el-tree
          ref="treeRef"
          v-loading="loading"
          :data="roleTree"
          :props="treeProps"
          node-key="id"
          :filter-node-method="filterNode"
          default-expand-all
          :expand-on-click-node="false"
          highlight-current
          draggable
          :allow-drop="allowDrop"
          @node-click="handleNodeClick"
          @node-drop="handleNodeDrop"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="node-name" :title="data.name">{{ data.name }}</span>
              <el-tag
                :type="
                  isReadonlyRoleType(data.roleTypeCode) ? 'info' : 'primary'
                "
                size="small"
                effect="plain"
              >
                {{ ROLE_TYPE_LABEL[data.roleTypeCode] || data.roleTypeCode }}
              </el-tag>
              <el-tag
                :type="statusTagType(data.status)"
                size="small"
                effect="light"
              >
                {{ data.status === 1 ? "启用" : "禁用" }}
              </el-tag>
            </div>
          </template>
        </el-tree>
      </el-scrollbar>
    </div>

    <!-- 右侧详情区 -->
    <div class="right-area">
      <template v-if="selectedRole">
        <!-- 角色信息卡片 -->
        <div class="role-info-card">
          <div class="role-info-header">
            <div class="role-info-title">
              <span class="role-name">{{ selectedRole.name }}</span>
              <el-tag
                :type="
                  isReadonlyRoleType(selectedRole.roleTypeCode)
                    ? 'info'
                    : 'primary'
                "
                size="small"
                effect="plain"
              >
                {{
                  ROLE_TYPE_LABEL[selectedRole.roleTypeCode] ||
                  selectedRole.roleTypeCode
                }}
              </el-tag>
              <el-tag
                :type="statusTagType(selectedRole.status)"
                size="small"
                effect="light"
              >
                {{ selectedRole.status === 1 ? "启用" : "禁用" }}
              </el-tag>
              <span v-if="selectedRole.externalId" class="role-external-id">
                外部标识：{{ selectedRole.externalId }}
              </span>
            </div>
            <div class="role-info-actions">
              <el-button
                v-if="
                  canGrant && !isReadonlyRoleType(selectedRole.roleTypeCode)
                "
                type="primary"
                size="small"
                :icon="Key"
                @click="goPermissionGrant(selectedRole)"
              >
                {{ grantEntryLabel }}
              </el-button>
              <el-button
                v-if="canEdit && isNodeEditable(selectedRole)"
                type="primary"
                plain
                size="small"
                :icon="Edit"
                @click="openRoleForm('edit', selectedRole)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canEdit && isNodeEditable(selectedRole)"
                plain
                size="small"
                @click="handleToggleStatus(selectedRole)"
              >
                {{ selectedRole.status === 1 ? "禁用" : "启用" }}
              </el-button>
              <el-button
                v-if="canDelete && isNodeEditable(selectedRole)"
                type="danger"
                plain
                size="small"
                :icon="Delete"
                @click="handleDelete(selectedRole)"
              >
                删除
              </el-button>
            </div>
          </div>
          <div class="role-info-meta">
            <span class="meta-item">排序：{{ selectedRole.sortOrder }}</span>
            <span class="meta-item">角色ID：{{ selectedRole.id }}</span>
            <span
              v-if="isReadonlyRoleType(selectedRole.roleTypeCode)"
              class="meta-readonly"
            >
              该角色类型由组织同步自动生成，不可手工编辑
            </span>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <el-empty description="请选择左侧角色查看详情" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.role-page {
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: var(--space-2);
  height: calc(100vh - var(--header-offset));
  overflow: hidden;
}

/* 左侧树面板 */
.tree-panel {
  display: flex;
  flex-direction: column;
  padding: var(--space-3);
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.tree-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tree-filter {
  margin-bottom: var(--space-2);
}

.tree-scroll {
  flex: 1;
  min-height: 0;
}

.tree-node {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  align-items: center;
  padding-right: var(--space-2);

  .node-name {
    margin-right: var(--space-1);
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 13px;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }
}

/* 右侧详情区 */
.right-area {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
}

/* 角色信息卡片 */
.role-info-card {
  padding: var(--space-4);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
}

.role-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.role-info-title {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.role-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.role-external-id {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.role-info-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.role-info-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.meta-readonly {
  color: var(--el-color-warning);
}

.empty-state {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 → 同级看顺序，再加 tag 提升至 0,2,1 */
div.role-page.main-content {
  margin: var(--space-3);
}
</style>
