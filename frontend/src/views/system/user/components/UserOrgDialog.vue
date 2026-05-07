<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage, type FormInstance } from "element-plus";
import {
  getOrgTree,
  setPrimaryOrg,
  assignUserToOrgs,
  getUserOrgs,
  type OrgNode
} from "@/api/admin/org";

defineOptions({
  name: "UserOrgDialog"
});

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const loading = ref(false);
const orgTreeData = ref<Array<OrgNode>>([]);
const userId = ref<number | null>(null);

const form = reactive({
  primaryOrgId: null as number | null,
  assignedOrgIds: [] as Array<number>
});

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "orgName",
  value: "id"
};

// ========== 加载组织树 ==========

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

// ========== 加载用户当前组织 ==========

const loadUserOrg = async () => {
  if (!userId.value) return;
  loading.value = true;
  try {
    const res = await getUserOrgs({ id: userId.value });
    if (res.success) {
      const orgs = res.data;
      // 设置主组织
      const primaryOrg = orgs.find(o => o.isPrimary);
      form.primaryOrgId = primaryOrg?.orgId || null;
      // 设置已分配组织
      form.assignedOrgIds = orgs.map(o => o.orgId);
    }
  } catch (error) {
    ElMessage.error("加载用户组织信息失败");
  } finally {
    loading.value = false;
  }
};

// ========== 打开弹窗 ==========

const openDialog = (id: number) => {
  userId.value = id;
  dialogVisible.value = true;
  resetForm();
  loadOrgTree();
  loadUserOrg();
};

const resetForm = () => {
  form.primaryOrgId = null;
  form.assignedOrgIds = [];
};

// ========== 提交 ==========

const handleSubmit = async () => {
  if (!userId.value) return;

  loading.value = true;
  try {
    // 1. 分配用户到组织
    if (form.assignedOrgIds.length > 0) {
      await assignUserToOrgs({
        userId: userId.value,
        orgIds: form.assignedOrgIds
      });
    }

    // 2. 设置主组织
    if (form.primaryOrgId) {
      await setPrimaryOrg({
        userId: userId.value,
        orgId: form.primaryOrgId
      });
    }

    ElMessage.success("组织配置成功");
    dialogVisible.value = false;
    emit("success");
  } catch (error) {
    ElMessage.error("组织配置失败");
  } finally {
    loading.value = false;
  }
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="配置用户组织"
    width="500px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form v-loading="loading" label-width="100px">
      <el-form-item label="所属组织">
        <el-tree-select
          v-model="form.assignedOrgIds"
          :data="orgTreeData"
          :props="defaultProps"
          node-key="id"
          multiple
          check-strictly
          placeholder="请选择所属组织"
          clearable
          class="w-full"
        />
      </el-form-item>

      <el-form-item label="主组织">
        <el-tree-select
          v-model="form.primaryOrgId"
          :data="orgTreeData"
          :props="defaultProps"
          node-key="id"
          check-strictly
          placeholder="请选择主组织"
          clearable
          class="w-full"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
