<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { message } from "@/utils/message";
import {
  CONDITION_LOGIC_OPTIONS,
  CONDITION_TYPE_OPTIONS,
  createEmptyItem,
  serializeRules,
  type ConditionRules,
  type ConditionItem
} from "@/utils/condition-rules";

defineOptions({ name: "ReConditionEditor" });

const props = defineProps<{
  /** 条件规则（v-model，结构化形式） */
  modelValue: ConditionRules;
  /** Gateway 评估开关作为校验上下文传入（开关属于父表单，规则属于 Editor） */
  gatewayEvaluable?: boolean;
  /** 禁用编辑 */
  disabled?: boolean;
}>();
const emit = defineEmits<{
  "update:modelValue": [rules: ConditionRules];
}>();

/** 内部副本：用户编辑在此进行，变更后 emit 给父；
 *  外部重置（编辑态切换 / 取消重开）通过 watch modelValue 同步回内部。 */
const rules = reactive<ConditionRules>(cloneRules(props.modelValue));
/** CIDR 输入框临时文本（key = item._id，避免删除中间项时错位） */
const cidrsInput = ref<Record<number, string>>({});

function cloneRules(r: ConditionRules): ConditionRules {
  return JSON.parse(JSON.stringify(r));
}

// 外部 -> 内部：仅当内容真的变化时同步，避免与内部 emit 形成循环
watch(
  () => props.modelValue,
  v => {
    if (serializeRules(v) !== serializeRules(rules)) {
      rules.logic = v.logic;
      rules.items.splice(0, rules.items.length, ...cloneRules(v).items);
      cidrsInput.value = {};
    }
  },
  { deep: true }
);

// 内部 -> 外部
watch(
  rules,
  v => {
    emit("update:modelValue", cloneRules(v));
  },
  { deep: true }
);

/** 规则项自定义校验：至少 1 项且每项 params 完整（前端 UX 约束，对齐 mock validateRules） */
const itemsValid = computed(() => {
  if (rules.items.length === 0) return false;
  return rules.items.every(item => {
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
  if (!props.gatewayEvaluable) return null;
  const PUSHABLE = ["IP_WHITELIST", "IP_BLACKLIST", "DATE_RANGE", "TIME_RANGE"];
  const bad = rules.items.find(i => !PUSHABLE.includes(i.type));
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
  rules.items.push(createEmptyItem("DATE_RANGE"));
}

function removeItem(index: number) {
  const removed = rules.items.splice(index, 1)[0];
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

/** 校验：规则完整性 + gateway 可下发预校验。供父表单在提交前调用。 */
function validate(): boolean {
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

defineExpose({ validate });
</script>

<template>
  <div class="rules-editor">
    <div class="rules-logic">
      <span class="rules-label">逻辑</span>
      <el-radio-group v-model="rules.logic" :disabled="disabled">
        <el-radio
          v-for="opt in CONDITION_LOGIC_OPTIONS"
          :key="opt.value"
          :label="opt.value"
          :disabled="disabled"
        >
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
    </div>

    <div v-for="(item, index) in rules.items" :key="item._id" class="rule-item">
      <div class="rule-item__header">
        <el-select
          :model-value="item.type"
          size="small"
          class="rule-type-select"
          :disabled="disabled"
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
          :disabled="disabled"
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
          :disabled="disabled"
        />
        <span class="rule-param-sep">至</span>
        <el-date-picker
          v-model="item.params.end"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束日期"
          size="small"
          class="rule-param-input"
          :disabled="disabled"
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
          :disabled="disabled"
        />
        <span class="rule-param-sep">至</span>
        <el-time-picker
          v-model="item.params.end"
          value-format="HH:mm:ss"
          placeholder="结束时间"
          size="small"
          class="rule-param-input"
          :disabled="disabled"
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
            :disabled="disabled"
            @keyup.enter="addCidr(item)"
          />
          <el-button size="small" :disabled="disabled" @click="addCidr(item)">
            添加
          </el-button>
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

    <el-button
      size="small"
      type="primary"
      plain
      :disabled="disabled"
      @click="addItem"
    >
      + 添加条件项
    </el-button>

    <div v-if="!itemsValid" class="rules-warning">
      至少 1 项条件，且每项参数需完整
    </div>
    <div v-if="gatewayPushableViolation" class="rules-warning">
      {{ gatewayPushableViolation }}
    </div>
  </div>
</template>

<style lang="scss" scoped>
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
