<script setup lang="ts">
/**
 * 保存前总览 bottom-sheet（T-FE-034，interaction §4.5）。
 *
 * 多态视图（按 savePhase）：
 * - SAVE_PREVIEW：变更清单 + 警告区（级联）+ 确认/取消
 * - SAVE_FAILED_MAIN：业务拒绝错误 + 重新保存/关闭（草稿完整保留）
 * - SAVE_FAILED_CHILD：失败子项数 + 重试失败项/放弃
 * - SAVE_OUTCOME_UNKNOWN：结果核对中（禁止盲目重试，等 fetchBaseline+reconcile）
 * - STALE：事实过期，重新选角色
 * - STALE_WITH_CHILD_FAILURE：一键刷新并重试/放弃
 *
 * SAVING/CLEAN/DIRTY 不显示 sheet。
 */
import { computed, inject } from "vue";
import { usePermissionGrantV2 } from "../utils/hook";
import type { V2DiffEntry } from "../utils/save-adapter";

defineOptions({ name: "SavePreviewSheetV2" });

const store = inject<ReturnType<typeof usePermissionGrantV2>>("pgV2Store")!;

const visible = computed(
  () =>
    store.savePhase.value !== "CLEAN" &&
    store.savePhase.value !== "DIRTY" &&
    store.savePhase.value !== "SAVING"
);

const addEntries = computed(() =>
  store.allDiff.value.filter(d => d.type === "add")
);
const updateEntries = computed(() =>
  store.allDiff.value.filter(d => d.type === "update")
);
const removeEntries = computed(() =>
  store.allDiff.value.filter(d => d.type === "remove")
);
/** 级联警告：移除主权限变体在 baseline 确有子权限将被级联删除 */
const cascadeRemoveCount = computed(() => {
  let n = 0;
  const childMap = store.baselineState.value.childMap;
  for (const d of removeEntries.value) {
    if (d.isChild) continue;
    const hasChild = [...childMap.values()].some(
      c => c.dependOn === d.variantId
    );
    if (hasChild) n++;
  }
  return n;
});
/** 冗余警告：keepDirectWhenAllCovered 且 cell 实际被 ALL 覆盖的 grant 命令数（排除混合批量写入的非冗余项） */
const redundantCount = computed(() => {
  let n = 0;
  for (const t of store.grantTasks.value) {
    for (const cmd of t.commands) {
      if (cmd.kind !== "grant" || !cmd.keepDirectWhenAllCovered) continue;
      if (store.resolveCell(cmd.cell).allCovered) n++;
    }
  }
  return n;
});

function entryLabel(d: V2DiffEntry): string {
  const cell = [
    d.perm.resourceTypeCode,
    d.perm.scopeMode === "ALL" ? "ALL" : (d.perm.resourceCode ?? "?"),
    d.perm.operationCode
  ].join(" / ");
  const cond = d.perm.conditionCode ? `〔${d.perm.conditionCode}〕` : "";
  const child = d.isChild ? "（子权限）" : "";
  return `${cell}${cond}${child}`;
}

