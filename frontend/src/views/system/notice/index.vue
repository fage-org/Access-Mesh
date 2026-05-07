<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  pageNotices,
  createNotice,
  updateNotice,
  deleteNotice,
  publishNotice,
  type NoticeItem
} from "@/api/system/notice";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "NoticeManagement"
});

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<NoticeItem>>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

// 表单
const dialogVisible = ref(false);
const form = reactive({
  id: null as number | null,
  title: "",
  content: "",
  type: 1
});

// 权限
const canCreate = hasPerms(PERM_CODES.SYS_NOTICE_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_NOTICE_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_NOTICE_DELETE);
const canPublish = hasPerms(PERM_CODES.SYS_NOTICE_PUBLISH);

// ========== 数据加载 ==========

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageNotices({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    if (res.success) {
      tableData.value = res.data.list || [];
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("加载通知列表失败:", error);
    ElMessage.error("加载失败");
  } finally {
    loading.value = false;
  }
};

// ========== 操作 ==========

const handleCreate = () => {
  form.id = null;
  form.title = "";
  form.content = "";
  form.type = 1;
  dialogVisible.value = true;
};

const handleEdit = (row: NoticeItem) => {
  form.id = row.id;
  form.title = row.title;
  form.content = row.content;
  form.type = row.type;
  dialogVisible.value = true;
};

const handleDelete = async (row: NoticeItem) => {
  try {
    await ElMessageBox.confirm(`确认删除通知 "${row.title}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });
    const res = await deleteNotice({ ids: [row.id] });
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

const handlePublish = async (row: NoticeItem) => {
  if (row.status === 1) {
    ElMessage.warning("该通知已发布");
    return;
  }
  try {
    await ElMessageBox.confirm(`确认发布通知 "${row.title}"?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "info"
    });
    const res = await publishNotice({ id: row.id });
    if (res.success) {
      ElMessage.success("发布成功");
      await loadData();
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("发布失败:", error);
      ElMessage.error("发布失败");
    }
  }
};

const handleSubmit = async () => {
  if (!form.title || !form.content) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  try {
    if (form.id) {
      const res = await updateNotice({
        id: form.id,
        title: form.title,
        content: form.content,
        type: form.type
      });
      if (res.success) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        await loadData();
      }
    } else {
      const res = await createNotice({
        title: form.title,
        content: form.content,
        type: form.type
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

// ========== 初始化 ==========

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="notice-management">
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
            新增通知
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'info' : 'success'" size="small">
              {{ row.type === 1 ? "系统通知" : "公告" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : 'warning'"
              size="small"
            >
              {{ row.status === 1 ? "已发布" : "未发布" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisher" label="发布人" width="100" />
        <el-table-column prop="publishTime" label="发布时间" width="160" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :disabled="!canUpdate || row.status === 1"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="success"
              link
              size="small"
              :disabled="!canPublish || row.status === 1"
              @click="handlePublish(row)"
            >
              发布
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

    <!-- 表单弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑通知' : '新增通知'"
      width="500px"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio :value="1">系统通知</el-radio>
            <el-radio :value="2">公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请输入内容"
          />
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
.notice-management {
  padding: 20px;
}
</style>
