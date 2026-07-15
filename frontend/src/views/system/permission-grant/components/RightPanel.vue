<script setup lang="ts">
import { computed, ref } from "vue";
import { inject } from "vue";
import { ElMessageBox } from "element-plus";
import { usePermissionGrant } from "../utils/hook";
import { permCellKey } from "../utils/types";

defineOptions({ name: "RightPanel" });

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const activeTab = ref<"current" | "changes">("changes");

// ---- 当前权限 Tab ----

/** 按资源类型分组 baseline 主权限 */
const groupedCurrent = computed(() => {
  const groups = new Map<
    string,
    typeof store.mainBaseline.value extends Map<infer K, infer V> ? V[] : never
  >();
  const map = new Map<string, DraftPermission[]>();
  for (const [, d] of store.mainBaseline.value) {
    if (!map.has(d.resourceTypeCode)) map.set(d.resourceTypeCode, []);
    map.get(d.resourceTypeCode)!.push(d);
  }
  return Array.from(map.entries()).map(([typeCode, items]) => ({
    typeCode,
    items: items.sort((a, b) => {
      // ALL 优先，再按 resourceCode
      if (a.scopeMode === "ALL" && b.scopeMode !== "ALL") return -1;
      if (b.scopeMode === "ALL" && a.scopeMode !== "ALL") return 1;
      return (a.resourceCode ?? "").localeCompare(b.resourceCode ?? "");
    })
  }));
});

// ---- 本次变更 Tab ----

const diffGroups = computed(() => {
  const groups: {
    label: string;
    type: string;
    items: typeof store.allDiff.value;
  }[] = [];
  const add = store.allDiff.value.filter(d => d.type === "add");
  const update = store.allDiff.value.filter(d => d.type === "update");
  const remove = store.allDiff.value.filter(d => d.type === "remove");
  if (add.length)
    groups.push({ label: `新增 ${add.length}`, type: "add", items: add });
  if (update.length)
    groups.push({
      label: `修改 ${update.length}`,
      type: "update",
      items: update
    });
  if (remove.length)
    groups.push({
      label: `移除 ${remove.length}`,
      type: "remove",
      items: remove
    });
  return groups;
});

function diffLabel(d: {
  permission: {
    scopeMode: string;
    resourceCode: string | null;
    resourceName: string | null;
    operationCode: string;
    resourceTypeCode: string;
  };
}) {
  const p = d.permission;
  const res =
    p.scopeMode === "ALL" ? "全部" : (p.resourceName ?? p.resourceCode ?? "");
  return `${p.resourceTypeCode} / ${res} / ${p.operationCode} / ${p.scopeMode}`;
}

function diffChangedFields(d: {
  type: string;
  changedFields: string[];
  before: { conditionCode: string | null; canGrant: boolean } | null;
  permission: { conditionCode: string | null; canGrant: boolean };
}) {
  if (d.type === "update" && d.before) {
    const parts: string[] = [];
    if (d.changedFields.includes("conditionCode")) {
      parts.push(
        `条件: ${d.before.conditionCode ?? "无"} → ${d.permission.conditionCode ?? "无"}`
      );
    }
    if (d.changedFields.includes("canGrant")) {
      parts.push(`canGrant: ${d.before.canGrant} → ${d.permission.canGrant}`);
    }
    return parts.join("；");
  }
  return "";
}

async function onSave() {
  await store.saveAll();
}

async function onDiscard() {
  try {
    await ElMessageBox.confirm("确定放弃所有未保存的变更？", "放弃更改", {
      type: "warning"
    });
    store.discardAll();
  } catch {
    // 取消
  }
}

const diffIcon = (type: string) => {
  if (type === "add") return "+";
  if (type === "update") return "~";
  return "−";
};
const diffType = (type: string) =>
  type === "add" ? "success" : type === "update" ? "warning" : "danger";

// 子权限变更标记
const isChild = (d: { isChild: boolean }) => d.isChild;
</script>

