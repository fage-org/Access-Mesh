<script setup lang="ts">
/**
 * 变更日志详情抽屉 -- 基本信息区 + Diff 对比面板 + 影响分析。
 *
 * 范式对齐 operation-log/components/LogDetailDrawer.vue，但变更日志详情更丰富：
 * - 基本信息区：el-descriptions 展示 entityType/entityId/operation/changeSource 等
 * - diff 对比区：DiffSnapshotPanel 结构化展示 + old/new 原始快照折叠
 * - 影响分析区：受影响用户/角色 ID 列表
 *
 * 无 detail 接口--ChangeLogResp 已含全部字段，纯展示 list 数据。
 */
import {
  entityTypeLabel,
  operationLabel,
  changeSourceLabel
} from "../utils/types";
import type { ChangeLogResp } from "@/api/permission-change-log";
import DiffSnapshotPanel from "./DiffSnapshotPanel.vue";

defineOptions({
  name: "ChangeLogDetailDrawer"
});

const props = defineProps<{
  visible: boolean;
  log: ChangeLogResp | null;
}>();

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
}>();

const handleClose = () => {
  emit("update:visible", false);
};
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="权限变更日志详情"
    direction="rtl"
    size="640px"
    @update:model-value="handleClose"
  >
    <div v-if="log" class="change-log-detail">
      <!-- 基本信息区 -->
      <el-descriptions :column="1" border class="log-detail-desc">
        <el-descriptions-item label="日志 ID">
          {{ log.id }}
        </el-descriptions-item>
        <el-descriptions-item label="实体类型">
          <el-tag size="small" effect="plain" class="font-mono">
            {{ log.entityType }}
          </el-tag>
          <span class="ml-2 text-gray-500">
            {{ entityTypeLabel(log.entityType) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="实体 ID">
          {{ log.entityId ?? "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="变更操作">
          <el-tag size="small" type="warning" effect="light" class="font-mono">
            {{ log.operation }}
          </el-tag>
          <span class="ml-2 text-gray-500">
            {{ operationLabel(log.operation) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="变更来源">
          <el-tag size="small" type="info" effect="plain" class="font-mono">
            {{ log.changeSource || "-" }}
          </el-tag>
          <span class="ml-2 text-gray-500">
            {{ changeSourceLabel(log.changeSource) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="变更原因">
          <span class="summary-text">{{ log.changeReason || "-" }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求 ID">
          <span class="font-mono">{{ log.requestId || "-" }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ log.createdAt || "-" }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- diff 对比区 -->
      <div class="detail-section">
        <div class="detail-section-title">变更差异（diff snapshot）</div>
        <DiffSnapshotPanel :log="log" />
      </div>

      <!-- 影响分析区 -->
      <div class="detail-section">
        <div class="detail-section-title">影响范围</div>
        <div class="impact-row">
          <span class="impact-label">受影响用户</span>
          <div v-if="log.affectedAbstractUserIds?.length" class="impact-tags">
            <el-tag
              v-for="uid in log.affectedAbstractUserIds"
              :key="uid"
              size="small"
              type="primary"
              effect="plain"
              class="font-mono"
            >
              {{ uid }}
            </el-tag>
          </div>
          <span v-else class="text-gray-400">无</span>
        </div>
        <div class="impact-row">
          <span class="impact-label">受影响角色</span>
          <div v-if="log.affectedAbstractRoleIds?.length" class="impact-tags">
            <el-tag
              v-for="rid in log.affectedAbstractRoleIds"
              :key="rid"
              size="small"
              type="warning"
              effect="plain"
              class="font-mono"
            >
              {{ rid }}
            </el-tag>
          </div>
          <span v-else class="text-gray-400">无</span>
        </div>
      </div>
    </div>
    <el-empty v-else description="无数据" />
  </el-drawer>
</template>

<style lang="scss" scoped>
.change-log-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.log-detail-desc {
  margin-top: var(--space-2);
}

.summary-text {
  display: block;
  max-height: 120px;
  overflow-y: auto;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail-section-title {
  padding-left: var(--space-2);
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-left: 3px solid var(--el-color-primary);
}

.impact-row {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  font-size: 13px;
}

.impact-label {
  flex-shrink: 0;
  min-width: 70px;
  color: var(--el-text-color-secondary);
}

.impact-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}
</style>
