<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { BizDomainResp } from "@/api/biz-domain";
import type { BizDomainFormData } from "../utils/types";

defineOptions({
  name: "BizDomainForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自表格行） */
  initialData?: BizDomainResp | null;
}>();

/** 表单默认值 */
const defaultFormData = (): BizDomainFormData => ({
  code: "",
  name: "",
  description: ""
});

const formData = reactive<BizDomainFormData>({ ...defaultFormData() });

const formRef = ref<FormInstance>();

/** 是否编辑态 */
const isEdit = computed(() => props.mode === "edit");

/** 表单校验规则。
 *  code 唯一键，新建可填（大写字母开头，含大写字母/数字/下划线，最长 64，对齐 schema VARCHAR(64)），编辑只读。
 *  name 必填，最长 128（对齐 schema VARCHAR(128)）。 */
const rules = computed<FormRules>(() => ({
  code: [
    { required: true, message: "请输入业务域编码", trigger: "blur" },
    {
      pattern: /^[A-Z][A-Z0-9_]*$/,
      message: "仅支持大写字母开头，含大写字母/数字/下划线",
      trigger: "blur"
    },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  name: [
    { required: true, message: "请输入业务域名称", trigger: "blur" },
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ]
}));

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      code: props.initialData.code,
      name: props.initialData.name,
      description: props.initialData.description ?? ""
    });
  } else {
    Object.assign(formData, defaultFormData());
  }
}

/** 获取表单数据 */
function getFormData(): BizDomainFormData {
  return { ...formData };
}

/** 表单校验 */
async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

/** 重置表单 */
function resetForm() {
  Object.assign(formData, defaultFormData());
  formRef.value?.resetFields();
}

// 监听 initialData 变化（编辑时）
watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

defineExpose({
  validate,
  getFormData,
  resetForm,
  formData
});
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="90px"
    class="biz-domain-form"
  >
    <el-form-item label="域编码" prop="code">
      <!--
        新建可填（唯一键，建议大写下划线命名）；编辑只读：code 是唯一键（uk_biz_domain），
        改它等于新建新域，与 type-def 稳定编码同口径。
      -->
      <el-input
        v-if="isEdit"
        :model-value="formData.code"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.code"
        placeholder="如 HR / ORDER / CRM"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="域名称" prop="name">
      <el-input
        v-model="formData.name"
        placeholder="如 人事域"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="描述" prop="description">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空，业务域用途说明"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.biz-domain-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
