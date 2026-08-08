<script setup lang="ts">
/**
 * 授权弹窗内的子权限配置器。
 * 父权限来自弹窗已暂存的主权限结果；组件只负责表单与交互，草稿变更由 GrantDialog 统一编排。
 */
import { computed, ref, watch } from "vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { ConditionResp } from "@/api/permission-condition";
import type { GrantRecordKey } from "@/api/permission-grant";
import {
  findDirectGrantConflict,
  type EffectiveRecord
} from "../utils/grant-plan";
import {
  mergeOperationsForType,
  type OperationDefInput
} from "../utils/source-chain";
import ConditionPicker from "./ConditionPicker.vue";

const props = defineProps<{
  parents: EffectiveRecord[];
  childrenProvider: (record: EffectiveRecord) => EffectiveRecord[];
  conditions: ConditionResp[];
  operations: OperationDefInput[];
  resourceForest: ResourceTreeNode[];
  canCondition: boolean;
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
  (
    e: "update",
    input: {
      record: EffectiveRecord;
      canGrant: boolean;
      conditionCode: string | null;
    }
  ): void;
  (
    e: "replace",
    input: {
      record: EffectiveRecord;
      newKey: GrantRecordKey;
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

function conditionName(code: string | null): string {
  if (!code) return "无条件";
  const hit = props.conditions.find(condition => condition.code === code);
  return hit ? hit.name : code;
}

function parentLabel(parent: EffectiveRecord): string {
  const resource =
    parent.resourceName ??
    parent.resourceCode ??
    `全部资源（${parent.resourceTypeCode}）`;
  return `${resource} · ${parent.operationCode ?? "组合位"} · ${conditionName(parent.conditionCode)}`;
}

const resourceTypes = computed(() => {
  const types = new Set<string>();
  for (const root of props.resourceForest) types.add(root.resourceTypeCode);
  return [...types].sort();
});

const nodesById = computed(() => {
  const map = new Map<number, ResourceTreeNode>();
  const walk = (nodes: ResourceTreeNode[]) => {
    for (const node of nodes) {
      map.set(node.id, node);
      if (node.children?.length) walk(node.children);
    }
  };
  walk(props.resourceForest);
  return map;
});

const form = ref<{
  resourceTypeCode: string | null;
  scopeMode: "INSTANCE" | "ALL";
  resourceNodeId: number | null;
  operationCode: string | null;
  conditionCode: string | null;
  canGrant: boolean;
}>({
  resourceTypeCode: null,
  scopeMode: "INSTANCE",
  resourceNodeId: null,
  operationCode: null,
  conditionCode: null,
  canGrant: false
});

const editingRecord = ref<EffectiveRecord | null>(null);
const formVisible = ref(false);

const typeNodes = computed(() => {
  const typeCode = form.value.resourceTypeCode;
  if (!typeCode) return [];
  return props.resourceForest.filter(
    root => root.resourceTypeCode === typeCode
  );
});

const resourceOptions = computed(() => {
  const options: Array<{ id: number; label: string }> = [];
  const walk = (nodes: ResourceTreeNode[], depth: number) => {
    for (const node of nodes) {
      options.push({
        id: node.id,
        label: `${"　".repeat(depth)}${node.name}（${node.code}）`
      });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  };
  walk(typeNodes.value, 0);
  return options;
});

const operationOptions = computed(() => {
  const typeCode = form.value.resourceTypeCode;
  return typeCode ? mergeOperationsForType(props.operations, typeCode) : [];
});

watch(
  () => form.value.conditionCode,
  conditionCode => {
    if (conditionCode) form.value.canGrant = false;
  }
);

function nodeIdOf(record: EffectiveRecord): number | null {
  for (const node of nodesById.value.values()) {
    if (
      node.resourceTypeCode === record.resourceTypeCode &&
      node.code === record.resourceCode &&
      node.codeType === record.codeType
    ) {
      return node.id;
    }
  }
  return null;
}

function resetForm(record?: EffectiveRecord) {
  form.value = {
    resourceTypeCode: record?.resourceTypeCode ?? null,
    scopeMode: record?.scopeMode ?? "INSTANCE",
    resourceNodeId: record ? nodeIdOf(record) : null,
    operationCode: record?.operationCode ?? null,
    conditionCode: record?.conditionCode ?? null,
    canGrant: record?.canGrant ?? false
  };
}

function startAdd() {
  editingRecord.value = null;
  resetForm();
  formVisible.value = true;
}

function startEdit(record: EffectiveRecord) {
  editingRecord.value = record;
  resetForm(record);
  formVisible.value = true;
}

function cancelForm() {
  formVisible.value = false;
  editingRecord.value = null;
  resetForm();
}

function handleTypeChange() {
  form.value.resourceNodeId = null;
  form.value.operationCode = null;
}

const formKey = computed<GrantRecordKey | null>(() => {
  const value = form.value;
  if (!value.resourceTypeCode || !value.operationCode) return null;
  const node =
    value.scopeMode === "INSTANCE" && value.resourceNodeId != null
      ? nodesById.value.get(value.resourceNodeId)
      : null;
  if (value.scopeMode === "INSTANCE" && !node) return null;
  return {
    resourceTypeCode: value.resourceTypeCode,
    resourceCode: node?.code ?? null,
    codeType: node?.codeType ?? null,
    operationCode: value.operationCode,
    scopeMode: value.scopeMode,
    conditionCode: value.conditionCode,
    canGrant: value.conditionCode == null ? value.canGrant : false
  };
});

const formConflict = computed(() => {
  const key = formKey.value;
  if (!key) return false;
  const hit = findDirectGrantConflict(childList.value, key);
  return hit != null && hit.id !== editingRecord.value?.id;
});

function resourceLabelOf(key: GrantRecordKey): string {
  if (key.scopeMode === "ALL") {
    return `全部资源（${key.resourceTypeCode}）`;
  }
  const node =
    form.value.resourceNodeId != null
      ? nodesById.value.get(form.value.resourceNodeId)
      : null;
  return node?.name ?? key.resourceCode ?? "资源";
}

function confirmForm() {
  const key = formKey.value;
  const parent = selectedParent.value;
  if (!key || !parent || formConflict.value) return;
  const resourceLabel = resourceLabelOf(key);
  const record = editingRecord.value;
  if (!record) {
    emit("add", { parent, recordKey: key, resourceLabel });
  } else {
    const sameStructuralKey =
      key.resourceTypeCode === record.resourceTypeCode &&
      key.resourceCode === record.resourceCode &&
      key.codeType === record.codeType &&
      key.operationCode === record.operationCode &&
      key.scopeMode === record.scopeMode;
    if (sameStructuralKey) {
      emit("update", {
        record,
        canGrant: key.canGrant ?? false,
        conditionCode: key.conditionCode
      });
    } else {
      emit("replace", { record, newKey: key, resourceLabel });
    }
  }
  cancelForm();
}

function childResourceLabel(record: EffectiveRecord): string {
  if (record.scopeMode === "ALL") {
    return `全部资源（${record.resourceTypeCode}）`;
  }
  const node = nodeIdOf(record);
  return (
    (node != null ? nodesById.value.get(node)?.name : null) ??
    record.resourceName ??
    record.resourceCode ??
    "资源"
  );
}

function draftLabel(record: EffectiveRecord): string | null {
  if (record.draftMark === "add") return "待新增";
  if (record.draftMark === "update") return "待更新";
  if (record.draftMark === "remove") return "待撤销";
  return null;
}
</script>

<template>
  <section class="child-configurator">
    <header class="config-header">
      <button type="button" class="back-button" @click="emit('back')">
        <span aria-hidden="true">←</span> 修改主权限
      </button>
      <div class="header-copy">
        <strong>配置子权限</strong>
        <span>子权限随所选主权限一起保存</span>
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
        <span class="child-count">{{ childList.length }} 项</span>
      </div>

      <div class="child-panel">
        <div class="child-rail" aria-hidden="true">
          <span class="rail-line" />
          <span class="child-node">子</span>
        </div>
        <div class="child-content">
          <div class="list-heading">
            <div>
              <strong>子权限</strong>
              <span>资源类型受 SUB_PERM 配置约束</span>
            </div>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="formVisible"
              @click="startAdd"
            >
              添加子权限
            </el-button>
          </div>

          <div v-if="formVisible" class="child-form">
            <div class="form-heading">
              {{ editingRecord ? "编辑子权限" : "添加子权限" }}
            </div>
            <div class="form-grid">
              <label class="form-field">
                <span>资源类型</span>
                <el-select
                  v-model="form.resourceTypeCode"
                  size="small"
                  placeholder="选择资源类型"
                  @change="handleTypeChange"
                >
                  <el-option
                    v-for="typeCode in resourceTypes"
                    :key="typeCode"
                    :value="typeCode"
                    :label="typeCode"
                  />
                </el-select>
              </label>

              <label class="form-field">
                <span>操作权限</span>
                <el-select
                  v-model="form.operationCode"
                  size="small"
                  placeholder="选择操作权限"
                  :disabled="!form.resourceTypeCode"
                >
                  <el-option
                    v-for="operation in operationOptions"
                    :key="operation.code"
                    :value="operation.code"
                    :label="`${operation.name}（${operation.code}）`"
                  />
                </el-select>
              </label>

              <label class="form-field scope-field">
                <span>授权范围</span>
                <el-radio-group v-model="form.scopeMode" size="small">
                  <el-radio-button value="INSTANCE">实例</el-radio-button>
                  <el-radio-button value="ALL">全量</el-radio-button>
                </el-radio-group>
              </label>

              <label v-if="form.scopeMode === 'INSTANCE'" class="form-field">
                <span>资源实例</span>
                <el-select
                  v-model="form.resourceNodeId"
                  size="small"
                  filterable
                  placeholder="选择资源实例"
                  :disabled="!form.resourceTypeCode"
                >
                  <el-option
                    v-for="option in resourceOptions"
                    :key="option.id"
                    :value="option.id"
                    :label="option.label"
                  />
                </el-select>
              </label>

              <label class="form-field">
                <span>生效条件</span>
                <ConditionPicker
                  v-model="form.conditionCode"
                  :conditions="conditions"
                  :disabled="!canCondition"
                  placeholder="无条件"
                />
              </label>

              <label class="form-field delegation-field">
                <span>转授</span>
                <el-tooltip
                  :disabled="form.conditionCode == null"
                  content="条件权限不可转授，需先清除条件"
                  placement="top"
                >
                  <span>
                    <el-checkbox
                      v-model="form.canGrant"
                      :disabled="form.conditionCode != null"
                    >
                      允许再授予
                    </el-checkbox>
                  </span>
                </el-tooltip>
              </label>
            </div>
            <div v-if="formConflict" class="form-error">
              当前主权限下已存在相同资源、操作和范围的子权限，可直接编辑已有记录。
            </div>
            <div class="form-actions">
              <el-button size="small" @click="cancelForm">取消</el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="!formKey || formConflict"
                @click="confirmForm"
              >
                应用
              </el-button>
            </div>
          </div>

          <div class="children-list">
            <div v-if="childList.length === 0" class="children-empty">
              暂无子权限，可按需添加。
            </div>
            <article
              v-for="child in childList"
              :key="child.id"
              class="child-item"
              :class="{ removed: child.draftMark === 'remove' }"
            >
              <div class="child-main">
                <div class="child-title">
                  <span>{{ childResourceLabel(child) }}</span>
                  <el-tag size="small" effect="plain">
                    {{ child.resourceTypeCode }}
                  </el-tag>
                  <el-tag
                    v-if="child.grantSource === 'AUTO_DEP'"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    自动补全
                  </el-tag>
                  <el-tag
                    v-if="draftLabel(child)"
                    size="small"
                    effect="plain"
                    :type="
                      child.draftMark === 'remove'
                        ? 'danger'
                        : child.draftMark === 'update'
                          ? 'warning'
                          : 'success'
                    "
                  >
                    {{ draftLabel(child) }}
                  </el-tag>
                </div>
                <div class="child-meta">
                  <span>{{ child.operationCode ?? "组合位" }}</span>
                  <span>{{ child.scopeMode === "ALL" ? "全量" : "实例" }}</span>
                  <span>条件：{{ conditionName(child.conditionCode) }}</span>
                  <span>可转授：{{ child.canGrant ? "是" : "否" }}</span>
                </div>
              </div>
              <div v-if="child.grantSource === 'MANUAL'" class="child-actions">
                <el-button
                  v-if="child.draftMark === 'remove'"
                  size="small"
                  text
                  type="primary"
                  @click="emit('restore', child)"
                >
                  恢复
                </el-button>
                <template v-else>
                  <el-button size="small" text @click="startEdit(child)">
                    编辑
                  </el-button>
                  <el-button
                    size="small"
                    text
                    type="danger"
                    @click="emit('remove', child)"
                  >
                    撤销
                  </el-button>
                </template>
              </div>
              <span v-else class="readonly-label">只读</span>
            </article>
          </div>
        </div>
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

.child-panel {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  margin-top: var(--space-2);
}

.child-rail {
  position: relative;
  min-height: 310px;

  .rail-line {
    position: absolute;
    top: 0;
    bottom: 18px;
    left: 26px;
    width: 1px;
    background: var(--el-color-primary-light-5);
  }

  .child-node {
    position: absolute;
    top: 18px;
    left: 12px;
    width: 26px;
    height: 26px;
    font-size: 11px;
    background: var(--el-color-primary-light-9);
  }
}

.child-content {
  min-width: 0;
  padding-top: var(--space-3);
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);

  div {
    display: flex;
    gap: var(--space-2);
    align-items: baseline;
  }

  strong {
    font-size: 13px;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.child-form {
  padding: var(--space-3);
  margin-bottom: var(--space-3);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
}

.form-heading {
  margin-bottom: var(--space-3);
  font-size: 13px;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;

  > span:first-child {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  :deep(.el-select),
  :deep(.condition-trigger) {
    width: 100%;
  }
}

.delegation-field {
  justify-content: flex-end;
}

.form-error {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--el-color-danger);
}

.form-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.children-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-height: 230px;
  overflow: auto;
}

.children-empty {
  padding: var(--space-5);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  border: 1px dashed var(--el-border-color);
  border-radius: var(--radius-md);
}

.child-item {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);

  &.removed {
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-7);
  }
}

.child-main {
  flex: 1;
  min-width: 0;
}

.child-title,
.child-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.child-title {
  font-size: 13px;
  font-weight: 500;
}

.child-meta {
  margin-top: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
}

.readonly-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
