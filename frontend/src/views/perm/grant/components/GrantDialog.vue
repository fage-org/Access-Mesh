<script setup lang="ts">
/**
 * 授权弹窗（§4 v3.1 记录级聚焦编辑，T-FE-040）。
 * - 打开即展示当前类型资源树；选择操作后按当前生效草稿预填授权状态；
 * - 勾选=授权，取消勾选=撤权（suspended 暂存，同弹窗重新勾选恢复）；
 * - 复选框切换勾选、点击行聚焦：授权设置（条件/canGrant）只作用于聚焦记录；
 * - ALL 收敛为资源树右上角的"全量"选择按钮；勾选 ALL 自动聚焦当前类型 ALL 槽位，
 *   取消 ALL 回 INSTANCE 焦点清空；显式复制把聚焦记录属性应用到已勾选目标；
 * - 子权限配置器单父上下文（父 = 聚焦记录），按 SUB_PERM 允许集过滤候选。
 */
import { computed, nextTick, ref, watch } from "vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { ConditionResp } from "@/api/permission-condition";
import type {
  GrantRecordKey,
  InlineConditionDef,
  RolePermissionItem
} from "@/api/permission-grant";
import ReConditionEditor from "@/components/ReConditionEditor/src/index.vue";
import { message } from "@/utils/message";
import {
  createEmptyRules,
  parseRules,
  serializeRules,
  validateRulesComplete,
  type ConditionRules
} from "@/utils/condition-rules";
import type { OperationDefInput } from "../utils/source-chain";
import {
  applyDraftToRecords,
  applyFocusAttributes,
  buildAddChange,
  buildRemoveChange,
  buildSummary,
  computePreset,
  copySlotAttributes,
  draftParentKey,
  expandSuspended,
  findSlotRecord,
  normalizeChildGrantKey,
  persistedParentKey,
  resourceGroupKeyOf,
  resumeSlot,
  slotKeyOf,
  uncheckSlot,
  validateInlineDefs,
  type EffectiveRecord,
  type FocusSlot,
  type SlotDraftState,
  type SuspendedSlot
} from "../utils/grant-plan";
import type { DraftChange } from "../utils/types";
import ConditionPicker from "./ConditionPicker.vue";
import GrantChildConfigurator from "./GrantChildConfigurator.vue";

const props = defineProps<{
  modelValue: boolean;
  /** 触发预填（无权限单元格点击：操作+资源/范围预填；含聚焦槽位） */
  initial: {
    operationCode?: string;
    resourceTypeCode?: string;
    resourceCode?: string | null;
    codeType?: string | null;
    scopeMode?: "INSTANCE" | "ALL";
  };
  operations: OperationDefInput[];
  resourceForest: ResourceTreeNode[];
  /** 子权限可跨类型，使用依赖加载阶段取得的全量定义。 */
  allOperations: OperationDefInput[];
  allResourceForest: ResourceTreeNode[];
  conditions: ConditionResp[];
  /** 生效主权限（baseline+draft；资源树授权现状预填） */
  records: EffectiveRecord[];
  /** 弹窗本地事务基线；确认前不写入页面草稿。 */
  baseline: RolePermissionItem[];
  draftChanges: DraftChange[];
  /** 目标角色业务键（sub-perm-allowed-types 门禁定位用，T-FE-040 S6） */
  subjectKey: { roleTypeCode: string; roleExternalId: string };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", changes: DraftChange[]): void;
}>();

const editorMode = ref<"main" | "children">("main");
const localChanges = ref<DraftChange[]>([]);

// ========== v3.1 焦点生命周期（S1/S2，设计 §4） ==========

/** 弹窗本地槽位草稿状态（suspended 暂存 + 焦点；确认时 expandSuspended 双路径展开） */
const slotDraft = ref<SlotDraftState>({
  changes: [],
  suspended: new Map<string, SuspendedSlot>(),
  focusSlotKey: null
});

/** 当前焦点槽位（null = 未聚焦；点击资源行聚焦） */
const focusSlot = ref<FocusSlot | null>(null);

/** 焦点记录属性（编辑态；聚焦未授权资源时为只读默认值） */
const focusConditionCode = ref<string | null>(null);
const focusCanGrant = ref(false);
/** 焦点属性是否被用户修改过（未修改则不产生 update） */
const focusTouched = ref(false);
/** 内联条件编辑态（T-PERM-048 双轨制定案①）：非空 = 该记录最终条件为内联定义；
 *  与 focusConditionCode 互斥（内联态下 conditionCode 恒 null） */
const focusInline = ref<InlineConditionDef | null>(null);
/** 内联规则编辑模型（结构化，ReConditionEditor v-model；序列化进 focusInline.conditionRules） */
const inlineRulesModel = ref<ConditionRules>(createEmptyRules());
/** 内联编辑器实例（确认前校验入口） */
const inlineEditorRef = ref<InstanceType<
  typeof ReConditionEditor
> | null>(null);

// ========== 操作权限（按资源类型分组；专属与全局分组展示） ==========

type OpOption = {
  key: string;
  code: string;
  name: string;
  resourceTypeCode: string;
};

