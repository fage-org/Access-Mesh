<script setup lang="ts">
/**
 * 授权弹窗（§4，以资源树为主体的单屏编辑器）。
 * - 打开即展示当前类型资源树；选择操作后按当前生效草稿预填授权状态；
 * - 勾选=授权，取消勾选=撤权，整行可点击；同键 MANUAL 只有一条直接授权；
 * - ALL 收敛为资源树右上角的“全量”选择按钮；条件/canGrant 可直接更新已有授权；
 * - 确定时可顺序提交 ALL 撤销与 INSTANCE 最终集合，避免取消全量后无法撤权。
 */
import { computed, nextTick, ref, watch } from "vue";
import { ElMessageBox } from "element-plus";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { ConditionResp } from "@/api/permission-condition";
import type {
  GrantRecordKey,
  RolePermissionItem
} from "@/api/permission-grant";
import type { OperationDefInput } from "../utils/source-chain";
import {
  applyDialogResultsToDraft,
  applyDraftToRecords,
  buildAddChange,
  buildRemoveChange,
  buildReplaceChange,
  buildSummary,
  buildUpdateChange,
  computePreset,
  draftParentKey,
  persistedParentKey,
  resourceGroupKeyOf,
  type DialogResult,
  type EffectiveRecord
} from "../utils/grant-plan";
import type { DraftChange } from "../utils/types";
import ConditionPicker from "./ConditionPicker.vue";
import GrantChildConfigurator from "./GrantChildConfigurator.vue";

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
  /** 子权限可跨类型，使用依赖加载阶段取得的全量定义。 */
  allOperations: OperationDefInput[];
  allResourceForest: ResourceTreeNode[];
  conditions: ConditionResp[];
  /** 生效主权限（baseline+draft；资源树授权现状预填） */
  records: EffectiveRecord[];
  /** 弹窗本地事务基线；确认前不写入页面草稿。 */
  baseline: RolePermissionItem[];
  draftChanges: DraftChange[];
  canCondition: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", changes: DraftChange[]): void;
}>();

const editorMode = ref<"main" | "children">("main");
const localChanges = ref<DraftChange[]>([]);
const stagedMainChanges = ref<DraftChange[]>([]);
const stagedParentResourceKeys = ref<Set<string>>(new Set());
const stagedOperationCode = ref<string | null>(null);
const stagedScopeMode = ref<"INSTANCE" | "ALL">("INSTANCE");

// ========== 操作权限（按资源类型分组；专属与全局分组展示） ==========

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

// ========== 资源树（INSTANCE 多选 / ALL 紧凑选择） ==========

const scopeMode = ref<"INSTANCE" | "ALL">("INSTANCE");
const treeRef = ref();
const allScopeType = ref<string | null>(null);

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

