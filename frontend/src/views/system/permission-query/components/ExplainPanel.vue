<script setup lang="ts">
import { REASON_META, IMPACT_LEVEL_META } from "../utils/types";
import type { ExplainResp } from "@/api/permission-query";

defineOptions({ name: "PermissionQueryExplainPanel" });

const props = defineProps<{ result: ExplainResp | null }>();

function reasonLabel(reason: string | null): string {
  if (!reason) return "";
  return REASON_META[reason]?.label ?? reason;
}

function impactMeta(level: string) {
  return IMPACT_LEVEL_META[level] ?? { label: level, type: "info" as const };
}
</script>

<template>
  <div class="explain-panel">
    <template v-if="result">
      <!-- 允许/拒绝大标识 -->
      <el-result
        :icon="result.allowed ? 'success' : 'error'"
        :title="result.allowed ? '允许访问' : '拒绝访问'"
        :sub-title="
          result.allowed ? '当前权限判定通过' : reasonLabel(result.reason)
        "
      />

      <!-- 权限键 -->
      <el-descriptions
        title="权限键"
        :column="2"
        border
        size="small"
        class="mb-4"
      >
        <el-descriptions-item label="业务域">
          {{ result.permission.domainCode || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="资源类型">
          {{ result.permission.resourceTypeCode }}
        </el-descriptions-item>
        <el-descriptions-item label="资源编码">
          {{ result.permission.resourceCode ?? "(全量)" }}
        </el-descriptions-item>
        <el-descriptions-item label="编码类型">
          {{ result.permission.codeType ?? "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="操作码">
          {{ result.permission.operationCode }}
        </el-descriptions-item>
        <el-descriptions-item label="范围模式">
          {{ result.permission.scopeMode }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 来源角色 -->
      <el-descriptions
        v-if="result.sourceRoles.length"
        title="来源角色"
        :column="1"
        border
        size="small"
        class="mb-4"
      >
        <el-descriptions-item
          v-for="(r, i) in result.sourceRoles"
          :key="i"
          :label="r.roleName"
        >
          <span class="font-mono"
            >{{ r.roleTypeCode }} / {{ r.roleExternalId }}</span
          >
          <span v-if="r.via.length" class="text-gray-400 ml-2">
            via: {{ r.via.join(" -> ") }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-else-if="!result.allowed"
        type="info"
        :closable="false"
        class="mb-4"
        title="无命中来源角色"
      />

      <!-- 近期影响事件 -->
      <div v-if="result.recentChanges.length">
        <div class="section-title mb-2">近期影响事件</div>
        <el-timeline>
          <el-timeline-item
            v-for="c in result.recentChanges"
            :key="c.changeLogId"
            :timestamp="c.createdAt"
            :type="impactMeta(c.impactLevel).type"
            placement="top"
          >
            <el-tag
              size="small"
              :type="impactMeta(c.impactLevel).type"
              effect="plain"
              class="mr-2"
            >
              {{ impactMeta(c.impactLevel).label }}
            </el-tag>
            <span>{{ c.message }}</span>
            <div class="text-gray-500 text-xs mt-1">
              操作人: {{ c.operatorName || "-" }} · 原因:
              {{ c.changeReason || "-" }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </template>

    <el-empty v-else description="输入目标权限后点击查询" />
  </div>
</template>

<style lang="scss" scoped>
.explain-panel {
  padding: var(--space-3);

  .section-title {
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-primary);
  }
}
</style>
