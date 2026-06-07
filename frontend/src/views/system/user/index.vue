<script setup lang="ts">
import { useUserManage } from "./utils/hook";
import UserDetailPanel from "./components/UserDetailPanel.vue";
import MemberTab from "./components/MemberTab.vue";
import PositionTab from "./components/PositionTab.vue";
import OrgForm from "./components/OrgForm.vue";
import { ReOrgTreePanel } from "@/components/ReOrgTreePanel";
import { addDialog } from "@/components/ReDialog";
import { getOrgTree, createOrg, updateOrg, deleteOrg } from "@/api/user-manage";
import type { OrgTreeNode } from "@/api/user-manage";
import { OfficeBuilding, Edit, Plus } from "@element-plus/icons-vue";
import { message } from "@/utils/message";
import { h, ref } from "vue";

defineOptions({
  name: "SystemUser"
});

const activeTab = ref("member");

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
            orgType: formData.orgType,
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
</script>

<template>
  <div class="user-page">
    <!-- 左侧组织树 -->
    <ReOrgTreePanel
      ref="orgTreePanelRef"
      class="tree-panel"
      :editable="true"
      :org-type-filter="[1]"
      @org-change="onOrgChange"
      @node-add="onNodeAdd"
      @node-edit="onNodeEdit"
      @node-delete="onNodeDelete"
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
              type="primary"
              plain
              size="small"
              @click="onNodeEdit(selectedOrg)"
            >
              <el-icon><Edit /></el-icon>
              编辑部门
            </el-button>
            <el-button
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
        <el-tab-pane label="岗位管理" name="position">
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
  padding: 16px;
  margin: 12px 12px 0;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.org-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.org-info-title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
  gap: 8px;
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
