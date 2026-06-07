<script setup lang="ts">
import { ref, reactive, h, computed, watch } from "vue";
import { useUserManage } from "./utils/hook";
import UserDetailPanel from "./components/UserDetailPanel.vue";
import MemberTab from "./components/MemberTab.vue";
import PositionTab from "./components/PositionTab.vue";
import { ReOrgTreePanel } from "@/components/ReOrgTreePanel";
import { addDialog } from "@/components/ReDialog";
import { getOrgTree } from "@/api/user-manage";
import type { OrgTreeNode } from "@/api/user-manage";
import { OfficeBuilding, Edit, Plus } from "@element-plus/icons-vue";

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

// 组织树操作
function onNodeAdd(parentNode: any) {
  console.log("新增组织", parentNode);
  // TODO: P0-5 实现 OrgForm 弹窗
}

function onNodeEdit(node: any) {
  console.log("编辑组织", node);
  // TODO: P0-5 实现 OrgForm 弹窗
}

function onNodeDelete(node: any) {
  console.log("删除组织", node);
  // TODO: 调用 /org/delete API
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
            <el-tag size="small" type="info" effect="plain">
              {{ selectedOrg.code }}
            </el-tag>
          </div>
          <div class="org-info-actions">
            <el-button type="primary" plain size="small">
              <el-icon><Edit /></el-icon>
              编辑部门
            </el-button>
            <el-button type="primary" plain size="small">
              <el-icon><Plus /></el-icon>
              新增下级
            </el-button>
          </div>
        </div>
        <div class="org-info-body">
          <div class="org-info-item">
            <span class="label">上级部门</span>
            <span class="value">{{ selectedOrg.parentOrgId ? '...' : '根组织' }}</span>
          </div>
          <div class="org-info-item">
            <span class="label">部门类型</span>
            <span class="value">{{ selectedOrg.orgType === 1 ? '普通组织' : '岗位' }}</span>
          </div>
          <div class="org-info-item">
            <span class="label">状态</span>
            <el-tag :type="selectedOrg.status === 1 ? 'success' : 'danger'" size="small">
              {{ selectedOrg.status === 1 ? '启用' : '禁用' }}
            </el-tag>
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
  grid-template-columns: 200px 1fr;
  gap: 8px;
  height: calc(100vh - 150px);
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
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.org-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.org-info-title {
  display: flex;
  align-items: center;
  gap: 8px;
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

.org-info-body {
  display: flex;
  gap: 32px;
}

.org-info-item {
  display: flex;
  align-items: center;
  gap: 8px;

  .label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .value {
    font-size: 13px;
    color: var(--el-text-color-primary);
  }
}

/* Tab 区 */
.content-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;

  :deep(.el-tabs__header) {
    margin: 0 12px;
    padding-top: 8px;
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
  margin: 12px;
}
</style>
