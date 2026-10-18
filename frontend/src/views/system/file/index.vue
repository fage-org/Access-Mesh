<script setup lang="ts">
import { onMounted, ref, shallowRef } from "vue";
import { ElMessageBox } from "element-plus";
import { useFileManager } from "./useFileManager";
import { message } from "@/utils/message";

defineOptions({ name: "SystemFile" });
const {
  tableData,
  loading,
  error,
  pagination,
  category,
  canView,
  canCreate,
  canDelete,
  detail,
  busy,
  uploading,
  refresh,
  onPageChange,
  onPageSizeChange,
  showDetail,
  download,
  upload,
  remove,
  cancelUpload
} = useFileManager();
const categories = ["default", "avatar", "document", "image"];
const uploadOpen = ref(false);
const uploadCategory = ref("document");
const selectedFile = shallowRef<File>();
const inputKey = ref(0);
function openUpload() {
  selectedFile.value = undefined;
  inputKey.value++;
  uploadCategory.value = category.value || "document";
  uploadOpen.value = true;
}
function selectFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0];
}
async function submitUpload() {
  if (!selectedFile.value) {
    message("请先选择一个文件", { type: "warning" });
    return;
  }
  const code = uploadCategory.value.trim();
  if (!/^[a-zA-Z0-9_-]{1,32}$/.test(code)) {
    message("分类编码须为 1–32 位字母、数字、下划线或连字符", {
      type: "warning"
    });
    return;
  }
  if (await upload(selectedFile.value, code)) uploadOpen.value = false;
}
function sizeText(value: string) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes)) return value;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
}
onMounted(refresh);
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="flex items-center justify-between">
        <span>文件管理</span>
        <div>
          <el-button v-if="canView" :loading="loading" @click="refresh"
            >刷新</el-button
          >
          <el-button
            v-if="canCreate"
            type="primary"
            :disabled="uploading"
            @click="openUpload"
            >上传文件</el-button
          >
        </div>
      </div>
    </template>
    <el-form v-if="canView" inline @submit.prevent>
      <el-form-item label="业务分类">
        <el-select
          v-model="category"
          aria-label="筛选业务分类"
          filterable
          allow-create
          default-first-option
          clearable
          placeholder="全部分类"
          :disabled="uploading"
        >
          <el-option
            v-for="code in categories"
            :key="code"
            :label="code"
            :value="code"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="!canView"
      title="当前账号没有文件读取权限"
      description="如有上传权限，仍可通过上方入口上传文件。"
      type="info"
      :closable="false"
    />
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        empty-text="当前范围暂无文件"
      >
        <el-table-column
          prop="originalName"
          label="文件名"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="bizType" label="业务分类" min-width="120" />
        <el-table-column label="大小" width="110"
          ><template #default="{ row }">{{
            sizeText(row.fileSize)
          }}</template></el-table-column
        >
        <el-table-column
          prop="fileType"
          label="MIME 类型"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="createdAt"
          label="创建时间（UTC）"
          min-width="190"
        />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="busy"
              @click="showDetail(row)"
              >信息</el-button
            >
            <el-button
              link
              type="primary"
              :disabled="busy"
              @click="download(row)"
              >下载</el-button
            >
            <el-button
              v-if="canDelete"
              link
              type="danger"
              :disabled="busy"
              @click="
                remove(row, () =>
                  ElMessageBox.confirm(
                    `确认删除文件「${row.originalName}」？删除后不可恢复。`,
                    '删除文件',
                    {
                      type: 'warning',
                      confirmButtonText: '删除',
                      cancelButtonText: '取消'
                    }
                  )
                )
              "
              >删除</el-button
            >
          </template>
        </el-table-column>
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
    </template>
    <el-dialog
      v-model="uploadOpen"
      title="上传文件"
      :show-close="!uploading"
      :close-on-click-modal="!uploading"
      :close-on-press-escape="!uploading"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitUpload">
        <el-form-item label="业务分类">
          <el-select
            v-model="uploadCategory"
            aria-label="上传业务分类"
            filterable
            allow-create
            default-first-option
            :disabled="uploading"
          >
            <el-option
              v-for="code in categories"
              :key="code"
              :label="code"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择一个文件" for="file-upload-input">
          <input
            id="file-upload-input"
            :key="inputKey"
            type="file"
            :disabled="uploading"
            @change="selectFile"
          />
        </el-form-item>
        <el-alert
          title="文件类型和大小以服务端限制为准"
          description="分类决定文件的访问权限；文件不会仅对上传者本人可见。上传取消或网络中断后，请刷新列表确认结果。"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button v-if="uploading" @click="cancelUpload">取消请求</el-button>
        <el-button v-else @click="uploadOpen = false">关闭</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload"
          >上传</el-button
        >
      </template>
    </el-dialog>
    <el-dialog :model-value="!!detail" title="文件信息" @close="detail = null">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="文件名">{{
          detail.originalName
        }}</el-descriptions-item>
        <el-descriptions-item label="业务分类">{{
          detail.bizType
        }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{
          sizeText(detail.fileSize)
        }}</el-descriptions-item>
        <el-descriptions-item label="MIME 类型">{{
          detail.fileType || "未提供"
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间（UTC）">{{
          detail.createdAt
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>
