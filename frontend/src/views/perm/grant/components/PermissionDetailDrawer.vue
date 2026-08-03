<script setup lang="ts">
/**
 * 权限详情层（§5：多分支 / 子权限 / 删除；右侧抽屉）。
 * Tab1 条件分支：同键多条件并存列表（MANUAL 可编辑/删除；AUTO_DEP 只读并列展示）；
 *   添加分支 / 改条件 = 重新绑定 conditionCode（选已有条件覆盖，不进入规则编辑）；
 *   撞已占用条件 → 前端禁用（完整键同键同 conditionCode 已有 MANUAL 分支）。
 * Tab2 子权限（depend_on）：统一走草稿（creates parentPermissionId 挂父 / updates 微变更 /
 *   removes 删除 / 跨键 = removes+creates）；草稿新父虚拟挂载（children 一次性建树）。
 *   SUB_PERM 允许集由后端校验（20011 提示）。
 */
import { computed, ref, watch } from "vue";
import { message } from "@/utils/message";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { ConditionResp } from "@/api/permission-condition";
import type { GrantRecordKey } from "@/api/permission-grant";
import { summarizeRules } from "@/utils/condition-rules";
import { findBranchConflict, type EffectiveRecord } from "../utils/grant-plan";
import {
  mergeOperationsForType,
  type OperationDefInput
} from "../utils/source-chain";
import ConditionPicker from "./ConditionPicker.vue";

const props = defineProps<{
  modelValue: boolean;
  /** 详情目标（单元格：资源 × 操作列） */
  target: {
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    resourceName: string;
    operationCode: string;
    scopeMode: "INSTANCE" | "ALL";
  } | null;
  /** 目标分组键下全部生效记录（MANUAL + AUTO_DEP 并列） */
  records: EffectiveRecord[];
  /** 子权限（按父记录解析，含草稿虚拟挂载） */
  childrenProvider: (record: EffectiveRecord) => EffectiveRecord[];
  conditions: ConditionResp[];
  operations: OperationDefInput[];
  resourceForest: ResourceTreeNode[];
  canManage: boolean;
  canCondition: boolean;
  /** 组合位未定义位（记录 id → 位串，详情层"未定义位"展示） */
  undefinedBitsByRecord: Map<number, string>;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (
    e: "addBranch",
    input: {
      record: EffectiveRecord;
      conditionCode: string | null;
      canGrant: boolean;
    }
  ): void;
  (
    e: "updateBranch",
    input: {
      record: EffectiveRecord;
      canGrant: boolean;
      conditionCode: string | null;
    }
  ): void;
  (e: "deleteRecord", record: EffectiveRecord): void;
  (
    e: "addChild",
    input: {
      parent: EffectiveRecord;
      recordKey: GrantRecordKey;
      resourceLabel: string;
    }
  ): void;
  (
    e: "replaceChild",
    input: {
      record: EffectiveRecord;
      newKey: GrantRecordKey;
      resourceLabel: string;
    }
  ): void;
}>();

const activeTab = ref("branches");

// ========== Tab1 条件分支 ==========

const manualRecords = computed(() =>
  props.records.filter(r => r.grantSource === "MANUAL")
);

const autoDepRecords = computed(() =>
  props.records.filter(r => r.grantSource === "AUTO_DEP")
);

function conditionName(code: string | null): string {
  if (!code) return "无条件";
  const hit = props.conditions.find(c => c.code === code);
  return hit ? `${hit.name}（${code}）` : code;
}

function conditionSummary(code: string | null): string {
  if (!code) return "不附加条件，始终生效";
  const hit = props.conditions.find(c => c.code === code);
  return hit ? summarizeRules(hit.conditionRules) : "";
}

function draftTagOf(record: EffectiveRecord): string | null {
  if (record.draftMark === "add") return "新增（未保存）";
  if (record.draftMark === "update") return "已修改（未保存）";
  if (record.draftMark === "remove") return "待删除（未保存）";
  return null;
}

// ---- 分支编辑（改条件 = 重新绑定 conditionCode + canGrant） ----

const editingBranchId = ref<number | null>(null);
const editConditionCode = ref<string | null>(null);
const editCanGrant = ref(false);

function startEditBranch(record: EffectiveRecord) {
  editingBranchId.value = record.id;
  editConditionCode.value = record.conditionCode;
  editCanGrant.value = record.canGrant;
}

