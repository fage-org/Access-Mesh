<script setup lang="ts">
/**
 * 授权弹窗内的子权限资源树编辑器。
 *
 * 交互与主权限保持一致：选择操作后，直接在资源树勾选授权、取消撤销，
 * ALL 使用资源区右上角的紧凑开关。子权限只表达父子挂载关系，
 * 不提供条件权限与再授予设置。
 */
import { computed, nextTick, ref, watch } from "vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { GrantRecordKey } from "@/api/permission-grant";
import {
  groupKeyOf,
  normalizeChildGrantKey,
  type EffectiveRecord
} from "../utils/grant-plan";
import {
  mergeOperationsForType,
  type OperationDefInput
} from "../utils/source-chain";

const props = defineProps<{
  parents: EffectiveRecord[];
  childrenProvider: (record: EffectiveRecord) => EffectiveRecord[];
  operations: OperationDefInput[];
  resourceForest: ResourceTreeNode[];
}>();

const emit = defineEmits<{
  (e: "back"): void;
  (
    e: "add",
    input: {
      parent: EffectiveRecord;
      recordKey: GrantRecordKey;
      resourceLabel: string;
    }
  ): void;
  (e: "remove", record: EffectiveRecord): void;
  (e: "restore", record: EffectiveRecord): void;
}>();

function parentKeyOf(record: EffectiveRecord): string {
  return record.draftMark === "add" && record.changeId
    ? `chg:${record.changeId}`
    : `id:${record.id}`;
}

function parentLabel(parent: EffectiveRecord): string {
  const resource =
    parent.resourceName ??
    parent.resourceCode ??
    `全部资源（${parent.resourceTypeCode}）`;
  return `${resource} · ${parent.operationCode ?? "组合位"} · ${parent.conditionCode ?? "无条件"}`;
}

const selectedParentKey = ref<string | null>(null);
const selectedParent = computed(
  () =>
    props.parents.find(
      parent => parentKeyOf(parent) === selectedParentKey.value
    ) ??
    props.parents[0] ??
    null
);

watch(
  () => props.parents.map(parentKeyOf),
  keys => {
    if (!keys.includes(selectedParentKey.value ?? "")) {
      selectedParentKey.value = keys[0] ?? null;
    }
  },
  { immediate: true }
);

const childList = computed(() =>
  selectedParent.value ? props.childrenProvider(selectedParent.value) : []
);
const activeChildCount = computed(
  () => childList.value.filter(child => child.draftMark !== "remove").length
);

type OpOption = {
  key: string;
  code: string;
  name: string;
};

const selectableTypes = computed(() => {
  const types = new Set<string>();
  for (const root of props.resourceForest) types.add(root.resourceTypeCode);
  return [...types].sort();
});

const selectedResourceType = ref<string | null>(null);
const operationOptions = computed<OpOption[]>(() => {
  const typeCode = selectedResourceType.value;
  if (!typeCode) return [];
  return mergeOperationsForType(props.operations, typeCode).map(operation => ({
    key: `${typeCode}:${operation.code}`,
    code: operation.code,
    name: operation.name
  }));
});

const selectedOpKey = ref<string | null>(null);
const selectedOp = computed<OpOption | null>(
  () =>
    operationOptions.value.find(option => option.key === selectedOpKey.value) ??
    null
);

const selectableForest = computed(() => {
  const typeCode = selectedResourceType.value;
  if (!typeCode) return [];
  return props.resourceForest.filter(
    root => root.resourceTypeCode === typeCode
  );
});

const defaultExpandedKeys = computed(() =>
  selectableForest.value.map(root => root.id)
);

const effectiveAllType = computed(() => selectedResourceType.value);

function childKey(input: {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  scopeMode: "INSTANCE" | "ALL";
}): GrantRecordKey | null {
  const operationCode = selectedOp.value?.code;
  if (!operationCode) return null;
  return normalizeChildGrantKey({
    ...input,
    operationCode,
    conditionCode: null,
    canGrant: false
  });
}

const manualChildrenByKey = computed(() => {
  const map = new Map<string, EffectiveRecord>();
  for (const child of childList.value) {
    if (child.grantSource === "MANUAL") map.set(groupKeyOf(child), child);
  }
  return map;
});

function recordForKey(key: GrantRecordKey | null): EffectiveRecord | null {
  return key ? (manualChildrenByKey.value.get(groupKeyOf(key)) ?? null) : null;
}

function recordForNode(node: ResourceTreeNode): EffectiveRecord | null {
  return recordForKey(
    childKey({
      resourceTypeCode: node.resourceTypeCode,
      resourceCode: node.code,
      codeType: node.codeType,
      scopeMode: "INSTANCE"
    })
  );
}

function allRecord(): EffectiveRecord | null {
  const resourceTypeCode = effectiveAllType.value;
  if (!resourceTypeCode) return null;
  return recordForKey(
    childKey({
      resourceTypeCode,
      resourceCode: null,
      codeType: null,
      scopeMode: "ALL"
    })
  );
}

