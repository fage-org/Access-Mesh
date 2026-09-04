<script setup lang="ts">
import { useUserManage } from "./utils/hook";
import UserDetailPanel from "./components/UserDetailPanel.vue";
import MemberTab from "./components/MemberTab.vue";
import PositionTab from "./components/PositionTab.vue";
import OrgForm from "./components/OrgForm.vue";
import { ReOrgTreePanel } from "@/components/ReOrgTreePanel";
import { addDialog } from "@/components/ReDialog";
import { createOrg, updateOrg, deleteOrg } from "@/api/user-manage";
import type { OrgTreeNode } from "@/api/user-manage";
import { OfficeBuilding, Edit, Plus, Key } from "@element-plus/icons-vue";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { ORG_USER_PERMS } from "./utils/perms";
import { ROLE_MANAGE_PERMS } from "@/views/system/role/utils/perms";
import { useRouter } from "vue-router";
import { h, ref, computed, watch } from "vue";

defineOptions({
  name: "SystemUser"
});

const activeTab = ref("member");

// ========== 权限门控 ==========
// 与 docs/design/org-user-permission-contract.md §4 矩阵 A/D 区对齐。
// 树编辑能力对其中任意一项写权限敏感（add/edit/delete 三选一即提供 hover 操作面板）。
const canAddOrg = computed(() => hasPerms(ORG_USER_PERMS.ORG_ADD));
const canEditOrg = computed(() => hasPerms(ORG_USER_PERMS.ORG_EDIT));
const canDeleteOrg = computed(() => hasPerms(ORG_USER_PERMS.ORG_DELETE));
/** 岗位 Tab 整体可见性（无 view 权限直接隐藏 Tab） */
const canViewPosition = computed(() => hasPerms(ORG_USER_PERMS.POSITION_VIEW));

// ========== 权限授予入口（4.1 v3 组织入口，T-FE-037） ==========

const router = useRouter();

/**
 * 权限授予入口可用：ROLE:VIEW（授予页矩阵查看门禁，permission-grant.md §10 轨道 2；
 * 矩阵查看/授权动作统一用对目标抽象角色的 ROLE:VIEW/ROLE:MANAGE）。
 */
const canGrantPerm = computed(() => hasPerms(ROLE_MANAGE_PERMS.ROLE_VIEW));

/** 跳转授权页组织入口并预选当前组织（GrantContext roleExternalId = String(sys_org.id)） */
function goPermissionGrant() {
  router.push({
    path: "/perm/grant",
    query: {
      subjectType: "ORG",
      ...(selectedOrg.value?.id != null
        ? { roleExternalId: String(selectedOrg.value.id) }
        : {})
    }
  });
}

// 角色热切换（mock）后，若用户停留在 position tab 而权限消失，自动切回 member 避免空白页
watch(canViewPosition, visible => {
  if (!visible && activeTab.value === "position") {
    activeTab.value = "member";
  }
});

const {
  selectedOrgId,
  tableData,
  loading,
  searchForm,
  pagination,
  loadTable,
  onSearch,
  onReset,
  onPageChange,
  onPageSizeChange,
  handleCreate,
  handleUpdate,
  handleDelete
} = useUserManage();

const orgTreePanelRef = ref<InstanceType<typeof ReOrgTreePanel>>();

// 选中组织详情
const selectedOrg = ref<OrgTreeNode | null>(null);

function onOrgChange(orgId: number | null) {
  selectedOrgId.value = orgId;
  pagination.page = 1;
  loadTable();
  // 加载选中组织详情
  if (orgId && orgTreePanelRef.value?.orgTree) {
    selectedOrg.value = findOrgById(orgTreePanelRef.value.orgTree, orgId);
  } else {
    selectedOrg.value = null;
  }
}

function findOrgById(nodes: OrgTreeNode[], id: number): OrgTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const found = findOrgById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

