<script setup lang="ts">
import { onMounted, ref } from "vue";
import { platformAuditPage, type PlatformAudit } from "@/api/platform";
import { usePagedList } from "@/utils/list-load";
defineOptions({ name: "PlatformAudit" });
const tenantId = ref<number>();
const list = usePagedList<PlatformAudit>({
  fetcher: (pageNum, pageSize) =>
    platformAuditPage({
      pageNum,
      pageSize,
      targetTenantId: tenantId.value ?? undefined
    }),
  errorText: "平台审计加载失败"
});
const { tableData, loading, error, pagination } = list;
onMounted(list.loadTable);
</script>
<template>
  <el-card>
    <template #header><h1>平台审计</h1></template>
    <el-form inline @submit.prevent="list.onSearch"
      ><el-form-item label="目标租户 ID"
        ><el-input-number
          v-model="tenantId"
          :min="1"
          :precision="0" /></el-form-item
      ><el-form-item
        ><el-button native-type="submit" :loading="loading"
          >查询</el-button
        ></el-form-item
      ></el-form
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="tableData" row-key="id">
      <el-table-column prop="createdAt" label="时间" min-width="180" />
      <el-table-column prop="operatorName" label="平台操作者" />
      <el-table-column prop="targetTenantId" label="目标租户" />
      <el-table-column prop="action" label="动作" min-width="220" />
      <el-table-column prop="outcome" label="结果" />
      <el-table-column prop="targetType" label="对象类型" />
      <el-table-column prop="targetId" label="对象 ID" />
      <el-table-column prop="summary" label="说明" min-width="240" />
      <el-table-column prop="requestId" label="请求 ID" min-width="260" />
    </el-table>
    <el-pagination
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total"
      layout="total, prev, pager, next"
      @current-change="list.onPageChange"
    />
  </el-card>
</template>
