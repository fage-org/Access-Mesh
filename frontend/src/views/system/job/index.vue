<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  pageJobs,
  createJob,
  updateJob,
  deleteJobs,
  toggleJobStatus,
  triggerJob,
  pageJobLogs,
  type JobItem,
  type JobLogItem
} from "@/api/system/job";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "JobManagement"
});

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<JobItem>>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

// 日志
const logDialogVisible = ref(false);
const logLoading = ref(false);
const logData = ref<Array<JobLogItem>>([]);
const logTotal = ref(0);
const logPageNum = ref(1);
const logPageSize = ref(10);
const currentJobId = ref<number | null>(null);

// 表单
const dialogVisible = ref(false);
const form = reactive({
  id: null as number | null,
  jobName: "",
  jobGroup: "DEFAULT",
  invokeTarget: "",
  cronExpression: "",
  misfirePolicy: 1,
  concurrent: 1,
  status: 0,
  remark: ""
});

// 权限
const canCreate = hasPerms(PERM_CODES.SYS_JOB_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_JOB_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_JOB_DELETE);
const canTrigger = hasPerms(PERM_CODES.SYS_JOB_TRIGGER);

// ========== 数据加载 ==========

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageJobs({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (res.success) {
      tableData.value = res.data.list || [];
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载任务列表失败:", error);
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
};

const loadLogs = async () => {
  if (!currentJobId.value) return;
  logLoading.value = true;
  try {
    const res = await pageJobLogs({
      pageNum: logPageNum.value,
      pageSize: logPageSize.value,
      jobId: currentJobId.value
    });
    if (res.success) {
      logData.value = res.data.list || [];
      logTotal.value = res.data.total;
    }
  } catch (error) {
    console.error("加载执行日志失败:", error);
  } finally {
    logLoading.value = false;
  }
};

// ========== 操作 ==========

const handleCreate = () => {
  form.id = null;
  form.jobName = "";
  form.jobGroup = "DEFAULT";
  form.invokeTarget = "";
  form.cronExpression = "";
  form.misfirePolicy = 1;
  form.concurrent = 1;
  form.status = 0;
  form.remark = "";
  dialogVisible.value = true;
};

const handleEdit = (row: JobItem) => {
  form.id = row.id;
  form.jobName = row.jobName;
  form.jobGroup = row.jobGroup;
  form.invokeTarget = row.invokeTarget;
  form.cronExpression = row.cronExpression;
  form.misfirePolicy = row.misfirePolicy;
  form.concurrent = row.concurrent;
  form.status = row.status;
  form.remark = row.remark || "";
  dialogVisible.value = true;
};

const handleDelete = async (row: JobItem) => {
  try {
    await ElMessageBox.confirm(`确认删除任务 "${row.jobName}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteJobs({ ids: [row.id] });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadData();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

const handleToggleStatus = async (row: JobItem) => {
  const newStatus = row.status === 0 ? 1 : 0;
  const action = newStatus === 0 ? "启用" : "暂停";
  try {
    await ElMessageBox.confirm(`确认${action}任务 "${row.jobName}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "info"
    });
    const res = await toggleJobStatus({ id: row.id, status: newStatus });
    if (res.success) {
      ElMessage.success(`${action}成功`);
      await loadData();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("切换状态失败:", error);
      ElMessage.error("操作失败");
    }
  }
};

const handleTrigger = async (row: JobItem) => {
  try {
    await ElMessageBox.confirm(`确认手动触发任务 "${row.jobName}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "info"
    });
    const res = await triggerJob({ id: row.id });
    if (res.success) {
      ElMessage.success("触发成功");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("触发失败:", error);
      ElMessage.error("触发失败");
    }
  }
};

const handleViewLogs = (row: JobItem) => {
  currentJobId.value = row.id;
  logPageNum.value = 1;
  logDialogVisible.value = true;
  loadLogs();
};

const handleSubmit = async () => {
  if (!form.jobName || !form.invokeTarget || !form.cronExpression) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  try {
    if (form.id) {
      const res = await updateJob({
        id: form.id,
        jobName: form.jobName,
        jobGroup: form.jobGroup,
        invokeTarget: form.invokeTarget,
        cronExpression: form.cronExpression,
        misfirePolicy: form.misfirePolicy,
        concurrent: form.concurrent,
        status: form.status,
        remark: form.remark
      });
      if (res.success) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        await loadData();
      }
    } else {
      const res = await createJob({
        jobName: form.jobName,
        jobGroup: form.jobGroup,
        invokeTarget: form.invokeTarget,
        cronExpression: form.cronExpression,
        misfirePolicy: form.misfirePolicy,
        concurrent: form.concurrent,
        status: form.status,
        remark: form.remark
      });
      if (res.success) {
        ElMessage.success("创建成功");
        dialogVisible.value = false;
        await loadData();
      }
    }
  } catch (error) {
    console.error("保存失败:", error);
    ElMessage.error("保存失败");
  }
};

// ========== 分页 ==========

const handlePageChange = (page: number) => {
  pageNum.value = page;
  loadData();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  pageNum.value = 1;
  loadData();
};

const handleLogPageChange = (page: number) => {
  logPageNum.value = page;
  loadLogs();
};

// ========== 初始化 ==========

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="job-management">
    <!-- 搜索栏 -->
    <el-card class="mb-4">
      <el-form inline>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button
            type="success"
            :disabled="!canCreate"
            @click="handleCreate"
          >
            新增任务
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="jobName" label="任务名称" width="150" />
        <el-table-column prop="jobGroup" label="任务分组" width="100" />
        <el-table-column prop="invokeTarget" label="调用目标" min-width="200" />
        <el-table-column prop="cronExpression" label="Cron表达式" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 0 ? 'success' : 'warning'"
              size="small"
            >
              {{ row.status === 0 ? "正常" : "暂停" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :disabled="!canUpdate"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="warning"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 0 ? "暂停" : "启用" }}
            </el-button>
            <el-button
              type="success"
              link
              size="small"
              :disabled="!canTrigger"
              @click="handleTrigger(row)"
            >
              执行一次
            </el-button>
            <el-button
              type="info"
              link
              size="small"
              @click="handleViewLogs(row)"
            >
              日志
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              :disabled="!canDelete"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 任务表单弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑任务' : '新增任务'"
      width="500px"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.jobName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务分组" required>
          <el-input v-model="form.jobGroup" placeholder="请输入任务分组" />
        </el-form-item>
        <el-form-item label="调用目标" required>
          <el-input
            v-model="form.invokeTarget"
            placeholder="请输入调用目标字符串"
          />
        </el-form-item>
        <el-form-item label="Cron表达式" required>
          <el-input
            v-model="form.cronExpression"
            placeholder="如: 0 0 1 * * ?"
          />
        </el-form-item>
        <el-form-item label="执行策略">
          <el-radio-group v-model="form.misfirePolicy">
            <el-radio :value="1">立即执行</el-radio>
            <el-radio :value="2">执行一次</el-radio>
            <el-radio :value="3">放弃执行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="并发处理">
          <el-radio-group v-model="form.concurrent">
            <el-radio :value="0">允许</el-radio>
            <el-radio :value="1">禁止</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 执行日志弹窗 -->
    <el-dialog v-model="logDialogVisible" title="执行日志" width="800px">
      <el-table v-loading="logLoading" :data="logData" stripe>
        <el-table-column prop="jobName" label="任务名称" width="150" />
        <el-table-column prop="invokeTarget" label="调用目标" min-width="200" />
        <el-table-column prop="status" label="执行状态" width="80">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 0 ? 'success' : 'danger'"
              size="small"
            >
              {{ row.status === 0 ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobMessage" label="执行消息" width="150" />
        <el-table-column prop="executeTime" label="执行时间" width="160" />
        <el-table-column label="异常信息" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.exceptionInfo"
              type="primary"
              link
              size="small"
            >
              查看
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="flex justify-end mt-4">
        <el-pagination
          :current-page="logPageNum"
          :page-size="logPageSize"
          :total="logTotal"
          layout="total, prev, pager, next"
          @current-change="handleLogPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.job-management {
  padding: 20px;
}
</style>
