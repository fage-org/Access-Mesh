<script setup lang="ts">
import { computed, ref } from "vue";
import { inject } from "vue";
import { usePermissionGrant } from "../utils/hook";
import { permCellKey } from "../utils/types";
import PermissionCell from "./PermissionCell.vue";
import type {
  PermissionCellContext,
  AdditionalSettingContext,
  ChildPermissionContext
} from "../utils/types";
import type { ResourceTreeNode, GrantScopeMode } from "@/api/permission-grant";

defineOptions({ name: "PermissionMatrixPanel" });

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;
const emit = defineEmits<{
  "open-setting": [ctx: AdditionalSettingContext];
  "open-child": [ctx: ChildPermissionContext];
}>();

interface TableRow extends ResourceTreeNode {
  children: TableRow[];
  isAllRow?: boolean;
}

const tableRef = ref();
const expandAll = ref(false);

const tableData = computed<TableRow[]>(() => {
  const rt = store.currentResourceType.value;
  const rows: TableRow[] = [];
  if (rt?.supportsAll) {
    rows.push({
      resourceCode: "__ALL__",
      codeType: "default",
      resourceName: `全部 ${rt.resourceTypeName} 资源`,
      parentCode: null,
      children: [],
      isAllRow: true
    });
  }
  const buildRows = (nodes: ResourceTreeNode[]): TableRow[] =>
    nodes.map(n => ({
      ...n,
      children: n.children?.length ? buildRows(n.children) : []
    }));
  rows.push(...buildRows(store.resourceTree.value));
  return rows;
});

function buildContext(
  row: TableRow,
  operationCode: string
): PermissionCellContext {
  const scopeMode: GrantScopeMode = row.isAllRow ? "ALL" : "INSTANCE";
  return store.buildMainCellContext(
    store.currentResourceTypeCode.value,
    scopeMode,
    row.isAllRow ? null : row.resourceCode,
    row.isAllRow ? null : row.codeType,
    operationCode
  );
}

function makeKey(row: TableRow, operationCode: string): string {
  const scopeMode: GrantScopeMode = row.isAllRow ? "ALL" : "INSTANCE";
  return permCellKey({
    domainCode: store.currentDomainCode.value,
    resourceTypeCode: store.currentResourceTypeCode.value,
    scopeMode,
    resourceCode: row.isAllRow ? null : row.resourceCode,
    codeType: row.isAllRow ? null : row.codeType,
    operationCode
  });
}

function onToggle(row: TableRow, operationCode: string) {
  const scopeMode: GrantScopeMode = row.isAllRow ? "ALL" : "INSTANCE";
  store.toggleMainCell(
    store.currentResourceTypeCode.value,
    scopeMode,
    row.isAllRow ? null : row.resourceCode,
    row.isAllRow ? null : row.codeType,
    operationCode
  );
}

function onOpenSetting(row: TableRow, operationCode: string) {
  const ctx = buildContext(row, operationCode);
  if (!ctx.draft) return;
  const rt = store.currentResourceType.value;
  emit("open-setting", {
    key: makeKey(row, operationCode),
    draft: ctx.draft,
    isNew: ctx.state === "PENDING_ADD",
    readonly: store.readonly.value,
    supportsCondition: rt?.supportsCondition,
    supportsDelegation: rt?.supportsDelegation
  });
}

function onOpenChild(row: TableRow, operationCode: string) {
  const ctx = buildContext(row, operationCode);
  if (!ctx.draft) return;
  emit("open-child", {
    parent: ctx.draft,
    childResourceTypeCodes: store.domainCapability.value.childResourceTypeCodes,
    readonly: store.readonly.value
  });
}

function toggleExpandAll() {
  expandAll.value = !expandAll.value;
  if (!tableRef.value) return;
  const toggle = (rows: TableRow[]) => {
    for (const r of rows) {
      tableRef.value.toggleRowExpansion(r, expandAll.value);
      if (r.children?.length) toggle(r.children);
    }
  };
  toggle(tableData.value);
}

const isGroupReadOnly = computed(
  () =>
    store.currentRole.value &&
    !store.currentRole.value.directGrantable &&
    !store.currentRole.value.roleExternalId.startsWith("__virtual_root_")
);
</script>

<template>
  <div class="matrix-panel">
    <div class="matrix-header">
      <el-select
        :model-value="store.currentResourceTypeCode.value"
        size="small"
        class="type-switcher"
        @change="(v: string) => store.switchResourceType(v)"
      >
        <el-option
          v-for="t in store.resourceTypes.value"
          :key="t.resourceTypeCode"
          :label="t.resourceTypeName"
          :value="t.resourceTypeCode"
        />
      </el-select>
      <div class="header-actions">
        <el-button link size="small" @click="toggleExpandAll">{{
          expandAll ? "收起全部" : "展开全部"
        }}</el-button>
      </div>
    </div>

    <!-- 组合角色只读说明（§3.4，决策点 3：只读空状态，有效权限展开暂缓） -->
    <el-alert
      v-if="isGroupReadOnly"
      type="info"
      :closable="false"
      show-icon
      title="组合角色不可直接配置权限"
      description="该角色为组合角色（GROUP_ROLE），通过聚合基础角色产生权限，不直接持有权限配置。有效权限展开及管理基础角色入口暂缓。"
    />

    <el-scrollbar class="matrix-scroll">
      <el-table
        v-if="!isGroupReadOnly"
        ref="tableRef"
        :data="tableData"
        row-key="resourceCode"
        :tree-props="{ children: 'children' }"
        :expand-on-click-node="false"
        border
        size="small"
        class="matrix-table"
      >
        <el-table-column prop="resourceName" label="资源" min-width="200">
          <template #default="{ row }">
            <span :class="['res-name', { 'res-all': row.isAllRow }]">
              {{ row.resourceName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          v-for="op in store.operations.value"
          :key="op.operationCode"
          :label="op.operationName"
          :prop="op.operationCode"
          align="center"
          min-width="100"
        >
          <template #header>
            <div class="op-header">
              <span>{{ op.operationName }}</span>
              <span class="op-code">{{ op.operationCode }}</span>
            </div>
          </template>
          <template #default="{ row }">
            <PermissionCell
              :context="buildContext(row, op.operationCode)"
              @toggle="onToggle(row, op.operationCode)"
              @open-setting="onOpenSetting(row, op.operationCode)"
              @open-child="onOpenChild(row, op.operationCode)"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.matrix-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.matrix-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.type-switcher {
  width: 160px;
}

.matrix-scroll {
  flex: 1;
  min-height: 0;
}

.matrix-table {
  width: 100%;

  :deep(.el-table__cell) {
    padding: 4px 0;
  }
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
