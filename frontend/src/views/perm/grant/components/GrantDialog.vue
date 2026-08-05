<script setup lang="ts">
/**
 * 授权弹窗（§4，以操作权限为维度；方案二全量语义：勾选=最终授权状态，取消勾选=撤权）。
 * Step 1 选操作（按资源类型分组）-> Step 2 现状（覆盖位集展开，只读）->
 * Step 3 范围+资源（INSTANCE 多选树 / ALL 全量）-> Step 4 条件+canGrant。
 * 确定语义（三键模型，匹配仅限 MANUAL）：分组键不存在->add；同键同条件->update；
 * 同键不同条件->add 新分支；取消勾选已有记录->remove（同分组键全部 MANUAL 分支）。
 *
 * 预填（评审问题 1）：拆 watch(selectedOpKey) + watch(scopeMode) 双 watcher；
 * initial 仅首次打开消费（initialConsumed 标记），切换操作后按现有授权重新预填
 * （工具栏打开选操作也会预填现有 INSTANCE 授权）；computePreset 纯函数算默认 scopeMode
 * + 勾选键；applyPresetForCurrentScope 用 presetToken 代际令牌 + nextTick 防快速切换
 * 乱序（DOM 写入前后均核对 token），不依赖固定 setTimeout。
 */
import { computed, nextTick, ref, watch } from "vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { ConditionResp } from "@/api/permission-condition";
import {
  collectOperationGrants,
  type OperationDefInput
} from "../utils/source-chain";
import {
  computePreset,
  type DialogResult,
  type EffectiveRecord
} from "../utils/grant-plan";
import ConditionPicker from "./ConditionPicker.vue";

const props = defineProps<{
  modelValue: boolean;
  /** 触发预填（无权限单元格点击：操作+资源/范围预填） */
  initial: {
    operationCode?: string;
    resourceTypeCode?: string;
    resourceCode?: string | null;
    codeType?: string | null;
    scopeMode?: "INSTANCE" | "ALL";
  };
  operations: OperationDefInput[];
  resourceForest: ResourceTreeNode[];
  conditions: ConditionResp[];
  /** 生效主权限（baseline+draft；Step2 现状 + Step3 预填） */
  records: EffectiveRecord[];
  canCondition: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", result: DialogResult): void;
}>();

// ========== Step 1 选操作（按资源类型分组；专属与全局分组展示） ==========

type OpOption = {
  key: string;
  code: string;
  name: string;
  resourceTypeCode: string | null;
};

const opGroups = computed(() => {
  const byType = new Map<string, OpOption[]>();
  const globalOps: OpOption[] = [];
  for (const op of props.operations) {
    const option: OpOption = {
      key: `${op.resourceTypeCode ?? "GLOBAL"}:${op.code}`,
      code: op.code,
      name: op.name,
      resourceTypeCode: op.resourceTypeCode
    };
    if (op.resourceTypeCode == null) {
      globalOps.push(option);
    } else {
      const list = byType.get(op.resourceTypeCode) ?? [];
      list.push(option);
      byType.set(op.resourceTypeCode, list);
    }
  }
  const groups = [...byType.entries()].map(([typeCode, options]) => ({
    label: `资源类型 ${typeCode}`,
    options
  }));
  if (globalOps.length) {
    groups.push({ label: "全局操作", options: globalOps });
  }
  return groups;
});

const selectedOpKey = ref<string | null>(null);
const selectedOp = computed<OpOption | null>(() => {
  if (!selectedOpKey.value) return null;
  for (const group of opGroups.value) {
    const hit = group.options.find(o => o.key === selectedOpKey.value);
    if (hit) return hit;
  }
  return null;
});

// ========== Step 2 现状（覆盖位集展开，P1-2；只读辅助决策） ==========

const currentGrants = computed(() => {
  const op = selectedOp.value;
  if (!op) return [];
  // 现状按草稿生效口径：已标记 remove 的记录不再展示
  return collectOperationGrants({
    records: props.records.filter(r => r.draftMark !== "remove"),
    operations: props.operations,
    target: { resourceTypeCode: op.resourceTypeCode, code: op.code }
  });
});

