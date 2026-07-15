<script setup lang="ts">
import { ref, computed, watch, inject } from "vue";
import { message } from "@/utils/message";
import { usePermissionGrant } from "../utils/hook";
import { ReConditionPicker } from "@/components/ReConditionPicker";
import { ChildPermissionInline } from "@/components/ChildPermissionInline";
import type {
  ChildPermissionBinding,
  ChildCellInput,
  ChildOpenSettingPayload
} from "@/components/ChildPermissionInline/src/types";
import {
  permCellKey,
  childPermCellKey,
  type DraftPermission,
  type CellState,
  type PermissionCellContext,
  type ChildPermissionContext,
  type GrantTaskSnapshot,
  type TaskResource,
  type TaskChildGroup,
  type GrantTriggerPayload,
  type AdjustTriggerPayload
} from "@/utils/permission-grant-types";
import type {
  OperationItem,
  ResourceTreeNode,
  GrantScopeMode,
  ConditionOption
} from "@/api/permission-grant";

defineOptions({ name: "GrantDialog" });

const props = defineProps<{
  /** null=关闭；GrantTriggerPayload=新建；AdjustTriggerPayload=调整（预填） */
  trigger: GrantTriggerPayload | AdjustTriggerPayload | null;
}>();
const emit = defineEmits<{
  close: [];
  committed: [taskId: string];
}>();

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const visible = computed(() => props.trigger !== null);
const isAdjust = computed(
  () => !!props.trigger && "operationCode" in props.trigger
);
const isAllScope = computed(() => props.trigger?.scopeMode === "ALL");

const ALL_ROW_KEY = "__ALL__";

// ---- 本地草稿 ----
const step = ref(0);
const selectedOps = ref<Set<string>>(new Set());
const selectedResourceKeys = ref<Set<string>>(new Set());
const conditionCode = ref<string | null>(null);
const canGrant = ref(false);
const keepDirectWhenAllCovered = ref(false);
const resKeyword = ref("");
// 子权限本地草稿（childKey -> DraftPermission）
const localChildDrafts = ref<Map<string, DraftPermission>>(new Map());
// 步骤四已展开的主权限 parentKey（完整集合替换语义，展开即初始化 baseline 副本）
const expandedChildParents = ref<Set<string>>(new Set());

const treeRef = ref();
// P1-6：ReConditionPicker ref（validate/reset 契约）
const pickerRef = ref<InstanceType<typeof ReConditionPicker>>();
const childPickerRef = ref<InstanceType<typeof ReConditionPicker>>();
// 步骤四 el-collapse v-model（提前声明供 watch 重置）
const expandedChild = ref<string[]>([]);
// P1-5：子权限附加设置编辑面板 state（提前声明供 watch 重置）
interface EditingChild {
  parentKey: string;
  childKey: string;
  input: ChildCellInput;
  supportsCondition: boolean;
  supportsDelegation: boolean;
}
const editingChild = ref<EditingChild | null>(null);
const editingConditionCode = ref<string | null>(null);
const editingCanGrant = ref(false);

// ---- 触发时初始化本地草稿 ----
watch(
  () => props.trigger,
  t => {
    // P2-1：关闭弹窗（t=null）时也重置条件创建态与编辑面板
    if (!t) {
      pickerRef.value?.reset();
      childPickerRef.value?.reset();
      editingChild.value = null;
      editingConditionCode.value = null;
      editingCanGrant.value = false;
      return;
    }
    step.value = 0;
    selectedOps.value = new Set();
    selectedResourceKeys.value = new Set();
    conditionCode.value = null;
    canGrant.value = false;
    keepDirectWhenAllCovered.value = false;
    localChildDrafts.value = new Map();
    expandedChildParents.value = new Set();
    resKeyword.value = "";
    // P2-1：完整重置树勾选、折叠项、条件创建态、子权限编辑面板
    expandedChild.value = [];
    treeRef.value?.setCheckedKeys([]);
    pickerRef.value?.reset();
    editingChild.value = null;
    editingConditionCode.value = null;
    editingCanGrant.value = false;
    childPickerRef.value?.reset();

    if (isAdjust.value && "operationCode" in t) {
      // 调整模式预填单操作/单资源/条件
      selectedOps.value = new Set([t.operationCode]);
      if (t.scopeMode === "INSTANCE") {
        selectedResourceKeys.value = new Set([
          `${t.codeType}|${t.resourceCode}`
        ]);
      } else {
        selectedResourceKeys.value = new Set([ALL_ROW_KEY]);
      }
      // P1-2：按资源类型能力归一化预填值，不支持时强制 null/false
      // （supportsCondition/supportsDelegation watch 只在能力标志变化时触发，
      // 能力一直 false 不会再次触发清空，初始化时必须归一化）
      const rt = store.currentResourceType.value;
      const sc = rt?.supportsCondition ?? false;
      const sd = rt?.supportsDelegation ?? false;
      conditionCode.value = sc ? t.conditionCode : null;
      canGrant.value = sd ? (t.draft?.canGrant ?? false) : false;
    }
  },
  { immediate: true }
);

