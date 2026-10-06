<script setup lang="ts">
import { onMounted, ref } from "vue";
import { listSyncStatus, type SyncStatus } from "@/api/sync-status";
import { usePagedList } from "@/utils/list-load";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_CODE } from "@/constants/access";
defineOptions({ name: "SystemSyncStatus" });
const sourceService = ref("");
const kinds: Record<string, string> = {
  ABSTRACT_USER: "主体",
  ABSTRACT_ROLE: "角色",
  USER_ROLE: "成员关系",
  RESOURCE_ENTITY: "资源"
};
const {
  tableData,
  loading,
  error,
  pagination,
  loadTable,
  onSearch,
  onPageChange,
  onPageSizeChange
} = usePagedList<SyncStatus>({
  fetcher: (pageNum, pageSize) =>
    listSyncStatus({
      sourceService: sourceService.value.trim() || undefined,
      pageNum,
      pageSize
    }),
  contextKey: () => sourceService.value.trim(),
  errorText: "同步状态加载失败"
});
onMounted(loadTable);
</script>

<template>
  <el-card shadow="never">
    <template #header>同步已应用状态</template>
    <el-alert
      title="这里展示已写入的状态，不代表最近一次尝试成功"
      description="失败、被拒绝和回滚的尝试不会更新此列表；没有记录不能说明从未同步。请凭调用响应中的请求编号，到操作日志排查失败。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-form inline class="mt-4" @submit.prevent="onSearch">
      <el-form-item label="来源服务"
        ><el-input
          v-model="sourceService"
          maxlength="128"
          clearable
          placeholder="服务编码（精确匹配）"
      /></el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="loading" @click="onSearch"
          >查询</el-button
        ></el-form-item
      >
      <el-form-item v-if="hasPerms(PERMISSION_CODE.OPERATION_LOG_VIEW)"
        ><router-link to="/system/operation-log"
          ><el-button>查看操作日志</el-button></router-link
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="tableData">
      <el-table-column prop="sourceService" label="来源服务" min-width="130" />
      <el-table-column label="对象种类" width="100"
        ><template #default="{ row }">{{
          kinds[row.entityKind] || row.entityKind
        }}</template></el-table-column
      >
      <el-table-column
        prop="scopeKey"
        label="同步范围"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column prop="trackedItems" label="对象记录数" width="110" />
      <el-table-column
        prop="maxGeneration"
        label="最高发布代次"
        min-width="130"
      />
      <el-table-column
        prop="lastFullGeneration"
        label="最近 FULL 代次"
        min-width="140"
      />
      <el-table-column label="已记录 FULL 状态" min-width="150"
        ><template #default="{ row }">{{
          row.lastFullStatus === "SUCCESS"
            ? "完整应用"
            : row.lastFullStatus === "PARTIAL"
              ? "部分应用"
              : row.lastFullStatus || "未记录"
        }}</template></el-table-column
      >
      <el-table-column
        prop="updatedAt"
        label="记录更新时间（UTC）"
        min-width="190"
      />
    </el-table>
    <el-pagination
      class="mt-4 justify-end"
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total"
      :page-sizes="[20, 50, 100, 200]"
      layout="total, sizes, prev, pager, next"
      @current-change="onPageChange"
      @size-change="onPageSizeChange"
    />
  </el-card>
</template>
