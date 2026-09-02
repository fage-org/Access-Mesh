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

/** 条件加载状态（T-PERM-033）：非 OK 恒 fail-close */
function conditionStatusMeta(status: string) {
  const map: Record<
    string,
    { label: string; type: "success" | "warning" | "danger" | "info" }
  > = {
    OK: { label: "正常", type: "success" },
    DISABLED: { label: "已停用(fail-close)", type: "warning" },
    NOT_FOUND: { label: "未找到(fail-close)", type: "warning" },
    INVALID: { label: "非法(fail-close)", type: "danger" }
  };
  return map[status] ?? { label: status, type: "info" as const };
}

/** 评估上下文来源（T-PERM-033）：管理员输入 or 回退当前请求 */
const CONTEXT_SOURCE_META: Record<string, string> = {
  ADMIN_INPUT: "管理员输入",
  CURRENT_REQUEST: "当前请求回退"
};
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

      <!-- 条件评估上下文（T-PERM-033） -->
      <el-descriptions
        v-if="result.evaluationContextSource"
        title="条件评估上下文"
        :column="2"
        border
        size="small"
        class="mb-4"
      >
        <el-descriptions-item label="上下文来源">
          {{
            CONTEXT_SOURCE_META[result.evaluationContextSource] ??
            result.evaluationContextSource
          }}
        </el-descriptions-item>
        <el-descriptions-item label="评估用客户端 IP">
          {{ result.evaluatedClientIp ?? "-" }}（无 IP 类条件上下文时为空）
        </el-descriptions-item>
      </el-descriptions>

      <!-- 条件评估明细（T-PERM-033） -->
      <div v-if="result.conditionEvaluations?.length">
        <div class="section-title mb-2">条件评估明细</div>
        <el-table
          :data="result.conditionEvaluations"
          size="small"
          border
          class="mb-4"
        >
          <el-table-column prop="conditionId" label="条件 ID" width="90" />
          <el-table-column label="加载状态" width="150">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="conditionStatusMeta(row.status).type"
                effect="plain"
              >
                {{ conditionStatusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="logic" label="逻辑" width="70">
            <template #default="{ row }">{{ row.logic ?? "-" }}</template>
          </el-table-column>
          <el-table-column label="整体" width="80">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.passed ? 'success' : 'danger'"
                effect="plain"
              >
                {{ row.passed ? "通过" : "拒绝" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="逐项评估（参数已脱敏）">
            <template #default="{ row }">
              <div v-for="(item, i) in row.items" :key="i" class="cond-item">
                <el-tag
                  size="small"
                  :type="item.matched ? 'success' : 'info'"
                  effect="plain"
                  class="mr-2"
                >
                  {{ item.matched ? "满足" : "不满足" }}
                </el-tag>
                <span class="font-mono text-xs">{{ item.type }}</span>
                <span
                  v-if="item.maskedParams"
                  class="text-gray-500 text-xs ml-2"
                >
                  {{ item.maskedParams }}
                </span>
              </div>
              <span v-if="!row.items.length" class="text-gray-400 text-xs">
                无评估项
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 互斥规则丢弃明细（T-PERM-033） -->
      <div v-if="result.conflictDrops?.length">
        <div class="section-title mb-2">互斥规则丢弃</div>
        <el-alert
          type="warning"
          :closable="false"
          class="mb-4"
          :title="`候选命中被权限互斥规则丢弃（规则/角色/权限 ID 见下）`"
        />
        <el-table :data="result.conflictDrops" size="small" border class="mb-4">
          <el-table-column prop="ruleId" label="规则 ID" width="90" />
          <el-table-column prop="roleId" label="角色 ID" width="90" />
          <el-table-column prop="permissionId" label="权限 ID" width="100" />
          <el-table-column
            prop="firstOperationCode"
            label="互斥操作 A"
            width="140"
          />
          <el-table-column
            prop="secondOperationCode"
            label="互斥操作 B"
            width="140"
          />
        </el-table>
      </div>

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

  .cond-item {
    line-height: 1.8;
  }
}
</style>