// ========== 步骤一：操作多选 ==========

const operations = computed(() => store.operations.value);

interface OpRow extends OperationItem {
  grantable: boolean;
  denyReason: string | null;
  ownedCount: number;
}

const opTableData = computed<OpRow[]>(() =>
  operations.value.map(op => {
    const { grantable, reason } = store.isGrantableByOperator(
      store.currentResourceTypeCode.value,
      op.operationCode
    );
    // 已拥有资源数：从 baseline 统计该操作的主权限数（不发查询）
    let ownedCount = 0;
    for (const [, d] of store.mainBaseline.value) {
      if (
        d.resourceTypeCode === store.currentResourceTypeCode.value &&
        d.operationCode === op.operationCode
      ) {
        ownedCount++;
      }
    }
    return { ...op, grantable, denyReason: reason, ownedCount };
  })
);

function opSelected(op: OperationItem) {
  return selectedOps.value.has(op.operationCode);
}

function toggleOp(op: OperationItem, checked: boolean) {
  const next = new Set(selectedOps.value);
  if (checked) next.add(op.operationCode);
  else next.delete(op.operationCode);
  selectedOps.value = next;
}

// ========== 步骤二：资源多选 ==========

const resourceTree = computed(() => store.resourceTree.value);

interface ResTreeNode extends ResourceTreeNode {
  nodeKey: string;
  children: ResTreeNode[];
}

const resTreeData = computed<ResTreeNode[]>(() => {
  const build = (nodes: ResourceTreeNode[]): ResTreeNode[] =>
    nodes.map(n => ({
      ...n,
      nodeKey: `${n.codeType}|${n.resourceCode}`,
      children: n.children?.length ? build(n.children) : []
    }));
  return build(resourceTree.value);
});

const checkedResourceKeys = computed(() => [...selectedResourceKeys.value]);

function onCheckChange() {
  const keys = treeRef.value?.getCheckedKeys() ?? [];
  selectedResourceKeys.value = new Set(keys as string[]);
}

watch(resKeyword, kw => {
  treeRef.value?.filter(kw);
});

function filterNode(value: string, data: ResTreeNode) {
  if (!value) return true;
  return (
    data.resourceName.toLowerCase().includes(value.toLowerCase()) ||
    data.resourceCode.toLowerCase().includes(value.toLowerCase())
  );
}

/** 选中资源 -> TaskResource 列表 */
const selectedResources = computed<TaskResource[]>(() => {
  if (isAllScope.value) {
    return [{ resourceCode: null, codeType: null, resourceName: null }];
  }
  const map = new Map<string, ResourceTreeNode>();
  const walk = (nodes: ResourceTreeNode[]) => {
    for (const n of nodes) {
      map.set(`${n.codeType}|${n.resourceCode}`, n);
      if (n.children?.length) walk(n.children);
    }
  };
  walk(resourceTree.value);
  return [...selectedResourceKeys.value].map(rk => {
    const node = map.get(rk);
    return {
      resourceCode: node?.resourceCode ?? rk.split("|")[1] ?? null,
      codeType: node?.codeType ?? rk.split("|")[0] ?? null,
      resourceName: node?.resourceName ?? null
    };
  });
});

/**
 * 资源节点已拥有 selectedOps 中几个操作的权限（baseline 判定，不发查询）。
 * 用于步骤二"已有/部分已有"标识：N=size 全部已有，0<N<size 部分已有，0 无。
 */
function resOwnedCount(node: ResTreeNode): number {
  if (selectedOps.value.size === 0) return 0;
  let count = 0;
  for (const op of selectedOps.value) {
    const key = permCellKey({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: store.currentResourceTypeCode.value,
      scopeMode: "INSTANCE",
      resourceCode: node.resourceCode,
      codeType: node.codeType,
      operationCode: op
    });
    if (store.mainBaseline.value.has(key)) count++;
  }
  return count;
}

// ========== 步骤三：条件 + canGrant + R11 开关 ==========

const showSplitHint = computed(() => selectedOps.value.size > 1);

/**
 * 是否存在"被 ALL 覆盖且无直接记录"的组合（决定 keepDirectWhenAllCovered 是否显示）。
 * P1-4：用 mainDraft（当前投影，含未保存 ALL 新增/移除），不用 mainBaseline，
 * 否则未保存的 ALL 新增会静默跳过、未保存的 ALL 移除会误显示开关。
 */