function grantSourceText(hit: (typeof currentGrants.value)[number]): string {
  const parts: string[] = [];
  if (hit.record.grantSource === "AUTO_DEP") parts.push("自动补全");
  if (hit.combinationBit) parts.push("组合位（含当前操作）");
  else if (hit.hit === "COVERED") parts.push("继承覆盖");
  else parts.push("直接授权");
  return parts.join(" · ");
}

// ========== Step 3 范围 + 资源（INSTANCE 多选树 / ALL 全量） ==========

const scopeMode = ref<"INSTANCE" | "ALL">("INSTANCE");
const treeRef = ref();
const allScopeType = ref<string | null>(null);

/** 资源树节点 key（类型:编码:编码类型） */
function nodeKeyOf(node: ResourceTreeNode): string {
  return `${node.resourceTypeCode}:${node.code}:${node.codeType}`;
}

/** Step 3 可选树：专属操作仅本类型树；全局操作全类型森林 */
const selectableForest = computed(() => {
  const op = selectedOp.value;
  if (!op) return [];
  if (op.resourceTypeCode != null) {
    return props.resourceForest.filter(
      root => root.resourceTypeCode === op.resourceTypeCode
    );
  }
  return props.resourceForest;
});

const selectableTypes = computed(() => {
  const types = new Set<string>();
  for (const root of props.resourceForest) types.add(root.resourceTypeCode);
  return [...types].sort();
});

const treeProps = { label: "name", children: "children" };

// ========== Step 4 条件 + canGrant ==========

const conditionCode = ref<string | null>(null);
const canGrant = ref(false);

// ========== 预填（评审问题 1：双 watcher + 代际令牌） ==========

/** tripleKey -> 资源节点 id（setCheckedKeys 用） */
const idByTripleKey = computed(() => {
  const map = new Map<string, number>();
  const collect = (nodes: ResourceTreeNode[]) => {
    for (const node of nodes) {
      map.set(nodeKeyOf(node), node.id);
      if (node.children?.length) collect(node.children);
    }
  };
  collect(props.resourceForest);
  return map;
});

/** 预填代际令牌（防快速切换操作/范围导致 setCheckedKeys 乱序） */
const presetToken = ref(0);
/** initial 仅首次打开消费标记（切换操作后不再用 initial.resourceCode/scopeMode） */
const initialConsumed = ref(false);
/** 首次打开 initial.resourceCode 额外勾选（切换操作后清空） */
const presetExtra = ref<Set<string>>(new Set());

/**
 * 应用当前 scopeMode 下的预填勾选（评审问题 1）。
 * nextTick 等树渲染 + presetToken 代际令牌防乱序（DOM 写入前后均核对）。
 */
async function applyPresetForCurrentScope() {
  const op = selectedOp.value;
  if (!op) return;
  const token = ++presetToken.value;
  await nextTick();
  if (token !== presetToken.value) return;
  if (scopeMode.value !== "INSTANCE") {
    // ALL 无树勾选
    if (token !== presetToken.value) return;
    treeRef.value?.setCheckedKeys([]);
    return;
  }
  const preset = computePreset({
    op: { code: op.code, resourceTypeCode: op.resourceTypeCode },
    records: props.records
  });
  const keys = new Set(preset.checkedTripleKeys);
  for (const k of presetExtra.value) keys.add(k);
  const ids = [...keys]
    .map(k => idByTripleKey.value.get(k))
    .filter((id): id is number => id != null);
  // DOM 写入前再核对 token
  if (token !== presetToken.value) return;
  treeRef.value?.setCheckedKeys(ids);
}

// ========== 打开初始化 + 手动切换预填（评审问题 1+2） ==========

/** 解析 op key -> OpOption */
function resolveOp(key: string | null): OpOption | null {
  if (!key) return null;
  for (const group of opGroups.value) {
    const hit = group.options.find(o => o.key === key);
    if (hit) return hit;
  }
  return null;
}

