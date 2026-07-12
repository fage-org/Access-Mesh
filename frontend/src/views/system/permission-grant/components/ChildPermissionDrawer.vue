<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { inject } from "vue";
import { message } from "@/utils/message";
import { usePermissionGrant } from "../utils/hook";
import {
  permCellKey,
  type ChildPermissionContext,
  type PermissionCellContext,
  type AdditionalSettingContext
} from "../utils/types";
import PermissionCell from "./PermissionCell.vue";
import {
  getResourceTree,
  getOperationList,
  type ResourceTreeNode,
  type OperationItem,
  type GrantScopeMode
} from "@/api/permission-grant";

defineOptions({ name: "ChildPermissionDrawer" });

const props = defineProps<{
  modelValue: boolean;
  context: ChildPermissionContext | null;
}>();
const emit = defineEmits<{
  "update:modelValue": [v: boolean];
  "open-setting": [ctx: AdditionalSettingContext];
}>();

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const visible = computed({
  get: () => props.modelValue,
  set: v => emit("update:modelValue", v)
});

const childResourceTree = ref<ResourceTreeNode[]>([]);
const childOperations = ref<OperationItem[]>([]);
const loading = ref(false);
// P1-5：子资源类型可切换（childResourceTypeCodes 多个时）
const selectedChildType = ref<string>("");

const parentInfo = computed(() => {
  if (!props.context) return "";
  const p = props.context.parent;
  const res =
    p.scopeMode === "ALL" ? "全部" : (p.resourceName ?? p.resourceCode ?? "");
  return `${p.resourceTypeCode} / ${res} / ${p.operationCode} / ${p.scopeMode}`;
});

const parentKey = computed(() => {
  if (!props.context) return "";
  const p = props.context.parent;
  return permCellKey({
    domainCode: store.currentDomainCode.value,
    resourceTypeCode: p.resourceTypeCode,
    scopeMode: p.scopeMode,
    resourceCode: p.resourceCode,
    codeType: p.codeType,
    operationCode: p.operationCode
  });
});

/** 子资源类型能力（supportsAll 决定是否显示 ALL 行；supportsCondition/Delegation 传给附加设置） */
const childResourceType = computed(
  () =>
    store.resourceTypes.value.find(
      t => t.resourceTypeCode === selectedChildType.value
    ) ?? null
);

watch(
  () => props.modelValue,
  async v => {
    if (v && props.context) {
      selectedChildType.value = props.context.childResourceTypeCodes[0] ?? "";
      if (selectedChildType.value) await loadChildContext();
    }
  }
);

watch(selectedChildType, async v => {
  if (v && props.modelValue && childResourceTree.value.length === 0) {
    await loadChildContext();
  } else if (v && props.modelValue) {
    await loadChildContext();
  }
});

async function loadChildContext() {
  if (!selectedChildType.value) return;
  loading.value = true;
  try {
    const [rt, ops] = await Promise.all([
      getResourceTree({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: selectedChildType.value
      }),
      getOperationList({ resourceTypeCode: selectedChildType.value })
    ]);
    childResourceTree.value = rt.items;
    childOperations.value = ops.items;
  } catch (e) {
    message(e instanceof Error ? e.message : "加载子权限上下文失败", {
      type: "error"
    });
  } finally {
    loading.value = false;
  }
}

/** 子权限 key = parentKey + "|" + childPermCellKey */
function childKey(
  resourceCode: string | null,
  codeType: string | null,
  operationCode: string,
  scopeMode: GrantScopeMode
): string {
  return (
    parentKey.value +
    "|" +
    permCellKey({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: selectedChildType.value,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    })
  );
}

/** 构建子权限单元格上下文 */
function buildChildContext(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
): PermissionCellContext {
  const resourceCode = scopeMode === "ALL" ? null : (row?.resourceCode ?? null);
  const codeType = scopeMode === "ALL" ? null : (row?.codeType ?? null);
  const ck = childKey(resourceCode, codeType, operationCode, scopeMode);
  const inDraft = store.childDraft.value.has(ck);
  const inBase = store.childBaseline.value.has(ck);
  let state: PermissionCellContext["state"] = "UNAUTHORIZED";
  let draft = store.childDraft.value.get(ck) ?? null;
  if (inDraft && inBase) {
    const d = store.childDraft.value.get(ck)!;
    const b = store.childBaseline.value.get(ck)!;
    state =
      d.conditionCode !== b.conditionCode || d.canGrant !== b.canGrant
        ? "MODIFIED"
        : "GRANTED";
    draft = d;
  } else if (inDraft) {
    state = "PENDING_ADD";
    draft = store.childDraft.value.get(ck)!;
  } else if (inBase) {
    state = "PENDING_REMOVE";
    draft = store.childBaseline.value.get(ck)!;
  }
  let allCovered = false;
  if (scopeMode === "INSTANCE" && state === "UNAUTHORIZED") {
    if (
      store.childDraft.value.has(childKey(null, null, operationCode, "ALL"))
    ) {
      state = "ALL_COVERED";
      allCovered = true;
    }
  }
  const condCode = draft?.conditionCode;
  const condSummary = condCode
    ? (store.conditions.value.find(c => c.code === condCode)?.name ?? condCode)
    : null;
  return {
    state,
    draft,
    allCovered,
    grantableByOperator: !props.context?.readonly,
    denyReason: props.context?.readonly ? "只读" : null,
    readonly: props.context?.readonly ?? true,
    childCount: 0,
    conditionSummary: condSummary
  };
}