const opGroups = computed(() => {
  // 全局操作概念已退役：操作定义必属某类型，按类型分组（无"全局操作"组）
  const byType = new Map<string, OpOption[]>();
  for (const op of props.operations) {
    const option: OpOption = {
      key: `${op.resourceTypeCode}:${op.code}`,
      code: op.code,
      name: op.name,
      resourceTypeCode: op.resourceTypeCode
    };
    const list = byType.get(op.resourceTypeCode) ?? [];
    list.push(option);
    byType.set(op.resourceTypeCode, list);
  }
  return [...byType.entries()].map(([typeCode, options]) => ({
    label: `资源类型 ${typeCode}`,
    options
  }));
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

// ========== 资源树（INSTANCE 多选 / ALL 紧凑选择） ==========

const scopeMode = ref<"INSTANCE" | "ALL">("INSTANCE");
const treeRef = ref();

/** 资源树节点 key（类型:编码:编码类型） */
function nodeKeyOf(node: ResourceTreeNode): string {
  return `${node.resourceTypeCode}:${node.code}:${node.codeType}`;
}

function nodeResourceKeyOf(node: ResourceTreeNode): string {
  return resourceGroupKeyOf({
    resourceTypeCode: node.resourceTypeCode,
    resourceCode: node.code,
    codeType: node.codeType
  });
}

/** 未选操作也展示当前资源树；选中操作后仅保留其类型（操作定义按类型隔离）。 */
const selectableForest = computed(() => {
  const op = selectedOp.value;
  if (!op) return props.resourceForest;
  return props.resourceForest.filter(
    root => root.resourceTypeCode === op.resourceTypeCode
  );
});

const selectableTypes = computed(() => {
  const types = new Set<string>();
  for (const root of props.resourceForest) types.add(root.resourceTypeCode);
  return [...types].sort();
});

/** ALL 范围目标类型：操作定义按类型隔离（全局操作概念已退役），ALL 固定用操作自身类型。 */
const effectiveAllType = computed(() => {
  return selectedOp.value?.resourceTypeCode ?? null;
});

const currentAllResourceKey = computed(() => {
  const typeCode = effectiveAllType.value;
  if (!typeCode) return null;
  return resourceGroupKeyOf({
    resourceTypeCode: typeCode,
    resourceCode: null,
    codeType: null
  });
});

const allScopeSelected = computed({
  get: () => scopeMode.value === "ALL",
  set: (selected: boolean) => {
    if (!selectedOp.value) return;
    if (selected) {
      const typeCode = effectiveAllType.value;
      if (typeCode) {
        // P1-1 修复：顺序必须为 构造 ALL slot → resume（add/恢复 suspended）→ 同步草稿 → focusOn，
        // 否则焦点会读到默认值（未授权 ALL 勾选后确定应有 add 草稿，取消后再勾选应恢复）
        const slot: FocusSlot = {
          resourceTypeCode: typeCode,
          resourceCode: null,
          codeType: null,
          operationCode: selectedOp.value.code,
          scopeMode: "ALL"
        };
        slotDraft.value = resumeSlot(slotDraft.value, {
          slot,
          view: localView.value,
          baseline: props.baseline,
          operations: props.allOperations
        });
        localChanges.value = slotDraft.value.changes;
        scopeMode.value = "ALL";
        focusOn(slot);
      }
    } else {
      // v3.1 S2：取消 ALL 回 INSTANCE → 原 ALL 记录取消勾选（suspended 暂存）+ 焦点清空
      const typeCode = effectiveAllType.value;
      if (typeCode) {
        const slot: FocusSlot = {
          resourceTypeCode: typeCode,
          resourceCode: null,
          codeType: null,
          operationCode: selectedOp.value.code,
          scopeMode: "ALL"
        };
        if (findSlotRecord(localView.value, slot)) {
          slotDraft.value = uncheckSlot(slotDraft.value, {
            slot,
            view: localView.value
          });
          localChanges.value = slotDraft.value.changes;
        }
      }
      scopeMode.value = "INSTANCE";
      focusOn(null);
    }
  }
});

const resourceSelectionDisabled = computed(
  () => selectedOp.value == null || scopeMode.value === "ALL"
);

const treeRenderKey = computed(
  () => `${selectedOpKey.value ?? "preview"}:${scopeMode.value}`
);

const resourceHint = computed(() => {
  if (!selectedOp.value) return "选择操作权限后可调整授权";
  if (scopeMode.value === "ALL") return "已选择当前类型的全部资源";
  return "勾选表示授权，取消勾选表示撤销";
});

const treeProps = {
  label: "name",
  children: "children",
  disabled: () => resourceSelectionDisabled.value
};

// ========== 授权设置（v3.1 记录级：只作用于聚焦记录，设计 §4） ==========

/** 焦点槽位的生效记录（聚焦且已有授权/已勾选时存在；排除 suspended 待撤销槽位，四审 P1-2） */
const focusedRecord = computed<EffectiveRecord | null>(() => {
  const slot = focusSlot.value;
  if (!slot) return null;
  if (slotDraft.value.suspended.has(slotKeyOf(slot))) return null;
  return findSlotRecord(localView.value, slot);
});

/**
 * 有效来源提示（D4，措辞限定）：聚焦记录带条件，且当前角色已加载来源中
 * 存在其他无条件来源（同槽位 AUTO_DEP/MANUAL 并列）→ 提示当前条件授权仅影响有条件来源。
 */
const sourceHint = computed<string | null>(() => {
  const slot = focusSlot.value;
  const record = focusedRecord.value;
  if (!slot || !record || record.conditionCode == null) return null;
  const hasUnconditional = localView.value.mains.some(
    m =>
      m.id !== record.id &&
      m.draftMark !== "remove" &&
      m.conditionCode == null &&
      slotKeyOf({
        resourceTypeCode: m.resourceTypeCode,
        resourceCode: m.resourceCode,
        codeType: m.codeType,
        operationCode: m.operationCode ?? "",
        scopeMode: m.scopeMode
      }) === slotKeyOf(slot)
  );
  return hasUnconditional
    ? "另有无条件来源，当前条件授权仅影响有条件来源"
    : null;
});

/** 聚焦槽位（点击资源行 / 打开预填 / ALL 勾选 调用；null = 清除焦点）；
 * suspended 待撤销槽位可聚焦但只读默认值（四审 P1-2：不恢复可编辑属性，避免同 ID 双 update） */
function focusOn(slot: FocusSlot | null) {
  focusSlot.value = slot;
  if (!slot) {
    focusConditionCode.value = null;
    focusCanGrant.value = false;
    focusInline.value = null;
    focusTouched.value = false;
    return;
  }
  const record = slotDraft.value.suspended.has(slotKeyOf(slot))
    ? null
    : findSlotRecord(localView.value, slot);
  focusCanGrant.value = record?.canGrant ?? false;
  // T-PERM-048 双轨：现绑定为 INLINE 条件（conditions 含内联实体）→ 内联编辑态预载规则；
  // 草稿 add 的内联绑定 → 从生效记录的 inlineCondition 预载；其余 → managed 引用态
  const boundInline = record?.conditionCode
    ? props.conditions.find(
        c => c.code === record.conditionCode && c.source === "INLINE"
      )
    : null;
  const draftInline =
    !boundInline && record?.inlineCondition ? record.inlineCondition : null;
  const inlineSource = boundInline
    ? {
        name: boundInline.name,
        conditionRules: boundInline.conditionRules,
        gatewayEvaluable: boundInline.gatewayEvaluable
      }
    : draftInline
      ? { ...draftInline }
      : null;
  if (inlineSource) {
    focusInline.value = inlineSource;
    inlineRulesModel.value = parseRules(inlineSource.conditionRules);
    focusConditionCode.value = null;
  } else {
    focusInline.value = null;
    focusConditionCode.value = record?.conditionCode ?? null;
  }
  focusTouched.value = false;
}

/**
 * 焦点属性修改 → 实时落草稿（S1）：仅聚焦记录生成 update / add 就地改；
 * 未聚焦或未修改不产生变更（设置区在未授权资源上只读展示默认值）。
 */
function applyFocusEdit() {
  const slot = focusSlot.value;
  if (!slot || !focusTouched.value) return;
  slotDraft.value = applyFocusAttributes(slotDraft.value, {
    slot,
    attributes: {
      conditionCode: focusConditionCode.value,
      inlineCondition: focusInline.value,
      canGrant: focusCanGrant.value
    },
    view: localView.value
  });
  localChanges.value = slotDraft.value.changes;
}

function handleConditionChange(value: string | null) {
  focusConditionCode.value = value;
  focusInline.value = null; // 引用轨/无条件：退出内联态（三态互斥）
  if (value != null) focusCanGrant.value = false; // 条件不可转授
  focusTouched.value = true;
  applyFocusEdit();
}

/** 切换内联编辑模式（T-PERM-048）：当前非内联态时以空定义进入；内联态重复点击为 no-op */
function handleSwitchToInline() {
  if (focusInline.value != null) return;
  focusInline.value = { name: "", conditionRules: "{}", gatewayEvaluable: false };
  inlineRulesModel.value = createEmptyRules();
  focusConditionCode.value = null;
  focusCanGrant.value = false; // 内联条件不可转授（20041 同口径）
  focusTouched.value = true;
  applyFocusEdit();
}

/** 退出内联态回无条件（内联定义未保存前移除即弃） */
function handleRemoveInline() {
  focusInline.value = null;
  focusConditionCode.value = null;
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineNameChange(value: string | string | number) {
  if (focusInline.value == null) return;
  focusInline.value = { ...focusInline.value, name: String(value ?? "") };
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineGatewayChange(value: boolean | string | number) {
  if (focusInline.value == null) return;
  focusInline.value = {
    ...focusInline.value,
    gatewayEvaluable: value === true
  };
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineRulesChange(rules: ConditionRules) {
  inlineRulesModel.value = rules;
  if (focusInline.value == null) return;
  focusInline.value = {
    ...focusInline.value,
    conditionRules: serializeRules(rules)
  };
  focusTouched.value = true;
  applyFocusEdit();
}

/** 内联定义确认前校验：名称必填 + 规则完整性（ReConditionEditor.validate 同款口径） */
function validateInlineBeforeConfirm(): string | null {
  if (focusInline.value == null) return null;
  if (!focusInline.value.name.trim()) return "内联条件名称不能为空";
  if (!inlineEditorRef.value?.validate()) return "内联条件规则不完整";
  return null;
}

/** 全草稿内联校验（claude/codex 外评 P2-2）：纯函数 validateInlineDefs（grant-plan.ts，可单测；
 *  第三参传 item 参数完整性校验——半成品条件项（空 start/end/cidrs）一并拦截） */
function validateAllDraftInline(): string | null {
  return validateInlineDefs(
    slotDraft.value.changes,
    parseRules,
    validateRulesComplete
  );
}

function handleCanGrantChange(value: boolean | string | number) {
  focusCanGrant.value = value === true;
  focusTouched.value = true;
  applyFocusEdit();
}

/** 条件不可转授：选择条件或内联态后自动清除 canGrant；未聚焦或聚焦未授权资源时表单不可用（P2-2）。 */
const canGrantDisabled = computed(
  () =>
    focusedRecord.value == null ||
    focusConditionCode.value != null ||
    focusInline.value != null
);
const conditionDelegationBlocked = computed(
  () => focusConditionCode.value != null || focusInline.value != null
);
const conditionPlaceholder = computed(() =>
  focusSlot.value == null ? "聚焦资源行后选择" : "无条件"
);

/** 设置区状态提示（v3.1：记录级，无混合态批量语义） */
const settingsHint = computed(() => {
  const record = focusedRecord.value;
  if (!focusSlot.value) return "点击资源行聚焦后编辑该记录的授权设置";
  if (!record) return "该资源未授权：新授权默认无条件、不可再授予";
  if (record.draftMark === "add") return "草稿新增记录：修改将就地应用";
  if (record.draftMark === "update") return "修改中：当前设置将覆盖原值";
  return "修改只作用于当前聚焦记录";
});

// ========== 显式复制（S3，设计 §4：源=聚焦记录属性 → 目标=已勾选 MANUAL 记录） ==========

const copyVisible = ref(false);
const copyTargets = ref<string[]>([]);

/** 复制入口可用：聚焦记录存在且条件为启用中（停用条件不可复制，20042 同口径） */
const copyEnabled = computed(() => {
  const record = focusedRecord.value;
  if (!record) return false;
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    // INLINE 实体 enabled 恒 true；停用不可复制仅针对 MANAGED（20042 同口径）
    return condition?.enabled === true;
  }
  return true;
});

/** 复制源内联定义（T-PERM-048）：baseline 绑定内联 → 从 conditions INLINE 实体深拷贝；
 *  草稿 add 内联 → 生效记录 inlineCondition；无内联 → null（复制走 managed 引用轨） */
const copySourceInline = computed<InlineConditionDef | null>(() => {
  const record = focusedRecord.value;
  if (!record) return null;
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode && c.source === "INLINE"
    );
    return condition
      ? {
          name: condition.name,
          conditionRules: condition.conditionRules,
          gatewayEvaluable: condition.gatewayEvaluable
        }
      : null;
  }
  return record.inlineCondition ? { ...record.inlineCondition } : null;
});

/** 复制目标候选：当前操作已勾选的 MANUAL 主记录（INSTANCE；排除源自身与 suspended 待撤销，四审 P1-2） */
const copyCandidates = computed(() => {
  const op = selectedOp.value;
  const sourceKey = focusSlot.value ? slotKeyOf(focusSlot.value) : null;
  if (!op || !sourceKey) return [];
  return localView.value.mains.filter(m => {
    if (m.grantSource !== "MANUAL" || m.draftMark === "remove") return false;
    if (m.operationCode !== op.code) return false;
    if (m.resourceTypeCode !== op.resourceTypeCode) return false;
    if (m.scopeMode !== "INSTANCE") return false;
    if (
      slotDraft.value.suspended.has(
        slotKeyOf({
          resourceTypeCode: m.resourceTypeCode,
          resourceCode: m.resourceCode,
          codeType: m.codeType,
          operationCode: m.operationCode ?? "",
          scopeMode: m.scopeMode
        })
      )
    ) {
      return false;
    }
    return (
      slotKeyOf({
        resourceTypeCode: m.resourceTypeCode,
        resourceCode: m.resourceCode,
        codeType: m.codeType,
        operationCode: m.operationCode ?? "",
        scopeMode: m.scopeMode
      }) !== sourceKey
    );
  });
});

function openCopyDialog() {
  copyTargets.value = [];
  copyVisible.value = true;
}

function handleCopyConfirm() {
  const slot = focusSlot.value;
  if (!slot) return;
  const targetSlots: FocusSlot[] = [];
  for (const key of copyTargets.value) {
    const record = copyCandidates.value.find(
      c =>
        slotKeyOf({
          resourceTypeCode: c.resourceTypeCode,
          resourceCode: c.resourceCode,
          codeType: c.codeType,
          operationCode: c.operationCode ?? "",
          scopeMode: c.scopeMode
        }) === key
    );
    if (!record) continue;
    targetSlots.push({
      resourceTypeCode: record.resourceTypeCode,
      resourceCode: record.resourceCode,
      codeType: record.codeType,
      operationCode: record.operationCode ?? "",
      scopeMode: record.scopeMode
    });
  }
  slotDraft.value = copySlotAttributes(slotDraft.value, {
    sourceSlot: slot,
    targetSlots,
    view: localView.value,
    sourceInline: copySourceInline.value
  });
  localChanges.value = slotDraft.value.changes;
  copyVisible.value = false;
}

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
/** 首次打开 initial.resourceCode 额外勾选（切换操作后清空） */
const presetExtra = ref<Set<string>>(new Set());

/**
 * 应用当前 scopeMode 下的预填勾选（评审问题 1）。
 * nextTick 等树渲染 + presetToken 代际令牌防乱序（DOM 写入前后均核对）。
 */
async function applyPresetForCurrentScope(
  op: OpOption | null = selectedOp.value
) {
  const token = ++presetToken.value;
  await nextTick();
  if (token !== presetToken.value) return;
  if (!op) {
    treeRef.value?.setCheckedKeys([]);
    prevCheckedIds = new Set();
    return;
  }
  if (scopeMode.value !== "INSTANCE") {
    // ALL 无树勾选
    if (token !== presetToken.value) return;
    treeRef.value?.setCheckedKeys([]);
    prevCheckedIds = new Set();
    return;
  }
  // P1（三审）：排除 suspended 待撤销槽位的记录——baseline 记录取消勾选后仅入
  // slotDraft.suspended，仍存在于 localView.mains，预填不得重新勾选（确认时 expandSuspended 生成 remove）
  const suspendedSlots = new Set(slotDraft.value.suspended.keys());
  const preset = computePreset({
    op: { code: op.code, resourceTypeCode: op.resourceTypeCode },
    records: localView.value.mains.filter(record => {
      if (record.operationCode !== op.code) return true;
      return !suspendedSlots.has(
        slotKeyOf({
          resourceTypeCode: record.resourceTypeCode,
          resourceCode: record.resourceCode,
          codeType: record.codeType,
          operationCode: record.operationCode,
          scopeMode: record.scopeMode
        })
      );
    })
  });
  const keys = new Set(preset.checkedTripleKeys);
  for (const k of presetExtra.value) keys.add(k);
  const ids = [...keys]
    .map(k => idByTripleKey.value.get(k))
    .filter((id): id is number => id != null);
  // DOM 写入前再核对 token
  if (token !== presetToken.value) return;
  treeRef.value?.setCheckedKeys(ids);
  // 同步勾选差量基线（v3.1：@check 差量驱动草稿；预填不触发 check 事件）
  prevCheckedIds = new Set(ids);
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
 * 原始授权视图中的 MANUAL 主权限（props.records = 打开弹窗时的状态，含页面草稿）。
 * 四审 P2：按 draftMark 保留草稿状态——排除页面 add（显示"待授权"）、保留页面 remove（显示"待撤销"）。
 */
function originalManualRecords(op: OpOption): EffectiveRecord[] {
  return props.records.filter(record => {
    if (record.grantSource !== "MANUAL") return false;
    if (record.draftMark === "add") return false;
    if (record.operationCode !== op.code) return false;
    return record.resourceTypeCode === op.resourceTypeCode;
  });
}

/**
 * 当前选中状态中的 MANUAL 主权限（localView 含弹窗本地变更，排除 suspended 待撤销槽位）。
 * 用于 scopeMode 判定 / ALL 默认类型（三审 P1+P2-2a：与原始状态分开计算）。
 */
function currentManualRecords(op: OpOption): EffectiveRecord[] {
  const suspendedSlots = new Set(slotDraft.value.suspended.keys());
  return localView.value.mains.filter(record => {
    if (record.grantSource !== "MANUAL") return false;
    if (record.draftMark === "remove") return false;
    if (record.operationCode !== op.code) return false;
    if (record.resourceTypeCode !== op.resourceTypeCode) return false;
    // P1：待撤销（suspended）槽位的记录不属于当前选中状态
    if (
      suspendedSlots.has(
        slotKeyOf({
          resourceTypeCode: record.resourceTypeCode,
          resourceCode: record.resourceCode,
          codeType: record.codeType,
          operationCode: record.operationCode,
          scopeMode: record.scopeMode
        })
      )
    ) {
      return false;
    }
    return true;
  });
}

// ========== 预填与焦点联动 ==========

/** 原始授权资源键（打开弹窗时状态；用于模板"已授权/待撤销"判定，三审 P2-2a）。 */
const originalInstanceResourceKeys = computed(() => {
  const op = selectedOp.value;
  if (!op) return new Set<string>();
  return new Set(
    originalManualRecords(op)
      .filter(record => record.scopeMode === "INSTANCE")
      .map(record => resourceGroupKeyOf(record))
  );
});

/** 当前选中 ALL 资源键（含弹窗本地变更、排除 suspended；scopeMode 判定用）。 */
const originalAllResourceKeys = computed(() => {
  const op = selectedOp.value;
  if (!op) return new Set<string>();
  return new Set(
    currentManualRecords(op)
      .filter(record => record.scopeMode === "ALL")
      .map(record => resourceGroupKeyOf(record))
  );
});

function originalAllSelectedForCurrentType(): boolean {
  const key = currentAllResourceKey.value;
  return key != null && originalAllResourceKeys.value.has(key);
}

/**
 * 打开预填（initial 首次消费，评审问题 1+2）。
 * initial.scopeMode 优先 + initial.resourceCode 入 extra + 聚焦 initial 槽位（v3.1 S1）。
 * 四审 P1-1：未授权 initial 只聚焦、不伪造勾选——INSTANCE 不再经 presetExtra 注入树勾选、
 * ALL 仅在已有 ALL 授权时置 scopeMode=ALL（否则保持 INSTANCE 并聚焦 ALL 槽位，设置区只读默认值）。
 */
async function applyOpenPreset(op: OpOption | null) {
  const initial = props.initial;
  if (!op) {
    scopeMode.value = "INSTANCE";
    presetExtra.value = new Set();
    await applyPresetForCurrentScope(op);
    return;
  }
  scopeMode.value = originalAllSelectedForCurrentType() ? "ALL" : "INSTANCE";
  presetExtra.value = new Set();
  await applyPresetForCurrentScope(op);
  // v3.1 S1 + 四审 P1-1：按完整授权键聚焦 initial 槽位（未授权也聚焦，设置区只读默认值；
  // 不调用 resumeSlot / 不伪造勾选——确认时未勾选的未授权 initial 不会产生 add）
  if (initial.scopeMode === "ALL") {
    focusOn({
      resourceTypeCode:
        initial.resourceTypeCode ??
        op.resourceTypeCode ??
        selectableTypes.value[0] ??
        "",
      resourceCode: null,
      codeType: null,
      operationCode: op.code,
      scopeMode: "ALL"
    });
  } else if (initial.resourceCode) {
    focusOn({
      resourceTypeCode:
        initial.resourceTypeCode ??
        op.resourceTypeCode ??
        selectableTypes.value[0] ??
        "",
      resourceCode: initial.resourceCode,
      codeType: initial.codeType ?? "default",
      operationCode: op.code,
      scopeMode: "INSTANCE"
    });
  }
}

/**
 * 弹窗内手动切换操作预填（不含 initial，评审问题 1+2）。
 */
async function applySwitchPreset(op: OpOption | null) {
  if (!op) {
    scopeMode.value = "INSTANCE";
    presetExtra.value = new Set();
    await applyPresetForCurrentScope(op);
    return;
  }
  scopeMode.value = originalAllSelectedForCurrentType() ? "ALL" : "INSTANCE";
  presetExtra.value = new Set();
  await applyPresetForCurrentScope(op);
  // 切换操作后焦点重置（旧操作的聚焦槽位不再有效）
  focusOn(null);
}

/** 打开流程标志：selectedOpKey sync watcher 跳过打开时的程序设值（评审问题 2） */
let isOpening = false;

watch(
  () => props.modelValue,
  async visible => {
    if (!visible) return;
    editorMode.value = "main";
    localChanges.value = [...props.draftChanges];
    slotDraft.value = {
      changes: [...props.draftChanges],
      suspended: new Map<string, SuspendedSlot>(),
      focusSlotKey: null
    };
    focusOn(null);
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
                o.resourceTypeCode === initial.resourceTypeCode)
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
  opKey => {
    if (isOpening) return;
    // 等操作切换触发的资源树重建完成后再预填，避免写入即将卸载的旧树实例。
    void applySwitchPreset(resolveOp(opKey));
  },
  { flush: "post" }
);

/** 范围切换 -> 重新预填（不改默认 scopeMode，评审问题 1；v3.1 焦点由 allScopeSelected 管理） */
watch(scopeMode, async () => {
  await applyPresetForCurrentScope();
});

// ========== 树勾选 ↔ 草稿实时同步 + 焦点（v3.1 S2） ==========

/** 节点 id → 焦点槽位（当前操作 + INSTANCE 范围） */
const slotByNodeId = computed(() => {
  const map = new Map<number, FocusSlot>();
  const opCode = selectedOp.value?.code;
  if (!opCode) return map;
  const collect = (nodes: ResourceTreeNode[]) => {
    for (const node of nodes) {
      map.set(node.id, {
        resourceTypeCode: node.resourceTypeCode,
        resourceCode: node.code,
        codeType: node.codeType,
        operationCode: opCode,
        scopeMode: "INSTANCE"
      });
      if (node.children?.length) collect(node.children);
    }
  };
  collect(selectableForest.value);
  return map;
});

/** 上次勾选集合（node-key=id），@check 差量驱动 uncheckSlot/resumeSlot */
let prevCheckedIds = new Set<number>();

function handleTreeCheck(
  _data: unknown,
  info: { checkedKeys: Array<number | string> }
) {
  if (!selectedOp.value) return;
  const current = new Set(info.checkedKeys.map(k => Number(k)));
  const newlyChecked: number[] = [];
  const newlyUnchecked: number[] = [];
  for (const id of current) {
    if (!prevCheckedIds.has(id)) newlyChecked.push(id);
  }
  for (const id of prevCheckedIds) {
    if (!current.has(id)) newlyUnchecked.push(id);
  }
  prevCheckedIds = current;

  for (const id of newlyChecked) {
    const slot = slotByNodeId.value.get(id);
    if (!slot) continue;
    slotDraft.value = resumeSlot(slotDraft.value, {
      slot,
      view: localView.value,
      baseline: props.baseline,
      operations: props.allOperations
    });
    localChanges.value = slotDraft.value.changes;
    // P2-3 修复：S1 首次勾选自动聚焦——循环内每次重新判断焦点是否为空，
    // 确保批量勾选仅第一项取得焦点（焦点非空时不抢焦点）
    if (!focusSlot.value) {
      focusOn(slot);
    }
  }
  for (const id of newlyUnchecked) {
    const slot = slotByNodeId.value.get(id);
    if (!slot) continue;
    slotDraft.value = uncheckSlot(slotDraft.value, {
      slot,
      view: localView.value
    });
    localChanges.value = slotDraft.value.changes;
    // 取消勾选焦点记录 → 焦点清空（S2）
    if (focusSlot.value && slotKeyOf(slot) === slotKeyOf(focusSlot.value)) {
      focusOn(null);
    }
  }
}

/** 点击资源行 → 聚焦该槽位（复选框仍用于勾选切换，S1 分离） */
function handleNodeClick(data: ResourceTreeNode) {
  if (!selectedOp.value || scopeMode.value !== "INSTANCE") return;
  focusOn({
    resourceTypeCode: data.resourceTypeCode,
    resourceCode: data.code,
    codeType: data.codeType,
    operationCode: selectedOp.value.code,
    scopeMode: "INSTANCE"
  });
}

/** 聚焦节点 id（行高亮） */
const focusNodeId = computed<number | null>(() => {
  const slot = focusSlot.value;
  if (!slot) return null;
  for (const [id, s] of slotByNodeId.value) {
    if (slotKeyOf(s) === slotKeyOf(slot)) return id;
  }
  return null;
});

/** 节点槽位摘要（S7）：条件名（无条件显示浅色[无条件]）/ 再授予 / 子权限数 / 草稿状态 */
function nodeSummary(node: ResourceTreeNode): string[] {
  const opCode = selectedOp.value?.code;
  if (!opCode) return [];
  const slot: FocusSlot = {
    resourceTypeCode: node.resourceTypeCode,
    resourceCode: node.code,
    codeType: node.codeType,
    operationCode: opCode,
    scopeMode: "INSTANCE"
  };
  // findSlotRecord 面向可编辑槽位，会排除 remove；摘要还需读取页面草稿中
  // 已标记待撤销的 baseline MANUAL 记录（S7），但不能放宽编辑/复制入口。
  const record =
    findSlotRecord(localView.value, slot) ??
    localView.value.mains.find(
      item =>
        item.grantSource === "MANUAL" &&
        item.draftMark === "remove" &&
        item.resourceTypeCode === slot.resourceTypeCode &&
        (item.resourceCode ?? null) === slot.resourceCode &&
        (item.codeType ?? null) === slot.codeType &&
        item.operationCode === slot.operationCode &&
        item.scopeMode === slot.scopeMode
    );
  if (!record) return [];
  const tags: string[] = [];
  if (record.inlineCondition != null) {
    // 草稿内联绑定（T-PERM-048）：定义未落库，无 code 可查——直接展示定义名
    tags.push(`内联:${record.inlineCondition.name || "未命名"}`);
  } else if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    // baseline 内联绑定按 code 命中 INLINE 实体（includeInline 加载），标注内联来源
    const inlineTag =
      condition?.source === "INLINE" ? `内联:${condition.name}` : null;
    tags.push(inlineTag ?? condition?.name ?? record.conditionCode);
  } else {
    tags.push("无条件");
  }
  if (record.canGrant) tags.push("可转授");
  if (record.draftMark === "update") tags.push("待更新");
  const children = localView.value.childrenByParent.get(
    record.draftMark === "add" && record.changeId
      ? draftParentKey(record.changeId)
      : persistedParentKey(record.id)
  );
  if (children && children.length > 0) tags.push(`${children.length} 子权限`);
  return tags;
}

// ========== 主权限确认（v3.1：expandSuspended 双路径展开） + 子权限单父上下文 ==========

const confirmDisabled = computed(() => {
  if (!selectedOp.value) return true;
  if (scopeMode.value === "ALL") {
    return !effectiveAllType.value;
  }
  return false;
});

/** 将当前主权限编辑状态转换为有序结果（v3.1 已移除：弹窗实时草稿 + 确认时 expandSuspended）。 */

const localView = computed(() =>
  applyDraftToRecords({
    baseline: props.baseline,
    changes: localChanges.value,
    operations: props.allOperations
  })
);

function childrenOfLocal(record: EffectiveRecord): EffectiveRecord[] {
  if (record.draftMark === "add" && record.changeId) {
    return (
      localView.value.childrenByParent.get(draftParentKey(record.changeId)) ??
      []
    );
  }
  return (
    localView.value.childrenByParent.get(persistedParentKey(record.id)) ?? []
  );
}

function effectiveResourceLabel(record: EffectiveRecord): string {
  return (
    record.resourceName ??
    record.resourceCode ??
    `全部资源（${record.resourceTypeCode}）`
  );
}

/** 草稿变更同步（localChanges 与 slotDraft.changes 保持同一引用；所有写路径统一入口） */
function syncChanges(changes: DraftChange[]) {
  localChanges.value = changes;
  slotDraft.value = { ...slotDraft.value, changes };
}

/** 子权限配置器单父上下文（S6）：父 = 聚焦记录（有效 MANUAL 主权限，INSTANCE 与 ALL 均可，设计 §4「ALL 可独立配置子权限」） */
const childParent = computed<EffectiveRecord | null>(() => {
  const record = focusedRecord.value;
  if (!record || record.grantSource !== "MANUAL") return null;
  if (record.draftMark === "remove") return null;
  return record;
});

/** 配置子权限：进入单父上下文（父 = 聚焦记录，设计 §4 v3.1 记录级入口） */
function handleConfigureChildren() {
  if (!childParent.value) return;
  editorMode.value = "children";
}

/** 返回主模式：实时草稿不丢弃（v3.1 弹窗本地草稿，取消弹窗整体丢弃） */
function handleBackToMain() {
  editorMode.value = "main";
}

function handleAddChild(input: {
  parent: EffectiveRecord;
  recordKey: GrantRecordKey;
  resourceLabel: string;
}) {
  const recordKey = normalizeChildGrantKey(input.recordKey);
  syncChanges([
    ...localChanges.value,
    buildAddChange({
      recordKey,
      ...(input.parent.draftMark === "add" && input.parent.changeId
        ? { parentChangeId: input.parent.changeId }
        : { parentPermissionId: input.parent.id }),
      summary: buildSummary({
        recordKey,
        resourceLabel: input.resourceLabel
      })
    })
  ]);
}

function handleRemoveChild(record: EffectiveRecord) {
  if (record.draftMark === "add" && record.changeId) {
    syncChanges(
      localChanges.value.filter(change => change.changeId !== record.changeId)
    );
    return;
  }
  let changes = localChanges.value.filter(
    change => !(change.kind === "update" && change.recordId === record.id)
  );
  changes = [
    ...changes,
    buildRemoveChange({
      records: [record],
      cascadeChildCount: 0,
      reason: "detail-delete",
      summary: buildSummary({
        recordKey: {
          resourceTypeCode: record.resourceTypeCode,
          resourceCode: record.resourceCode,
          codeType: record.codeType,
          operationCode: record.operationCode,
          scopeMode: record.scopeMode,
          conditionCode: record.conditionCode,
          canGrant: record.canGrant
        },
        resourceLabel: effectiveResourceLabel(record)
      })
    })
  ];
  syncChanges(changes);
}

function handleRestoreChild(record: EffectiveRecord) {
  if (!record.changeId) return;
  syncChanges(
    localChanges.value.filter(change => change.changeId !== record.changeId)
  );
}

/** 确定：expandSuspended 双路径展开（baseline→remove / add→取消变更组）后提交完整草稿。
 *  内联定义在场时先校验（名称必填 + 规则完整性），不合法阻断提交（T-PERM-048）。 */
function handleConfirm() {
  const inlineError =
    validateAllDraftInline() ?? validateInlineBeforeConfirm();
  if (inlineError != null) {
    message(inlineError, { type: "warning" });
    return;
  }
  emit("confirm", expandSuspended(slotDraft.value));
}

function handleClose() {
  emit("update:modelValue", false);
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="授权"
    width="780px"
    class="grant-dialog"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div v-show="editorMode === 'main'" class="dialog-content">
      <div class="field-block operation-field">
        <div class="field-label">操作权限</div>
        <el-select
          v-model="selectedOpKey"
          placeholder="请选择操作权限"
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

      <section class="resource-editor">
        <div class="resource-header">
          <div class="resource-heading">
            <span class="field-label">资源</span>
            <span class="resource-hint">{{ resourceHint }}</span>
          </div>
          <div class="resource-actions">
            <el-checkbox
              v-model="allScopeSelected"
              border
              size="small"
              :disabled="!selectedOp"
              class="all-scope-checkbox"
            >
              全量
            </el-checkbox>
          </div>
        </div>

        <div
          class="scope-tree"
          :class="{ disabled: resourceSelectionDisabled }"
        >
          <el-tree
            :key="treeRenderKey"
            ref="treeRef"
            :data="selectableForest"
            :props="treeProps"
            show-checkbox
            check-strictly
            node-key="id"
            :expand-on-click-node="false"
            :check-on-click-node="false"
            default-expand-all
            @check="handleTreeCheck"
          >
            <template #default="{ node, data }">
              <span
                class="tree-node"
                :class="{ focused: data.id === focusNodeId }"
                @click.stop="handleNodeClick(data)"
              >
                <span class="node-label">
                  {{ data.name }}<span class="node-code">{{ data.code }}</span>
                </span>
                <template v-if="scopeMode === 'INSTANCE'">
                  <!-- S7 节点紧凑摘要：条件名（无条件浅色）/ 可再授予 / 子权限数 / 待更新 -->
                  <span
                    v-for="tag in nodeSummary(data)"
                    :key="tag"
                    class="node-tag"
                    :class="{ 'node-tag--unconditional': tag === '无条件' }"
                    >{{ tag }}</span
                  >
                  <span
                    v-if="
                      node.checked &&
                      originalInstanceResourceKeys.has(nodeResourceKeyOf(data))
                    "
                    class="node-state existing"
                    >已授权</span
                  >
                  <span v-else-if="node.checked" class="node-state pending-add"
                    >待授权</span
                  >
                  <span
                    v-else-if="
                      originalInstanceResourceKeys.has(nodeResourceKeyOf(data))
                    "
                    class="node-state pending-remove"
                    >待撤销</span
                  >
                </template>
              </span>
            </template>
          </el-tree>
          <div v-if="selectedOp && scopeMode === 'INSTANCE'" class="tree-hint">
            点击行聚焦后可编辑该记录的授权设置；复选框用于勾选/撤销。
          </div>
        </div>
      </section>

      <section class="grant-options">
        <div class="options-heading">
          <span class="field-label">授权设置</span>
          <span class="options-hint">{{ settingsHint }}</span>
        </div>
        <div class="option-fields">
          <ConditionPicker
            :model-value="focusConditionCode"
            :conditions="conditions"
            :disabled="!focusedRecord"
            :placeholder="conditionPlaceholder"
            :inline-active="focusInline != null"
            @update:model-value="handleConditionChange"
            @select-inline="handleSwitchToInline"
          />
          <el-tooltip
            :disabled="!conditionDelegationBlocked"
            content="条件权限不可转授：带条件的权限不能设置可再授予，需先清除条件"
            placement="top"
          >
            <span>
              <el-checkbox
                :model-value="focusCanGrant"
                :disabled="canGrantDisabled"
                @update:model-value="handleCanGrantChange"
              >
                允许再授予
              </el-checkbox>
            </span>
          </el-tooltip>
          <el-tooltip
            :disabled="copyEnabled"
            content="源条件已停用，不可复制（需先选择启用中的条件）"
            placement="top"
          >
            <span>
              <el-button
                size="small"
                :disabled="!copyEnabled"
                @click="openCopyDialog"
              >
                复制设置到已勾选授权
              </el-button>
            </span>
          </el-tooltip>
        </div>
            <div v-if="focusInline != null" class="inline-condition-editor">
              <div class="inline-editor-heading">
                <span class="field-label">内联条件（随本条授权保存创建）</span>
                <el-button size="small" text type="danger" @click="handleRemoveInline">
                  移除内联条件
                </el-button>
              </div>
              <el-input
                :model-value="focusInline.name"
                placeholder="内联条件名称（必填）"
                size="small"
                class="inline-name-input"
                @update:model-value="handleInlineNameChange"
              />
              <ReConditionEditor
                ref="inlineEditorRef"
                :model-value="inlineRulesModel"
                :gateway-evaluable="focusInline.gatewayEvaluable === true"
                @update:model-value="handleInlineRulesChange"
              />
              <el-checkbox
                :model-value="focusInline.gatewayEvaluable === true"
                @update:model-value="handleInlineGatewayChange"
              >
                可下发 Gateway 评估
              </el-checkbox>
            </div>
        <!-- D4 有效来源提示（v3.1，措辞限定） -->
        <div v-if="sourceHint" class="source-hint">{{ sourceHint }}</div>
      </section>

      <section class="child-entry">
        <div class="child-entry-copy">
          <span class="child-entry-mark" aria-hidden="true">↳</span>
          <div>
            <span class="field-label">子权限</span>
            <span>{{
              childParent
                ? `为聚焦记录「${effectiveResourceLabel(childParent)}」挂载下级权限`
                : "请先点击资源行聚焦一条已授权主权限"
            }}</span>
          </div>
        </div>
        <el-button
          size="small"
          type="primary"
          plain
          :disabled="!childParent"
          @click="handleConfigureChildren"
        >
          配置子权限
        </el-button>
      </section>
    </div>

    <GrantChildConfigurator
      v-if="editorMode === 'children' && childParent"
      :parent="childParent"
      :subject-key="props.subjectKey"
      :children-provider="childrenOfLocal"
      :operations="allOperations"
      :resource-forest="allResourceForest"
      @back="handleBackToMain"
      @add="handleAddChild"
      @remove="handleRemoveChild"
      @restore="handleRestoreChild"
    />

    <!-- S3 显式复制弹窗：目标 = 已勾选 MANUAL 记录多选 -->
    <el-dialog
      v-model="copyVisible"
      title="复制授权设置"
      width="480px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="copy-dialog-body">
        <p class="copy-dialog-tip">
          将聚焦记录的条件与再授予设置应用到以下已勾选授权（属性相同项自动跳过）：
        </p>
        <el-checkbox-group v-model="copyTargets" class="copy-target-list">
          <el-checkbox
            v-for="candidate in copyCandidates"
            :key="candidate.id"
            :value="
              slotKeyOf({
                resourceTypeCode: candidate.resourceTypeCode,
                resourceCode: candidate.resourceCode,
                codeType: candidate.codeType,
                operationCode: candidate.operationCode ?? '',
                scopeMode: candidate.scopeMode
              })
            "
            class="copy-target-item"
          >
            {{ effectiveResourceLabel(candidate) }}（{{
              candidate.operationCode
            }}）
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="copyCandidates.length === 0"
          description="当前无可复制目标（已勾选的其他授权）"
          :image-size="48"
        />
      </div>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="copyTargets.length === 0"
          @click="handleCopyConfirm"
        >
          复制
        </el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="editorMode === 'main' && confirmDisabled"
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
  max-height: 72vh;
  padding: var(--space-4) var(--space-5);
  overflow-y: auto;
}

:global(.grant-dialog .el-dialog__footer) {
  padding: var(--space-3) var(--space-5);
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color-lighter);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.operation-field {
  display: flex;
  gap: var(--space-4);
  align-items: center;

  .field-label {
    flex-shrink: 0;
    width: 64px;
  }

  .op-select {
    width: 320px;
  }
}

.resource-editor {
  .resource-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-2);
  }

  .resource-heading,
  .resource-actions {
    display: flex;
    gap: var(--space-2);
    align-items: center;
  }

  .resource-hint,
  .options-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .all-type-select {
    width: 150px;
  }

  .all-scope-checkbox {
    margin-right: 0;
  }
}

