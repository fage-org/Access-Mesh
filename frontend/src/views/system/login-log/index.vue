<script setup lang="ts">
import { onMounted } from "vue";
import { pageLoginLogs, type LoginLog } from "@/api/login-log";
import { usePagedList } from "@/utils/list-load";
defineOptions({ name: "SystemLoginLog" });
const {
  tableData,
  loading,
  error,
  pagination,
  loadTable,
  onPageChange,
  onPageSizeChange
} = usePagedList<LoginLog>({
  fetcher: pageLoginLogs,
  errorText: "登录日志加载失败"
});
onMounted(loadTable);
</script>

<template>
  <el-card shadow="never">
    <template #header
      ><div class="flex items-center justify-between">
        <span>登录日志</span
        ><el-button :loading="loading" @click="loadTable">刷新</el-button>
      </div></template
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="tableData" row-key="id">
      <el-table-column prop="loginAt" label="登录时间（UTC）" min-width="180" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="clientId" label="客户端" min-width="130" />
      <el-table-column prop="loginType" label="登录方式" min-width="110" />
      <el-table-column label="结果" width="90"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
            row.status === 1 ? "成功" : "失败"
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column prop="ipAddress" label="IP 地址" min-width="140" />
      <el-table-column
        prop="failReason"
        label="失败原因"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="userAgent"
        label="客户端信息"
        min-width="200"
        show-overflow-tooltip
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
