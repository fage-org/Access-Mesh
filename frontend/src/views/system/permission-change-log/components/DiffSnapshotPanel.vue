<script setup lang="ts">
/**
 * Diff 对比面板 -- 结构化展示 permission_change_log.diff_snapshot（§5.8 diff_snapshot 规范（原 §6.8））。
 *
 * 设计：
 * - 结构化 diff_snapshot 为主：eventType tag + items 列表（每项 changeType tag + permission/role/resource
 *   业务键卡片 + before/after 状态对比）。
 * - old/new 原始快照折叠为辅：审计追溯用，默认收起。
 *
 * Step 1.5 组件候选（登记 T-FE-001 组件池）：与 7.1 操作日志详情共用候选。
 * 当前 T-FE-005 OperationLogResp 无 diffSnapshot 字段，共用性待后续扩展确认，
 * 故本组件内联于本页 components/ 下，不现在抽取（遵循「2+ 页确认后才抽取」）。
 */
import { computed, ref } from "vue";
import {
  parseDiffSnapshot,
  type ChangeLogResp,
  type DiffItem
} from "@/api/permission-change-log";
import { EVENT_TYPE_META, CHANGE_TYPE_META } from "../utils/types";

defineOptions({
  name: "DiffSnapshotPanel"
});

const props = defineProps<{
  log: ChangeLogResp | null;
}>();

/** 解析 diff_snapshot 结构化对象 */
const diff = computed(() => parseDiffSnapshot(props.log?.diffSnapshot));

/** 事件类型标签 meta（解析失败时回退原值或"-"） */
const eventTypeMeta = computed(() => {
  const et = diff.value?.eventType;
  if (!et) return null;
  return EVENT_TYPE_META[et] ?? { label: et, type: "info" as const };
});

/** 判断 item 是否含 before/after 状态对比 */
function hasState(item: DiffItem): boolean {
  return item.before != null || item.after != null;
}

/** 权限项业务键拼接（domainCode/resourceTypeCode/resourceCode/codeType/operationCode/scopeMode） */
function permText(item: DiffItem): string {
  const p = item.permission;
  if (!p) return "-";
  return [
    p.domainCode,
    p.resourceTypeCode,
    p.resourceCode,
    p.codeType,
    p.operationCode,
    p.scopeMode
  ]
    .filter(v => v != null && v !== "")
    .join(" / ");
}

/** 角色业务键拼接 */
function roleText(item: DiffItem): string {
  const r = item.role;
  if (!r) return "-";
  const parts = [r.roleName, r.roleExternalId, r.roleTypeCode].filter(
    v => v != null && v !== ""
  );
  return parts.length === 0 ? "-" : parts.join(" / ");
}

/** 资源业务键拼接 */
function resourceText(item: DiffItem): string {
  const r = item.resource;
  if (!r) return "-";
  const parts = [
    r.domainCode,
    r.resourceTypeCode,
    r.resourceCode,
    r.codeType
  ].filter(v => v != null && v !== "");
  return parts.length === 0 ? "-" : parts.join(" / ");
}

/** changeType 标签 meta */
function changeTypeMeta(changeType: DiffItem["changeType"]) {
  return (
    CHANGE_TYPE_META[changeType] ?? { label: changeType, type: "info" as const }
  );
}

/** 将 before/after 对象格式化为可读键值对列表 */
function stateEntries(
  obj: Record<string, unknown> | null | undefined
): Array<{ key: string; value: string }> {
  if (!obj || typeof obj !== "object") return [];
  return Object.entries(obj).map(([key, value]) => ({
    key,
    value: typeof value === "object" ? JSON.stringify(value) : String(value)
  }));
}

// ========== old/new 原始快照折叠 ==========
const showRawSnapshot = ref(false);