function cancelEditBranch() {
  editingBranchId.value = null;
}

/** 改条件撞已占用条件 → 前端禁用（S4；完整键同键同 conditionCode 已有 MANUAL 分支） */
const editConflict = computed(() => {
  const target = props.target;
  const editing = props.records.find(r => r.id === editingBranchId.value);
  if (!target || !editing) return false;
  const key: GrantRecordKey = {
    resourceTypeCode: target.resourceTypeCode,
    resourceCode: target.resourceCode,
    codeType: target.codeType,
    operationCode: target.operationCode,
    scopeMode: target.scopeMode,
    conditionCode: null,
    canGrant: false
  };
  const hit = findBranchConflict(props.records, key, editConditionCode.value);
  return hit != null && hit.id !== editing.id;
});

function confirmEditBranch(record: EffectiveRecord) {
  if (editConflict.value) return;
  emit("updateBranch", {
    record,
    canGrant: editCanGrant.value,
    conditionCode: editConditionCode.value
  });
  editingBranchId.value = null;
}

// ---- 添加分支（同键多条件并存） ----

const addingBranch = ref(false);
const addConditionCode = ref<string | null>(null);
const addCanGrant = ref(false);

const addConflict = computed(() => {
  const target = props.target;
  if (!target || !addingBranch.value) return false;
  const key: GrantRecordKey = {
    resourceTypeCode: target.resourceTypeCode,
    resourceCode: target.resourceCode,
    codeType: target.codeType,
    operationCode: target.operationCode,
    scopeMode: target.scopeMode,
    conditionCode: null,
    canGrant: false
  };
  return findBranchConflict(props.records, key, addConditionCode.value) != null;
});

function confirmAddBranch() {
  const target = props.target;
  const anchor = manualRecords.value[0] ?? props.records[0];
  if (!target || !anchor || addConflict.value) return;
  emit("addBranch", {
    record: anchor,
    conditionCode: addConditionCode.value,
    canGrant: addCanGrant.value
  });
  addingBranch.value = false;
  addConditionCode.value = null;
  addCanGrant.value = false;
}

// ========== Tab2 子权限 ==========

/** 子权限父选择（多分支时选定挂载父；默认首个 MANUAL 主权限） */
const childParentId = ref<number | null>(null);

const selectableParents = computed(() => manualRecords.value);

const childParent = computed(
  () =>
    selectableParents.value.find(r => r.id === childParentId.value) ??
    selectableParents.value[0] ??
    null
);

watch(
  () => [props.modelValue, props.records.length],
  () => {
    if (props.modelValue) {
      childParentId.value = selectableParents.value[0]?.id ?? null;
      activeTab.value = "branches";
      addingBranch.value = false;
      editingBranchId.value = null;
      addingChild.value = false;
      editingChildId.value = null;
    }
  }
);

const childList = computed(() =>
  childParent.value ? props.childrenProvider(childParent.value) : []
);

// ---- 子权限表单（添加 / 跨键编辑共用） ----

const addingChild = ref(false);
const editingChildId = ref<number | null>(null);
const childForm = ref<{
  resourceTypeCode: string | null;
  scopeMode: "INSTANCE" | "ALL";
  resourceKey: string | null; // `${code}:${codeType}`
  operationCode: string | null;
  conditionCode: string | null;
  canGrant: boolean;
}>({
  resourceTypeCode: null,
  scopeMode: "INSTANCE",
  resourceKey: null,
  operationCode: null,
  conditionCode: null,
  canGrant: false
});

const childFormTypes = computed(() => {
  const types = new Set<string>();
  for (const root of props.resourceForest) types.add(root.resourceTypeCode);
  return [...types].sort();
});

const childFormTypeTree = computed(() => {
  const typeCode = childForm.value.resourceTypeCode;
  if (!typeCode) return [];
  return props.resourceForest.filter(r => r.resourceTypeCode === typeCode);
});

const childFormTypeOptions = computed(() => {
  const list: Array<{ key: string; label: string }> = [];
  const walk = (nodes: ResourceTreeNode[], depth: number) => {
    for (const node of nodes) {
      list.push({
        key: `${node.code}:${node.codeType}`,
        label: `${"　".repeat(depth)}${node.name}（${node.code}）`
      });
      if (node.children?.length) walk(node.children, depth + 1);
    }
  };
  walk(childFormTypeTree.value, 0);
  return list;
});