function confirmSave() {
  store.confirmSave();
}
/** SAVE_PREVIEW 取消（回 DIRTY） */
function cancelPreview() {
  store.cancelSavePreview();
}
/** MAIN_FAILED 关闭（回 DIRTY 修改草稿） */
function dismissMainError() {
  store.dismissMainError();
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    direction="btt"
    size="auto"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :with-header="false"
    append-to-body
  >
    <div class="save-sheet">
      <!-- SAVE_PREVIEW：保存前总览 -->
      <template v-if="store.savePhase.value === 'SAVE_PREVIEW'">
        <div class="sheet-title">
          确认保存 {{ store.allDiff.value.length }} 项变更
        </div>
        <div class="sheet-body">
          <div v-if="addEntries.length" class="diff-group">
            <span class="group-label add">新增（{{ addEntries.length }}）</span>
            <div
              v-for="d in addEntries"
              :key="`${d.variantId}`"
              class="diff-item add"
            >
              ＋ {{ entryLabel(d) }}
            </div>
          </div>
          <div v-if="updateEntries.length" class="diff-group">
            <span class="group-label update">
              修改（{{ updateEntries.length }}）
            </span>
            <div
              v-for="d in updateEntries"
              :key="`${d.variantId}`"
              class="diff-item update"
            >
              ✎ {{ entryLabel(d) }}
            </div>
          </div>
          <div v-if="removeEntries.length" class="diff-group">
            <span class="group-label remove">
              移除（{{ removeEntries.length }}）
            </span>
            <div
              v-for="d in removeEntries"
              :key="`${d.variantId}`"
              class="diff-item remove"
            >
              － {{ entryLabel(d) }}
            </div>
          </div>
        </div>
        <div class="sheet-alerts">
          <el-alert
            v-if="cascadeRemoveCount > 0"
            type="warning"
            :closable="false"
            show-icon
            :title="`${cascadeRemoveCount} 项主权限移除将级联移除其子权限`"
          />
          <el-alert
            v-if="redundantCount > 0"
            type="warning"
            :closable="false"
            show-icon
            :title="`${redundantCount} 项被 ALL 覆盖仍保留直接记录（冗余）`"
          />
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="冲突规则在运行时查询侧评估，保存前不阻断"
          />
        </div>
        <div class="sheet-actions">
          <el-button @click="cancelPreview">取消</el-button>
          <el-button type="primary" @click="confirmSave">确认保存</el-button>
        </div>
      </template>

      <!-- SAVE_FAILED_MAIN：主请求业务拒绝 -->
      <template v-else-if="store.savePhase.value === 'SAVE_FAILED_MAIN'">
        <div class="sheet-title failed">保存失败</div>
        <div class="sheet-body">
          <el-alert
            type="error"
            :closable="false"
            show-icon
            :title="store.saveError.value ?? '保存失败'"
          />
          <p class="sheet-hint">服务端未提交，草稿已保留，修正后可重新保存。</p>
        </div>
        <div class="sheet-actions">
          <el-button @click="dismissMainError">关闭</el-button>
          <el-button type="primary" @click="store.requestSave"
            >重新保存</el-button
          >
        </div>
      </template>

      <!-- SAVE_FAILED_CHILD：主成功子部分失败 -->
      <template v-else-if="store.savePhase.value === 'SAVE_FAILED_CHILD'">
        <div class="sheet-title failed">部分子权限保存失败</div>
        <div class="sheet-body">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="`主权限已保存，${store.failedChildren.value.length} 项子权限待重试`"
          />
          <p class="sheet-hint">{{ store.saveError.value }}</p>
        </div>
        <div class="sheet-actions">
          <el-button @click="store.discardAll">放弃全部</el-button>
          <el-button type="primary" @click="store.retryFailedChildren">
            重试失败项
          </el-button>
        </div>
      </template>

      <!-- SAVE_OUTCOME_UNKNOWN：结果核对中 / 核对失败 -->
      <template v-else-if="store.savePhase.value === 'SAVE_OUTCOME_UNKNOWN'">
        <div class="sheet-title unknown">
          {{ store.reconciling.value ? "保存结果核对中" : "结果核对失败" }}
        </div>
        <div class="sheet-body">
          <el-alert
            :type="store.reconciling.value ? 'info' : 'error'"
            :closable="false"
            show-icon
            :title="
              store.reconciling.value
                ? '正在重新拉取事实核对…'
                : (store.saveError.value ?? '事实刷新失败')
            "
          />
          <p class="sheet-hint">
            {{
              store.reconciling.value
                ? "禁止盲目重试，请等待核对完成后按提示续传。"
                : "可重新核对，或放弃后刷新事实（放弃后需重新加载或切换角色）。"
            }}
          </p>
        </div>
        <div v-if="!store.reconciling.value" class="sheet-actions">
          <el-button @click="store.discardOutcomeUnknown">放弃并刷新</el-button>
          <el-button type="primary" @click="store.retryReconcile">
            重新核对
          </el-button>
        </div>
      </template>

      <!-- STALE：事实过期 -->
      <template v-else-if="store.savePhase.value === 'STALE'">
        <div class="sheet-title failed">权限事实已过期</div>
        <div class="sheet-body">
          <el-alert
            type="error"
            :closable="false"
            show-icon
            title="保存已提交但事实刷新失败，请重新选择角色刷新"
          />
        </div>
        <div class="sheet-actions">
          <el-button @click="store.reloadBaseline">重新加载</el-button>
        </div>
      </template>

      <!-- STALE_WITH_CHILD_FAILURE：组合态一键刷新并重试 -->
      <template
        v-else-if="store.savePhase.value === 'STALE_WITH_CHILD_FAILURE'"
      >
        <div class="sheet-title failed">事实过期且子权限待重试</div>
        <div class="sheet-body">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="`事实已过期，且 ${store.failedChildren.value.length} 项子权限待重试`"
          />
          <p class="sheet-hint">
            将先刷新事实再重试子项，避免基于陈旧事实重试。
          </p>
        </div>
        <div class="sheet-actions">
          <el-button @click="store.discardAll">放弃全部</el-button>
          <el-button type="primary" @click="store.refreshAndRetry">
            刷新并重试
          </el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.save-sheet {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
}

.sheet-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);

  &.failed {
    color: var(--el-color-danger);
  }

  &.unknown {
    color: var(--el-color-info);
  }
}

.sheet-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-height: 40vh;
  overflow-y: auto;
}

.diff-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.group-label {
  font-size: 12px;
  font-weight: 600;

  &.add {
    color: var(--el-color-success);
  }

  &.update {
    color: var(--el-color-warning);
  }

  &.remove {
    color: var(--el-color-danger);
  }
}

.diff-item {
  padding: var(--space-1) var(--space-2);
  font-size: 13px;
  border-radius: 4px;

  &.add {
    background: var(--el-color-success-light-9);
  }

  &.update {
    background: var(--el-color-warning-light-9);
  }

  &.remove {
    background: var(--el-color-danger-light-9);
  }
}

.sheet-alerts {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.sheet-hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sheet-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  padding-top: var(--space-2);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