// 查找父组织名称
function findParentOrgName(
  nodes: OrgTreeNode[],
  parentId: number | null
): string {
  if (!parentId) return "根组织";
  for (const node of nodes) {
    if (node.id === parentId) return node.orgName;
    if (node.children) {
      const found = findParentOrgName(node.children, parentId);
      if (found) return found;
    }
  }
  return "未知";
}

function openUserDetail(row: any) {
  addDialog({
    title: `${row.name} 的用户信息`,
    width: "520px",
    contentRenderer: () =>
      h(UserDetailPanel, {
        user: row,
        showPermission: true,
        orgTree: orgTreePanelRef.value?.orgTree,
        onSyncUserOrgs: orgs => {
          row.orgs = orgs;
        }
      })
  });
}

// 组织表单弹窗
function openOrgForm(mode: "create" | "edit", node?: OrgTreeNode) {
  const isEdit = mode === "edit";
  const parentOrgId = isEdit ? (node?.parentOrgId ?? null) : (node?.id ?? null);
  const parentOrgName = isEdit
    ? node?.parentOrgId
      ? findParentOrgName(
          orgTreePanelRef.value?.orgTree || [],
          node.parentOrgId
        )
      : "根组织"
    : (node?.orgName ?? "");

  // 编辑时准备初始数据
  const initialData =
    isEdit && node
      ? {
          orgName: node.orgName,
          code: node.code,
          orgType: node.orgType,
          parentOrgId: node.parentOrgId,
          status: node.status,
          sort: node.sort
        }
      : undefined;

  // 用于保存表单组件引用
  let formRef: any = null;

  addDialog({
    title: isEdit ? "编辑组织" : node ? "新增子组织" : "新增组织",
    width: "480px",
    contentRenderer: () =>
      h(OrgForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        initialData,
        parentOrgId,
        parentOrgName: isEdit ? parentOrgName : (node?.orgName ?? "")
      }),
    beforeSure: async (done: Function) => {
      if (!formRef) {
        done();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) return;

      const formData = formRef.getFormData();
      try {
        if (isEdit && node) {
          await updateOrg({
            id: node.id,
            orgName: formData.orgName,
            code: formData.code,
            parentOrgId: formData.parentOrgId,
            status: formData.status,
            sort: formData.sort
          });
          message("更新成功", { type: "success" });
        } else {
          await createOrg(formData);
          message("创建成功", { type: "success" });
        }
        // 刷新组织树
        orgTreePanelRef.value?.loadTree?.();
        done();
      } catch (error: any) {
        message(error.message || "操作失败", { type: "error" });
      }
    }
  });
}

// 组织树操作
function onNodeAdd(parentNode: OrgTreeNode | null) {
  openOrgForm("create", parentNode ?? undefined);
}

function onNodeEdit(node: OrgTreeNode) {
  openOrgForm("edit", node);
}

async function onNodeDelete(node: OrgTreeNode) {
  try {
    await deleteOrg(node.id);
    message("删除成功", { type: "success" });
    // 刷新组织树
    orgTreePanelRef.value?.loadTree?.();
    // 如果删除的是当前选中的组织，清空选中
    if (selectedOrgId.value === node.id) {
      selectedOrgId.value = null;
      selectedOrg.value = null;
    }
  } catch (error: any) {
    message(error.message || "删除失败", { type: "error" });
  }
}

/**
 * 组织拖拽 → 调用 /org/update 修改 parentOrgId（契约 §4 A 区第 5 行：
 * 移动节点 = ORG:UPDATE），不调则刷新即丢
 */
async function onNodeMove(node: OrgTreeNode, targetParentId: number) {
  try {
    await updateOrg({
      id: node.id,
      orgName: node.orgName,
      code: node.code,
      parentOrgId: targetParentId,
      status: node.status,
      sort: node.sort
    });
    message("移动成功", { type: "success" });
    orgTreePanelRef.value?.loadTree?.();
  } catch (error: any) {
    message(error.message || "移动失败", { type: "error" });
    // 失败时刷新树以恢复 UI 状态
    orgTreePanelRef.value?.loadTree?.();
  }
}
</script>