function isActive(record: EffectiveRecord | null): boolean {
  return record != null && record.draftMark !== "remove";
}

function toggleRecord(
  key: GrantRecordKey | null,
  resourceLabel: string,
  selected: boolean
) {
  const parent = selectedParent.value;
  if (!key || !parent) return;
  const record = recordForKey(key);
  if (selected) {
    if (record?.draftMark === "remove") {
      emit("restore", record);
    } else if (!record) {
      emit("add", { parent, recordKey: key, resourceLabel });
    }
    return;
  }
  if (isActive(record)) emit("remove", record!);
}

const allScopeSelected = computed({
  get: () => isActive(allRecord()),
  set: (selected: boolean) => {
    const typeCode = effectiveAllType.value;
    if (!typeCode) return;
    toggleRecord(
      childKey({
        resourceTypeCode: typeCode,
        resourceCode: null,
        codeType: null,
        scopeMode: "ALL"
      }),
      `全部资源（${typeCode}）`,
      selected
    );
  }
});

const treeRef = ref();
const treeDisabled = computed(
  () =>
    selectedResourceType.value == null ||
    selectedOp.value == null ||
    allScopeSelected.value
);
const treeRenderKey = computed(
  () =>
    `${selectedParentKey.value ?? "none"}:${selectedResourceType.value ?? "no-type"}:${selectedOpKey.value ?? "preview"}`
);
const treeProps = {
  label: "name",
  children: "children",
  disabled: () => treeDisabled.value
};

const checkedNodeIds = computed(() => {
  const op = selectedOp.value;
  if (!op) return [];
  const result: number[] = [];
  const walk = (nodes: ResourceTreeNode[]) => {
    for (const node of nodes) {
      if (isActive(recordForNode(node))) result.push(node.id);
      if (node.children?.length) walk(node.children);
    }
  };
  walk(selectableForest.value);
  return result;
});

let presetToken = 0;
async function applyTreePreset() {
  const token = ++presetToken;
  await nextTick();
  if (token !== presetToken) return;
  treeRef.value?.setCheckedKeys(checkedNodeIds.value);
}

const childSelectionSignature = computed(() =>
  childList.value
    .filter(child => child.grantSource === "MANUAL")
    .map(
      child => `${child.id}:${child.draftMark ?? "active"}:${groupKeyOf(child)}`
    )
    .sort()
    .join(";")
);

watch(
  [
    selectedParentKey,
    selectedResourceType,
    selectedOpKey,
    childSelectionSignature
  ],
  () => void applyTreePreset(),
  { immediate: true, flush: "post" }
);

watch(
  selectedResourceType,
  (typeCode, previousTypeCode) => {
    if (typeCode !== previousTypeCode) selectedOpKey.value = null;
  },
  { flush: "sync" }
);

watch(
  selectableTypes,
  types => {
    if (
      selectedResourceType.value != null &&
      !types.includes(selectedResourceType.value)
    ) {
      selectedResourceType.value = null;
    }
  },
  { immediate: true }
);

function handleNodeCheck(
  node: ResourceTreeNode,
  state: { checkedKeys: Array<string | number> }
) {
  toggleRecord(
    childKey({
      resourceTypeCode: node.resourceTypeCode,
      resourceCode: node.code,
      codeType: node.codeType,
      scopeMode: "INSTANCE"
    }),
    node.name,
    state.checkedKeys.includes(node.id)
  );
}

function nodeState(node: ResourceTreeNode): {
  text: string;
  className: string;
} | null {
  const record = recordForNode(node);
  if (!record) return null;
  if (record.draftMark === "remove") {
    return { text: "待撤销", className: "pending-remove" };
  }
  if (record.draftMark === "add") {
    return { text: "待授权", className: "pending-add" };
  }
  // 当前子权限入口不产生 update；保留共享 EffectiveRecord 联合类型的防御性展示。
  if (record.draftMark === "update") {
    return { text: "待更新", className: "pending-update" };
  }
  return { text: "已授权", className: "existing" };
}

const resourceHint = computed(() => {
  if (!selectedResourceType.value) return "选择资源类型后展示可配置资源";
  if (!selectedOp.value) return "选择操作权限后可调整授权";
  if (allScopeSelected.value) return "已选择当前类型的全部资源";
  return "勾选表示授权，取消勾选表示撤销";
});

const emptyResourceDescription = computed(() =>
  selectedResourceType.value
    ? "当前资源类型暂无可配置资源"
    : "选择资源类型后展示资源树"
);
</script>

