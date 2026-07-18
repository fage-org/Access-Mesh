<script setup lang="ts">
/**
 * T-FE-032 批量配置侧拉（设置条件 / 设置转授权统一面板）。
 *
 * 为选中每个单元格新增一条统一 conditionCode + canGrant 的 OR 分支（add）。
 * - 重复条件逐单元格阻断（同 cell+规范化 conditionCode 已存在 -> 跳过）
 * - R11 redundant 候选（被 ALL 覆盖+无直接记录）默认跳过；keepDirect=true 仍创建
 * - canGrant 归一化：supportsDelegation=false 时强制 false
 * - 修改既有分支须逐分支（非批量），本面板只新增
 *
 * preview 自算 add/redundant/skip（不依赖父传），用于警告文案 + 确认按钮 disabled。
 */
import { ref, computed, watch, inject } from "vue";
import { usePermissionGrantV2 } from "../utils/hook";
import { type PermCellKey } from "@/utils/permission-grant-types";
import { type ConditionOption } from "@/api/permission-grant";
import { permCellKeyStr, normalizeConditionCode } from "../utils/grant-variant";

defineOptions({ name: "BatchConfigDrawerV2" });

const props = defineProps<{
  visible: boolean;
  cells: PermCellKey[];
  /** 入口预设：设置转授权=true / 设置条件=false */
  presetCanGrant: boolean;
  supportsCondition: boolean;
  supportsDelegation: boolean;
  conditionOptions: ConditionOption[];
}>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
  (
    e: "confirm",
    payload: {
      conditionCode: string | null;
      canGrant: boolean;
      keepDirect: boolean;
    }
  ): void;
}>();

type Store = ReturnType<typeof usePermissionGrantV2>;
const store = inject<Store>("pgV2Store")!;

const drawerVisible = computed({
  get: () => props.visible,
  set: v => emit("update:visible", v)
});

const conditionCode = ref<string>("");
const canGrant = ref(false);
const keepDirect = ref(false);

// 打开时按入口预设重置
watch(
  () => props.visible,
  v => {
    if (v) {
      conditionCode.value = "";
      canGrant.value = props.presetCanGrant;
      keepDirect.value = false;
    }
  }
);

const conditionSelectOptions = computed(() => [
  { label: "无条件", value: "" },
  ...props.conditionOptions.map(c => ({
    label: c.name || c.code,
    value: c.code
  }))
]);

const normalizedCond = computed(() =>
  normalizeConditionCode(
    conditionCode.value === "" ? null : conditionCode.value
  )
);

/**
 * 预览：逐单元格判定
 * - add：可新增且非 redundant 候选
 * - redundant：可新增+无重复但被 ALL 覆盖（keepDirect=false 跳过，true 创建）
 * - skip：不可新增或重复
 */
const preview = computed(() => {
  let add = 0;
  let redundant = 0;
  let skip = 0;
  for (const cell of props.cells) {
    if (!store.grantableByOperator(cell).ok) {
      skip++;
      continue;
    }
    const key = permCellKeyStr(cell);
    const existing = store.mainIndex.value.get(key) ?? [];
    const dup = existing.some(vid => {
      const v = store.mainDraft.value.get(vid);
      return (
        v && normalizeConditionCode(v.conditionCode) === normalizedCond.value
      );
    });
    if (dup) {
      skip++;
      continue;
    }
    if (store.redundantCandidate(cell)) {
      redundant++;
      continue;
    }
    add++;
  }
  return { add, redundant, skip };
});

const showRedundantWarning = computed(() => preview.value.redundant > 0);

/** 将实际创建的分支数（add + keepDirect 时的 redundant） */
const willCreate = computed(
  () => preview.value.add + (keepDirect.value ? preview.value.redundant : 0)
);

function onConfirm() {
  if (willCreate.value === 0) return;
  emit("confirm", {
    conditionCode: conditionCode.value === "" ? null : conditionCode.value,
    canGrant: props.supportsDelegation ? canGrant.value : false,
    keepDirect: keepDirect.value
  });
}

function onCancel() {
  emit("update:visible", false);
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    title="批量新增分支"
    size="360px"
    direction="rtl"
    :close-on-click-modal="false"
  >
    <div class="batch-drawer-body">
      <div class="form-section">
        <div class="section-label">选择集（{{ cells.length }} 个单元格）</div>
        <div class="cell-preview-list">
          <div
            v-for="(cell, idx) in cells"
            :key="idx"
            class="cell-preview-item"
          >
            <span class="cell-op">{{ cell.operationCode }}</span>
            <span class="cell-res">{{
              cell.scopeMode === "ALL" ? "ALL" : cell.resourceCode
            }}</span>
          </div>
        </div>
      </div>

      <div class="form-section">
        <div class="section-label">统一配置</div>
        <div v-if="supportsCondition" class="form-row">
          <span class="form-label">条件</span>
          <el-select
            v-model="conditionCode"
            size="small"
            placeholder="选择条件"
            class="form-select"
          >
            <el-option
              v-for="opt in conditionSelectOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
        <div v-if="supportsDelegation" class="form-row">
          <span class="form-label">转授</span>
          <el-switch v-model="canGrant" size="small" />
        </div>
      </div>

      <el-alert
        v-if="showRedundantWarning"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>
          {{ preview.redundant }} 个单元格被 ALL 覆盖，创建直接记录冗余
        </template>
        <div class="redundant-action">
          <span>默认跳过这些单元格</span>
          <span class="keep-direct-wrap">
            仍创建
            <el-switch v-model="keepDirect" size="small" />
          </span>
        </div>
      </el-alert>

      <div class="preview-summary">
        <span>将新增 {{ willCreate }} 条分支</span>
        <span v-if="preview.skip > 0" class="skip-count">
          （跳过 {{ preview.skip }} 项：重复或不可新增）
        </span>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="onCancel">取消</el-button>
        <el-button
          type="primary"
          :disabled="willCreate === 0"
          @click="onConfirm"
        >
          确认
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
.batch-drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-2);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cell-preview-list {
  display: flex;
  flex-direction: column;
  max-height: 180px;
  padding: var(--space-1);
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.cell-preview-item {
  display: flex;
  gap: var(--space-2);
  padding: 2px 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cell-op {
  font-weight: 600;
  color: var(--el-color-primary);
}

.cell-res {
  color: var(--el-text-color-secondary);
}

.form-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.form-label {
  flex-shrink: 0;
  width: 40px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.form-select {
  flex: 1;
}

.redundant-action {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-top: var(--space-1);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.keep-direct-wrap {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.preview-summary {
  padding: var(--space-2);
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.skip-count {
  color: var(--el-text-color-secondary);
}

.drawer-footer {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
}
</style>
