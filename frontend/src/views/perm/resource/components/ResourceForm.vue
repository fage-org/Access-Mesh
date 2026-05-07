<script setup lang="ts">
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  createResource,
  updateResource,
  type ResourceDetail,
  RESOURCE_TYPE_CODES,
  getResourceTypeTag
} from "@/api/perm/resource";
import { STATUS_ENABLED } from "@/constants/common";

defineOptions({
  name: "ResourceForm"
});

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const loading = ref(false);
const isEdit = ref(false);
const parentId = ref<number | null>(null);
const editData = ref<ResourceDetail | null>(null);

const form = ref({
  resourceTypeCode: "",
  code: "",
  codeType: "",
  name: "",
  status: STATUS_ENABLED,
  sortOrder: 0,
  path: "",
  extra: ""
});

// ========== 标题计算 ==========

const dialogTitle = computed(() => {
  if (isEdit.value) return "编辑资源";
  return parentId.value ? "新增子资源" : "新增根资源";
});

// ========== 打开弹窗 ==========

const openDialog = (parent: number | null, data?: ResourceDetail) => {
  parentId.value = parent;
  editData.value = data || null;
  isEdit.value = !!data;

  if (data) {
    // 编辑模式：填充表单
    form.value = {
      resourceTypeCode: data.resourceTypeCode,
      code: data.code,
      codeType: data.codeType || "",
      name: data.name,
      status: data.status,
      sortOrder: data.sortOrder,
      path: data.path || "",
      extra: data.extra || ""
    };
  } else {
    // 创建模式：重置表单
    form.value = {
      resourceTypeCode: "",
      code: "",
      codeType: "",
      name: "",
      status: STATUS_ENABLED,
      sortOrder: 0,
      path: "",
      extra: ""
    };
  }

  dialogVisible.value = true;
};

// ========== 提交表单 ==========

const handleSubmit = async () => {
  // 验证必填字段
  if (!form.value.resourceTypeCode) {
    ElMessage.warning("请选择资源类型");
    return;
  }
  if (!form.value.code) {
    ElMessage.warning("请输入资源编码");
    return;
  }
  if (!form.value.name) {
    ElMessage.warning("请输入资源名称");
    return;
  }

  loading.value = true;
  try {
    if (isEdit.value && editData.value) {
      // 更新
      const res = await updateResource({
        id: editData.value.id,
        code: form.value.code,
        name: form.value.name,
        status: form.value.status,
        sortOrder: form.value.sortOrder,
        path: form.value.path || undefined,
        extra: form.value.extra || undefined
      });
      if (res.success) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        emit("success");
      }
    } else {
      // 创建
      const res = await createResource({
        parentId: parentId.value || undefined,
        resourceTypeCode: form.value.resourceTypeCode,
        code: form.value.code,
        codeType: form.value.codeType || undefined,
        name: form.value.name,
        status: form.value.status,
        sortOrder: form.value.sortOrder,
        path: form.value.path || undefined,
        extra: form.value.extra || undefined
      });
      if (res.success) {
        ElMessage.success("创建成功");
        dialogVisible.value = false;
        emit("success");
      }
    }
  } catch {
    ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
  } finally {
    loading.value = false;
  }
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="资源类型">
        <el-select
          v-model="form.resourceTypeCode"
          placeholder="请选择"
          :disabled="isEdit"
        >
          <el-option
            v-for="(text, code) in RESOURCE_TYPE_CODES"
            :key="code"
            :label="getResourceTypeTag(text).text"
            :value="text"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="资源编码">
        <el-input
          v-model="form.code"
          :disabled="isEdit"
          placeholder="唯一标识"
        />
      </el-form-item>
      <el-form-item label="编码类型">
        <el-input v-model="form.codeType" placeholder="可选" />
      </el-form-item>
      <el-form-item label="资源名称">
        <el-input v-model="form.name" placeholder="显示名称" />
      </el-form-item>
      <el-form-item label="路径">
        <el-input v-model="form.path" placeholder="可选" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="扩展配置">
        <el-input
          v-model="form.extra"
          type="textarea"
          :rows="3"
          placeholder="JSON 格式扩展配置"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
