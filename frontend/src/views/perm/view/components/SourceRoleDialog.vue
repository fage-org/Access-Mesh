<script setup lang="ts">
import { ref } from "vue";
import {
  type EffectivePermissionItem,
  type SourceRole
} from "@/api/perm/permissionView";
import { getResourceTypeTag } from "@/api/perm/resource";

defineOptions({
  name: "SourceRoleDialog"
});

// ========== 组件暴露接口类型 ==========

export interface SourceRoleDialogExpose {
  openDialog: (item: EffectivePermissionItem) => void;
}

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const currentPermission = ref<EffectivePermissionItem | null>(null);

// ========== 打开弹窗 ==========

const openDialog = (item: EffectivePermissionItem) => {
  currentPermission.value = item;
  dialogVisible.value = true;
};

// ========== 格式化路径 ==========

const formatViaPath = (via: Array<string>): string => {
  if (!via || via.length === 0) return "直接授权";
  return via.join(" → ");
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="权限来源角色"
    width="600px"
    :close-on-click-modal="false"
  >
    <div v-if="currentPermission" class="source-role-dialog">
      <!-- 权限信息 -->
      <div class="mb-4 p-3 bg-gray-50 rounded">
        <div class="flex items-center gap-4">
          <el-tag size="small">
            {{ getResourceTypeTag(currentPermission.resourceTypeCode).text }}
          </el-tag>
          <span class="font-medium">{{ currentPermission.resourceName }}</span>
          <span class="text-gray-500">{{
            currentPermission.resourceCode
          }}</span>
        </div>
        <div class="mt-2">
          <span class="text-gray-600">操作权限:</span>
          <el-tag
            v-for="op in currentPermission.operationCodes"
            :key="op"
            size="small"
            class="ml-1"
          >
            {{ op }}
          </el-tag>
        </div>
      </div>

      <!-- 来源角色列表 -->
      <div class="mb-2 font-medium text-gray-700">
        来源角色 (共 {{ currentPermission.sourceRoleCount }} 个)
        <span
          v-if="currentPermission.sourceRolesTruncated"
          class="text-gray-500 text-sm"
        >
          (已截断，仅显示前 {{ currentPermission.sourceRoles.length }} 个)
        </span>
      </div>

      <el-table :data="currentPermission.sourceRoles" stripe border>
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="roleExternalId" label="角色编码" width="150" />
        <el-table-column prop="roleTypeCode" label="角色类型" width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.roleTypeCode === 'GROUP_ROLE' ? 'warning' : 'success'"
              size="small"
            >
              {{ row.roleTypeCode === "GROUP_ROLE" ? "分组角色" : "基本角色" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="via" label="授权路径" min-width="200">
          <template #default="{ row }">
            <span class="text-gray-600">{{ formatViaPath(row.via) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 无来源角色 -->
      <div
        v-if="currentPermission.sourceRoles.length === 0"
        class="text-center py-4 text-gray-500"
      >
        该权限无来源角色记录
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="dialogVisible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>
