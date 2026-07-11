<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { message } from "@/utils/message";
import { type ConditionResp } from "@/api/permission-condition";
import {
  CONDITION_LOGIC_OPTIONS,
  CONDITION_TYPE_OPTIONS,
  createEmptyConditionForm,
  createEmptyItem,
  parseRules,
  type ConditionFormData,
  type ConditionItem
} from "../utils/types";

defineOptions({ name: "ConditionForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的完整条件数据 */
  initialData?: ConditionResp | null;
}>();

const formData = reactive<ConditionFormData>({ ...createEmptyConditionForm() });
const formRef = ref<FormInstance>();
/** CIDR 输入框临时文本（key = item._id，避免删除中间项时错位） */
const cidrsInput = ref<Record<number, string>>({});

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

/** 规则项自定义校验：至少 1 项且每项 params 完整（前端 UX 约束，对齐 mock validateRules） */
const itemsValid = computed(() => {
  if (formData.rules.items.length === 0) return false;
  return formData.rules.items.every(item => {
    if (item.type === "DATE_RANGE" || item.type === "TIME_RANGE") {
      return !!item.params.start && !!item.params.end;
    }
    if (item.type === "IP_WHITELIST" || item.type === "IP_BLACKLIST") {
      return (item.params.cidrs?.length ?? 0) > 0;
    }
    return false;
  });
});

/** gatewayEvaluable=true 时，规则含非白名单类型则不可保存（前端预校验，对齐后端 validateGatewayPushable）。
 *  当前 4 类全在白名单，实际总能通过；保留以防未来扩展类型。 */
const gatewayPushableViolation = computed(() => {
  if (!formData.gatewayEvaluable) return null;
  const PUSHABLE = ["IP_WHITELIST", "IP_BLACKLIST", "DATE_RANGE", "TIME_RANGE"];
  const bad = formData.rules.items.find(i => !PUSHABLE.includes(i.type));
  return bad ? `gatewayEvaluable=true 不允许类型: ${bad.type}` : null;
});

function onTypeChange(item: ConditionItem, newType: string) {
  // 切换类型时重置 params 结构（旧值不兼容新类型）
  item.type = newType;
  if (newType === "IP_WHITELIST" || newType === "IP_BLACKLIST") {
    item.params = { cidrs: [] };
  } else {
    item.params = { start: "", end: "" };
  }
}

function addItem() {
  formData.rules.items.push(createEmptyItem("DATE_RANGE"));
}

function removeItem(index: number) {
  const removed = formData.rules.items.splice(index, 1)[0];
  if (removed) delete cidrsInput.value[removed._id];
}

function addCidr(item: ConditionItem) {
  const raw = (cidrsInput.value[item._id] ?? "").trim();
  if (!raw) return;
  if (!item.params.cidrs) item.params.cidrs = [];
  // 支持逗号/换行/空格分隔批量输入
  const parts = raw.split(/[\s,]+/).filter(Boolean);
  for (const p of parts) {
    if (!item.params.cidrs.includes(p)) item.params.cidrs.push(p);
  }
  cidrsInput.value[item._id] = "";
}

function removeCidr(item: ConditionItem, cidr: string) {
  if (item.params.cidrs) {
    item.params.cidrs = item.params.cidrs.filter(c => c !== cidr);
  }
}

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
  cidrsInput.value = {};
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
  if (!itemsValid.value) {
    message("请完善条件规则（至少 1 项且参数完整）");
    return false;
  }
  if (gatewayPushableViolation.value) {
    message(gatewayPushableViolation.value);
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
      <div class="rules-editor">
        <div class="rules-logic">
          <span class="rules-label">逻辑</span>
          <el-radio-group v-model="formData.rules.logic">
            <el-radio
              v-for="opt in CONDITION_LOGIC_OPTIONS"
              :key="opt.value"
              :label="opt.value"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </div>

        <div
          v-for="(item, index) in formData.rules.items"
          :key="item._id"
          class="rule-item"
        >
          <div class="rule-item__header">
            <el-select
              :model-value="item.type"
              size="small"
              class="rule-type-select"
              @change="(v: string) => onTypeChange(item, v)"
            >
              <el-option
                v-for="opt in CONDITION_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button
              link
              type="danger"
              size="small"
              @click="removeItem(index)"
            >
              删除
            </el-button>
          </div>

          <!-- DATE_RANGE -->
          <div v-if="item.type === 'DATE_RANGE'" class="rule-params">
            <el-date-picker
              v-model="item.params.start"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              size="small"
              class="rule-param-input"
            />
            <span class="rule-param-sep">至</span>
            <el-date-picker
              v-model="item.params.end"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              size="small"
              class="rule-param-input"
            />
          </div>

          <!-- TIME_RANGE -->
          <div v-else-if="item.type === 'TIME_RANGE'" class="rule-params">
            <el-time-picker
              v-model="item.params.start"
              value-format="HH:mm:ss"
              placeholder="开始时间"
              size="small"
              class="rule-param-input"
            />
            <span class="rule-param-sep">至</span>
            <el-time-picker
              v-model="item.params.end"
              value-format="HH:mm:ss"
              placeholder="结束时间"
              size="small"
              class="rule-param-input"
            />
          </div>

          <!-- IP_WHITELIST / IP_BLACKLIST -->
          <div v-else class="rule-params rule-params--ip">
            <div class="cidr-input-row">
              <el-input
                v-model="cidrsInput[item._id]"
                placeholder="输入 CIDR 回车添加（如 192.168.1.0/24）"
                size="small"
                class="rule-cidr-input"
                @keyup.enter="addCidr(item)"
              />
              <el-button size="small" @click="addCidr(item)"> 添加 </el-button>
            </div>
            <div v-if="item.params.cidrs?.length" class="cidr-tags">
              <el-tag
                v-for="cidr in item.params.cidrs"
                :key="cidr"
                closable
                size="small"
                @close="removeCidr(item, cidr)"
              >
                {{ cidr }}
              </el-tag>
            </div>
          </div>
        </div>

        <el-button size="small" type="primary" plain @click="addItem">
          + 添加条件项
        </el-button>

        <div v-if="!itemsValid" class="rules-warning">
          至少 1 项条件，且每项参数需完整
        </div>
        <div v-if="gatewayPushableViolation" class="rules-warning">
          {{ gatewayPushableViolation }}
        </div>
      </div>
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

.rules-editor {
  width: 100%;
}

.rules-logic {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-2);
}

.rules-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.rule-item {
  padding: var(--space-2);
  margin-bottom: var(--space-2);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.rule-item__header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.rule-type-select {
  width: 160px;
}

.rule-params {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.rule-params--ip {
  flex-direction: column;
  align-items: stretch;
}

.rule-param-input {
  width: 180px;
}

.rule-param-sep {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.cidr-input-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.rule-cidr-input {
  flex: 1;
  min-width: 200px;
}

.cidr-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-1);
}

.rules-warning {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