/** 格式化 JSON 字符串（解析失败回退原值） */
function prettyJson(raw: string | null | undefined): string {
  if (!raw) return "-";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
</script>

<template>
  <div class="diff-snapshot-panel">
    <!-- 结构化 diff_snapshot -->
    <div v-if="diff" class="diff-structured">
      <div class="diff-header">
        <el-tag :type="eventTypeMeta?.type" effect="light" class="font-mono">
          {{ eventTypeMeta?.label ?? "-" }}
        </el-tag>
        <span class="diff-event-code text-gray-400">
          {{ diff.eventType }}
        </span>
        <span class="diff-items-count text-gray-400">
          共 {{ diff.items.length }} 项变更
        </span>
      </div>

      <div v-for="(item, idx) in diff.items" :key="idx" class="diff-item">
        <div class="diff-item-header">
          <el-tag
            :type="changeTypeMeta(item.changeType).type"
            effect="dark"
            size="small"
            class="font-mono"
          >
            {{ changeTypeMeta(item.changeType).label }}
          </el-tag>
          <span class="text-gray-400 text-xs">
            {{ item.changeType }}
          </span>
        </div>

        <!-- 业务键卡片：permission / role / resource（按存在性展示） -->
        <div class="diff-item-body">
          <div v-if="item.permission" class="diff-field">
            <span class="diff-field-label">权限</span>
            <span class="diff-field-value font-mono">{{ permText(item) }}</span>
          </div>
          <div v-if="item.role" class="diff-field">
            <span class="diff-field-label">角色</span>
            <span class="diff-field-value">{{ roleText(item) }}</span>
          </div>
          <div v-if="item.resource" class="diff-field">
            <span class="diff-field-label">资源</span>
            <span class="diff-field-value font-mono">{{
              resourceText(item)
            }}</span>
          </div>
          <div v-if="item.message" class="diff-field">
            <span class="diff-field-label">说明</span>
            <span class="diff-field-value">{{ item.message }}</span>
          </div>

          <!-- before/after 状态对比 -->
          <div v-if="hasState(item)" class="diff-state-compare">
            <div class="diff-state-col diff-state-before">
              <div class="diff-state-title">变更前</div>
              <div
                v-for="entry in stateEntries(item.before)"
                :key="entry.key"
                class="diff-state-row"
              >
                <span class="diff-state-key">{{ entry.key }}</span>
                <span class="diff-state-val">{{ entry.value }}</span>
              </div>
              <div
                v-if="stateEntries(item.before).length === 0"
                class="text-gray-400"
              >
                （空）
              </div>
            </div>
            <div class="diff-state-arrow">→</div>
            <div class="diff-state-col diff-state-after">
              <div class="diff-state-title">变更后</div>
              <div
                v-for="entry in stateEntries(item.after)"
                :key="entry.key"
                class="diff-state-row"
              >
                <span class="diff-state-key">{{ entry.key }}</span>
                <span class="diff-state-val">{{ entry.value }}</span>
              </div>
              <div
                v-if="stateEntries(item.after).length === 0"
                class="text-gray-400"
              >
                （空）
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="无结构化 diff 快照" :image-size="60" />

    <!-- old/new 原始快照折叠（审计追溯） -->
    <div class="diff-raw-section">
      <el-button
        link
        type="primary"
        @click="showRawSnapshot = !showRawSnapshot"
      >
        {{ showRawSnapshot ? "收起" : "展开" }}原始快照（old/new snapshot）
      </el-button>
      <div v-show="showRawSnapshot" class="diff-raw-content">
        <div class="diff-raw-block">
          <div class="diff-raw-title">old_snapshot</div>
          <pre class="diff-raw-json">{{ prettyJson(log?.oldSnapshot) }}</pre>
        </div>
        <div class="diff-raw-block">
          <div class="diff-raw-title">new_snapshot</div>
          <pre class="diff-raw-json">{{ prettyJson(log?.newSnapshot) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.diff-snapshot-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.diff-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.diff-event-code {
  font-size: 12px;
}

.diff-items-count {
  margin-left: auto;
  font-size: 12px;
}

.diff-item {
  padding: var(--space-2) var(--space-3);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.diff-item-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-2);
}

.diff-item-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.diff-field {
  display: flex;
  gap: var(--space-2);
  align-items: baseline;
  font-size: 13px;
}

.diff-field-label {
  flex-shrink: 0;
  min-width: 42px;
  color: var(--el-text-color-secondary);
}

.diff-field-value {
  word-break: break-all;
}

.diff-state-compare {
  display: flex;
  gap: var(--space-2);
  align-items: stretch;
  margin-top: var(--space-2);
}

.diff-state-col {
  flex: 1;
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.diff-state-before {
  border-left: 3px solid var(--el-color-danger);
}

.diff-state-after {
  border-left: 3px solid var(--el-color-success);
}

.diff-state-arrow {
  display: flex;
  align-items: center;
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.diff-state-title {
  margin-bottom: var(--space-1);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.diff-state-row {
  display: flex;
  gap: var(--space-2);
  font-size: 12px;
}

.diff-state-key {
  flex-shrink: 0;
  min-width: 72px;
  color: var(--el-text-color-secondary);
}

.diff-state-val {
  font-family: monospace;
  word-break: break-all;
}

.diff-raw-section {
  padding-top: var(--space-2);
  border-top: 1px dashed var(--el-border-color-lighter);
}

.diff-raw-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.diff-raw-block {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.diff-raw-title {
  padding: var(--space-1) var(--space-2);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.diff-raw-json {
  max-height: 200px;
  padding: var(--space-2);
  margin: 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
