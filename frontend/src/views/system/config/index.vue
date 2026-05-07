<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  pageConfigs,
  updateConfig,
  deleteConfig,
  type ConfigItem
} from "@/api/system/config";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "ConfigManagement"
});

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<ConfigItem>>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

// 编辑表单
const dialogVisible = ref(false);
const form = reactive({
  id: null as number | null,
  configKey: "",
  configValue: "",
  configName: "",
  remark: ""
});

// 权限
const canUpdate = hasPerms(PERM_CODES.SYS_CONFIG_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_CONFIG_DELETE);

// ========== 数据加载 ==========

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageConfigs({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (res.success) {
      tableData.value = res.data.list || [];
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载配置列表失败:", error);
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
};

// ========== 操作 ==========

const handleEdit = (row: ConfigItem) => {
  form.id = row.id;
  form.configKey = row.configKey;
  form.configValue = row.configValue;
  form.configName = row.configName;
  form.remark = row.remark || "";
  dialogVisible.value = true;
};

const handleDelete = async (row: ConfigItem) => {
  try {
    await ElMessageBox.confirm(`确认删除配置 "${row.configName}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteConfig({ ids: [row.id] });
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

const handleSubmit = async () => {
  if (!form.id) {
    ElMessage.warning("无效的配置ID");
    return;
  }
  if (!form.configValue) {
    ElMessage.warning("请填写配置值");
    return;
  }
  try {
    const res = await updateConfig({
      id: form.id,
      configValue: form.configValue,
      remark: form.remark
    });
    if (res.success) {
      ElMessage.success("更新成功");
      dialogVisible.value = false;
      await loadData();
    }
  } catch (error) {
    console.error("更新失败:", error);
    ElMessage.error("更新失败");
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

// ========== 初始化 ==========

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="config-management">
    <!-- 搜索栏 -->
    <el-card class="mb-4">
      <el-form inline>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="configKey" label="配置键" width="200" />
        <el-table-column prop="configName" label="配置名称" width="150" />
        <el-table-column prop="configValue" label="配置值" min-width="200" />
        <el-table-column prop="remark" label="备注" width="150" />
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
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

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="编辑配置" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="配置键">
          <el-input v-model="form.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置名称">
          <el-input v-model="form.configName" disabled />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.configValue" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.config-management {
  padding: 20px;
}
</style>
