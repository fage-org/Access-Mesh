<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { SystemConfigResp } from "@/api/system-config";
import type { SystemConfigFormData } from "../utils/types";
import { parseConfigValue } from "../utils/types";

defineOptions({
  name: "ConfigForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自表格行） */
  initialData?: SystemConfigResp | null;
}>();

/** 表单默认值 */
const defaultFormData = (): SystemConfigFormData => ({
  configKey: "",
  configValue: "{}",
  description: ""
});

const formData = reactive<SystemConfigFormData>({ ...defaultFormData() });

const formRef = ref<FormInstance>();

/** 是否编辑态 */
const isEdit = computed(() => props.mode === "edit");

/** 表单校验规则。
 *  configKey 唯一键，新建可填（命名空间前缀 + 大写字母/数字/下划线，最长 128，对齐 schema
 *  VARCHAR(128) 与后端 T-ACCESS-007 §5.2 命名空间前缀强制——无前缀键后端 400），编辑只读。
 *  configValue 必填 + JSON 合法性校验（与后端 JsonValidationUtils.validateJson 对齐）。 */
const rules = computed<FormRules>(() => ({
  configKey: [
    { required: true, message: "请输入配置键", trigger: "blur" },
    // 前缀 Pattern 仅新建挂载：编辑态 configKey 只读，后端允许的存量键（前缀后字符
    // 不受字符集约束）不应在不可修改字段上报校验错阻断保存
    ...(isEdit.value
      ? []
      : [
          {
            pattern: /^(admin|permission|access)\.[A-Z][A-Z0-9_]*$/,
            message:
              "必须使用 admin./permission./access. 命名空间前缀，后接大写字母开头的键名",
            trigger: "blur"
          }
        ]),
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ],
  configValue: [
    { required: true, message: "请输入配置值", trigger: "blur" },
    {
      validator: (_rule, value: string, callback) => {
        const parsed = parseConfigValue(value);
        if (!parsed.ok) {
          callback(new Error(parsed.error || "配置值必须是合法 JSON"));
        } else {
          callback();
        }
      },
      trigger: "blur"
    }
  ]
}));

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      configKey: props.initialData.configKey,
      configValue: props.initialData.configValue,
      description: props.initialData.description ?? ""
    });
  } else {
    Object.assign(formData, defaultFormData());
  }
}

/** 获取表单数据 */
function getFormData(): SystemConfigFormData {
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
    class="config-form"
  >
    <el-form-item label="配置键" prop="configKey">
      <!--
        新建可填（唯一键，命名空间前缀 + 大写下划线命名，前缀后端强制）；编辑只读：configKey 是唯一键，
        改它等于新建新项（唯一索引 uk_system_config），与 type-def 稳定编码同口径。
      -->
      <el-input
        v-if="isEdit"
        :model-value="formData.configKey"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.configKey"
        placeholder="如 permission.MY_SETTING"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="配置值" prop="configValue">
      <!--
        JSON 字符串编辑（后端 schema 为 JSONB，前端按字符串编辑 + JSON.parse 校验）。
        placeholder 给出示例结构。textarea 高度足够编辑中等 JSON。
      -->
      <el-input
        v-model="formData.configValue"
        type="textarea"
        :rows="5"
        placeholder='JSON 格式，如 {"mode":"DOMAIN_UNIQUE"}'
      />
    </el-form-item>

    <el-form-item label="描述" prop="description">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空，配置项用途说明"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.config-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
