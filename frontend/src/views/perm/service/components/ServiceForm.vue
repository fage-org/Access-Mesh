<script setup lang="ts">
import { ref, reactive } from "vue";
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type FormItemRule
} from "element-plus";
import { saveServiceConfig, type ServiceConfigItem } from "@/api/perm/service";
import { STATUS_ENABLED, STATUS_DISABLED } from "@/constants/common";

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
const formRef = ref<FormInstance>();

const form = reactive({
  serviceCode: "",
  name: "",
  basePath: "",
  description: "",
  status: STATUS_ENABLED,
  extra: ""
});

// ========== 表单验证规则 ==========

const validateJson = (
  _rule: FormItemRule,
  value: string,
  callback: (error?: Error) => void
) => {
  if (!value) {
    callback();
    return;
  }
  try {
    JSON.parse(value);
    callback();
  } catch {
    callback(new Error("扩展配置必须是有效的 JSON 格式"));
  }
};

const rules = reactive<FormRules>({
  serviceCode: [
    { required: true, message: "请输入服务编码", trigger: "blur" },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9\-_]*$/,
      message: "服务编码必须以字母开头，只能包含字母、数字、下划线和连字符",
      trigger: "blur"
    }
  ],
  name: [{ required: true, message: "请输入服务名称", trigger: "blur" }],
  basePath: [
    {
      pattern: /^\/[\w\-\/]*$/,
      message: "基础路径必须以 / 开头",
      trigger: "blur"
    }
  ],
  extra: [{ validator: validateJson, trigger: "blur" }]
});

// ========== 表单重置 ==========

const resetForm = () => {
  form.serviceCode = "";
  form.name = "";
  form.basePath = "";
  form.description = "";
  form.status = STATUS_ENABLED;
  form.extra = "";
};

// ========== 打开弹窗 ==========

const openDialog = (data: ServiceConfigItem | null) => {
  editData.value = data;
  isEdit.value = !!data;

  if (data) {
    form.serviceCode = data.serviceCode;
    form.name = data.name;
    form.basePath = data.basePath || "";
    form.description = data.description || "";
    form.status = data.status;
    form.extra = data.extra || "";
  } else {
    resetForm();
  }

  dialogVisible.value = true;
};

// ========== 提交表单 ==========

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const res = await saveServiceConfig({
      serviceCode: form.serviceCode,
      name: form.name,
      basePath: form.basePath || undefined,
      description: form.description || undefined,
      status: form.status,
      extra: form.extra || undefined
    });
    if (res.success) {
      ElMessage.success(isEdit.value ? "更新成功" : "创建成功");
      dialogVisible.value = false;
      emit("success");
    }
  } catch (error) {
    console.error("保存服务配置失败:", error);
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
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="服务编码" prop="serviceCode">
        <el-input
          v-model="form.serviceCode"
          placeholder="唯一标识"
          :disabled="isEdit"
        />
      </el-form-item>
      <el-form-item label="服务名称" prop="name">
        <el-input v-model="form.name" placeholder="显示名称" />
      </el-form-item>
      <el-form-item label="基础路径" prop="basePath">
        <el-input v-model="form.basePath" placeholder="如 /admin/api" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="可选" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="STATUS_ENABLED">启用</el-radio>
          <el-radio :value="STATUS_DISABLED">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="扩展配置" prop="extra">
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
