<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getRoleTree,
  getExtraRoles,
  addExtraRole,
  removeExtraRole,
  transformRoleTreeResponse,
  flattenRoleTree,
  ROLE_TYPE_CODES,
  type RoleTreeNode,
  type RoleSummary
} from "@/api/perm/role";

defineOptions({
  name: "ExtraRolesPanel"
});

const props = defineProps<{
  roleId: number | null;
  roleTypeCode: string;
  roleExternalId: string;
}>();

// ========== 状态定义 ==========

const loading = ref(false);
const extraRolesList = ref<Array<RoleSummary>>([]);
const allBasicRoles = ref<Array<RoleTreeNode>>([]);
const selectedBasicRole = ref<RoleTreeNode | null>(null);

// ========== 加载所有基本角色 ==========

const loadAllBasicRoles = async () => {
  try {
    const res = await getRoleTree();
    if (res.success) {
      const tree = transformRoleTreeResponse(res);
      allBasicRoles.value = flattenRoleTree(tree).filter(
        r => r.roleTypeCode === ROLE_TYPE_CODES.BASIC_ROLE
      );
    }
  } catch {
    ElMessage.error("加载基本角色列表失败");
  }
};

// ========== 加载额外角色配置 ==========

const loadExtraRoles = async () => {
  if (!props.roleTypeCode || !props.roleExternalId) return;

  loading.value = true;
  try {
    const res = await getExtraRoles({
      groupRoleTypeCode: props.roleTypeCode,
      groupRoleExternalId: props.roleExternalId
    });
    if (res.success) {
      extraRolesList.value = res.data.items || [];
    }
  } catch {
    ElMessage.error("加载额外角色配置失败");
  } finally {
    loading.value = false;
  }
};

// ========== 添加基本角色 ==========

const handleAddExtraRole = async () => {
  if (!selectedBasicRole.value) {
    ElMessage.warning("请选择要添加的基本角色");
    return;
  }

  try {
    const res = await addExtraRole({
      groupRoleTypeCode: props.roleTypeCode,
      groupRoleExternalId: props.roleExternalId,
      basicRoleTypeCode: selectedBasicRole.value.roleTypeCode,
      basicRoleExternalId: selectedBasicRole.value.externalId
    });
    if (res.success) {
      ElMessage.success("添加成功");
      selectedBasicRole.value = null;
      await loadExtraRoles();
    }
  } catch {
    ElMessage.error("添加失败");
  }
};

// ========== 移除基本角色 ==========

const handleRemoveExtraRole = async (role: RoleSummary) => {
  try {
    await ElMessageBox.confirm("确认移除该基本角色?", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });

    const res = await removeExtraRole({
      groupRoleTypeCode: props.roleTypeCode,
      groupRoleExternalId: props.roleExternalId,
      basicRoleTypeCode: role.roleTypeCode,
      basicRoleExternalId: role.externalId
    });
    if (res.success) {
      ElMessage.success("移除成功");
      await loadExtraRoles();
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("移除失败");
    }
  }
};

// ========== 监听角色变化 ==========

watch(
  () => [props.roleTypeCode, props.roleExternalId],
  () => {
    loadExtraRoles();
    loadAllBasicRoles();
  },
  { immediate: true }
);
</script>

<template>
  <div class="extra-roles-panel">
    <!-- 添加基本角色 -->
    <div class="flex gap-2 mb-4">
      <el-select
        v-model="selectedBasicRole"
        placeholder="选择基本角色"
        clearable
        filterable
        class="w-[300px]"
        value-key="id"
      >
        <el-option
          v-for="role in allBasicRoles"
          :key="role.id"
          :label="`${role.name} (${role.externalId})`"
          :value="role"
        />
      </el-select>
      <el-button type="primary" size="small" @click="handleAddExtraRole">
        添加
      </el-button>
    </div>

    <!-- 已配置的额外角色列表 -->
    <el-table v-loading="loading" :data="extraRolesList" border stripe>
      <el-table-column prop="name" label="角色名称" />
      <el-table-column prop="roleTypeCode" label="角色类型" />
      <el-table-column prop="externalId" label="角色编码" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            type="danger"
            link
            size="small"
            @click="handleRemoveExtraRole(row)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="extraRolesList.length === 0 && !loading"
      class="text-center py-10 text-gray-500"
    >
      暂未配置额外基本角色
    </div>
  </div>
</template>
