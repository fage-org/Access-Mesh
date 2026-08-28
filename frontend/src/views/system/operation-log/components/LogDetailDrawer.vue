<script setup lang="ts">
import { MODULE_OPTIONS } from "../utils/types";
import type { OperationLogResp } from "@/api/operation-log";

defineOptions({
  name: "LogDetailDrawer"
});

const props = defineProps<{
  visible: boolean;
  log: OperationLogResp | null;
}>();

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
}>();

/** 模块编码 → 中文标签（用于展示，匹配不上回退原值；三值封闭集合） */
const moduleLabel = (code?: string | null): string => {
  if (!code) return "—";
  return MODULE_OPTIONS.find(o => o.value === code)?.label ?? code;
};

/** action 事件码开放增长（@OperationLog 注解维护），直接展示原始编码（T-PERM-025 动态字典口径） */
const actionLabel = (code?: string | null): string => {
  return code || "—";
};

const handleClose = () => {
  emit("update:visible", false);
};
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="操作日志详情"
    direction="rtl"
    size="480px"
    @update:model-value="handleClose"
  >
    <el-descriptions v-if="log" :column="1" border class="log-detail-desc">
      <el-descriptions-item label="日志 ID">
        {{ log.id }}
      </el-descriptions-item>
      <el-descriptions-item label="模块">
        <el-tag size="small" effect="plain" class="font-mono">
          {{ log.module }}
        </el-tag>
        <span class="ml-2 text-gray-500">{{ moduleLabel(log.module) }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="操作类型">
        <el-tag size="small" type="warning" effect="light" class="font-mono">
          {{ log.action }}
        </el-tag>
        <span class="ml-2 text-gray-500">{{ actionLabel(log.action) }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="目标类型">
        <span class="font-mono">{{ log.targetType || "—" }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="目标 ID">
        {{ log.targetId ?? "—" }}
      </el-descriptions-item>
      <el-descriptions-item label="操作摘要">
        <span class="summary-text">{{ log.summary || "—" }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="操作人 ID">
        {{ log.operatorId ?? "—" }}
      </el-descriptions-item>
      <el-descriptions-item label="操作人">
        {{ log.operatorName || "—" }}
      </el-descriptions-item>
      <el-descriptions-item label="IP 地址">
        <span class="font-mono">{{ log.ipAddress || "—" }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="请求 ID">
        <span class="font-mono">{{ log.requestId || "—" }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">
        {{ log.createdAt || "—" }}
      </el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="无数据" />
  </el-drawer>
</template>

<style lang="scss" scoped>
.log-detail-desc {
  margin-top: var(--space-2);
}

.summary-text {
  display: block;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