function onToggle(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
) {
  if (!props.context || props.context.readonly) return;
  const resourceCode = scopeMode === "ALL" ? null : (row?.resourceCode ?? null);
  const codeType = scopeMode === "ALL" ? null : (row?.codeType ?? null);
  store.toggleChildCell(
    parentKey.value,
    props.context.parent,
    selectedChildType.value,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  );
}

/** P1-5：子权限附加设置入口（emit 到 index.vue 打开 AdditionalSettingDialog） */
function onOpenSetting(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
) {
  if (!props.context) return;
  const ctx = buildChildContext(row, operationCode, scopeMode);
  if (!ctx.draft) return;
  const resourceCode = scopeMode === "ALL" ? null : (row?.resourceCode ?? null);
  const codeType = scopeMode === "ALL" ? null : (row?.codeType ?? null);
  const ck = childKey(resourceCode, codeType, operationCode, scopeMode);
  emit("open-setting", {
    key: parentKey.value,
    draft: ctx.draft,
    isNew: ctx.state === "PENDING_ADD",
    readonly: props.context.readonly,
    isChild: true,
    childKey: ck,
    supportsCondition: childResourceType.value?.supportsCondition,
    supportsDelegation: childResourceType.value?.supportsDelegation
  });
}

interface TableRow extends ResourceTreeNode {
  isAllRow?: boolean;
}

/** P1-5：ALL 行（子资源类型 supportsAll 时显示） */
const tableData = computed<TableRow[]>(() => {
  const rows: TableRow[] = [];
  if (childResourceType.value?.supportsAll) {
    rows.push({
      resourceCode: "__ALL__",
      codeType: "default",
      resourceName: `全部 ${childResourceType.value.resourceTypeName} 资源`,
      parentCode: null,
      children: [],
      isAllRow: true
    });
  }
  rows.push(...childResourceTree.value);
  return rows;
});

function rowScopeMode(row: TableRow): GrantScopeMode {
  return row.isAllRow ? "ALL" : "INSTANCE";
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="子权限 / 范围权限配置"
    size="640px"
    :close-on-click-modal="false"
  >
    <template v-if="context">
      <div class="child-header">
        <span class="child-label">主权限：</span>
        <span class="child-value">{{ parentInfo }}</span>
      </div>
      <div class="child-header">
        <span class="child-label">子资源类型：</span>
        <el-select
          v-model="selectedChildType"
          size="small"
          class="child-type-select"
          :disabled="context.childResourceTypeCodes.length <= 1"
        >
          <el-option
            v-for="code in context.childResourceTypeCodes"
            :key="code"
            :label="code"
            :value="code"
          />
        </el-select>
      </div>

      <el-alert
        v-if="context.childResourceTypeCodes.length === 0"
        type="warning"
        :closable="false"
        title="当前业务域未配置子权限资源类型（SUB_PERM）"
        description="子权限只允许一层；子权限资源类型由业务域配置驱动。"
        show-icon
      />

      <el-alert
        v-else-if="context.readonly"
        type="info"
        :closable="false"
        title="只读模式"
        show-icon
      />

      <div v-if="selectedChildType" v-loading="loading" class="child-matrix">
        <el-table
          :data="tableData"
          row-key="resourceCode"
          :tree-props="{ children: 'children' }"
          border
          size="small"
        >
          <el-table-column prop="resourceName" label="资源" min-width="180">
            <template #default="{ row }">
              <span :class="['res-name', { 'res-all': row.isAllRow }]">
                {{ row.resourceName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            v-for="op in childOperations"
            :key="op.operationCode"
            :label="op.operationName"
            align="center"
            min-width="90"
          >
            <template #default="{ row }">
              <PermissionCell
                :context="
                  buildChildContext(
                    row.isAllRow ? null : row,
                    op.operationCode,
                    rowScopeMode(row)
                  )
                "
                @toggle="
                  onToggle(
                    row.isAllRow ? null : row,
                    op.operationCode,
                    rowScopeMode(row)
                  )
                "
                @open-setting="
                  onOpenSetting(
                    row.isAllRow ? null : row,
                    op.operationCode,
                    rowScopeMode(row)
                  )
                "
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
.child-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-2);
}

.child-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.child-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.child-type-select {
  width: 200px;
}

.child-matrix {
  margin-top: var(--space-3);
}

.res-name {
  font-size: 13px;
}

.res-all {
  font-weight: 600;
  color: var(--el-color-primary);
}

.op-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.2;
}

.op-code {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