<template>
  <section class="child-configurator">
    <header class="config-header">
      <button type="button" class="back-button" @click="emit('back')">
        <span aria-hidden="true">←</span> 修改主权限
      </button>
      <div class="header-copy">
        <strong>配置子权限</strong>
        <span>沿用主权限的资源树选择方式，随所选主权限一起保存</span>
      </div>
    </header>

    <div v-if="parents.length === 0" class="empty-parent">
      当前没有可挂载子权限的主权限，请返回并至少选择一项授权。
    </div>

    <template v-else>
      <div class="parent-context">
        <span class="parent-node">主</span>
        <div class="parent-copy">
          <span class="context-label">挂载到主权限</span>
          <el-select
            v-model="selectedParentKey"
            size="small"
            class="parent-select"
          >
            <el-option
              v-for="parent in parents"
              :key="parentKeyOf(parent)"
              :value="parentKeyOf(parent)"
              :label="parentLabel(parent)"
            />
          </el-select>
        </div>
        <span class="child-count">{{ activeChildCount }} 项</span>
      </div>

      <div class="child-editor">
        <div class="editor-heading">
          <span class="child-node" aria-hidden="true">子</span>
          <div>
            <strong>子权限</strong>
            <span>仅配置资源与操作，不设置生效条件或再授予</span>
          </div>
        </div>

        <div class="selection-fields">
          <div class="selection-field">
            <span class="field-label">资源类型</span>
            <el-select
              v-model="selectedResourceType"
              clearable
              filterable
              class="type-select"
              placeholder="选择资源类型"
            >
              <el-option
                v-for="typeCode in selectableTypes"
                :key="typeCode"
                :label="typeCode"
                :value="typeCode"
              />
            </el-select>
          </div>

          <div class="selection-field">
            <span class="field-label">操作权限</span>
            <el-select
              v-model="selectedOpKey"
              clearable
              filterable
              class="op-select"
              placeholder="选择操作权限"
              :disabled="!selectedResourceType"
            >
              <el-option
                v-for="option in operationOptions"
                :key="option.key"
                :label="`${option.name}（${option.code}）`"
                :value="option.key"
              />
            </el-select>
          </div>
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
                :disabled="!selectedOp || !effectiveAllType"
                class="all-scope-checkbox"
              >
                全量
              </el-checkbox>
            </div>
          </div>

          <div class="scope-tree" :class="{ disabled: treeDisabled }">
            <el-empty
              v-if="selectableForest.length === 0"
              :description="emptyResourceDescription"
              :image-size="48"
            />
            <el-tree
              v-else
              :key="treeRenderKey"
              ref="treeRef"
              :data="selectableForest"
              :props="treeProps"
              show-checkbox
              check-strictly
              check-on-click-node
              node-key="id"
              :expand-on-click-node="false"
              :default-expanded-keys="defaultExpandedKeys"
              @check="handleNodeCheck"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span class="node-label">
                    {{ data.name
                    }}<span class="node-code">{{ data.code }}</span>
                  </span>
                  <span
                    v-if="nodeState(data)"
                    class="node-state"
                    :class="nodeState(data)!.className"
                  >
                    {{ nodeState(data)!.text }}
                  </span>
                </span>
              </template>
            </el-tree>
            <div v-if="selectedOp && !allScopeSelected" class="tree-hint">
              父节点授权会自动覆盖其子孙节点；资源类型仍受 SUB_PERM 配置约束。
            </div>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style lang="scss" scoped>
.child-configurator {
  min-height: 420px;
}

.config-header {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.back-button {
  padding: 0;
  font: inherit;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.header-copy {
  display: flex;
  gap: var(--space-2);
  align-items: baseline;

  strong {
    font-size: 14px;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.empty-parent {
  padding: var(--space-5);
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: var(--radius-md);
}

.parent-context {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--radius-md);
}

.parent-node,
.child-node {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border: 2px solid var(--el-color-primary-light-3);
  border-radius: var(--radius-full);
}

.parent-copy {
  display: flex;
  flex: 1;
  gap: var(--space-3);
  align-items: center;
  min-width: 0;
}

.context-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.parent-select {
  flex: 1;
  min-width: 0;
}

.child-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-top: var(--space-4);
}

.editor-heading {
  display: flex;
  gap: var(--space-2);
  align-items: center;

  > div {
    display: flex;
    gap: var(--space-2);
    align-items: baseline;
  }

  strong {
    font-size: 13px;
  }

  span:not(.child-node) {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.child-node {
  width: 26px;
  height: 26px;
  font-size: 11px;
  background: var(--el-color-primary-light-9);
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.selection-fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.selection-field {
  display: flex;
  gap: var(--space-4);
  align-items: center;

  .field-label {
    flex-shrink: 0;
    width: 64px;
  }

  .type-select,
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

  .resource-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
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

    &.pending-update {
      color: var(--el-color-warning-dark-2);
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

@media (width <= 640px) {
  .config-header,
  .parent-copy,
  .selection-field,
  .resource-header {
    align-items: stretch;
  }

  .config-header,
  .parent-copy,
  .selection-field,
  .resource-header,
  .resource-actions {
    flex-direction: column;
  }

  .selection-field .field-label,
  .selection-field .type-select,
  .selection-field .op-select {
    width: 100%;
  }
}
</style>
