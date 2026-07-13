<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { type ConditionResp } from "@/api/permission-condition";
import { ReConditionEditor } from "@/components/ReConditionEditor";
import {
  createEmptyConditionForm,
  parseRules,
  type ConditionFormData
} from "../utils/types";

defineOptions({ name: "ConditionForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的完整条件数据 */
  initialData?: ConditionResp | null;
}>();

const formData = reactive<ConditionFormData>({ ...createEmptyConditionForm() });
const formRef = ref<FormInstance>();
const editorRef = ref<InstanceType<typeof ReConditionEditor>>();

const isEdit = computed(() => props.mode === "edit");

const rules = computed<FormRules>(() => ({
  code: [
    { required: true, message: "请输入条件编码", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_-]+$/,
      message: "仅支持字母、数字、下划线、中划线",
      trigger: "blur"
    },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  name: [
    { required: true, message: "请输入条件名称", trigger: "blur" },
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ]
}));

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    const parsed = parseRules(props.initialData.conditionRules);
    Object.assign(formData, {
      code: props.initialData.code,
      name: props.initialData.name,
      enabled: props.initialData.enabled,
      gatewayEvaluable: props.initialData.gatewayEvaluable,
      description: props.initialData.description ?? "",
      rules: parsed
    });
  } else {
    Object.assign(formData, createEmptyConditionForm());
  }
}

function getFormData(): ConditionFormData {
  return {
    code: formData.code,
    name: formData.name,
    enabled: formData.enabled,
    gatewayEvaluable: formData.gatewayEvaluable,
    description: formData.description,
    rules: JSON.parse(JSON.stringify(formData.rules))
  };
}

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
  } catch {
    return false;
  }
  // 规则完整性 + gateway 可下发预校验委托给 ReConditionEditor
  if (!editorRef.value || !editorRef.value.validate()) {
    return false;
  }
  return true;
}

watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

defineExpose({ validate, getFormData });
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="100px"
    class="condition-form"
  >
    <el-form-item label="条件编码" prop="code">
      <!-- 编辑态只读：code 为业务键（uk_permission_condition: tenant+code） -->
      <el-input
        v-if="isEdit"
        :model-value="formData.code"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.code"
        placeholder="条件编码（业务键，创建后不可改）"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="条件名称" prop="name">
      <el-input
        v-model="formData.name"
        placeholder="请输入条件名称"
        clearable
        maxlength="128"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="启用">
      <el-switch v-model="formData.enabled" />
    </el-form-item>

    <el-form-item label="Gateway 评估">
      <el-switch v-model="formData.gatewayEvaluable" />
      <span class="form-tip">
        开启后规则随接口快照下发 Gateway 本地重评（T-PERM-017）
      </span>
    </el-form-item>

    <el-form-item label="描述">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空"
        maxlength="512"
      />
    </el-form-item>

    <el-form-item label="条件规则" required>
      <ReConditionEditor
        ref="editorRef"
        v-model="formData.rules"
        :gateway-evaluable="formData.gatewayEvaluable"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.condition-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.form-tip {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