const hasRedundantCandidate = computed(() => {
  if (isAllScope.value) return false;
  const typeCode = store.currentResourceTypeCode.value;
  for (const op of selectedOps.value) {
    const allKey = permCellKey({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: typeCode,
      scopeMode: "ALL",
      resourceCode: null,
      codeType: null,
      operationCode: op
    });
    const allCovered = store.mainDraft.value.has(allKey);
    if (!allCovered) continue;
    for (const res of selectedResources.value) {
      const instKey = permCellKey({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: typeCode,
        scopeMode: "INSTANCE",
        resourceCode: res.resourceCode,
        codeType: res.codeType,
        operationCode: op
      });
      if (!store.mainDraft.value.has(instKey)) return true;
    }
  }
  return false;
});

/**
 * P2-5：将被 R11 跳过的"被 ALL 覆盖且无直接记录"组合数（keepDirectWhenAllCovered=false 时生效）。
 * 用于确认摘要展示。
 */
const redundantSkippedCount = computed(() => {
  if (isAllScope.value || keepDirectWhenAllCovered.value) return 0;
  const typeCode = store.currentResourceTypeCode.value;
  let count = 0;
  for (const op of selectedOps.value) {
    const allKey = permCellKey({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: typeCode,
      scopeMode: "ALL",
      resourceCode: null,
      codeType: null,
      operationCode: op
    });
    if (!store.mainDraft.value.has(allKey)) continue;
    for (const res of selectedResources.value) {
      const instKey = permCellKey({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: typeCode,
        scopeMode: "INSTANCE",
        resourceCode: res.resourceCode,
        codeType: res.codeType,
        operationCode: op
      });
      if (!store.mainDraft.value.has(instKey)) count++;
    }
  }
  return count;
});

const showKeepDirect = computed(
  () => isAdjust.value === false && hasRedundantCandidate.value
);

// P1-6：资源类型能力门控（条件/转授权），不支持时强制清空非法值
const supportsCondition = computed(
  () => store.currentResourceType.value?.supportsCondition ?? false
);
const supportsDelegation = computed(
  () => store.currentResourceType.value?.supportsDelegation ?? false
);

watch(supportsCondition, ok => {
  if (!ok && conditionCode.value !== null) conditionCode.value = null;
});
watch(supportsDelegation, ok => {
  if (!ok && canGrant.value) canGrant.value = false;
});

/** ReConditionPicker 内联新建成功：去重追加到 store 单源并选中 */
function onConditionCreated(condition: ConditionOption) {
  if (store.conditions.value.some(c => c.code === condition.code)) return;
  store.conditions.value = [...store.conditions.value, condition];
}

// ========== 步骤四：子权限逐项 ==========

const supportsChildren = computed(
  () =>
    store.domainCapability.value.supportsChildren &&
    store.domainCapability.value.childResourceTypeCodes.length > 0
);

interface ChildGroupMeta {
  parentKey: string;
  operationCode: string;
  resourceCode: string | null;
  codeType: string | null;
  resourceName: string | null;
  title: string;
}

/**
 * P1-1：组合是否会被 R11 跳过（INSTANCE + 被 ALL 覆盖 + 无直接记录 + 未显式保留）。
 * 这类组合的父权限不会进入 replay 投影，子权限配置将无效，步骤四应过滤掉。
 */
function isRedundantSkipped(op: string, res: TaskResource): boolean {
  const t = props.trigger;
  if (!t || t.scopeMode !== "INSTANCE") return false;
  if (keepDirectWhenAllCovered.value) return false;
  const allKey = permCellKey({
    domainCode: t.domainCode,
    resourceTypeCode: t.resourceTypeCode,
    scopeMode: "ALL",
    resourceCode: null,
    codeType: null,
    operationCode: op
  });
  if (!store.mainDraft.value.has(allKey)) return false;
  const instKey = permCellKey({
    domainCode: t.domainCode,
    resourceTypeCode: t.resourceTypeCode,
    scopeMode: "INSTANCE",
    resourceCode: res.resourceCode,
    codeType: res.codeType,
    operationCode: op
  });
  return !store.mainDraft.value.has(instKey);
}

const childGroupMetas = computed<ChildGroupMeta[]>(() => {
  if (!supportsChildren.value) return [];
  const t = props.trigger;
  if (!t) return [];
  const metas: ChildGroupMeta[] = [];
  for (const op of selectedOps.value) {
    for (const res of selectedResources.value) {
      // P1-1：R11 跳过的组合父权限不会进入投影，子权限配置无效，过滤掉
      if (isRedundantSkipped(op, res)) continue;
      const parentKey = permCellKey({
        domainCode: t.domainCode,
        resourceTypeCode: t.resourceTypeCode,
        scopeMode: t.scopeMode,
        resourceCode: res.resourceCode,
        codeType: res.codeType,
        operationCode: op
      });
      const resName =
        t.scopeMode === "ALL"
          ? "全部"
          : (res.resourceName ?? res.resourceCode ?? "");
      metas.push({
        parentKey,
        operationCode: op,
        resourceCode: res.resourceCode,
        codeType: res.codeType,
        resourceName: res.resourceName,
        title: `${resName} / ${op}`
      });
    }
  }
  return metas;
});

