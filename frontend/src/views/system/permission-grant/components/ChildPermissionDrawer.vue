<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { inject } from "vue";
import { message } from "@/utils/message";
import { usePermissionGrant } from "../utils/hook";
import {
  permCellKey,
  type ChildPermissionContext,
  type PermissionCellContext
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
}>();

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const visible = computed({
  get: () => props.modelValue,
  set: v => emit("update:modelValue", v)
});

const childResourceTree = ref<ResourceTreeNode[]>([]);
const childOperations = ref<OperationItem[]>([]);
const loading = ref(false);

const childResourceTypeCode = computed(
  () => props.context?.childResourceTypeCodes[0] ?? ""
);

const parentInfo = computed(() => {
  if (!props.context) return "";
  const p = props.context.parent;
  const res =
    p.scopeMode === "ALL" ? "全部" : (p.resourceName ?? p.resourceCode ?? "");
  return `${p.resourceTypeCode} / ${res} / ${p.operationCode} / ${p.scopeMode}`;
});

watch(
  () => props.modelValue,
  async v => {
    if (v && props.context && childResourceTypeCode.value) {
      await loadChildContext();
    }
  }
);

async function loadChildContext() {
  if (!childResourceTypeCode.value) return;
  loading.value = true;
  try {
    const [rt, ops] = await Promise.all([
      getResourceTree({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: childResourceTypeCode.value
      }),
      getOperationList({ resourceTypeCode: childResourceTypeCode.value })
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

/** 父权限稳定键 */
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
      resourceTypeCode: childResourceTypeCode.value,
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
    if (d.conditionCode !== b.conditionCode || d.canGrant !== b.canGrant) {
      state = "MODIFIED";
    } else {
      state = "GRANTED";
    }
    draft = d;
  } else if (inDraft && !inBase) {
    state = "PENDING_ADD";
    draft = store.childDraft.value.get(ck)!;
  } else if (!inDraft && inBase) {
    state = "PENDING_REMOVE";
    draft = store.childBaseline.value.get(ck)!;
  }
  // ALL 覆盖
  let allCovered = false;
  if (scopeMode === "INSTANCE" && state === "UNAUTHORIZED") {
    const allCk = childKey(null, null, operationCode, "ALL");
    if (store.childDraft.value.has(allCk)) {
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
    childResourceTypeCode.value,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  );
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
        <span class="child-label">允许子资源类型：</span>
        <span class="child-value">{{ childResourceTypeCode || "无" }}</span>
      </div>

      <el-alert
        v-if="!childResourceTypeCode"
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

      <div
        v-if="childResourceTypeCode"
        v-loading="loading"
        class="child-matrix"
      >
        <el-table
          :data="childResourceTree"
          row-key="resourceCode"
          :tree-props="{ children: 'children' }"
          border
          size="small"
        >
          <el-table-column prop="resourceName" label="资源" min-width="180" />
          <el-table-column
            v-for="op in childOperations"
            :key="op.operationCode"
            :label="op.operationName"
            align="center"
            min-width="90"
          >
            <template #header>
              <div class="op-header">
                <span>{{ op.operationName }}</span>
                <span class="op-code">{{ op.operationCode }}</span>
              </div>
            </template>
            <template #default="{ row }">
              <PermissionCell
                :context="buildChildContext(row, op.operationCode, 'INSTANCE')"
                @toggle="onToggle(row, op.operationCode, 'INSTANCE')"
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

.child-matrix {
  margin-top: var(--space-3);
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
