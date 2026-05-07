<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getOrgTree,
  getOrgDetail,
  deleteOrg,
  type OrgNode
} from "@/api/admin/org";
import OrgTree from "./components/OrgTree.vue";
import OrgForm from "./components/OrgForm.vue";
import OrgMembers from "./components/OrgMembers.vue";
import OrgTreeConfig from "./components/OrgTreeConfig.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "OrgManagement"
});

// ========== 状态定义 ==========

const treeData = ref<Array<OrgNode>>([]);
const selectedOrgId = ref<number | null>(null);
const selectedOrgDetail = ref<OrgNode | null>(null);
const loading = ref(false);
const activeTab = ref("detail");

// ========== 权限计算 ==========

const canCreate = hasPerms(PERM_CODES.SYS_ORG_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_ORG_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_ORG_DELETE);

// ========== 数据加载 ==========

const loadOrgTree = async () => {
  loading.value = true;
  try {
    const res = await getOrgTree();
    if (res.success) {
      treeData.value = res.data;
    }
  } catch (error) {
    console.error("[OrgManagement] Load org tree failed:", error);
    ElMessage.error("加载组织树失败");
  } finally {
    loading.value = false;
  }
};

const loadOrgDetail = async (orgId: number) => {
  try {
    const res = await getOrgDetail({ id: orgId });
    if (res.success) {
      selectedOrgDetail.value = res.data;
    }
  } catch (error) {
    console.error("[OrgManagement] Load org detail failed:", error);
    ElMessage.error("加载组织详情失败");
  }
};

// ========== 组织树交互 ==========

const handleOrgNodeClick = async (orgId: number) => {
  selectedOrgId.value = orgId;
  activeTab.value = "detail";
  await loadOrgDetail(orgId);
};

const handleCreateRoot = () => {
  selectedOrgId.value = null;
  selectedOrgDetail.value = null;
  activeTab.value = "detail";
};

const handleCreateChild = (parentId: number) => {
  selectedOrgId.value = parentId;
  selectedOrgDetail.value = null;
  activeTab.value = "detail";
};

const handleDeleteOrg = async (orgId: number) => {
  try {
    await ElMessageBox.confirm(
      "确认删除该组织?删除后子组织将一并删除",
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await deleteOrg({ id: orgId });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadOrgTree();
      selectedOrgId.value = null;
      selectedOrgDetail.value = null;
    }
  } catch (error) {
    // 区分用户取消和API错误
    if (error !== "cancel") {
      console.error("[OrgManagement] Delete org failed:", error);
      ElMessage.error("删除组织失败");
    }
  }
};

// ========== 表单成功回调 ==========

const handleFormSuccess = async () => {
  await loadOrgTree();
  if (selectedOrgId.value) {
    await loadOrgDetail(selectedOrgId.value);
  }
};

// ========== 初始化 ==========

onMounted(() => {
  loadOrgTree();
});
</script>

<template>
  <div class="org-management">
    <div class="flex h-full">
      <!-- 左侧组织树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            :disabled="!canCreate"
            @click="handleCreateRoot"
          >
            新增根节点
          </el-button>
        </div>
        <OrgTree
          :data="treeData"
          :loading="loading"
          :selected-org-id="selectedOrgId"
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

          <el-tab-pane
            label="组织成员"
            name="members"
            :disabled="!selectedOrgId"
          >
            <OrgMembers :org-id="selectedOrgId" />
          </el-tab-pane>

          <el-tab-pane label="树配置" name="config" :disabled="!selectedOrgId">
            <OrgTreeConfig :org-id="selectedOrgId" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.org-management {
  height: calc(100vh - 100px);
  padding: 20px;
}
</style>