<template>
  <div class="right-panel">
    <el-tabs v-model="activeTab" class="right-tabs">
      <el-tab-pane name="current">
        <template #label>
          当前权限<span class="tab-count">{{
            store.mainBaseline.value.size
          }}</span>
        </template>
        <el-scrollbar class="tab-scroll">
          <div v-for="g in groupedCurrent" :key="g.typeCode" class="perm-group">
            <div class="group-title">{{ g.typeCode }}</div>
            <div
              v-for="item in g.items"
              :key="permCellKey(item)"
              class="perm-item"
            >
              <el-tag
                :type="item.scopeMode === 'ALL' ? 'primary' : 'info'"
                size="small"
                effect="plain"
                >{{ item.scopeMode }}</el-tag
              >
              <span class="perm-item-name">{{
                item.scopeMode === "ALL"
                  ? "全部"
                  : (item.resourceName ?? item.resourceCode)
              }}</span>
              <span class="perm-item-op">{{ item.operationCode }}</span>
              <el-tag
                v-if="item.conditionCode"
                size="small"
                type="warning"
                effect="plain"
                >条件</el-tag
              >
              <el-tag
                v-if="item.canGrant"
                size="small"
                type="success"
                effect="plain"
                >可授权</el-tag
              >
            </div>
          </div>
          <el-empty
            v-if="groupedCurrent.length === 0"
            description="尚未配置直接权限"
            :image-size="60"
          />
        </el-scrollbar>
      </el-tab-pane>

      <el-tab-pane name="changes">
        <template #label>
          本次变更<span v-if="store.allDiff.value.length" class="tab-count">{{
            store.allDiff.value.length
          }}</span>
        </template>
        <el-scrollbar class="tab-scroll">
          <div v-for="g in diffGroups" :key="g.type" class="diff-group">
            <div :class="['diff-group-title', `diff-group-${g.type}`]">
              {{ g.label }}
            </div>
            <div v-for="d in g.items" :key="d.key" class="diff-item">
              <span :class="['diff-icon', `diff-icon-${d.type}`]">{{
                diffIcon(d.type)
              }}</span>
              <div class="diff-body">
                <div class="diff-label">
                  {{ diffLabel(d) }}
                  <el-tag
                    v-if="isChild(d)"
                    size="small"
                    type="info"
                    effect="plain"
                    >子权限</el-tag
                  >
                </div>
                <div v-if="diffChangedFields(d)" class="diff-changed">
                  {{ diffChangedFields(d) }}
                </div>
              </div>
            </div>
          </div>
          <el-empty
            v-if="diffGroups.length === 0"
            description="无变更，配置已同步"
            :image-size="60"
          />
          <el-alert
            v-if="diffGroups.length > 0"
            type="info"
            :closable="false"
            show-icon
            class="revert-notice"
            title="当前暂不支持单条撤销，可放弃全部；任务粒度撤销将在 T-FE-028 提供"
          />
          <div
            v-if="store.failedChildren.value.length > 0"
            class="failed-notice"
          >
            <el-alert
              :title="`${store.failedChildren.value.length} 条子权限保存失败`"
              type="warning"
              :closable="false"
              show-icon
            >
              <el-button
                size="small"
                type="primary"
                :loading="store.saving.value"
                @click="store.retryFailedChildren()"
                >重试未完成项</el-button
              >
            </el-alert>
          </div>
        </el-scrollbar>
      </el-tab-pane>
    </el-tabs>
    <div class="right-actions">
      <el-button :disabled="!store.hasDraft.value" @click="onDiscard"
        >放弃全部更改</el-button
      >
      <el-button
        type="primary"
        :disabled="!store.hasDraft.value || store.readonly.value"
        :loading="store.saving.value"
        @click="onSave"
        >保存全部更改</el-button
      >
    </div>
  </div>
</template>

<script lang="ts">
import type { DraftPermission } from "../utils/types";
</script>

<style lang="scss" scoped>
.right-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.right-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
  }
}

.tab-count {
  padding: 0 6px;
  margin-left: var(--space-1);
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
}

.tab-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 var(--space-2);
}

.perm-group {
  margin-bottom: var(--space-3);
}

.group-title {
  margin-bottom: var(--space-2);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.perm-item {
  display: flex;
  gap: var(--space-1);
  align-items: center;
  padding: var(--space-1) 0;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.perm-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.perm-item-op {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.diff-group {
  margin-bottom: var(--space-3);
}

.diff-group-title {
  margin-bottom: var(--space-2);
  font-size: 12px;
  font-weight: 600;
}

.diff-group-add {
  color: var(--el-color-success);
}

.diff-group-update {
  color: var(--el-color-warning);
}

.diff-group-remove {
  color: var(--el-color-danger);
}

.diff-item {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  padding: var(--space-1) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.diff-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 12px;
  font-weight: 700;
  border-radius: 50%;
}

.diff-icon-add {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.diff-icon-update {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.diff-icon-remove {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.diff-body {
  flex: 1;
  min-width: 0;
}

.diff-label {
  display: flex;
  gap: var(--space-1);
  align-items: center;
  font-size: 13px;
}

.diff-changed {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.failed-notice {
  margin-top: var(--space-3);
}

.revert-notice {
  margin-top: var(--space-3);
}

.right-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
