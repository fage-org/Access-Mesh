<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { type OperationPermissionResp } from "@/api/resource-operation";
import {
  PRESET_OPERATION_EXAMPLES,
  type OperationFormData
} from "../utils/types";

defineOptions({ name: "OperationForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据 */
  initialData?: OperationPermissionResp | null;
  /** 当前资源类型编码（新建预填，固定只读） */
  resourceTypeCode: string;
}>();

const defaultFormData = (): OperationFormData => ({
  resourceTypeCode: props.resourceTypeCode,
  code: "",
  name: "",
  binaryBit: 0,
  inheritMask: 0
});

const formData = reactive<OperationFormData>({ ...defaultFormData() });
const formRef = ref<FormInstance>();
const isEdit = computed(() => props.mode === "edit");

/** 校验规则。
 *  code 为业务键（uk_operation_permission_typed: tenant+type+code），编辑态只读；
 *  binaryBit 必须为 2 的幂次（uk_operation_permission_typed_bit 同类型内唯一）。 */
const rules = computed<FormRules>(() => ({
  code: [
    { required: true, message: "请输入操作编码", trigger: "blur" },
    {
      pattern: /^[A-Z][A-Z0-9_]*$/,
      message: "大写字母开头，仅大写字母、数字、下划线",
      trigger: "blur"
    },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  name: [
    { required: true, message: "请输入操作名称", trigger: "blur" },
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ],
  binaryBit: [
    { required: true, message: "请输入二进制位", trigger: "blur" },
    {
      validator: (_rule: any, value: number, cb: (e?: Error) => void) => {
        if (!Number.isInteger(value) || value <= 0) {
          cb(new Error("必须为正整数"));
        } else if ((value & (value - 1)) !== 0) {
          cb(new Error("必须为 2 的幂次（1/2/4/8/16…）"));
        } else {
          cb();
        }
      },
      trigger: "blur"
    }
  ]
}));

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      resourceTypeCode: props.initialData.resourceTypeCode ?? "",
      code: props.initialData.code,
      name: props.initialData.name,
      binaryBit: props.initialData.binaryBit,
      inheritMask: props.initialData.inheritMask
    });
  } else {
    Object.assign(formData, defaultFormData());
  }
}

function getFormData(): OperationFormData {
  return { ...formData };
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
    label-width="90px"
    class="operation-form"
  >
    <el-form-item label="资源类型">
      <el-input
        :model-value="formData.resourceTypeCode"
        readonly
        class="w-full!"
      />
    </el-form-item>

    <el-form-item label="操作编码" prop="code">
      <!-- 编辑态只读：code 为业务键（uk_operation_permission_typed） -->
      <el-input
        v-if="isEdit"
        :model-value="formData.code"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.code"
        placeholder="如 CREATE / VIEW / UPDATE / DELETE"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="操作名称" prop="name">
      <el-input
        v-model="formData.name"
        placeholder="用于显示的名称"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="二进制位" prop="binaryBit">
      <el-input-number
        v-model="formData.binaryBit"
        :min="1"
        controls-position="right"
        class="w-full!"
      />
      <div class="field-tip">
        独占位，2 的幂次（1/2/4/8/16…）。同资源类型内不可重复。
      </div>
    </el-form-item>

    <el-form-item label="继承掩码" prop="inheritMask">
      <el-input-number
        v-model="formData.inheritMask"
        :min="0"
        controls-position="right"
        class="w-full!"
      />
      <div class="field-tip">
        所继承操作的 binaryBit 之和。实际权限 = binaryBit | inheritMask。
      </div>
    </el-form-item>

    <div class="preset-examples">
      <div class="preset-title">预置 CRUD 示例（参考）</div>
      <div class="preset-grid">
        <span
          v-for="ex in PRESET_OPERATION_EXAMPLES"
          :key="ex.code"
          class="preset-item"
        >
          {{ ex.code }}({{ ex.name }}): bit={{ ex.binaryBit }}, mask={{
            ex.inheritMask
          }}
        </span>
      </div>
    </div>
  </el-form>
</template>

<style lang="scss" scoped>
.operation-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }
}

.field-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.preset-examples {
  padding: var(--space-2) var(--space-3);
  margin-top: var(--space-1);
  background: var(--el-fill-color-light);
  border-radius: 4px;

  .preset-title {
    margin-bottom: var(--space-1);
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  .preset-grid {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .preset-item {
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    color: var(--el-text-color-regular);
  }
}
</style>