.scope-tree {
  max-height: 330px;
  overflow: auto;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  transition: background-color 0.15s ease;

  &.disabled {
    background: var(--el-fill-color-extra-light);
  }

  :deep(.el-tree) {
    min-height: 184px;
    padding: var(--space-1) 0;
    background: transparent;
  }

  :deep(.el-tree-node__content) {
    height: 32px;
    padding-right: var(--space-3);
  }

  .tree-node {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-width: 0;
    padding-right: 4px;
    border-radius: var(--radius-sm);

    &.focused {
      background: var(--el-color-primary-light-9);
      box-shadow: inset 2px 0 0 var(--el-color-primary);
    }
  }

  .node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-code {
    margin-left: 6px;
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  .node-tag {
    flex-shrink: 0;
    padding: 0 6px;
    margin-left: var(--space-2);
    font-size: 11px;
    line-height: 18px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    border-radius: 4px;

    &.node-tag--unconditional {
      color: var(--el-text-color-placeholder);
      background: transparent;
    }
  }

  .node-state {
    flex-shrink: 0;
    margin-left: var(--space-3);
    font-size: 11px;

    &.existing {
      color: var(--el-color-success-dark-2);
    }

    &.pending-add {
      color: var(--el-color-primary);
    }

    &.pending-remove {
      color: var(--el-color-danger);
    }
  }

  .tree-hint {
    position: sticky;
    bottom: 0;
    padding: var(--space-1) var(--space-2);
    font-size: 12px;
    color: var(--el-text-color-secondary);
    background: var(--el-bg-color);
    border-top: 1px solid var(--el-border-color-lighter);
  }
}

.source-hint {
  padding: var(--space-1) var(--space-2);
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-radius: var(--radius-sm);
}

.copy-dialog-body {
  .copy-dialog-tip {
    margin: 0 0 var(--space-2);
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .copy-target-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    max-height: 320px;
    overflow: auto;
  }

  .copy-target-item {
    height: auto;
    margin-right: 0;
  }
}

.grant-options {
  padding-top: var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);

  .options-heading {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-2);
  }

  .inline-condition-editor {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    padding: var(--space-2);
    margin-top: var(--space-1);
    border: 1px dashed var(--el-color-warning-light-5);
    border-radius: var(--radius-md);

    .inline-editor-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .inline-name-input {
      max-width: 320px;
    }
  }

  .option-fields {
    display: flex;
    gap: var(--space-3);
    align-items: center;
  }

  .condition-blocked {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.child-entry {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);
}

.child-entry-copy {
  display: flex;
  gap: var(--space-2);
  align-items: center;

  > div {
    display: flex;
    gap: var(--space-2);
    align-items: baseline;
  }

  > div > span:last-child {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.child-entry-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: var(--radius-full);
}
</style>