/**
 * 打开预填（initial 首次消费，评审问题 1+2）。
 * initial.scopeMode 优先 + initial.resourceCode 入 extra。
 */
async function applyOpenPreset(op: OpOption | null) {
  const initial = props.initial;
  if (!op) {
    scopeMode.value = initial.scopeMode ?? "INSTANCE";
    presetExtra.value = new Set();
    initialConsumed.value = true;
    await applyPresetForCurrentScope();
    return;
  }
  const preset = computePreset({
    op: { code: op.code, resourceTypeCode: op.resourceTypeCode },
    records: props.records
  });
  scopeMode.value = initial.scopeMode ?? preset.scopeMode;
  const extra = new Set<string>();
  if (initial.resourceCode && initial.codeType && initial.resourceTypeCode) {
    extra.add(
      `${initial.resourceTypeCode}:${initial.resourceCode}:${initial.codeType}`
    );
  }
  presetExtra.value = extra;
  initialConsumed.value = true;
  await applyPresetForCurrentScope();
}

/**
 * 弹窗内手动切换操作预填（不含 initial，评审问题 1+2）。
 */
async function applySwitchPreset(op: OpOption | null) {
  if (!op) {
    scopeMode.value = "INSTANCE";
    presetExtra.value = new Set();
    await applyPresetForCurrentScope();
    return;
  }
  const preset = computePreset({
    op: { code: op.code, resourceTypeCode: op.resourceTypeCode },
    records: props.records
  });
  scopeMode.value = preset.scopeMode;
  presetExtra.value = new Set();
  await applyPresetForCurrentScope();
}

/** 打开流程标志：selectedOpKey sync watcher 跳过打开时的程序设值（评审问题 2） */
let isOpening = false;

watch(
  () => props.modelValue,
  async visible => {
    if (!visible) return;
    conditionCode.value = null;
    canGrant.value = false;
    allScopeType.value = null;
    initialConsumed.value = false;
    presetExtra.value = new Set();
    const initial = props.initial;
    let opKey = null;
    if (initial.operationCode) {
      const match =
        opGroups.value
          .flatMap(g => g.options)
          .find(
            o =>
              o.code === initial.operationCode &&
              (initial.resourceTypeCode == null ||
                o.resourceTypeCode === initial.resourceTypeCode ||
                o.resourceTypeCode == null)
          ) ?? null;
      opKey = match?.key ?? null;
    }
    // 显式预填（不依赖 selectedOpKey watcher，避免重开同操作不触发，评审问题 2）
    isOpening = true;
    selectedOpKey.value = opKey;
    await applyOpenPreset(resolveOp(opKey));
    isOpening = false;
  }
);

/** 选操作 -> 手动切换预填（sync + isOpening 跳过打开触发，评审问题 2） */
watch(
  selectedOpKey,
  () => {
    if (isOpening) return;
    void applySwitchPreset(selectedOp.value);
  },
  { flush: "sync" }
);

/** 范围切换 -> 重新预填（不改默认 scopeMode，评审问题 1） */
watch(scopeMode, async () => {
  await applyPresetForCurrentScope();
});

// ========== 确定 ==========

const confirmDisabled = computed(() => {
  if (!selectedOp.value) return true;
  if (scopeMode.value === "ALL") {
    return !(effectiveAllType.value || allScopeType.value);
  }
  return false;
});

/** ALL 范围目标类型：专属操作 = 操作类型；全局操作 = 用户选择 */
const effectiveAllType = computed(() => {
  const op = selectedOp.value;
  if (!op) return null;
  return op.resourceTypeCode ?? allScopeType.value;
});

