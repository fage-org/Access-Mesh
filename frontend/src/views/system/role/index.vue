<script setup lang="ts">
import { ref, computed } from "vue";
import { h } from "vue";
import { useRoleManage } from "./utils/hook";
import RoleForm from "./components/RoleForm.vue";
import { addDialog } from "@/components/ReDialog";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { ROLE_MANAGE_PERMS } from "./utils/perms";
import {
  ROLE_TYPE_LABEL,
  ROLE_TYPE_CODE,
  MANAGEABLE_ROLE_TYPES,
  type RoleTreeNode,
  type RoleTypeCode,
  type RoleSummaryResp
} from "@/api/role-manage";
import { isTypeRootNode, isReadonlyRoleType } from "./utils/types";
import { Plus, Edit, Delete, Setting } from "@element-plus/icons-vue";

defineOptions({
  name: "SystemRole"
});

const {
  roleTree,
  loading,
  selectedRole,
  extraRoles,
  extraRolesLoading,
  filterText,
  treeProps,
  loadTree,
  filterNode,
  handleNodeClick,
  handleAddExtraRole,
  handleRemoveExtraRole,
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
const canGrant = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_GRANT));

// 监听过滤文本
function onFilterInput(val: string) {
  treeRef.value?.filter(val);
}

// ========== 新建/编辑弹窗 ==========

function openRoleForm(mode: "create" | "edit", node?: RoleTreeNode | null) {
  const isEdit = mode === "edit";
  // 新建时若点了类型虚拟根，沿用其类型
  const defaultType = (
    !isEdit && node && !isTypeRootNode(node)
      ? node.roleTypeCode
      : !isEdit && node && isTypeRootNode(node)
        ? (node.roleTypeCode as RoleTypeCode)
        : ROLE_TYPE_CODE.BASIC_ROLE
  ) as RoleTypeCode;

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
        initialData: isEdit ? node : null,
        defaultRoleTypeCode: defaultType,
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
        isEdit ? node?.id : undefined
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
  // 找到对应类型虚拟根作为 parentNode
  const typeRoot = findTypeRoot(type);
  openRoleForm("create", typeRoot);
}

function findTypeRoot(type: RoleTypeCode): RoleTreeNode | null {
  for (const root of roleTree.value) {
    const found = findTypeRootInNode(root, type);
    if (found) return found;
  }
  return null;
}

function findTypeRootInNode(
  node: RoleTreeNode,
  type: RoleTypeCode
): RoleTreeNode | null {
  if (isTypeRootNode(node) && node.roleTypeCode === type) return node;
  for (const child of node.children || []) {
    const found = findTypeRootInNode(child, type);
    if (found) return found;
  }
  return null;
}

// ========== 跳转权限授予 ==========

function goGrant() {
  if (!selectedRole.value) return;
  // 跳转 4.1 权限授予页（T-FE-014，待实现）；当前仅提示
  message(`配权功能待 4.1 权限授予页实现（角色：${selectedRole.value.name}）`, {
    type: "info"
  });
}

// ========== 可用基本角色（分组角色添加额外角色候选） ==========

const availableBasicRoles = computed<RoleSummaryResp[]>(() => {
  const result: RoleSummaryResp[] = [];
  function collect(node: RoleTreeNode) {
    if (
      node.roleTypeCode === ROLE_TYPE_CODE.BASIC_ROLE &&
      !isTypeRootNode(node)
    ) {
      result.push({
        id: node.id,
        roleTypeCode: node.roleTypeCode,
        externalId: node.externalId || "",
        name: node.name
      });
    }
    for (const child of node.children || []) collect(child);
  }
  for (const root of roleTree.value) collect(root);
  return result;
});

/** 当前分组角色未添加的基本角色候选 */
const candidateBasicRoles = computed(() => {
  const addedIds = new Set(extraRoles.value.map(r => r.id));
  return availableBasicRoles.value.filter(r => !addedIds.has(r.id));
});

const showAddExtraPopover = ref(false);

function onAddExtra(basic: RoleSummaryResp) {
  handleAddExtraRole(basic);
  showAddExtraPopover.value = false;
}

// ========== 树节点操作权限 ==========

/** 节点是否可编辑（非类型根 + 非只读类型） */
function isNodeEditable(node: RoleTreeNode) {
  return !isTypeRootNode(node) && !isReadonlyRoleType(node.roleTypeCode);
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
          @node-click="handleNodeClick"
          @node-drop="handleNodeDrop"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="node-name" :title="data.name">{{ data.name }}</span>
              <el-tag
                v-if="!isTypeRootNode(data)"
                :type="
                  isReadonlyRoleType(data.roleTypeCode) ? 'info' : 'primary'
                "
                size="small"
                effect="plain"
              >
                {{ ROLE_TYPE_LABEL[data.roleTypeCode] || data.roleTypeCode }}
              </el-tag>
              <el-tag
                v-if="!isTypeRootNode(data)"
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
      <template v-if="selectedRole && !isTypeRootNode(selectedRole)">
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
                v-if="
                  canGrant && !isReadonlyRoleType(selectedRole.roleTypeCode)
                "
                type="primary"
                plain
                size="small"
                :icon="Setting"
                @click="goGrant"
              >
                配权
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

        <!-- 分组角色额外角色管理 -->
        <div
          v-if="selectedRole.roleTypeCode === 'GROUP_ROLE'"
          class="extra-roles-section"
        >
          <div class="section-header">
            <span class="section-title">额外基本角色</span>
            <el-popover
              v-model:visible="showAddExtraPopover"
              trigger="click"
              placement="bottom-end"
              :width="280"
              :show-arrow="false"
            >
              <template #reference>
                <el-button
                  v-if="canEdit"
                  type="primary"
                  size="small"
                  plain
                  :icon="Plus"
                  :disabled="candidateBasicRoles.length === 0"
                >
                  添加
                </el-button>
              </template>
              <div class="extra-candidate-list">
                <div
                  v-for="basic in candidateBasicRoles"
                  :key="basic.id"
                  class="candidate-item"
                  @click="onAddExtra(basic)"
                >
                  <span>{{ basic.name }}</span>
                  <span class="candidate-ext">{{ basic.externalId }}</span>
                </div>
                <div v-if="candidateBasicRoles.length === 0" class="empty-tip">
                  无可添加的基本角色
                </div>
              </div>
            </el-popover>
          </div>
          <el-table
            v-loading="extraRolesLoading"
            :data="extraRoles"
            size="small"
            empty-text="暂无额外角色"
          >
            <el-table-column prop="name" label="角色名称" min-width="120" />
            <el-table-column
              prop="externalId"
              label="外部标识"
              min-width="120"
            />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canEdit"
                  link
                  type="danger"
                  size="small"
                  @click="handleRemoveExtraRole(row)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
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

/* 额外角色区 */
.extra-roles-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.extra-candidate-list {
  max-height: var(--popover-max-height);
  overflow-y: auto;
}

.candidate-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2);
  cursor: pointer;
  border-radius: var(--radius-sm);

  &:hover {
    background: var(--el-fill-color-light);
  }

  .candidate-ext {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.empty-tip {
  padding: var(--space-3);
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
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
