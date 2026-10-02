<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { OperationPermissionResp } from "@/api/resource-operation";
import {
  PRESET_OPERATION_EXAMPLES,
  operationBitError,
  type OperationFormData
} from "../utils/types";

defineOptions({ name: "OperationForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据 */
  initialData?: OperationPermissionResp | null;
  /** 当前资源类型编码（新建预填，固定只读） */
  resourceTypeCode: string;
  /** 打开弹窗时读取的同类型完整目录，不使用搜索过滤后的行。 */
  operations: OperationPermissionResp[];
  suggestedBit?: string;
}>();

const defaultFormData = (): OperationFormData => ({
  resourceTypeCode: props.resourceTypeCode,
  code: "",
  name: "",
  binaryBit: props.suggestedBit ? toEditableBit(props.suggestedBit) : 0,
  inheritMask: 0
});

const formData = reactive<OperationFormData>({ ...defaultFormData() });
const formRef = ref<FormInstance>();
const isEdit = computed(() => props.mode === "edit");
const usedBits = computed(() =>
  props.operations
    .filter(
      op =>
        op.resourceTypeCode === props.resourceTypeCode &&
        !(isEdit.value && op.code === props.initialData?.code)
    )
    .map(op => op.binaryBit)
);

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
      validator: (
        _rule: any,
        value: number | string,
        cb: (e?: Error) => void
      ) => {
        const error = operationBitError(value, usedBits.value);
        cb(error ? new Error(error) : undefined);
      },
      trigger: "blur"
    }
  ]
}));

/** 安全整数值转数值控件可编辑；超精度高位值保留原始字符串只读展示
 *  （Number 往返会改写 2^62 级位值——4611686018427387904 → 4611686018427388000，
 *  T-PERM-028 复评 P1：只改名称的编辑也不得静默写坏位字段）。 */
function toEditableBit(value: string): number | string {
  const n = Number(value);
  return Number.isSafeInteger(n) ? n : value;
}

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    // 位字段为十进制字符串线格式（T-PERM-028）；安全值转数值控件编辑
    Object.assign(formData, {
      resourceTypeCode: props.initialData.resourceTypeCode ?? "",
      code: props.initialData.code,
      name: props.initialData.name,
      binaryBit: toEditableBit(props.initialData.binaryBit),
      inheritMask: toEditableBit(props.initialData.inheritMask)
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
        v-if="typeof formData.binaryBit === 'number'"
        v-model="formData.binaryBit"
        :min="1"
        :max="Number.MAX_SAFE_INTEGER"
        controls-position="right"
        class="w-full!"
      />
      <!-- 超精度高位值（超过安全整数上限）只读精确展示：数值控件往返会丢精度（T-PERM-028 复评 P1） -->
      <el-input
        v-else
        :model-value="String(formData.binaryBit)"
        readonly
        class="w-full! font-mono"
      />
      <div class="field-tip">
        独占位，2 的幂次（1/2/4/8/16…）。同资源类型内不可重复。
        <template v-if="!isEdit"
          >已自动填入最小可用位；保存时若已被占用，请重新打开表单。</template
        >
        <template v-if="typeof formData.binaryBit === 'string'">
          当前为超过安全整数上限的高位值（只读保护），如需修改请经 API
          提交十进制字符串。
        </template>
      </div>
    </el-form-item>

    <el-form-item label="继承掩码" prop="inheritMask">
      <el-input-number
        v-if="typeof formData.inheritMask === 'number'"
        v-model="formData.inheritMask"
        :min="0"
        controls-position="right"
        class="w-full!"
      />
      <el-input
        v-else
        :model-value="String(formData.inheritMask)"
        readonly
        class="w-full! font-mono"
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