<template>
  <div class="user-page">
    <!-- 左侧组织树 -->
    <ReOrgTreePanel
      ref="orgTreePanelRef"
      class="tree-panel"
      :can-add="canAddOrg"
      :can-edit="canEditOrg"
      :can-delete="canDeleteOrg"
      :org-type-filter="[1]"
      @org-change="onOrgChange"
      @node-add="onNodeAdd"
      @node-edit="onNodeEdit"
      @node-delete="onNodeDelete"
      @node-move="onNodeMove"
    />

    <!-- 右侧内容区 -->
    <div class="right-area">
      <!-- 顶部组织信息卡片 -->
      <div v-if="selectedOrg" class="org-info-card">
        <div class="org-info-header">
          <div class="org-info-title">
            <el-icon class="org-icon"><OfficeBuilding /></el-icon>
            <span class="org-name">{{ selectedOrg.orgName }}</span>
            <el-tag size="small" type="info" effect="plain">{{
              selectedOrg.code
            }}</el-tag>
            <el-tag
              :type="selectedOrg.status === 1 ? 'success' : 'danger'"
              size="small"
              effect="light"
            >
              {{ selectedOrg.status === 1 ? "启用" : "禁用" }}
            </el-tag>
            <span class="org-parent-info">
              上级部门：{{
                selectedOrg.parentOrgId
                  ? findParentOrgName(
                      orgTreePanelRef?.orgTree || [],
                      selectedOrg.parentOrgId
                    )
                  : "根组织"
              }}
            </span>
          </div>
          <div class="org-info-actions">
            <el-button
              v-if="canGrantPerm"
              type="primary"
              size="small"
              :icon="Key"
              @click="goPermissionGrant"
            >
              权限授予
            </el-button>
            <el-button
              v-if="canEditOrg"
              type="primary"
              plain
              size="small"
              @click="onNodeEdit(selectedOrg)"
            >
              <el-icon><Edit /></el-icon>
              编辑部门
            </el-button>
            <el-button
              v-if="canAddOrg"
              type="primary"
              plain
              size="small"
              @click="onNodeAdd(selectedOrg)"
            >
              <el-icon><Plus /></el-icon>
              新增下级
            </el-button>
          </div>
        </div>
      </div>

      <!-- Tab 区 -->
      <el-tabs v-model="activeTab" class="content-tabs">
        <el-tab-pane label="成员管理" name="member">
          <MemberTab
            :org-id="selectedOrgId"
            :org-tree="orgTreePanelRef?.orgTree"
            @open-user-detail="openUserDetail"
          />
        </el-tab-pane>
        <el-tab-pane v-if="canViewPosition" label="岗位管理" name="position">
          <PositionTab :org-id="selectedOrgId" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.user-page {
  display: grid;
  grid-template-columns: minmax(180px, 240px) 1fr;
  gap: var(--space-2);
  height: calc(100vh - var(--header-offset));
  overflow: hidden;
}

.tree-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
}

.right-area {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
}

/* 顶部组织信息卡片 */
.org-info-card {
  padding: var(--space-4);
  margin: var(--space-3) var(--space-3) 0;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.org-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.org-info-title {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.org-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.org-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.org-info-actions {
  display: flex;
  gap: var(--space-2);
}

.org-parent-info {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* Tab 区 */
.content-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;

  :deep(.el-tabs__header) {
    padding-top: 8px;
    margin: 0 12px;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  :deep(.el-tab-pane) {
    height: 100%;
    overflow: hidden;
  }
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 → 同级看顺序，再加 tag 提升至 0,2,1 */
div.user-page.main-content {
  margin: var(--space-3);
}
</style>