interface ChildGroupView extends ChildGroupMeta {
  parent: DraftPermission;
  context: ChildPermissionContext;
  binding: ChildPermissionBinding;
}

const childGroupViews = computed<ChildGroupView[]>(() =>
  childGroupMetas.value.map(m => {
    const parent = buildParentDraft(m.operationCode, {
      resourceCode: m.resourceCode,
      codeType: m.codeType,
      resourceName: m.resourceName
    });
    return {
      ...m,
      parent,
      context: makeChildContext(parent),
      binding: makeChildBinding(m.parentKey, parent)
    };
  })
);

function onChildExpand(parentKey: string) {
  if (expandedChildParents.value.has(parentKey)) return;
  // P1-3：从当前投影（childDraft = baseline + grantTasks + overlay）初始化，
  // 而非 childBaseline，避免覆盖前序未保存任务的子权限修改
  const newMap = new Map(localChildDrafts.value);
  const prefix = parentKey + "|";
  for (const [ck, c] of store.childDraft.value) {
    if (ck.startsWith(prefix)) newMap.set(ck, { ...c });
  }
  localChildDrafts.value = newMap;
  const next = new Set(expandedChildParents.value);
  next.add(parentKey);
  expandedChildParents.value = next;
}

/** el-collapse 展开变化：新展开的项初始化本地子权限草稿 */
function onCollapseChange(activeNames: string | string[]) {
  const names = Array.isArray(activeNames) ? activeNames : [activeNames];
  for (const name of names) {
    onChildExpand(name);
  }
}

function buildChildContext(
  parentKey: string,
  parent: DraftPermission,
  input: ChildCellInput
): PermissionCellContext {
  const ck = childPermCellKey(parentKey, {
    domainCode: parent.domainCode,
    resourceTypeCode: input.childResourceTypeCode,
    scopeMode: input.scopeMode,
    resourceCode: input.resourceCode,
    codeType: input.codeType,
    operationCode: input.operationCode
  });
  const inLocal = localChildDrafts.value.has(ck);
  // P1-3：inBase 用当前投影（childDraft = baseline + grantTasks + overlay），
  // 使前序未保存任务的子权限修改在弹窗内可见
  const inBase = store.childDraft.value.has(ck);
  let state: CellState;
  if (inLocal && inBase) {
    const d = localChildDrafts.value.get(ck)!;
    const b = store.childDraft.value.get(ck)!;
    state =
      d.conditionCode !== b.conditionCode || d.canGrant !== b.canGrant
        ? "MODIFIED"
        : "GRANTED";
  } else if (inLocal) {
    state = "PENDING_ADD";
  } else if (inBase) {
    state = "PENDING_REMOVE";
  } else if (input.scopeMode === "INSTANCE") {
    const allCk = childPermCellKey(parentKey, {
      domainCode: parent.domainCode,
      resourceTypeCode: input.childResourceTypeCode,
      scopeMode: "ALL",
      resourceCode: null,
      codeType: null,
      operationCode: input.operationCode
    });
    state =
      localChildDrafts.value.has(allCk) || store.childDraft.value.has(allCk)
        ? "ALL_COVERED"
        : "UNAUTHORIZED";
  } else {
    state = "UNAUTHORIZED";
  }
  const draft =
    state === "PENDING_REMOVE"
      ? (store.childDraft.value.get(ck) ?? null)
      : (localChildDrafts.value.get(ck) ?? null);
  const { grantable, reason } = store.isGrantableByOperator(
    input.childResourceTypeCode,
    input.operationCode
  );
  const condCode = draft?.conditionCode;
  const condSummary = condCode
    ? (store.conditions.value.find(c => c.code === condCode)?.name ?? condCode)
    : null;
  return {
    state,
    draft,
    allCovered: state === "ALL_COVERED",
    // R6 副标记：baseline 直接记录事实（服务端），保持 childBaseline
    hasBaselineDirectRecord:
      input.scopeMode === "INSTANCE" && store.childBaseline.value.has(ck),
    grantableByOperator: grantable,
    denyReason: reason,
    readonly: store.readonly.value,
    childCount: 0,
    conditionSummary: condSummary
  };
}

