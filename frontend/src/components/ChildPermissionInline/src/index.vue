<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { message } from "@/utils/message";
import { RePermissionCell } from "@/components/RePermissionCell";
import {
  getResourceTree,
  getOperationList,
  type ResourceTreeNode,
  type OperationItem,
  type ResourceTypeItem,
  type GrantScopeMode
} from "@/api/permission-grant";
import type {
  ChildPermissionContext,
  PermissionCellContext
} from "@/utils/permission-grant-types";
import type {
  ChildCellInput,
  ChildPermissionBinding,
  ChildOpenSettingPayload
} from "./types";

defineOptions({ name: "ChildPermissionInline" });

const props = defineProps<{
  /** 子权限上下文（父权限 + 子资源类型码 + 只读） */
  context: ChildPermissionContext;
  /** 草稿操作窄接口（T-FE-014 传 pgStore adapter，T-FE-026 传弹窗 adapter） */
  binding: ChildPermissionBinding;
  /** 资源类型能力表（查子资源类型 supportsAll/Condition/Delegation） */
  resourceTypes: readonly ResourceTypeItem[];
}>();
const emit = defineEmits<{
  "open-setting": [payload: ChildOpenSettingPayload];
}>();

const childResourceTree = ref<ResourceTreeNode[]>([]);
const childOperations = ref<OperationItem[]>([]);
const loading = ref(false);
// 子资源类型可切换（childResourceTypeCodes 多个时）
const selectedChildType = ref<string>("");

const parentInfo = computed(() => {
  const p = props.context.parent;
  const res =
    p.scopeMode === "ALL" ? "全部" : (p.resourceName ?? p.resourceCode ?? "");
  return `${p.resourceTypeCode} / ${res} / ${p.operationCode} / ${p.scopeMode}`;
});

/** 子资源类型能力（supportsAll 决定是否显示 ALL 行；supportsCondition/Delegation 传给附加设置） */
const childResourceType = computed(
  () =>
    props.resourceTypes.find(
      t => t.resourceTypeCode === selectedChildType.value
    ) ?? null
);

let reqId = 0;

watch(
  () => props.context,
  ctx => {
    if (ctx) {
      const first = ctx.childResourceTypeCodes[0] ?? "";
      if (first !== selectedChildType.value) {
        // 类型变化 -> 由 selectedChildType watch 触发 load
        selectedChildType.value = first;
      } else {
        // 同类型但上下文变化（如父权限/domainCode 变）-> 手动 load
        void loadChildContext();
      }
    }
  },
  { immediate: true }
);

watch(
  selectedChildType,
  v => {
    if (v) void loadChildContext();
  },
  { immediate: true }
);

async function loadChildContext() {
  if (!selectedChildType.value) return;
  const myId = ++reqId;
  loading.value = true;
  // 清空旧数据，避免过期树与新类型串写（P1：A->B 切换时 A 树不应保留）
  childResourceTree.value = [];
  childOperations.value = [];
  try {
    const [rt, ops] = await Promise.all([
      getResourceTree({
        domainCode: props.context.parent.domainCode,
        resourceTypeCode: selectedChildType.value
      }),
      getOperationList({ resourceTypeCode: selectedChildType.value })
    ]);
    // 过期响应丢弃（P1：A->B 快速切换时 A 的迟到响应不覆盖 B）
    if (myId !== reqId) return;
    childResourceTree.value = rt.items;
    childOperations.value = ops.items;
  } catch (e) {
    if (myId !== reqId) return;
    message(e instanceof Error ? e.message : "加载子权限上下文失败", {
      type: "error"
    });
  } finally {
    if (myId === reqId) loading.value = false;
  }
}

function makeInput(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
): ChildCellInput {
  return {
    childResourceTypeCode: selectedChildType.value,
    scopeMode,
    resourceCode: scopeMode === "ALL" ? null : (row?.resourceCode ?? null),
    codeType: scopeMode === "ALL" ? null : (row?.codeType ?? null),
    operationCode
  };
}

/** 构建子权限单元格上下文（委托 binding.getCell） */
function buildChildContext(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
): PermissionCellContext {
  return props.binding.getCell(makeInput(row, operationCode, scopeMode));
}

function onToggle(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
) {
  if (props.context.readonly) return;
  const input = makeInput(row, operationCode, scopeMode);
  // 新增子权限时检查 grantableByOperator（撤销/恢复不检查，同主权限口径）
  const ctx = props.binding.getCell(input);
  if (ctx.state === "UNAUTHORIZED" || ctx.state === "ALL_COVERED") {
    if (!ctx.grantableByOperator) {
      message(`不可授予：${ctx.denyReason}`, { type: "warning" });
      return;
    }
  }
  props.binding.toggleCell(input);
}

/** 子权限附加设置入口（emit open-setting，使用方算 childKey 并打开附加设置） */
function onOpenSetting(
  row: ResourceTreeNode | null,
  operationCode: string,
  scopeMode: GrantScopeMode
) {
  const input = makeInput(row, operationCode, scopeMode);
  const ctx = props.binding.getCell(input);
  if (!ctx.draft) return;
  emit("open-setting", {
    input,
    draft: ctx.draft,
    state: ctx.state,
    supportsCondition: childResourceType.value?.supportsCondition,
    supportsDelegation: childResourceType.value?.supportsDelegation
  });
}

interface TableRow extends ResourceTreeNode {
  isAllRow?: boolean;
}

/** ALL 行（子资源类型 supportsAll 时显示） */
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
  <div class="child-perm-inline">
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
            <RePermissionCell
              :show-child-action="false"
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
  </div>
</template>

<style lang="scss" scoped>
.child-perm-inline {
  width: 100%;
}

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
</style>
