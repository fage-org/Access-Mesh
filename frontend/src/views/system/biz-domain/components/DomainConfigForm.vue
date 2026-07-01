<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { DomainConfigResp } from "@/api/domain-config";
import {
  CONFIG_TYPE_OPTIONS,
  parseExtra,
  type DomainConfigFormData
} from "../utils/types";

defineOptions({
  name: "DomainConfigForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自子表行） */
  initialData?: DomainConfigResp | null;
  /** 当前选中业务域编码（只读展示，save 时作为 domainCode 入参） */
  domainCode: string;
}>();

/** 表单默认值 */
const defaultFormData = (): DomainConfigFormData => ({
  configType: "",
  extra: "{}"
});

const formData = reactive<DomainConfigFormData>({ ...defaultFormData() });

const formRef = ref<FormInstance>();

/** 是否编辑态 */
const isEdit = computed(() => props.mode === "edit");

/** 表单校验规则。
 *  configType 必填（新建下拉选 5 种，编辑只读——upsert 键 domainCode+configType 的一部分）。
 *  extra 必填 + JSON 合法性校验（与后端 JsonValidationUtils.validateJson 对齐）。 */
const rules = computed<FormRules>(() => ({
  configType: [
    { required: true, message: "请选择配置类型", trigger: "change" }
  ],
  extra: [
    { required: true, message: "请输入配置值", trigger: "blur" },
    {
      validator: (_rule, value: string, callback) => {
        const parsed = parseExtra(value);
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
      configType: props.initialData.configType,
      extra: props.initialData.extra
    });
  } else {
    Object.assign(formData, defaultFormData());
  }
}

/** 获取表单数据 */
function getFormData(): DomainConfigFormData {
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
    class="domain-config-form"
  >
    <el-form-item label="业务域">
      <!-- 当前选中域编码，只读展示（save 时作为 domainCode 入参） -->
      <el-input :model-value="domainCode" readonly class="w-full!" />
    </el-form-item>

    <el-form-item label="配置类型" prop="configType">
      <!--
        新建下拉选 5 种（对齐 schema config_type 注释）；编辑只读：configType 是 upsert 键的一部分
        （domainCode+configType），改它等于新建新配置，与 system-config configKey 稳定键同口径。
      -->
      <el-select
        v-if="isEdit"
        :model-value="formData.configType"
        disabled
        class="w-full!"
      >
        <el-option
          v-for="opt in CONFIG_TYPE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-else
        v-model="formData.configType"
        placeholder="请选择配置类型"
        class="w-full!"
      >
        <el-option
          v-for="opt in CONFIG_TYPE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="配置值" prop="extra">
      <!--
        JSON 字符串编辑（后端 schema 为 JSONB，前端按字符串编辑 + JSON.parse 校验）。
        placeholder 给出示例结构。textarea 高度足够编辑中等 JSON。
      -->
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="5"
        placeholder='JSON 格式，如 {"typeCodes":["USER","ORG"]}'
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.domain-config-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