function toggleChildLocal(
  parentKey: string,
  parent: DraftPermission,
  input: ChildCellInput
) {
  if (store.readonly.value) return;
  const ck = childPermCellKey(parentKey, {
    domainCode: parent.domainCode,
    resourceTypeCode: input.childResourceTypeCode,
    scopeMode: input.scopeMode,
    resourceCode: input.resourceCode,
    codeType: input.codeType,
    operationCode: input.operationCode
  });
  const inLocal = localChildDrafts.value.has(ck);
  const inBase = store.childDraft.value.has(ck);
  if (!inLocal && !inBase) {
    const { grantable, reason } = store.isGrantableByOperator(
      input.childResourceTypeCode,
      input.operationCode
    );
    if (!grantable) {
      message(`不可授予：${reason}`, { type: "warning" });
      return;
    }
  }
  const newMap = new Map(localChildDrafts.value);
  if (!inLocal && !inBase) {
    newMap.set(ck, {
      id: null,
      domainCode: parent.domainCode,
      resourceTypeCode: input.childResourceTypeCode,
      scopeMode: input.scopeMode,
      resourceCode: input.resourceCode,
      codeType: input.codeType,
      operationCode: input.operationCode,
      conditionCode: null,
      canGrant: false,
      dependOn: parent.id,
      dependOnTempKey: parent.id ? null : parentKey,
      grantSource: "MANUAL",
      resourceName: null
    });
  } else if (inLocal) {
    newMap.delete(ck);
  } else if (inBase) {
    // 恢复后移除：把当前投影副本写入本地（使其出现在期望集合中），再由用户决定
    const base = store.childDraft.value.get(ck)!;
    newMap.set(ck, { ...base });
  }
  localChildDrafts.value = newMap;
}

function makeChildBinding(
  parentKey: string,
  parent: DraftPermission
): ChildPermissionBinding {
  return {
    getCell: (input: ChildCellInput) =>
      buildChildContext(parentKey, parent, input),
    toggleCell: (input: ChildCellInput) =>
      toggleChildLocal(parentKey, parent, input)
  };
}

function makeChildContext(parent: DraftPermission): ChildPermissionContext {
  return {
    parent,
    childResourceTypeCodes: store.domainCapability.value.childResourceTypeCodes,
    readonly: store.readonly.value
  };
}

// P1-5：子权限附加设置内联编辑面板（步骤四内，单弹窗约束）
// editingChild 只保存稳定定位信息，不持有 draft 引用；实际值从 localChildDrafts 读
function onChildOpenSetting(
  parentKey: string,
  payload: ChildOpenSettingPayload
) {
  // P2-1：切换编辑目标前重置旧 picker（飞行创建请求失效，创建态清空，避免选中到新 editingChild）
  if (editingChild.value) {
    childPickerRef.value?.reset();
  }
  const domainCode = payload.draft?.domainCode ?? store.currentDomainCode.value;
  const childKey = childPermCellKey(parentKey, {
    domainCode,
    resourceTypeCode: payload.input.childResourceTypeCode,
    scopeMode: payload.input.scopeMode,
    resourceCode: payload.input.resourceCode,
    codeType: payload.input.codeType,
    operationCode: payload.input.operationCode
  });
  const current = localChildDrafts.value.get(childKey);
  editingChild.value = {
    parentKey,
    childKey,
    input: payload.input,
    supportsCondition: payload.supportsCondition ?? false,
    supportsDelegation: payload.supportsDelegation ?? false
  };
  editingConditionCode.value = current?.conditionCode ?? null;
  editingCanGrant.value = current?.canGrant ?? false;
}

function onChildEditCancel() {
  childPickerRef.value?.reset();
  editingChild.value = null;
  editingConditionCode.value = null;
  editingCanGrant.value = false;
}

function onChildEditDone() {
  if (!editingChild.value) return;
  const ec = editingChild.value;
  // 校验条件（支持条件时）
  if (ec.supportsCondition && !childPickerRef.value?.validate()) return;
  // 写回 localChildDrafts（新 Map，避免原地修改导致响应式失效）
  const newMap = new Map(localChildDrafts.value);
  const existing = newMap.get(ec.childKey);
  if (existing) {
    newMap.set(ec.childKey, {
      ...existing,
      // 能力不支持时强制清空，不保留历史非法值
      conditionCode: ec.supportsCondition ? editingConditionCode.value : null,
      canGrant: ec.supportsDelegation ? editingCanGrant.value : false
    });
    localChildDrafts.value = newMap;
  }
  onChildEditCancel();
}

// 父权限因 R11 未进入投影时，立即关闭编辑面板
watch(
  () => store.mainDraft.value,
  md => {
    if (editingChild.value && !md.has(editingChild.value.parentKey)) {
      onChildEditCancel();
    }
  }
);

