<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { TYPE_KEY_LABEL, type TypeDefResp, type TypeKey } from "@/api/type-def";
import type { TypeDefFormData } from "../utils/types";
import { TYPE_KEY_OPTIONS, isSystemPreset } from "../utils/types";

defineOptions({
  name: "TypeForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自表格行） */
  initialData?: TypeDefResp | null;
  /** 默认 typeKey（新建时，从搜索下拉传入） */
  defaultTypeKey?: TypeKey | "";
}>();

/** 表单默认值 */
const defaultFormData = (): TypeDefFormData => ({
  typeKey: props.defaultTypeKey ?? "",
  typeCode: "",
  name: "",
  description: "",
  isSystem: false,
  sortOrder: 0,
  extra: ""
});

const formData = reactive<TypeDefFormData>({ ...defaultFormData() });

const formRef = ref<FormInstance>();

/** 是否编辑态 */
const isEdit = computed(() => props.mode === "edit");

/** 编辑态是否系统预置（name 只读，仅 description/sortOrder/extra 可改） */
const isSystemRow = computed(
  () => isEdit.value && isSystemPreset(props.initialData)
);

/** 表单校验规则。
 *  typeCode 对外稳定编码，新建可空（留空由服务端按 TYPEKEY_<typeValue> 生成），格式仅字母数字下划线中划线。
 *  不含 typeValue（服务端自动分配，D1）。 */
const rules = computed<FormRules>(() => ({
  typeKey: [{ required: true, message: "请选择类型分组", trigger: "change" }],
  name: [
    { required: true, message: "请输入名称", trigger: "blur" },
    { min: 2, max: 64, message: "长度 2-64 字符", trigger: "blur" }
  ],
  typeCode: [
    {
      pattern: /^[a-zA-Z0-9_-]*$/,
      message: "仅支持字母、数字、下划线、中划线",
      trigger: "blur"
    },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  sortOrder: [{ required: true, message: "请输入排序号", trigger: "blur" }]
}));

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      typeKey: props.initialData.typeKey as TypeKey,
      typeCode: props.initialData.typeCode,
      name: props.initialData.name,
      description: props.initialData.description ?? "",
      isSystem: props.initialData.isSystem,
      sortOrder: props.initialData.sortOrder,
      extra: props.initialData.extra ?? ""
    });
  } else {
    Object.assign(formData, defaultFormData());
  }
}

/** 获取表单数据 */
function getFormData(): TypeDefFormData {
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
    class="type-form"
  >
    <el-form-item label="类型分组" prop="typeKey">
      <!-- 编辑态只读：typeKey 是稳定分组键，不可改 -->
      <el-input
        v-if="isEdit"
        :model-value="TYPE_KEY_LABEL[formData.typeKey] || formData.typeKey"
        readonly
        class="w-full!"
      />
      <el-select
        v-else
        v-model="formData.typeKey"
        placeholder="请选择类型分组"
        class="w-full!"
      >
        <el-option
          v-for="opt in TYPE_KEY_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="类型编码" prop="typeCode">
      <!--
        新建可填（留空=服务端按规则自动生成）；编辑只读：typeCode 是对外稳定编码，
        唯一索引 uk_type_definition_code 保证唯一，改它破坏既有引用（与 typeKey 同口径）。
      -->
      <el-input
        v-if="isEdit"
        :model-value="formData.typeCode"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.typeCode"
        placeholder="留空自动生成（建议填写以便外部系统引用）"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="名称" prop="name">
      <!-- 系统预置项编辑态 name 只读（不可改名） -->
      <el-input
        v-if="isSystemRow"
        :model-value="formData.name"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.name"
        placeholder="请输入名称"
        clearable
        maxlength="64"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="描述" prop="description">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空，类型用途说明"
      />
    </el-form-item>

    <el-form-item v-if="isEdit" label="系统预置" prop="isSystem">
      <!-- 仅编辑态只读展示。新建不暴露——系统预置走初始化种子，前端创建固定为租户自定义（isSystem=false） -->
      <el-tag :type="formData.isSystem ? 'info' : 'success'">
        {{ formData.isSystem ? "系统预置" : "租户自定义" }}
      </el-tag>
    </el-form-item>

    <el-form-item label="排序号" prop="sortOrder">
      <el-input-number
        v-model="formData.sortOrder"
        :min="0"
        :max="9999"
        controls-position="right"
        class="w-full!"
      />
    </el-form-item>

    <el-form-item label="扩展属性" prop="extra">
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="2"
        placeholder="可空，JSON 格式扩展属性"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.type-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
