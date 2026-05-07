<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  pageFiles,
  deleteFiles,
  getFileDownloadUrl,
  type FileItem
} from "@/api/system/file";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms, getToken } from "@/utils/auth";
import type { UploadInstance } from "element-plus";

defineOptions({
  name: "FileManagement"
});

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<FileItem>>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

// 搜索条件
const searchForm = reactive({
  fileName: "",
  bizType: ""
});

// 上传
const uploadDialogVisible = ref(false);
const uploadRef = ref<UploadInstance>();
const fileList = ref<Array<UploadInstance>>([]);
const uploadBizType = ref("default");

// 权限
const canUpload = hasPerms(PERM_CODES.SYS_FILE_UPLOAD);
const canDelete = hasPerms(PERM_CODES.SYS_FILE_DELETE);

// ========== 数据加载 ==========

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageFiles({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      fileName: searchForm.fileName || undefined,
      bizType: searchForm.bizType || undefined
    });
    if (res.success) {
      tableData.value = res.data.list || [];
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载文件列表失败:", error);
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
};

// ========== 操作 ==========

const handleUpload = () => {
  fileList.value = [];
  uploadBizType.value = "default";
  uploadDialogVisible.value = true;
};

const handleUploadSubmit = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning("请选择文件");
    return;
  }
  // 上传通过el-upload组件自动完成，这里关闭弹窗刷新列表
  uploadDialogVisible.value = false;
  ElMessage.success("上传成功");
  await loadData();
};

const handleDownload = (row: FileItem) => {
  const url = getFileDownloadUrl(row.id);
  window.open(url, "_blank");
};

const handleDelete = async (row: FileItem) => {
  try {
    await ElMessageBox.confirm(`确认删除文件 "${row.fileName}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteFiles({ ids: [row.id] });
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

const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择要删除的文件");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedRows.value.length} 个文件?`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    const res = await deleteFiles({ ids: selectedRows.value.map(r => r.id) });
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

// ========== 多选 ==========

const selectedRows = ref<Array<FileItem>>([]);

const handleSelectionChange = (rows: Array<FileItem>) => {
  selectedRows.value = rows;
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

// ========== 格式化 ==========

const formatFileSize = (size: number): string => {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

// ========== 初始化 ==========

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="file-management">
    <!-- 搜索栏 -->
    <el-card class="mb-4">
      <el-form :model="searchForm" inline>
        <el-form-item label="文件名">
          <el-input
            v-model="searchForm.fileName"
            placeholder="请输入文件名"
            clearable
          />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input
            v-model="searchForm.bizType"
            placeholder="请输入业务类型"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button
            type="success"
            :disabled="!canUpload"
            @click="handleUpload"
          >
            上传文件
          </el-button>
          <el-button
            type="danger"
            :disabled="!canDelete || selectedRows.length === 0"
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="fileType" label="文件类型" width="100" />
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="100" />
        <el-table-column prop="uploader" label="上传人" width="100" />
        <el-table-column prop="uploadedAt" label="上传时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleDownload(row)"
            >
              下载
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

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="400px">
      <el-form label-width="80px">
        <el-form-item label="业务类型">
          <el-input v-model="uploadBizType" placeholder="default" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :action="`${import.meta.env.VITE_API_BASE_URL}/admin/api/file/upload`"
            :headers="{
              Authorization: `Bearer ${getToken()?.accessToken || ''}`
            }"
            :data="{ bizType: uploadBizType }"
            :limit="1"
            :on-success="
              () => {
                ElMessage.success('上传成功');
                void loadData(); // Intentionally not awaited in event callback
              }
            "
            :on-error="
              (err: unknown) => {
                console.error(err);
                ElMessage.error('上传失败');
              }
            "
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">只能上传一个文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-management {
  padding: 20px;
}
</style>