function handleConfirm() {
  const op = selectedOp.value;
  if (!op) return;
  let resources: DialogResult["resources"] = [];
  if (scopeMode.value === "ALL") {
    const typeCode = effectiveAllType.value!;
    resources = [
      {
        resourceTypeCode: typeCode,
        resourceCode: null,
        codeType: null,
        name: `全部资源（${typeCode}）`
      }
    ];
  } else {
    const checked = treeRef.value?.getCheckedNodes(false) as
      | ResourceTreeNode[]
      | undefined;
    resources = (checked ?? []).map(node => ({
      resourceTypeCode: node.resourceTypeCode,
      resourceCode: node.code,
      codeType: node.codeType,
      name: node.name
    }));
  }
  emit("confirm", {
    operation: { code: op.code, resourceTypeCode: op.resourceTypeCode },
    scopeMode: scopeMode.value,
    resources,
    conditionCode: conditionCode.value,
    canGrant: canGrant.value
  });
}

function handleClose() {
  emit("update:modelValue", false);
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="授权"
    width="720px"
    class="grant-dialog"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <!-- Step 1 选操作权限（按资源类型分组） -->
    <div class="step">
      <div class="step-title">
        <span class="step-index">1</span>选择操作权限
      </div>
      <el-select
        v-model="selectedOpKey"
        placeholder="选择操作权限（按资源类型分组）"
        filterable
        class="op-select"
      >
        <el-option-group
          v-for="group in opGroups"
          :key="group.label"
          :label="group.label"
        >
          <el-option
            v-for="op in group.options"
            :key="op.key"
            :value="op.key"
            :label="`${op.name}（${op.code}）`"
          />
        </el-option-group>
      </el-select>
    </div>

    <!-- Step 2 现状：该操作在当前主体下的资源授予情况（只读，覆盖位集展开） -->
    <div v-if="selectedOp" class="step">
      <div class="step-title">
        <span class="step-index">2</span>当前授予现状（只读）
      </div>
      <el-table
        v-if="currentGrants.length"
        :data="currentGrants"
        size="small"
        max-height="180"
      >
        <el-table-column label="资源" min-width="160">
          <template #default="{ row }">
            {{
              row.record.resourceName ?? row.record.resourceCode ?? "全部资源"
            }}
          </template>
        </el-table-column>
        <el-table-column label="范围" width="70">
          <template #default="{ row }">
            {{ row.record.scopeMode === "ALL" ? "全量" : "实例" }}
          </template>
        </el-table-column>
        <el-table-column label="条件" min-width="100">
          <template #default="{ row }">
            {{ row.record.conditionCode ?? "无" }}
          </template>
        </el-table-column>
        <el-table-column label="可转授" width="70">
          <template #default="{ row }">
            {{ row.record.canGrant ? "是" : "否" }}
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="140">
          <template #default="{ row }">
            {{ grantSourceText(row) }}
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="step-empty">该操作在当前主体下暂无授权记录</div>
    </div>

    <!-- Step 3 范围 + 资源 -->
    <div v-if="selectedOp" class="step">
      <div class="step-title">
        <span class="step-index">3</span>选择范围与资源
      </div>
      <div class="preset-hint">
        切换操作或范围后，将按当前授权重新加载；本弹窗内尚未确认的勾选不会保留。
      </div>
      <el-radio-group v-model="scopeMode">
        <el-radio-button value="INSTANCE">实例（多选）</el-radio-button>
        <el-radio-button value="ALL">全量（ALL）</el-radio-button>
      </el-radio-group>

      <!-- 全局操作范围提示（问题 2）：明确全量编辑边界，避免误撤权 -->
      <div
        v-if="selectedOp.resourceTypeCode == null"
        class="scope-hint"
        :class="scopeMode === 'ALL' ? 'hint-info' : 'hint-warning'"
      >
        <template v-if="scopeMode === 'ALL'"
          >本次只编辑所选资源类型的 ALL 授权，其他资源类型不受影响。</template
        >
        <template v-else
          >当前勾选是该全局操作在所有资源类型下的最终实例授权；取消勾选将产生撤权变更。</template
        >
      </div>

      <div v-if="scopeMode === 'INSTANCE'" class="scope-tree">
        <el-tree
          ref="treeRef"
          :data="selectableForest"
          :props="treeProps"
          show-checkbox
          check-strictly
          node-key="id"
          :expand-on-click-node="false"
          default-expand-all
        >
          <template #default="{ data }">
            <span
              >{{ data.name
              }}<span class="node-code">{{ data.code }}</span></span
            >
          </template>
        </el-tree>
        <div class="tree-hint">
          严格勾选：勾选父节点不会自动选中子节点；父节点授权经资源继承自动覆盖全部子孙，无需重复勾选。
        </div>
      </div>
      <div v-else class="scope-all">
        <template v-if="selectedOp.resourceTypeCode != null">
          <el-tag type="warning"
            >全部资源（{{ selectedOp.resourceTypeCode }}）</el-tag
          >
          <span class="hint">该操作所属资源类型的全部资源</span>
        </template>
        <template v-else>
          <el-select
            v-model="allScopeType"
            placeholder="选择资源类型（全局操作需指定类型）"
          >
            <el-option
              v-for="typeCode in selectableTypes"
              :key="typeCode"
              :value="typeCode"
              :label="typeCode"
            />
          </el-select>
          <span class="hint">全局操作按所选类型全量生效</span>
        </template>
      </div>
    </div>

    <!-- Step 4 条件 + canGrant -->
    <div v-if="selectedOp" class="step">
      <div class="step-title"><span class="step-index">4</span>条件与转授</div>
      <div class="step-4">
        <ConditionPicker
          v-model="conditionCode"
          :conditions="conditions"
          :disabled="!canCondition"
          placeholder="无条件"
        />
        <el-tooltip
          v-if="!canCondition"
          content="无 CONDITION:VIEW 权限，条件选择不可用（可前往 3.2 权限条件页了解）"
          placement="top"
        >
          <span class="condition-blocked">条件置灰</span>
        </el-tooltip>
        <el-checkbox v-model="canGrant">允许再授予（canGrant）</el-checkbox>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="confirmDisabled"
        @click="handleConfirm"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
/* 弹窗局部细化：el-dialog teleport 到 body，scoped 无法命中，
   通过唯一 class grant-dialog + :global() 限定在本页授权弹窗 */
:global(.grant-dialog.el-dialog) {
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 32px rgb(15 23 42 / 12%);
}

:global(.grant-dialog .el-dialog__header) {
  padding: var(--space-4) var(--space-5) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:global(.grant-dialog .el-dialog__header .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
}

:global(.grant-dialog .el-dialog__body) {
  padding: var(--space-4) var(--space-5);
}

:global(.grant-dialog .el-dialog__footer) {
  padding: var(--space-3) var(--space-5);
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color-lighter);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.step {
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-3);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);

  .step-title {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-2);
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);

    .step-index {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      height: 18px;
      font-size: 11px;
      color: #fff;
      background: var(--el-color-primary);
      border-radius: var(--radius-full);
    }
  }

  .op-select {
    width: 320px;
  }

  .step-empty {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .preset-hint {
    margin-bottom: var(--space-2);
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .scope-hint {
    padding: var(--space-1) var(--space-2);
    margin-top: var(--space-2);
    font-size: 12px;
    border-radius: var(--radius-sm);

    &.hint-info {
      color: var(--el-color-info);
      background: var(--el-color-info-light-9);
    }

    &.hint-warning {
      color: var(--el-color-warning);
      background: var(--el-color-warning-light-9);
    }
  }

  .scope-tree {
    max-height: 260px;
    margin-top: var(--space-2);
    overflow: auto;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--radius-sm);

    .node-code {
      margin-left: 6px;
      font-size: 11px;
      color: var(--el-text-color-secondary);
    }

    .tree-hint {
      position: sticky;
      bottom: 0;
      padding: 4px 8px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      background: var(--el-bg-color);
      border-top: 1px solid var(--el-border-color-lighter);
    }
  }

  .scope-all {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-top: var(--space-2);

    .hint {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .step-4 {
    display: flex;
    gap: var(--space-3);
    align-items: center;

    .condition-blocked {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
