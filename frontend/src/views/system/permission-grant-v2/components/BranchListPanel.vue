<script setup lang="ts">
import { ref, computed, inject } from "vue";
import { type CellDisplay } from "../utils/cell-summary";
import { type GrantVariantId, type V2DraftPermission } from "../utils/v2-types";
import { type ConditionOption } from "@/api/permission-grant";
import { normalizeConditionCode } from "../utils/grant-variant";
import { usePermissionGrantV2 } from "../utils/hook";
import { message } from "@/utils/message";
import BranchRow from "./BranchRow.vue";
import ChildMatrixPanel from "./ChildMatrixPanel.vue";

defineOptions({ name: "BranchListPanelV2" });

type Store = ReturnType<typeof usePermissionGrantV2>;
const store = inject<Store>("pgV2Store")!;

const props = defineProps<{
  display: CellDisplay;
  supportsCondition: boolean;
  supportsDelegation: boolean;
  conditionOptions: ConditionOption[];
  /** 是否允许展开子权限矩阵（默认 true；子权限分支传 false 防止二层嵌套，T-FE-033） */
  allowChildren?: boolean;
}>();

const emit = defineEmits<{
  (
    e: "add-branch",
    payload: {
      conditionCode: string | null;
      canGrant: boolean;
      keepDirect: boolean;
    }
  ): void;
  (
    e: "edit-branch",
    payload: {
      variantId: GrantVariantId;
      conditionCode: string | null;
      canGrant: boolean;
    }
  ): void;
  (e: "revoke-branch", variantId: GrantVariantId): void;
  (e: "restore-branch", variantId: GrantVariantId): void;
  (e: "restore-modify", variantId: GrantVariantId): void;
  (e: "close"): void;
}>();

interface BranchItem {
  variant: V2DraftPermission;
  status: "baseline" | "pendingAdd" | "pendingRemove" | "modified";
}

/** 合并 draft + 被移除的 baseline，标注分支状态（含 modified 属性已修改） */
const branches = computed<BranchItem[]>(() => {
  const baselineMap = new Map(
    props.display.baselineVariants.map(v => [v.variantId, v] as const)
  );
  const draftIds = new Set(props.display.draftVariants.map(v => v.variantId));
  const items: BranchItem[] = props.display.draftVariants.map(v => {
    const b = baselineMap.get(v.variantId);
    let status: BranchItem["status"] = "baseline";
    if (!b) {
      status = "pendingAdd";
    } else if (
      normalizeConditionCode(b.conditionCode) !==
        normalizeConditionCode(v.conditionCode) ||
      b.canGrant !== v.canGrant
    ) {
      status = "modified";
    }
    return { variant: v, status };
  });
  for (const b of props.display.baselineVariants) {
    if (!draftIds.has(b.variantId)) {
      items.push({ variant: b, status: "pendingRemove" });
    }
  }
  return items;
});

// 添加分支：内联表单让用户选条件（P1 修复：不再固定 null）
const adding = ref(false);
const newCondition = ref<string>("");
const newCanGrant = ref(false);
const newKeepDirect = ref(false);

const canAdd = computed(() => props.display.grantableByOperator);

/** R11 redundant 候选：被 ALL 覆盖+无直接记录，新增分支默认 redundantSkipped */
const redundantCandidate = computed(() =>
  store.redundantCandidate(props.display.cell)
);

const addConditionOptions = computed(() => [
  { label: "无条件", value: "" },
  ...props.conditionOptions.map(c => ({
    label: c.name || c.code,
    value: c.code
  }))
]);

function startAdd() {
  if (!canAdd.value) return;
  newCondition.value = "";
  newCanGrant.value = false;
  newKeepDirect.value = false;
  adding.value = true;
}

function confirmAdd() {
  // R11：redundant 候选 + 未显式仍创建 -> 跳过（不提交任务，避免污染 grantTasks）
  if (redundantCandidate.value && !newKeepDirect.value) {
    message("已跳过：该操作被 ALL 覆盖，创建直接记录冗余", {
      type: "info"
    });
    adding.value = false;
    return;
  }
  emit("add-branch", {
    conditionCode: newCondition.value === "" ? null : newCondition.value,
    canGrant: newCanGrant.value,
    keepDirect: newKeepDirect.value
  });
  adding.value = false;
}

function cancelAdd() {
  adding.value = false;
}