// P2：childGroupMetas 动态过滤（keepDirect/操作/资源选择变化）导致目标组消失时，
// 立即关闭编辑面板，避免 v-if 隐藏后 onConfirm 持续阻断"请先完成或取消"
watch(
  () => childGroupMetas.value,
  metas => {
    if (!editingChild.value) return;
    const exists = metas.some(
      m => m.parentKey === editingChild.value!.parentKey
    );
    if (!exists) onChildEditCancel();
  }
);

// ========== 确认/移除 ==========

function genTaskId(): string {
  return `task-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function buildChildGroups(): TaskChildGroup[] {
  const groups: TaskChildGroup[] = [];
  for (const parentKey of expandedChildParents.value) {
    const meta = childGroupMetas.value.find(m => m.parentKey === parentKey);
    if (!meta) continue;
    const prefix = parentKey + "|";
    const children: DraftPermission[] = [];
    for (const [ck, c] of localChildDrafts.value) {
      if (ck.startsWith(prefix)) children.push(c);
    }
    groups.push({
      parentKey,
      operationCode: meta.operationCode,
      resourceCode: meta.resourceCode,
      codeType: meta.codeType,
      children
    });
  }
  return groups;
}

function buildParentDraft(
  operationCode: string,
  res: TaskResource
): DraftPermission {
  const t = props.trigger!;
  return {
    id: null,
    domainCode: t.domainCode,
    resourceTypeCode: t.resourceTypeCode,
    scopeMode: t.scopeMode,
    resourceCode: res.resourceCode,
    codeType: res.codeType,
    operationCode,
    conditionCode: conditionCode.value,
    canGrant: canGrant.value,
    dependOn: null,
    dependOnTempKey: null,
    grantSource: "MANUAL",
    resourceName: res.resourceName
  };
}

function buildTask(intent: "grant" | "adjust" | "remove"): GrantTaskSnapshot {
  const t = props.trigger!;
  return {
    taskId: genTaskId(),
    domainCode: t.domainCode,
    roleExternalId: t.roleExternalId,
    roleTypeCode: t.roleTypeCode,
    resourceTypeCode: t.resourceTypeCode,
    scopeMode: t.scopeMode,
    intent,
    operationCodes: [...selectedOps.value],
    resources: selectedResources.value,
    // P1-2：按能力归一化，禁用字段不提交（防止历史非法值落入任务快照）
    conditionCode: supportsCondition.value ? conditionCode.value : null,
    canGrant: supportsDelegation.value ? canGrant.value : false,
    keepDirectWhenAllCovered: keepDirectWhenAllCovered.value,
    children: buildChildGroups(),
    createdAt: Date.now()
  };
}

function onConfirm() {
  if (selectedOps.value.size === 0) {
    message("请至少选择一个操作", { type: "warning" });
    step.value = 0;
    return;
  }
  if (!isAllScope.value && selectedResources.value.length === 0) {
    message("请至少选择一个资源", { type: "warning" });
    step.value = 1;
    return;
  }
  // P1-6：确认前校验条件选择（缺失/停用）
  if (supportsCondition.value && !pickerRef.value?.validate()) return;
  // P1-5：子权限编辑面板未完成时阻止确认
  if (editingChild.value) {
    message("请先完成或取消子权限附加设置", { type: "warning" });
    return;
  }
  try {
    const intent = isAdjust.value ? "adjust" : "grant";
    const task = buildTask(intent);
    store.commitGrantTask(task);
    message("已加入变更", { type: "success" });
    emit("committed", task.taskId);
    emit("close");
  } catch (e) {
    message(e instanceof Error ? e.message : "提交授权任务失败", {
      type: "error"
    });
  }
}

function onRemove() {
  const t = props.trigger;
  if (!isAdjust.value || !t || !("operationCode" in t)) return;
  // R6：移除被 ALL 覆盖的直接记录提示不影响 ALL 覆盖（用当前投影 mainDraft）
  if (t.scopeMode === "INSTANCE") {
    const allKey = permCellKey({
      domainCode: t.domainCode,
      resourceTypeCode: t.resourceTypeCode,
      scopeMode: "ALL",
      resourceCode: null,
      codeType: null,
      operationCode: t.operationCode
    });
    if (store.mainDraft.value.has(allKey)) {
      message("该操作存在 ALL 覆盖，移除直接记录不影响 ALL 覆盖", {
        type: "info"
      });
    }
  }
  try {
    // P2-3：移除使用原始 trigger 的操作/资源，而非当前弹窗选择
    const task: GrantTaskSnapshot = {
      taskId: genTaskId(),
      domainCode: t.domainCode,
      roleExternalId: t.roleExternalId,
      roleTypeCode: t.roleTypeCode,
      resourceTypeCode: t.resourceTypeCode,
      scopeMode: t.scopeMode,
      intent: "remove",
      operationCodes: [t.operationCode],
      resources: [
        {
          resourceCode: t.resourceCode,
          codeType: t.codeType,
          resourceName: t.draft?.resourceName ?? null
        }
      ],
      conditionCode: null,
      canGrant: false,
      keepDirectWhenAllCovered: false,
      children: [],
      createdAt: Date.now()
    };
    store.commitGrantTask(task);
    message("已加入移除变更", { type: "success" });
    emit("committed", task.taskId);
    emit("close");
  } catch (e) {
    message(e instanceof Error ? e.message : "提交移除任务失败", {
      type: "error"
    });
  }
}

function onCancel() {
  emit("close");
}

function onVisibleChange(v: boolean) {
  if (!v) emit("close");
}

// 步骤导航
function canNext(): boolean {
  if (step.value === 0) return selectedOps.value.size > 0;
  if (step.value === 1)
    return isAllScope.value || selectedResources.value.length > 0;
  return true;
}

function next() {
  if (!canNext()) {
    message("请先完成当前步骤", { type: "warning" });
    return;
  }
  if (step.value < 3) step.value++;
}

function prev() {
  if (step.value > 0) step.value--;
}

// 弹窗标题（acceptance：「{角色}」授权 · {资源类型}）
const title = computed(() => {
  const t = props.trigger;
  if (!t) return "";
  const type =
    store.currentResourceType.value?.resourceTypeName ?? t.resourceTypeCode;
  return `「${t.roleName}」授权 · ${type}`;
});
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="80vw"
    top="8vh"
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
  >
    <el-steps :active="step" finish-status="success" simple>
      <el-step title="选择操作" />
      <el-step title="选择资源" />
      <el-step title="条件与转授权" />
      <el-step title="子权限（可跳过）" />
    </el-steps>

    <el-scrollbar class="step-body">
      <!-- 步骤一：操作多选 -->
      <div v-show="step === 0" class="step-pane">
        <el-table :data="opTableData" border size="small">
          <el-table-column label="选择" width="64" align="center">
            <template #default="{ row }">
              <el-checkbox
                :model-value="opSelected(row)"
                :disabled="!row.grantable"
                @change="(v: boolean) => toggleOp(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="operationName"
            label="操作名称"
            min-width="120"
          />
          <el-table-column prop="operationCode" label="编码" width="140" />
          <el-table-column label="说明" min-width="120">
            <template #default="{ row }">
              <span class="op-desc">{{ row.inheritMask ?? "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已拥有资源数" width="120" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="plain">
                {{ row.ownedCount }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可授予" width="160">
            <template #default="{ row }">
              <el-tag
                v-if="row.grantable"
                size="small"
                type="success"
                effect="plain"
              >
                可授予
              </el-tag>
              <el-tooltip
                v-else
                :content="row.denyReason ?? ''"
                placement="top"
              >
                <el-tag size="small" type="info" effect="plain"
                  >不可授予</el-tag
                >
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 步骤二：资源多选 -->
      <div v-show="step === 1" class="step-pane">
        <el-alert
          v-if="isAllScope"
          type="info"
          :closable="false"
          show-icon
          title="范围：全部资源（ALL）"
          description="ALL 节点不创建虚拟资源编码，资源步骤固定为全量范围。"
        />
        <template v-else>
          <el-input
            v-model="resKeyword"
            placeholder="搜索资源名称或编码"
            size="small"
            clearable
            class="res-search"
          />
          <span class="selected-count">
            已选 {{ selectedResources.length }} 个资源
          </span>
          <el-tree
            ref="treeRef"
            :data="resTreeData"
            :props="{ children: 'children', label: 'resourceName' }"
            show-checkbox
            node-key="nodeKey"
            :default-checked-keys="checkedResourceKeys"
            :filter-node-method="filterNode"
            @check="onCheckChange"
          >
            <template #default="{ data }">
              <span class="res-node">
                <span class="res-node-name">{{ data.resourceName }}</span>
                <el-tag
                  v-if="resOwnedCount(data) > 0"
                  size="small"
                  :type="
                    resOwnedCount(data) === selectedOps.size
                      ? 'success'
                      : 'warning'
                  "
                  effect="plain"
                >
                  {{ resOwnedCount(data) === selectedOps.size ? "✓" : "◑" }}
                  已有 {{ resOwnedCount(data) }}/{{ selectedOps.size }}
                </el-tag>
              </span>
            </template>
          </el-tree>
        </template>
      </div>

      <!-- 步骤三：条件 + canGrant + R11 开关 -->
      <div v-show="step === 2" class="step-pane">
        <el-alert
          v-if="showSplitHint"
          type="warning"
          :closable="false"
          show-icon
          title="本组条件和 canGrant 将应用于全部所选操作与资源；如需按操作配置不同条件，请拆分为多次授权。"
        />
        <el-form label-width="140px" class="cond-form">
          <el-form-item label="权限条件">
            <ReConditionPicker
              ref="pickerRef"
              v-model="conditionCode"
              :conditions="store.conditions.value"
              :disabled="store.readonly.value || !supportsCondition"
              :can-create="store.canCreateCondition.value"
              @created="onConditionCreated"
            />
          </el-form-item>
          <el-form-item label="允许转授权">
            <el-switch
              v-model="canGrant"
              :disabled="store.readonly.value || !supportsDelegation"
            />
            <span class="form-tip">canGrant：允许该权限持有人继续授予他人</span>
          </el-form-item>
          <el-form-item v-if="showKeepDirect" label="仍创建直接记录">
            <el-switch v-model="keepDirectWhenAllCovered" />
            <span class="form-tip">
              部分"操作×资源"被 ALL
              覆盖且无直接记录；默认不创建冗余直接记录，开启后将为这些组合创建直接记录
            </span>
          </el-form-item>
        </el-form>
        <div class="apply-summary">
          同一配置将应用于 {{ selectedOps.size }} 个操作 ×
          {{ selectedResources.length }} 个资源
          <span v-if="redundantSkippedCount > 0" class="redundant-hint">
            （{{ redundantSkippedCount }} 个被 ALL
            覆盖且无直接记录的组合将不创建冗余直接记录）
          </span>
        </div>
      </div>

      <!-- 步骤四：子权限逐项 -->
      <div v-show="step === 3" class="step-pane">
        <el-empty
          v-if="!supportsChildren || childGroupMetas.length === 0"
          description="无可配置子权限的组合，可直接确认"
          :image-size="60"
        />
        <template v-else>
          <p class="step-hint">
            子权限属于单个主权限，不批量配置；展开组合后逐项配置，可跳过。
          </p>
          <!-- P1-5：子权限附加设置内联编辑面板 -->
          <el-alert
            v-if="editingChild"
            type="info"
            :closable="false"
            show-icon
            class="child-edit-panel"
            :title="`编辑子权限：${editingChild.input.operationCode}`"
          >
            <el-form label-width="100px" size="small">
              <el-form-item
                v-if="editingChild.supportsCondition"
                label="权限条件"
              >
                <ReConditionPicker
                  ref="childPickerRef"
                  v-model="editingConditionCode"
                  :conditions="store.conditions.value"
                  :disabled="store.readonly.value"
                  :can-create="store.canCreateCondition.value"
                  @created="onConditionCreated"
                />
              </el-form-item>
              <el-form-item
                v-if="editingChild.supportsDelegation"
                label="允许转授权"
              >
                <el-switch
                  v-model="editingCanGrant"
                  :disabled="store.readonly.value"
                />
              </el-form-item>
            </el-form>
            <div class="child-edit-actions">
              <el-button size="small" @click="onChildEditCancel">
                取消
              </el-button>
              <el-button size="small" type="primary" @click="onChildEditDone">
                完成
              </el-button>
            </div>
          </el-alert>
          <el-collapse v-model="expandedChild" @change="onCollapseChange">
            <el-collapse-item
              v-for="v in childGroupViews"
              :key="v.parentKey"
              :name="v.parentKey"
              :title="v.title"
            >
              <ChildPermissionInline
                :context="v.context"
                :binding="v.binding"
                :resource-types="store.resourceTypes.value"
                @open-setting="p => onChildOpenSetting(v.parentKey, p)"
              />
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
    </el-scrollbar>

    <template #footer>
      <el-button v-if="isAdjust" type="danger" @click="onRemove">
        移除
      </el-button>
      <el-button @click="onCancel">取消</el-button>
      <el-button v-if="step > 0" @click="prev">上一步</el-button>
      <el-button
        v-if="step < 3"
        type="primary"
        :disabled="!canNext()"
        @click="next"
      >
        下一步
      </el-button>
      <el-button v-if="step === 3" type="primary" @click="onConfirm">
        确认加入变更
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.step-body {
  max-height: calc(80vh - 220px);
  padding: var(--space-3);
}

.step-pane {
  min-height: 200px;
}

.op-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.res-search {
  width: 240px;
  margin-bottom: var(--space-2);
}

.selected-count {
  margin-left: var(--space-3);
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.res-node {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.res-node-name {
  font-size: 13px;
}

.cond-form {
  max-width: 640px;
  margin-top: var(--space-2);
}

.form-tip {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.apply-summary {
  margin-top: var(--space-3);
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.step-hint {
  margin-bottom: var(--space-2);
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.child-edit-panel {
  margin-bottom: var(--space-3);
}

.child-edit-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.redundant-hint {
  margin-left: var(--space-1);
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