/** 子权限操作集（所选类型的合并列：专属优先、全局回退） */
const childFormOps = computed(() => {
  const typeCode = childForm.value.resourceTypeCode;
  if (!typeCode) return [];
  return mergeOperationsForType(props.operations, typeCode);
});

function resetChildForm(record?: EffectiveRecord) {
  childForm.value = {
    resourceTypeCode: record?.resourceTypeCode ?? null,
    scopeMode: record?.scopeMode ?? "INSTANCE",
    resourceKey:
      record?.resourceCode && record?.codeType
        ? `${record.resourceCode}:${record.codeType}`
        : null,
    operationCode: record?.operationCode ?? null,
    conditionCode: record?.conditionCode ?? null,
    canGrant: record?.canGrant ?? false
  };
}

function startAddChild() {
  resetChildForm();
  addingChild.value = true;
  editingChildId.value = null;
}

function startReplaceChild(record: EffectiveRecord) {
  resetChildForm(record);
  editingChildId.value = record.id;
  addingChild.value = false;
}

const childFormValid = computed(() => {
  const form = childForm.value;
  if (!form.resourceTypeCode || !form.operationCode) return false;
  if (form.scopeMode === "INSTANCE" && !form.resourceKey) return false;
  return true;
});

function childFormToKey(): GrantRecordKey {
  const form = childForm.value;
  // resourceKey = `${code}:${codeType}`，code 可能含冒号 → 按最后一个冒号切分
  let resourceCode: string | null = null;
  let codeType: string | null = null;
  if (form.scopeMode === "INSTANCE" && form.resourceKey) {
    const idx = form.resourceKey.lastIndexOf(":");
    if (idx > 0) {
      resourceCode = form.resourceKey.slice(0, idx);
      codeType = form.resourceKey.slice(idx + 1);
    }
  }
  return {
    resourceTypeCode: form.resourceTypeCode!,
    resourceCode,
    codeType,
    operationCode: form.operationCode,
    scopeMode: form.scopeMode,
    conditionCode: form.conditionCode,
    canGrant: form.canGrant
  };
}

function childFormResourceLabel(): string {
  const form = childForm.value;
  if (form.scopeMode === "ALL") return `全部资源（${form.resourceTypeCode}）`;
  const hit = childFormTypeOptions.value.find(o => o.key === form.resourceKey);
  return hit?.label.trim() ?? form.resourceKey ?? "";
}

function confirmChildForm() {
  if (!childFormValid.value) return;
  const key = childFormToKey();
  const label = childFormResourceLabel();
  if (addingChild.value) {
    if (!childParent.value) return;
    emit("addChild", {
      parent: childParent.value,
      recordKey: key,
      resourceLabel: label
    });
    addingChild.value = false;
  } else if (editingChildId.value != null) {
    const record = childList.value.find(c => c.id === editingChildId.value);
    if (!record) return;
    // 跨键未变化（仅条件/canGrant 差异）→ 走 updates 微变更而非替换
    if (
      key.resourceTypeCode === record.resourceTypeCode &&
      key.resourceCode === record.resourceCode &&
      key.codeType === record.codeType &&
      key.operationCode === record.operationCode &&
      key.scopeMode === record.scopeMode
    ) {
      emit("updateBranch", {
        record,
        canGrant: key.canGrant ?? false,
        conditionCode: key.conditionCode
      });
    } else {
      emit("replaceChild", { record, newKey: key, resourceLabel: label });
    }
    editingChildId.value = null;
  }
  resetChildForm();
}

// ---- 子权限行内微编辑（条件/canGrant） ----

const microEditChildId = ref<number | null>(null);
const microConditionCode = ref<string | null>(null);
const microCanGrant = ref(false);

function startMicroEdit(record: EffectiveRecord) {
  microEditChildId.value = record.id;
  microConditionCode.value = record.conditionCode;
  microCanGrant.value = record.canGrant;
}

function confirmMicroEdit(record: EffectiveRecord) {
  emit("updateBranch", {
    record,
    canGrant: microCanGrant.value,
    conditionCode: microConditionCode.value
  });
  microEditChildId.value = null;
}