// ========== 子权限矩阵展开（T-FE-033，挂具体父变体行） ==========
const supportsChildren = computed(
  () =>
    store.domainCapability.value.supportsChildren &&
    (props.allowChildren ?? true)
);
const expandedChildParents = ref<Set<GrantVariantId>>(new Set());

function toggleChildren(variantId: GrantVariantId): void {
  const s = new Set(expandedChildParents.value);
  if (s.has(variantId)) s.delete(variantId);
  else s.add(variantId);
  expandedChildParents.value = s;
}

function childCountOf(variantId: GrantVariantId): number {
  return store.childVariantsOf(variantId).length;
}

function isChildrenExpanded(variantId: GrantVariantId): boolean {
  return expandedChildParents.value.has(variantId);
}

function canExpandChildren(status: BranchItem["status"]): boolean {
  return supportsChildren.value && status !== "pendingRemove";
}
</script>

<template>
  <div class="branch-list-panel">
    <div class="branch-list-header">
      <span class="branch-list-title">
        分支列表（{{ display.cell.operationCode }}
        <template v-if="display.cell.scopeMode === 'ALL'">· ALL</template>
        <template v-else>· {{ display.cell.resourceCode }}</template>
        ）
      </span>
      <el-button size="small" link @click="emit('close')"> 收起 </el-button>
    </div>
    <div class="branch-list-body">
      <template v-for="item in branches" :key="String(item.variant.variantId)">
        <BranchRow
          :variant="item.variant"
          :status="item.status"
          :supports-condition="supportsCondition"
          :supports-delegation="supportsDelegation"
          :condition-options="conditionOptions"
          :child-count="childCountOf(item.variant.variantId)"
          :children-expanded="isChildrenExpanded(item.variant.variantId)"
          :can-expand-children="canExpandChildren(item.status)"
          @edit="
            payload =>
              emit('edit-branch', {
                ...payload,
                variantId: item.variant.variantId
              })
          "
          @revoke="emit('revoke-branch', item.variant.variantId)"
          @restore="emit('restore-branch', item.variant.variantId)"
          @restore-modify="emit('restore-modify', item.variant.variantId)"
          @toggle-children="toggleChildren(item.variant.variantId)"
        />
        <ChildMatrixPanel
          v-if="isChildrenExpanded(item.variant.variantId)"
          :parent-variant-id="item.variant.variantId"
        />
      </template>
      <div v-if="branches.length === 0" class="empty-branches">
        无分支（未授权）
      </div>

      <!-- R11 redundant 警告（被 ALL 覆盖+无直接记录） -->
      <el-alert
        v-if="adding && redundantCandidate"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title> 该操作已被 ALL 覆盖，创建直接记录冗余 </template>
        <div class="keep-direct-wrap">
          <span>仍创建直接记录</span>
          <el-switch v-model="newKeepDirect" size="small" />
        </div>
      </el-alert>

      <!-- 添加分支表单 -->
      <div v-if="adding" class="add-form">
        <el-select
          v-if="supportsCondition"
          v-model="newCondition"
          size="small"
          placeholder="选择条件"
          class="add-cond-select"
        >
          <el-option
            v-for="opt in addConditionOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span v-if="supportsDelegation" class="add-can-grant">
          <span class="can-grant-label">转授</span>
          <el-switch v-model="newCanGrant" size="small" />
        </span>
        <span class="add-form-actions">
          <el-button size="small" type="primary" @click="confirmAdd">
            确认添加
          </el-button>
          <el-button size="small" @click="cancelAdd"> 取消 </el-button>
        </span>
      </div>

      <div class="add-branch-wrap">
        <el-button
          size="small"
          type="primary"
          plain
          :disabled="!canAdd"
          @click="startAdd"
        >
          + 添加分支
        </el-button>
        <span v-if="!canAdd" class="deny-hint">不可新增（只读或能力不足）</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.branch-list-panel {
  display: flex;
  flex-direction: column;
  padding: var(--space-2) var(--space-3);
  background: var(--el-fill-color-light);
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.branch-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-1);
}

.branch-list-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.branch-list-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.empty-branches {
  padding: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.add-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-1) var(--space-2);
  background: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
}

.add-cond-select {
  width: 160px;
}

.add-can-grant {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.keep-direct-wrap {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
  margin-top: var(--space-1);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.can-grant-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add-form-actions {
  display: inline-flex;
  gap: var(--space-1);
  margin-left: auto;
}

.add-branch-wrap {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: flex-end;
  margin-top: var(--space-1);
}

.deny-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
