<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import {
  createEmptyRules,
  serializeRules,
  summarizeRules,
  type ConditionRules
} from "@/utils/condition-rules";
import { ReConditionEditor } from "@/components/ReConditionEditor";
import {
  createCondition,
  type ConditionResp
} from "@/api/permission-condition";
import { type ConditionOption } from "@/api/permission-grant";

defineOptions({ name: "ReConditionPicker" });

const props = defineProps<{
  /** 选中条件 code（v-model） */
  modelValue: string | null;
  /** 条件候选列表（必传，由使用方持有单源） */
  conditions: readonly ConditionOption[];
  /** 禁用整个选择器（supportsCondition=false 时） */
  disabled?: boolean;
  /** 是否显示内联新建入口，默认 hasPerms("CONDITION:CREATE") */
  canCreate?: boolean;
}>();
const emit = defineEmits<{
  "update:modelValue": [code: string | null];
  /** 内联创建成功，使用方据此追加到自己的条件列表（保持单源） */
  created: [condition: ConditionOption];
}>();

const selected = computed({
  get: () => props.modelValue,
  set: v => emit("update:modelValue", v ?? null)
});

const canCreateActual = computed(
  () => props.canCreate ?? hasPerms("CONDITION:CREATE")
);

/** 候选项过滤：只显示启用项 + 当前选中的停用项（不展示所有停用项） */
const filteredOptions = computed(() =>
  props.conditions.filter(c => c.enabled || c.code === props.modelValue)
);

/** 当前选中条件是否不存在（已删除或列表过期，保存前需清除或重选） */
const selectedMissing = computed(() => {
  if (!props.modelValue) return false;
  return !props.conditions.some(c => c.code === props.modelValue);
});

/** 当前选中条件是否已停用（保存前需清除或替换） */
const selectedDisabled = computed(() => {
  if (!props.modelValue) return false;
  const c = props.conditions.find(c => c.code === props.modelValue);
  return c ? !c.enabled : false;
});

// ========== 内联新建（精简 create-only 元信息表单 + ReConditionEditor） ==========

const showCreate = ref(false);
const creating = ref(false);
/** 创建请求代际：reset 时递增使飞行请求失效，迟到响应只追加候选不选中 */
let createReqId = 0;
const formRef = ref<FormInstance>();
const editorRef = ref<InstanceType<typeof ReConditionEditor>>();

const form = reactive({
  code: "",
  name: "",
  enabled: true,
  gatewayEvaluable: false,
  description: "",
  rules: createEmptyRules() as ConditionRules
});