/** 未选操作也展示当前资源树；专属操作选中后仅保留其类型。 */
const selectableForest = computed(() => {
  const op = selectedOp.value;
  if (!op) return props.resourceForest;
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

/** ALL 范围目标类型：专属操作固定类型；全局操作使用当前树类型。 */
const effectiveAllType = computed(() => {
  const op = selectedOp.value;
  if (!op) return null;
  return op.resourceTypeCode ?? allScopeType.value;
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
    scopeMode.value = selected ? "ALL" : "INSTANCE";
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

// ========== 授权设置（条件 + canGrant） ==========

const conditionCode = ref<string | null>(null);
const canGrant = ref(false);
/** 未主动改设置时，已有授权只参与最终选中集合，不被批量改写。 */
const settingsTouched = ref(false);
const settingsMixed = ref(false);
const existingSettingsCount = ref(0);

/** 条件不可转授：选择条件后自动清除 canGrant；未选操作时表单不可用。 */
const canGrantDisabled = computed(
  () => selectedOp.value == null || conditionCode.value != null
);
const conditionDelegationBlocked = computed(() => conditionCode.value != null);
const conditionPlaceholder = computed(() =>
  settingsMixed.value && !settingsTouched.value
    ? "多种条件（保持已有设置）"
    : "无条件"
);
const optionsHint = computed(() => {
  if (existingSettingsCount.value === 0) return "应用于新勾选的资源";
  if (settingsTouched.value) {
    return `将统一应用于新授权及 ${existingSettingsCount.value} 项已有授权`;
  }
  if (settingsMixed.value) {
    return "已有授权设置不一致；不修改则保持原值，修改后统一覆盖";
  }
  return "不修改则保持原值，修改后同步更新已勾选授权";
});

function handleConditionChange(value: string | null) {
  conditionCode.value = value;
  if (value != null) canGrant.value = false;
  settingsTouched.value = true;
  settingsMixed.value = false;
}

function handleCanGrantChange(value: boolean | string | number) {
  canGrant.value = value === true;
  settingsTouched.value = true;
  settingsMixed.value = false;
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
    return;
  }
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

/** 当前草稿视图中可由弹窗管理的 MANUAL 主权限。 */
function activeManualRecords(op: OpOption): EffectiveRecord[] {
  return props.records.filter(record => {
    if (record.grantSource !== "MANUAL") return false;
    if (record.draftMark === "remove") return false;
    if (record.operationCode !== op.code) return false;
    return (
      op.resourceTypeCode == null ||
      record.resourceTypeCode === op.resourceTypeCode
    );
  });
}

/**
 * 读取当前范围已有直接授权设置。
 * 同值时直接预填；存在不同值时显示混合态。只有用户主动改字段才统一覆盖。
 */
function syncGrantSettings(
  op: OpOption | null,
  mode: "INSTANCE" | "ALL" = scopeMode.value
) {
  if (!op) {
    conditionCode.value = null;
    canGrant.value = false;
    settingsTouched.value = false;
    settingsMixed.value = false;
    existingSettingsCount.value = 0;
    return;
  }
  const records = activeManualRecords(op).filter(record => {
    if (record.scopeMode !== mode) return false;
    if (mode !== "ALL") return true;
    const targetType = effectiveAllType.value;
    return targetType == null || record.resourceTypeCode === targetType;
  });
  existingSettingsCount.value = records.length;
  settingsTouched.value = false;
  if (records.length === 0) {
    conditionCode.value = null;
    canGrant.value = false;
    settingsMixed.value = false;
    return;
  }
  const first = records[0];
  const mixed = records.some(
    record =>
      record.conditionCode !== first.conditionCode ||
      record.canGrant !== first.canGrant
  );
  settingsMixed.value = mixed;
  conditionCode.value = mixed ? null : first.conditionCode;
  canGrant.value = mixed ? false : first.canGrant;
}

/** 当前操作已生效的 INSTANCE/ALL 资源键，用于区分保持、授权与撤销。 */
const originalInstanceResourceKeys = computed(() => {
  const op = selectedOp.value;
  if (!op) return new Set<string>();
  return new Set(
    activeManualRecords(op)
      .filter(record => record.scopeMode === "INSTANCE")
      .map(record => resourceGroupKeyOf(record))
  );
});

const originalAllResourceKeys = computed(() => {
  const op = selectedOp.value;
  if (!op) return new Set<string>();
  return new Set(
    activeManualRecords(op)
      .filter(record => record.scopeMode === "ALL")
      .map(record => resourceGroupKeyOf(record))
  );
});

/** 切换操作时记录初始授权集合，并选定 ALL 的默认目标类型。 */
function captureOriginalSelections(op: OpOption | null) {
  if (!op) {
    allScopeType.value = null;
    return;
  }
  const records = activeManualRecords(op);
  const allRecords = records.filter(record => record.scopeMode === "ALL");
  const visibleTypes = new Set(selectableTypes.value);
  const existingAllType = allRecords.find(
    record =>
      visibleTypes.size === 0 || visibleTypes.has(record.resourceTypeCode)
  )?.resourceTypeCode;
  allScopeType.value =
    op.resourceTypeCode ?? existingAllType ?? selectableTypes.value[0] ?? null;
}

function originalAllSelectedForCurrentType(): boolean {
  const key = currentAllResourceKey.value;
  return key != null && originalAllResourceKeys.value.has(key);
}

/**
 * 打开预填（initial 首次消费，评审问题 1+2）。
 * initial.scopeMode 优先 + initial.resourceCode 入 extra。
 */
async function applyOpenPreset(op: OpOption | null) {
  const initial = props.initial;
  captureOriginalSelections(op);
  if (!op) {
    scopeMode.value = initial.scopeMode ?? "INSTANCE";
    syncGrantSettings(op, scopeMode.value);
    presetExtra.value = new Set();
    await applyPresetForCurrentScope(op);
    return;
  }
  scopeMode.value = originalAllSelectedForCurrentType()
    ? "ALL"
    : (initial.scopeMode ?? "INSTANCE");
  syncGrantSettings(op, scopeMode.value);
  const extra = new Set<string>();
  if (initial.resourceCode && initial.codeType && initial.resourceTypeCode) {
    extra.add(
      `${initial.resourceTypeCode}:${initial.resourceCode}:${initial.codeType}`
    );
  }
  presetExtra.value = extra;
  await applyPresetForCurrentScope(op);
}

/**
 * 弹窗内手动切换操作预填（不含 initial，评审问题 1+2）。
 */
async function applySwitchPreset(op: OpOption | null) {
  captureOriginalSelections(op);
  if (!op) {
    scopeMode.value = "INSTANCE";
    syncGrantSettings(op, scopeMode.value);
    presetExtra.value = new Set();
    await applyPresetForCurrentScope(op);
    return;
  }
  scopeMode.value = originalAllSelectedForCurrentType() ? "ALL" : "INSTANCE";
  syncGrantSettings(op, scopeMode.value);
  presetExtra.value = new Set();
  await applyPresetForCurrentScope(op);
}

/** 打开流程标志：selectedOpKey sync watcher 跳过打开时的程序设值（评审问题 2） */
let isOpening = false;

watch(
  () => props.modelValue,
  async visible => {
    if (!visible) return;
    editorMode.value = "main";
    localChanges.value = [...props.draftChanges];
    stagedMainChanges.value = [];
    stagedParentResourceKeys.value = new Set();
    stagedOperationCode.value = null;
    stagedScopeMode.value = "INSTANCE";
    conditionCode.value = null;
    canGrant.value = false;
    settingsTouched.value = false;
    settingsMixed.value = false;
    existingSettingsCount.value = 0;
    allScopeType.value = null;
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
  opKey => {
    if (isOpening) return;
    // 等操作切换触发的资源树重建完成后再预填，避免写入即将卸载的旧树实例。
    void applySwitchPreset(resolveOp(opKey));
  },
  { flush: "post" }
);

/** 范围切换 -> 重新预填（不改默认 scopeMode，评审问题 1） */
watch(scopeMode, async mode => {
  syncGrantSettings(selectedOp.value, mode);
  await applyPresetForCurrentScope();
});

watch(allScopeType, () => {
  if (scopeMode.value === "ALL") {
    syncGrantSettings(selectedOp.value, "ALL");
  }
});

// ========== 主权限暂存 + 子权限本地事务 + 确定 ==========

const confirmDisabled = computed(() => {
  if (!selectedOp.value) return true;
  if (scopeMode.value === "ALL") {
    return !effectiveAllType.value;
  }
  return false;
});

/** 将当前主权限编辑状态转换为有序结果（ALL 撤销必须先于 INSTANCE 最终集合）。 */
function buildDialogResults(): DialogResult[] | null {
  const op = selectedOp.value;
  if (!op) return null;
  const operation = { code: op.code, resourceTypeCode: op.resourceTypeCode };
  const results: DialogResult[] = [];
  if (scopeMode.value === "ALL") {
    const typeCode = effectiveAllType.value!;
    const resource = {
      resourceTypeCode: typeCode,
      resourceCode: null,
      codeType: null,
      name: `全部资源（${typeCode}）`
    };
    const resourceKey = resourceGroupKeyOf(resource);
    results.push({
      operation,
      scopeMode: "ALL",
      targetResourceTypeCode: typeCode,
      resources: [resource],
      untouchedResourceKeys:
        !settingsTouched.value && originalAllResourceKeys.value.has(resourceKey)
          ? [resourceKey]
          : [],
      conditionCode: conditionCode.value,
      canGrant: canGrant.value
    });
  } else {
    const checked = treeRef.value?.getCheckedNodes(false) as
      | ResourceTreeNode[]
      | undefined;
    const resources = (checked ?? []).map(node => ({
      resourceTypeCode: node.resourceTypeCode,
      resourceCode: node.code,
      codeType: node.codeType,
      name: node.name
    }));
    const allType = effectiveAllType.value;
    const allKey = currentAllResourceKey.value;
    if (
      allType != null &&
      allKey != null &&
      originalAllResourceKeys.value.has(allKey)
    ) {
      // 取消“全量”后先撤销当前类型 ALL，再应用实例树最终集合。
      results.push({
        operation,
        scopeMode: "ALL",
        targetResourceTypeCode: allType,
        resources: [],
        conditionCode: null,
        canGrant: false
      });
    }
    results.push({
      operation,
      scopeMode: "INSTANCE",
      resources,
      untouchedResourceKeys: settingsTouched.value
        ? []
        : [...originalInstanceResourceKeys.value],
      conditionCode: conditionCode.value,
      canGrant: canGrant.value
    });
  }
  return results;
}

const localView = computed(() =>
  applyDraftToRecords({
    baseline: props.baseline,
    changes: localChanges.value,
    operations: props.allOperations
  })
);

/** 当前主权限选择对应的 MANUAL 记录，包含本次弹窗刚新增的草稿父。 */
const childParents = computed(() =>
  localView.value.mains.filter(record => {
    if (record.grantSource !== "MANUAL" || record.draftMark === "remove") {
      return false;
    }
    if (record.operationCode !== stagedOperationCode.value) return false;
    if (record.scopeMode !== stagedScopeMode.value) return false;
    return stagedParentResourceKeys.value.has(resourceGroupKeyOf(record));
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

function enforceNotDelegable(
  canGrantValue: boolean,
  condition: string | null
): boolean {
  return condition == null ? canGrantValue : false;
}

function effectiveResourceLabel(record: EffectiveRecord): string {
  return (
    record.resourceName ??
    record.resourceCode ??
    `全部资源（${record.resourceTypeCode}）`
  );
}

/** 主权限先在弹窗内暂存，随后才允许挂载子权限。 */
function handleConfigureChildren() {
  const dialogs = buildDialogResults();
  if (!dialogs) return;
  const result = applyDialogResultsToDraft({
    baseline: props.baseline,
    changes: props.draftChanges,
    dialogs,
    operations: props.allOperations
  });
  localChanges.value = result.changes;
  stagedMainChanges.value = [...result.changes];
  const finalDialog = dialogs[dialogs.length - 1];
  stagedParentResourceKeys.value = new Set(
    finalDialog.resources.map(resource => resourceGroupKeyOf(resource))
  );
  stagedOperationCode.value = finalDialog.operation.code;
  stagedScopeMode.value = finalDialog.scopeMode;
  editorMode.value = "children";
}

const childDraftDirty = computed(
  () =>
    JSON.stringify(localChanges.value) !==
    JSON.stringify(stagedMainChanges.value)
);

async function handleBackToMain() {
  if (childDraftDirty.value) {
    try {
      await ElMessageBox.confirm(
        "返回修改主权限将放弃本次子权限调整，是否继续？",
        "放弃子权限调整",
        {
          confirmButtonText: "继续返回",
          cancelButtonText: "留在当前页",
          type: "warning"
        }
      );
    } catch {
      return;
    }
  }
  localChanges.value = [...stagedMainChanges.value];
  editorMode.value = "main";
}

function handleAddChild(input: {
  parent: EffectiveRecord;
  recordKey: GrantRecordKey;
  resourceLabel: string;
}) {
  const recordKey = {
    ...input.recordKey,
    canGrant: enforceNotDelegable(
      input.recordKey.canGrant ?? false,
      input.recordKey.conditionCode
    )
  };
  localChanges.value = [
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
  ];
}

function handleUpdateChild(input: {
  record: EffectiveRecord;
  canGrant: boolean;
  conditionCode: string | null;
}) {
  const condition = input.conditionCode;
  const canGrantValue = enforceNotDelegable(input.canGrant, condition);
  const recordKey: GrantRecordKey = {
    resourceTypeCode: input.record.resourceTypeCode,
    resourceCode: input.record.resourceCode,
    codeType: input.record.codeType,
    operationCode: input.record.operationCode,
    scopeMode: input.record.scopeMode,
    conditionCode: condition,
    canGrant: canGrantValue
  };
  const summary = buildSummary({
    recordKey,
    resourceLabel: effectiveResourceLabel(input.record)
  });

  if (input.record.draftMark === "add" && input.record.changeId) {
    localChanges.value = localChanges.value.map(change => {
      if (change.changeId !== input.record.changeId) return change;
      if (change.kind === "add") {
        return { ...change, recordKey, summary };
      }
      if (change.kind === "replace") {
        return { ...change, newKey: recordKey, summary };
      }
      return change;
    });
    return;
  }

  const existing = localChanges.value.find(
    (change): change is Extract<DraftChange, { kind: "update" }> =>
      change.kind === "update" && change.recordId === input.record.id
  );
  if (existing) {
    localChanges.value = localChanges.value
      .map(change =>
        change.changeId === existing.changeId && change.kind === "update"
          ? {
              ...change,
              after: {
                canGrant: canGrantValue,
                conditionCode: condition
              },
              summary
            }
          : change
      )
      .filter(change => {
        if (change.kind !== "update") return true;
        return (
          change.before.canGrant !== change.after.canGrant ||
          change.before.conditionCode !== change.after.conditionCode
        );
      });
    return;
  }

  if (
    input.record.canGrant === canGrantValue &&
    input.record.conditionCode === condition
  ) {
    return;
  }
  localChanges.value = [
    ...localChanges.value,
    buildUpdateChange({
      before: input.record,
      after: { canGrant: canGrantValue, conditionCode: condition },
      summary
    })
  ];
}

function handleReplaceChild(input: {
  record: EffectiveRecord;
  newKey: GrantRecordKey;
  resourceLabel: string;
}) {
  const newKey = {
    ...input.newKey,
    canGrant: enforceNotDelegable(
      input.newKey.canGrant ?? false,
      input.newKey.conditionCode
    )
  };
  const summary = buildSummary({
    recordKey: newKey,
    resourceLabel: input.resourceLabel
  });

  if (input.record.draftMark === "add" && input.record.changeId) {
    localChanges.value = localChanges.value.map(change => {
      if (change.changeId !== input.record.changeId) return change;
      if (change.kind === "add") {
        return { ...change, recordKey: newKey, summary };
      }
      if (change.kind === "replace") {
        return { ...change, newKey, summary };
      }
      return change;
    });
    return;
  }

  localChanges.value = localChanges.value.filter(
    change => !(change.kind === "update" && change.recordId === input.record.id)
  );
  localChanges.value = [
    ...localChanges.value,
    buildReplaceChange({
      removedRecords: [input.record],
      newKey,
      parentPermissionId: input.record.dependOn ?? undefined,
      cascadeChildCount: 0,
      summary
    })
  ];
}

function handleRemoveChild(record: EffectiveRecord) {
  if (record.draftMark === "add" && record.changeId) {
    localChanges.value = localChanges.value.filter(
      change => change.changeId !== record.changeId
    );
    return;
  }
  localChanges.value = localChanges.value.filter(
    change => !(change.kind === "update" && change.recordId === record.id)
  );
  localChanges.value = [
    ...localChanges.value,
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
}

function handleRestoreChild(record: EffectiveRecord) {
  if (!record.changeId) return;
  localChanges.value = localChanges.value.filter(
    change => change.changeId !== record.changeId
  );
}

function handleConfirm() {
  if (editorMode.value === "children") {
    emit("confirm", localChanges.value);
    return;
  }
  const dialogs = buildDialogResults();
  if (!dialogs) return;
  const result = applyDialogResultsToDraft({
    baseline: props.baseline,
    changes: props.draftChanges,
    dialogs,
    operations: props.allOperations
  });
  emit("confirm", result.changes);
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
            <el-select
              v-if="
                selectedOp?.resourceTypeCode == null &&
                selectableTypes.length > 1
              "
              v-model="allScopeType"
              size="small"
              class="all-type-select"
              placeholder="资源类型"
            >
              <el-option
                v-for="typeCode in selectableTypes"
                :key="typeCode"
                :value="typeCode"
                :label="typeCode"
              />
            </el-select>
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
            check-on-click-node
            node-key="id"
            :expand-on-click-node="false"
            default-expand-all
          >
            <template #default="{ node, data }">
              <span class="tree-node">
                <span class="node-label">
                  {{ data.name }}<span class="node-code">{{ data.code }}</span>
                </span>
                <template v-if="scopeMode === 'INSTANCE'">
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
            父节点授权会自动覆盖其子孙节点。
          </div>
        </div>
      </section>

      <section v-if="selectedOp" class="grant-options">
        <div class="options-heading">
          <span class="field-label">授权设置</span>
          <span class="options-hint">{{ optionsHint }}</span>
        </div>
        <div class="option-fields">
          <ConditionPicker
            :model-value="conditionCode"
            :conditions="conditions"
            :disabled="!canCondition"
            :placeholder="conditionPlaceholder"
            @update:model-value="handleConditionChange"
          />
          <el-tooltip
            v-if="!canCondition"
            content="无 CONDITION:VIEW 权限，条件选择不可用（可前往 3.2 权限条件页了解）"
            placement="top"
          >
            <span class="condition-blocked">条件置灰</span>
          </el-tooltip>
          <el-tooltip
            :disabled="!conditionDelegationBlocked"
            content="条件权限不可转授：带条件的权限不能设置可再授予，需先清除条件"
            placement="top"
          >
            <span>
              <el-checkbox
                :model-value="canGrant"
                :indeterminate="settingsMixed && !settingsTouched"
                :disabled="canGrantDisabled"
                @update:model-value="handleCanGrantChange"
              >
                允许再授予
              </el-checkbox>
            </span>
          </el-tooltip>
        </div>
      </section>

      <section v-if="selectedOp" class="child-entry">
        <div class="child-entry-copy">
          <span class="child-entry-mark" aria-hidden="true">↳</span>
          <div>
            <span class="field-label">子权限</span>
            <span>为所选主权限按需挂载下级权限</span>
          </div>
        </div>
        <el-button
          size="small"
          type="primary"
          plain
          :disabled="confirmDisabled"
          @click="handleConfigureChildren"
        >
          配置子权限
        </el-button>
      </section>
    </div>

    <GrantChildConfigurator
      v-if="editorMode === 'children'"
      :parents="childParents"
      :children-provider="childrenOfLocal"
      :conditions="conditions"
      :operations="allOperations"
      :resource-forest="allResourceForest"
      :can-condition="canCondition"
      @back="handleBackToMain"
      @add="handleAddChild"
      @update="handleUpdateChild"
      @replace="handleReplaceChild"
      @remove="handleRemoveChild"
      @restore="handleRestoreChild"
    />

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

.grant-options {
  padding-top: var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);

  .options-heading {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    margin-bottom: var(--space-2);
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
