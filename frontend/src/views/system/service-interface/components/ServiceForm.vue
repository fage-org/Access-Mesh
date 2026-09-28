<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ServiceConfigResp } from "@/api/service-interface";
import {
  createEmptyServiceForm,
  validateOptionalJson,
  type ServiceConfigFormData
} from "../utils/types";

defineOptions({ name: "ServiceInterfaceServiceForm" });

const props = defineProps<{
  mode: "create" | "edit";
  initialData?: ServiceConfigResp | null;
}>();

const formRef = ref<FormInstance>();
const formData = reactive<ServiceConfigFormData>(createEmptyServiceForm());
const isEdit = computed(() => props.mode === "edit");

const rules = computed<FormRules>(() => ({
  serviceCode: [
    { required: true, message: "请输入服务编码", trigger: "blur" },
    { max: 128, message: "服务编码最长 128 字符", trigger: "blur" }
  ],
  name: [
    { required: true, message: "请输入服务名称", trigger: "blur" },
    { max: 128, message: "服务名称最长 128 字符", trigger: "blur" }
  ],
  basePath: [
    { required: true, message: "请输入基础路径", trigger: "blur" },
    {
      pattern: /^\//,
      message: "基础路径必须以 / 开头",
      trigger: "blur"
    },
    { max: 256, message: "基础路径最长 256 字符", trigger: "blur" }
  ],
  extra: [
    {
      validator: (_rule, value: string, callback) => {
        const error = validateOptionalJson(value);
        callback(error ? new Error(error) : undefined);
      },
      trigger: "blur"
    }
  ]
}));

function initForm() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      serviceCode: props.initialData.serviceCode,
      name: props.initialData.name,
      basePath: props.initialData.basePath || "/",
      description: props.initialData.description || "",
      status: props.initialData.status,
      extra: props.initialData.extra || ""
    });
    return;
  }
  Object.assign(formData, createEmptyServiceForm());
}

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

function getFormData(): ServiceConfigFormData {
  return { ...formData };
}

watch(() => props.initialData, initForm, { immediate: true });

defineExpose({ validate, getFormData });
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="96px"
    class="service-form"
  >
    <el-form-item label="服务编码" prop="serviceCode">
      <el-input
        v-if="isEdit"
        :model-value="formData.serviceCode"
        readonly
        class="font-mono"
      />
      <el-input
        v-else
        v-model.trim="formData.serviceCode"
        placeholder="如 example-service"
        maxlength="128"
        clearable
      />
    </el-form-item>
    <el-form-item label="服务名称" prop="name">
      <el-input
        v-model.trim="formData.name"
        placeholder="如 管理服务"
        maxlength="128"
        clearable
      />
    </el-form-item>
    <el-form-item label="基础路径" prop="basePath">
      <el-input
        v-model.trim="formData.basePath"
        placeholder="如 /admin"
        maxlength="256"
        class="font-mono"
      />
    </el-form-item>
    <el-form-item label="服务状态">
      <el-radio-group v-model="formData.status">
        <el-radio :value="1">启用</el-radio>
        <el-radio :value="0">停用</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="服务说明">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="3"
        maxlength="512"
        show-word-limit
        placeholder="说明服务的职责或接入边界（可选）"
      />
    </el-form-item>
    <el-form-item label="扩展属性" prop="extra">
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="3"
        placeholder='可选 JSON，如 {"owner":"platform"}'
        class="font-mono"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.service-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