const formRules = computed<FormRules>(() => ({
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

function resetForm() {
  form.code = "";
  form.name = "";
  form.enabled = true;
  form.gatewayEvaluable = false;
  form.description = "";
  form.rules = createEmptyRules();
}

function cancelCreate() {
  // 复用 reset：递增 createReqId 使飞行创建请求失效，迟到响应不选中
  // P2：取消后请求返回不应自动选中
  reset();
}

async function onCreate() {
  // 防御 disabled 上下文（外层关闭/切换残留创建态时不应提交）
  if (props.disabled) return;
  if (!formRef.value || !editorRef.value) return;
  const myId = ++createReqId;
  creating.value = true;
  try {
    const formValid = await formRef.value.validate().catch(() => false);
    if (!formValid) return;
    if (!editorRef.value.validate()) return;

    const resp: ConditionResp = await createCondition({
      code: form.code,
      name: form.name,
      conditionRules: serializeRules(form.rules),
      enabled: form.enabled,
      gatewayEvaluable: form.gatewayEvaluable,
      description: form.description
    });
    const option: ConditionOption = {
      conditionId: resp.id,
      code: resp.code,
      name: resp.name,
      enabled: resp.enabled,
      gatewayEvaluable: resp.gatewayEvaluable,
      summary: summarizeRules(resp.conditionRules)
    };
    // 迟到响应：仅追加全局候选（emit created），不更新当前选中
    // P1：reset 后迟到响应不污染新上下文
    emit("created", option);
    if (myId === createReqId) {
      // 当前代际仍有效 -> 选中并切回选择态
      emit("update:modelValue", option.code);
      showCreate.value = false;
      resetForm();
      message("条件创建成功", { type: "success" });
    } else {
      // 迟到响应（reset 已使此请求失效）：仅追加候选，不选中
      message("条件已创建，但当前上下文已切换，未自动选中", {
        type: "info"
      });
    }
  } catch (e) {
    if (myId === createReqId) {
      message(e instanceof Error ? e.message : "创建条件失败", {
        type: "error"
      });
    }
  } finally {
    // creating 仅由当前代际收尾（新请求或 reset 已接管）
    if (myId === createReqId) creating.value = false;
  }
}

/** 校验：缺失/停用条件均阻止保存。供父弹窗 onSave 调用。 */
function validate(): boolean {
  if (selectedMissing.value) {
    message("当前选中的条件不存在或已删除，请清除或重新选择", {
      type: "warning"
    });
    return false;
  }
  if (selectedDisabled.value) {
    message("当前选中的条件已停用，请清除或替换后再保存", {
      type: "warning"
    });
    return false;
  }
  return true;
}

/** 重置内联新建态（供外层弹窗关闭/上下文切换时调用，避免状态残留） */
function reset() {
  // 递增代际使飞行创建请求失效（迟到响应不再 emit update:modelValue）
  createReqId++;
  showCreate.value = false;
  creating.value = false;
  resetForm();
}

// disabled 上下文变化时退出创建态（防止残留创建表单在不支持条件的权限上显示）
watch(
  () => props.disabled,
  d => {
    if (d && showCreate.value) reset();
  }
);

defineExpose({ validate, reset });
</script>

<template>
  <div class="cond-picker">
    <!-- 选择态 -->
    <template v-if="!showCreate">
      <el-select
        v-model="selected"
        placeholder="无条件"
        clearable
        filterable
        :disabled="disabled"
        class="cond-select"
      >
        <el-option
          v-for="c in filteredOptions"
          :key="c.code"
          :label="`${c.name}${c.enabled ? '' : '（已停用）'}`"
          :value="c.code"
          :disabled="!c.enabled && c.code !== modelValue"
        >
          <span class="cond-name">{{ c.name }}</span>
          <el-tag
            v-if="c.gatewayEvaluable"
            size="small"
            type="warning"
            effect="plain"
            class="cond-tag"
          >
            已下发
          </el-tag>
          <span class="cond-summary">{{ c.summary }}</span>
        </el-option>
      </el-select>

      <div v-if="selectedDisabled" class="cond-warning">
        当前选中的条件已停用，保存前需清除或替换
      </div>

      <el-button
        v-if="canCreateActual && !disabled"
        link
        type="primary"
        size="small"
        class="cond-create-btn"
        @click="showCreate = true"
      >
        新建条件
      </el-button>
    </template>

    <!-- 内联新建态 -->
    <div v-else class="cond-create">
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="条件编码" prop="code">
          <el-input
            v-model="form.code"
            placeholder="条件编码（业务键，创建后不可改）"
            clearable
            maxlength="64"
          />
        </el-form-item>

        <el-form-item label="条件名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入条件名称"
            clearable
            maxlength="128"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-form-item label="Gateway 评估">
          <el-switch v-model="form.gatewayEvaluable" />
          <span class="form-tip">
            开启后规则随接口快照下发 Gateway 本地重评（T-PERM-017）
          </span>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可空"
            maxlength="512"
          />
        </el-form-item>

        <el-form-item label="条件规则" required>
          <ReConditionEditor
            ref="editorRef"
            v-model="form.rules"
            :gateway-evaluable="form.gatewayEvaluable"
          />
        </el-form-item>
      </el-form>

      <div class="create-actions">
        <el-button size="small" @click="cancelCreate">取消</el-button>
        <el-button
          type="primary"
          size="small"
          :loading="creating"
          @click="onCreate"
        >
          创建并选中
        </el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.cond-picker {
  width: 100%;
}

.cond-select {
  width: 100%;
}

.cond-name {
  font-size: 13px;
}

.cond-tag {
  margin-left: var(--space-1);
}

.cond-summary {
  float: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cond-warning {
  margin-top: var(--space-1);
  font-size: 12px;
  color: var(--el-color-warning);
}

.cond-create-btn {
  margin-top: var(--space-1);
}

.cond-create {
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.form-tip {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.create-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-2);
}
</style>
