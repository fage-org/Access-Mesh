<script setup lang="ts">
import { computed } from "vue";
import {
  type EffectivePermissionItem,
  type SourceRole
} from "@/api/perm/permissionView";
import { getResourceTypeTag } from "@/api/perm/resource";

defineOptions({
  name: "PermissionResultTable"
});

const props = defineProps<{
  data: Array<EffectivePermissionItem>;
  loading: boolean;
  total: number;
  pageNum: number;
  pageSize: number;
  showSourceRoles: boolean;
}>();

const emit = defineEmits<{
  viewSourceRoles: [item: EffectivePermissionItem];
  pageChange: [page: number];
  sizeChange: [size: number];
}>();

// ========== 表格列定义 ==========

const handleViewSourceRoles = (row: EffectivePermissionItem) => {
  emit("viewSourceRoles", row);
};

const handlePageChange = (page: number) => {
  emit("pageChange", page);
};

const handleSizeChange = (size: number) => {
  emit("sizeChange", size);
};

// ========== 格式化函数 ==========

const formatSourceRoles = (roles: Array<SourceRole>): string => {
  if (!roles || roles.length === 0) return "-";
  const names = roles.map(r => r.roleName);
  return names.join(", ");
};
</script>

<template>
  <div class="permission-result-table">
    <el-table v-loading="props.loading" :data="props.data" stripe>
      <el-table-column prop="resourceTypeCode" label="资源类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">
            {{ getResourceTypeTag(row.resourceTypeCode).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceCode" label="资源编码" width="150" />
      <el-table-column prop="resourceName" label="资源名称" min-width="200" />
      <el-table-column prop="operationCodes" label="操作权限" width="200">
        <template #default="{ row }">
          <el-tag
            v-for="op in row.operationCodes"
            :key="op"
            size="small"
            class="mr-1"
          >
            {{ op }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="scopeAll" label="范围" width="80">
        <template #default="{ row }">
          <el-tag :type="row.scopeAll ? 'success' : 'info'" size="small">
            {{ row.scopeAll ? "全部" : "限定" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="props.showSourceRoles"
        prop="sourceRoles"
        label="来源角色"
        min-width="150"
      >
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <span class="text-gray-600">
              {{ formatSourceRoles(row.sourceRoles) }}
            </span>
            <el-button
              v-if="row.sourceRoleCount > 0"
              type="primary"
              link
              size="small"
              @click="handleViewSourceRoles(row)"
            >
              查看({{ row.sourceRoleCount }}
              {{ row.sourceRolesTruncated ? "+" : "" }})
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="flex justify-end mt-4">
      <el-pagination
        :current-page="props.pageNum"
        :page-size="props.pageSize"
        :total="props.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 无数据提示 -->
    <div
      v-if="props.data.length === 0 && !props.loading"
      class="text-center py-10 text-gray-500"
    >
      请选择用户/角色并点击"查询权限"查看结果
    </div>
  </div>
</template>