function handleDelete(record: EffectiveRecord) {
  if (record.grantSource === "AUTO_DEP") {
    message("自动补全记录只读，不可修改或删除", { type: "warning" });
    return;
  }
  emit("deleteRecord", record);
}

function handleClose() {
  emit("update:modelValue", false);
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="
      target
        ? `权限详情：${target.resourceName} · ${target.operationCode}`
        : '权限详情'
    "
    size="480px"
    @update:model-value="handleClose"
  >
    <template v-if="target">
      <!-- 主权限摘要（只读） -->
      <div class="main-summary">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="资源">
            {{ target.resourceName }}
          </el-descriptions-item>
          <el-descriptions-item label="操作">
            {{ target.operationCode }}
          </el-descriptions-item>
          <el-descriptions-item label="范围">
            {{ target.scopeMode === "ALL" ? "全量（ALL）" : "实例" }}
          </el-descriptions-item>
          <el-descriptions-item label="资源编码">
            {{ target.resourceCode ?? "—" }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 纯继承单元格（无直接记录） -->
      <el-alert
        v-if="records.length === 0"
        type="info"
        :closable="false"
        title="本单元格权限来自继承，无直接授权记录"
        description="请在来源资源行（父资源 / 操作覆盖来源）的对应单元格管理该授权。"
      />

      <el-tabs v-else v-model="activeTab">
        <!-- Tab1 条件分支 -->
        <el-tab-pane label="条件分支" name="branches">
          <div class="branch-list">
            <div
              v-for="record in records"
              :key="record.id"
              class="branch-item"
              :class="{
                'auto-dep': record.grantSource === 'AUTO_DEP',
                removed: record.draftMark === 'remove'
              }"
            >
              <template v-if="editingBranchId === record.id">
                <div class="branch-edit">
                  <ConditionPicker
                    v-model="editConditionCode"
                    :conditions="conditions"
                    :disabled="!canCondition"
                    placeholder="无条件"
                  />
                  <el-checkbox v-model="editCanGrant">可转授</el-checkbox>
                  <div class="edit-actions">
                    <el-button size="small" @click="cancelEditBranch"
                      >取消</el-button
                    >
                    <el-tooltip
                      :disabled="!editConflict"
                      content="该条件已被同键分支占用（完整键冲突）"
                      placement="top"
                    >
                      <span>
                        <el-button
                          size="small"
                          type="primary"
                          :disabled="editConflict"
                          @click="confirmEditBranch(record)"
                        >
                          确定
                        </el-button>
                      </span>
                    </el-tooltip>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="branch-head">
                  <span class="branch-condition">
                    {{ conditionName(record.conditionCode) }}
                    <el-tag
                      v-if="record.grantSource === 'AUTO_DEP'"
                      size="small"
                      type="info"
                      effect="plain"
                    >
                      自动补全
                    </el-tag>
                    <el-tag
                      v-if="record.operationCode == null"
                      size="small"
                      type="warning"
                      effect="plain"
                    >
                      组合位
                    </el-tag>
                    <el-tag
                      v-if="draftTagOf(record)"
                      size="small"
                      :type="
                        record.draftMark === 'remove'
                          ? 'danger'
                          : record.draftMark === 'add'
                            ? 'success'
                            : 'warning'
                      "
                      effect="plain"
                    >
                      {{ draftTagOf(record) }}
                    </el-tag>
                  </span>
                  <span class="branch-actions">
                    <template
                      v-if="
                        record.grantSource === 'MANUAL' &&
                        canManage &&
                        record.draftMark !== 'remove'
                      "
                    >
                      <el-button
                        size="small"
                        text
                        type="primary"
                        @click="startEditBranch(record)"
                      >
                        编辑
                      </el-button>
                      <el-button
                        size="small"
                        text
                        type="danger"
                        @click="handleDelete(record)"
                      >
                        删除
                      </el-button>
                    </template>
                    <el-tooltip
                      v-else-if="record.grantSource === 'AUTO_DEP'"
                      content="由资源依赖自动补全，只读不可编辑/删除"
                      placement="top"
                    >
                      <span class="readonly-text">只读</span>
                    </el-tooltip>
                  </span>
                </div>
                <div class="branch-meta">
                  <span>{{ conditionSummary(record.conditionCode) }}</span>
                  <span>可转授：{{ record.canGrant ? "是" : "否" }}</span>
                  <span v-if="record.childCount > 0"
                    >子权限：{{ record.childCount }}</span
                  >
                  <span v-if="record.createdAt"
                    >创建：{{ record.createdAt }}</span
                  >
                  <span v-if="undefinedBitsByRecord.get(record.id)">
                    未定义位：{{ undefinedBitsByRecord.get(record.id) }}
                  </span>
                </div>
              </template>
            </div>
          </div>

          <!-- 添加分支 -->
          <div v-if="canManage && manualRecords.length > 0" class="branch-add">
            <template v-if="addingBranch">
              <div class="branch-edit">
                <ConditionPicker
                  v-model="addConditionCode"
                  :conditions="conditions"
                  :disabled="!canCondition"
                  placeholder="无条件"
                />
                <el-checkbox v-model="addCanGrant">可转授</el-checkbox>
                <div class="edit-actions">
                  <el-button size="small" @click="addingBranch = false"
                    >取消</el-button
                  >
                  <el-tooltip
                    :disabled="!addConflict"
                    content="该条件已被同键分支占用（完整键冲突）"
                    placement="top"
                  >
                    <span>
                      <el-button
                        size="small"
                        type="primary"
                        :disabled="addConflict"
                        @click="confirmAddBranch"
                      >
                        确定
                      </el-button>
                    </span>
                  </el-tooltip>
                </div>
              </div>
            </template>
            <el-button
              v-else
              size="small"
              type="primary"
              plain
              @click="addingBranch = true"
            >
              添加分支
            </el-button>
          </div>
        </el-tab-pane>

        <!-- Tab2 子权限（depend_on） -->
        <el-tab-pane label="子权限" name="children">
          <div v-if="selectableParents.length === 0" class="children-empty">
            <el-alert
              type="info"
              :closable="false"
              title="无 MANUAL 主权限可挂载子权限"
              description="AUTO_DEP 记录只读，不能作为子权限父。"
            />
          </div>
          <template v-else>
            <div class="children-toolbar">
              <el-select
                v-model="childParentId"
                size="small"
                placeholder="选择挂载父（主权限分支）"
                class="parent-select"
              >
                <el-option
                  v-for="parent in selectableParents"
                  :key="parent.id"
                  :value="parent.id"
                  :label="`父：${conditionName(parent.conditionCode)}${parent.draftMark === 'add' ? '（新增未保存）' : ''}`"
                />
              </el-select>
              <el-button
                v-if="canManage"
                size="small"
                type="primary"
                plain
                @click="startAddChild"
              >
                添加子权限
              </el-button>
            </div>
            <div class="children-hint">
              子权限资源类型受 SUB_PERM 配置约束（后端校验，不允许 → 保存时提示
              20011）。
            </div>

            <!-- 子权限表单（添加 / 跨键编辑） -->
            <div
              v-if="addingChild || editingChildId != null"
              class="child-form"
            >
              <div class="form-title">
                {{
                  addingChild
                    ? "添加子权限"
                    : "编辑子权限（跨键变更 = 移除+新建）"
                }}
              </div>
              <el-form label-width="80px" size="small">
                <el-form-item label="资源类型">
                  <el-select
                    v-model="childForm.resourceTypeCode"
                    placeholder="选资源类型"
                    @change="
                      childForm.resourceKey = null;
                      childForm.operationCode = null;
                    "
                  >
                    <el-option
                      v-for="typeCode in childFormTypes"
                      :key="typeCode"
                      :value="typeCode"
                      :label="typeCode"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="范围">
                  <el-radio-group v-model="childForm.scopeMode">
                    <el-radio-button value="INSTANCE">实例</el-radio-button>
                    <el-radio-button value="ALL">全量</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item
                  v-if="childForm.scopeMode === 'INSTANCE'"
                  label="资源实例"
                >
                  <el-select
                    v-model="childForm.resourceKey"
                    placeholder="选资源实例"
                    filterable
                    :disabled="!childForm.resourceTypeCode"
                  >
                    <el-option
                      v-for="option in childFormTypeOptions"
                      :key="option.key"
                      :value="option.key"
                      :label="option.label"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="操作">
                  <el-select
                    v-model="childForm.operationCode"
                    placeholder="选操作"
                    :disabled="!childForm.resourceTypeCode"
                  >
                    <el-option
                      v-for="op in childFormOps"
                      :key="op.code"
                      :value="op.code"
                      :label="`${op.name}（${op.code}）${op.globalFallback ? ' · 全局' : ''}`"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="条件">
                  <ConditionPicker
                    v-model="childForm.conditionCode"
                    :conditions="conditions"
                    :disabled="!canCondition"
                    placeholder="无条件"
                  />
                </el-form-item>
                <el-form-item label="可转授">
                  <el-checkbox v-model="childForm.canGrant" />
                </el-form-item>
              </el-form>
              <div class="edit-actions">
                <el-button
                  size="small"
                  @click="
                    addingChild = false;
                    editingChildId = null;
                  "
                >
                  取消
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  :disabled="!childFormValid"
                  @click="confirmChildForm"
                >
                  确定
                </el-button>
              </div>
            </div>

            <!-- 子权限列表 -->
            <div class="children-list">
              <el-empty
                v-if="childList.length === 0"
                description="暂无子权限"
                :image-size="48"
              />
              <div
                v-for="child in childList"
                :key="child.id"
                class="child-item"
                :class="{ removed: child.draftMark === 'remove' }"
              >
                <template v-if="microEditChildId === child.id">
                  <div class="branch-edit">
                    <ConditionPicker
                      v-model="microConditionCode"
                      :conditions="conditions"
                      :disabled="!canCondition"
                      placeholder="无条件"
                    />
                    <el-checkbox v-model="microCanGrant">可转授</el-checkbox>
                    <div class="edit-actions">
                      <el-button size="small" @click="microEditChildId = null"
                        >取消</el-button
                      >
                      <el-button
                        size="small"
                        type="primary"
                        @click="confirmMicroEdit(child)"
                      >
                        确定
                      </el-button>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="child-head">
                    <span>
                      {{
                        child.resourceName ??
                        child.resourceCode ??
                        `全部资源（${child.resourceTypeCode}）`
                      }}
                      · {{ child.operationCode ?? "组合位" }} ·
                      {{ child.scopeMode === "ALL" ? "全量" : "实例" }}
                      <el-tag
                        v-if="draftTagOf(child)"
                        size="small"
                        :type="
                          child.draftMark === 'remove'
                            ? 'danger'
                            : child.draftMark === 'add'
                              ? 'success'
                              : 'warning'
                        "
                        effect="plain"
                      >
                        {{ draftTagOf(child) }}
                      </el-tag>
                    </span>
                    <span
                      v-if="canManage && child.draftMark !== 'remove'"
                      class="branch-actions"
                    >
                      <el-button
                        size="small"
                        text
                        type="primary"
                        @click="startMicroEdit(child)"
                      >
                        改条件/转授
                      </el-button>
                      <el-button
                        size="small"
                        text
                        type="primary"
                        @click="startReplaceChild(child)"
                      >
                        编辑
                      </el-button>
                      <el-button
                        size="small"
                        text
                        type="danger"
                        @click="handleDelete(child)"
                      >
                        删除
                      </el-button>
                    </span>
                  </div>
                  <div class="branch-meta">
                    <span>条件：{{ conditionName(child.conditionCode) }}</span>
                    <span>可转授：{{ child.canGrant ? "是" : "否" }}</span>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
.main-summary {
  margin-bottom: var(--space-3);
}

.branch-list,
.children-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.branch-item,
.child-item {
  padding: var(--space-2);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-sm);

  &.auto-dep {
    background: var(--el-fill-color-lighter);
    border-style: dashed;
  }

  &.removed {
    text-decoration: line-through;
    opacity: 0.5;
  }

  .branch-head,
  .child-head {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .branch-condition {
      display: inline-flex;
      gap: 6px;
      align-items: center;
    }

    .readonly-text {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .branch-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.branch-add {
  margin-top: var(--space-2);
}

.branch-edit {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);

  .edit-actions {
    display: flex;
    gap: var(--space-2);
    justify-content: flex-end;
  }
}

.children-toolbar {
  display: flex;
  gap: var(--space-2);
  align-items: center;

  .parent-select {
    flex: 1;
  }
}

.children-hint {
  margin: var(--space-2) 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-form {
  padding: var(--space-2);
  margin-bottom: var(--space-2);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-sm);

  .form-title {
    margin-bottom: var(--space-2);
    font-weight: 600;
  }

  .edit-actions {
    display: flex;
    gap: var(--space-2);
    justify-content: flex-end;
  }
}
</style>
