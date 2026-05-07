<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import {
  saveServiceConfig,
  type ServiceConfigItem,
  getServiceStatusTag
} from "@/api/perm/service";
import { STATUS_ENABLED } from "@/constants/common";

defineOptions({
  name: "ServiceForm"
});

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const loading = ref(false);
const isEdit = ref(false);
const editData = ref<ServiceConfigItem | null>(null);

const form = ref({
  serviceCode: "",
  name: "",
  basePath: "",
  description: "",
  status: STATUS_ENABLED,
  extra: ""
});

// ========== 打开弹窗 ==========

const openDialog = (data: ServiceConfigItem | null) => {
  editData.value = data;
  isEdit.value = !!data;

  if (data) {
    form.value = {
      serviceCode: data.serviceCode,
      name: data.name,
      basePath: data.basePath || "",
      description: data.description || "",
      status: data.status,
      extra: data.extra || ""
    };
  } else {
    form.value = {
      serviceCode: "",
      name: "",
      basePath: "",
      description: "",
      status: STATUS_ENABLED,
      extra: ""
    };
  }

  dialogVisible.value = true;
};

// ========== 提交表单 ==========

const handleSubmit = async () => {
  if (!form.value.serviceCode) {
    ElMessage.warning("请输入服务编码");
    return;
  }
  if (!form.value.name) {
    ElMessage.warning("请输入服务名称");
    return;
  }

  loading.value = true;
  try {
    const res = await saveServiceConfig({
      serviceCode: form.value.serviceCode,
      name: form.value.name,
      basePath: form.value.basePath || undefined,
      description: form.value.description || undefined,
      status: form.value.status,
      extra: form.value.extra || undefined
    });
    if (res.success) {
      ElMessage.success(isEdit.value ? "更新成功" : "创建成功");
      dialogVisible.value = false;
      emit("success");
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
    :title="isEdit ? '编辑服务' : '新增服务'"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="服务编码">
        <el-input
          v-model="form.serviceCode"
          placeholder="唯一标识"
          :disabled="isEdit"
        />
      </el-form-item>
      <el-form-item label="服务名称">
        <el-input v-model="form.name" placeholder="显示名称" />
      </el-form-item>
      <el-form-item label="基础路径">
        <el-input v-model="form.basePath" placeholder="如 /admin/api" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="可选" />
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
          :rows="2"
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
