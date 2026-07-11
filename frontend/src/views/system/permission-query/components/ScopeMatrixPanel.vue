<script setup lang="ts">
import { computed } from "vue";
import { SCOPE_MODE_META, REASON_META } from "../utils/types";
import type { QueryScopesResp, ScopeGroup } from "@/api/permission-query";

defineOptions({ name: "PermissionQueryScopeMatrixPanel" });

const props = defineProps<{ result: QueryScopesResp | null }>();

/** 矩阵行：资源类型（去重保序） */
const resourceTypes = computed(() => {
  if (!props.result?.scopeGroups) return [];
  return Array.from(
    new Set(props.result.scopeGroups.map(g => g.resourceTypeCode))
  );
});

/** 矩阵列：操作码（去重保序） */
const operations = computed(() => {
  if (!props.result?.scopeGroups) return [];
  return Array.from(
    new Set(props.result.scopeGroups.map(g => g.operationCode))
  );
});

/** 矩阵行数据：每行一个资源类型 */
const rows = computed(() =>
  resourceTypes.value.map(rt => ({
    rt,
    cells: operations.value.map(op => groupAt(rt, op))
  }))
);

function groupAt(rt: string, op: string): ScopeGroup | undefined {
  return props.result?.scopeGroups.find(
    g => g.resourceTypeCode === rt && g.operationCode === op
  );
}

function modeMeta(mode: string) {
  return SCOPE_MODE_META[mode] ?? { label: mode, type: "info" as const };
}

/** tooltip 内容：INSTANCE 列实例，ALL/DENIED/EMPTY 给说明 */
function cellTooltip(g: ScopeGroup | undefined): string {
  if (!g) return "无数据";
  if (g.scopeMode === "INSTANCE") {
    return g.items.length
      ? g.items.map(i => `${i.resourceName ?? i.resourceCode}`).join("、")
      : "实例为空";
  }
  if (g.scopeMode === "ALL") return "全量授权（不限实例）";
  if (g.scopeMode === "DENIED") return "无操作权限";
  if (g.scopeMode === "EMPTY") return "有权限但条件/互斥过滤后无数据";
  return g.scopeMode;
}

const reasonMeta = computed(() => {
  const r = props.result?.reason;
  if (!r) return null;
  return REASON_META[r] ?? { label: r, type: "info" as const };
});
</script>

<template>
  <div class="scope-matrix-panel">
    <!-- reason 提示（USER_NOT_FOUND / OBJECT_KEY_NOT_FOUND / NO_PERMISSION） -->
    <el-alert
      v-if="result?.reason"
      :title="reasonMeta?.label ?? result.reason"
      type="warning"
      :closable="false"
      show-icon
      class="mb-3"
    >
      主权限未通过或主体/资源未解析，无范围权限返回。
    </el-alert>

    <!-- 主权限元信息 -->
    <el-descriptions
      v-if="result && !result.reason"
      :column="3"
      border
      size="small"
      class="mb-3"
    >
      <el-descriptions-item label="匹配主操作">
        {{
          result.matchedParentOperations.length
            ? result.matchedParentOperations.join("、")
            : "-"
        }}
      </el-descriptions-item>
      <el-descriptions-item label="主权限ID">
        {{
          result.parentPermissionIds.length
            ? result.parentPermissionIds.join(",")
            : "-"
        }}
      </el-descriptions-item>
      <el-descriptions-item label="缓存TTL">
        {{ result.cacheTtlSeconds }}s
      </el-descriptions-item>
    </el-descriptions>

    <!-- 矩阵：行=资源类型，列=操作，格=scopeMode 四态 tag -->
    <el-table
      v-if="result && !result.reason && rows.length"
      :data="rows"
      border
      table-layout="auto"
    >
      <el-table-column label="资源类型 \ 操作" prop="rt" width="160" fixed />
      <el-table-column
        v-for="op in operations"
        :key="op"
        :label="op"
        min-width="140"
      >
        <template #default="{ row }">
          <template v-if="row.cells[operations.indexOf(op)]">
            <el-tooltip
              :content="cellTooltip(row.cells[operations.indexOf(op)])"
              placement="top"
              :show-after="200"
            >
              <el-tag
                size="small"
                :type="
                  modeMeta(row.cells[operations.indexOf(op)].scopeMode).type
                "
                effect="light"
                class="font-mono"
              >
                {{
                  modeMeta(row.cells[operations.indexOf(op)].scopeMode).label
                }}
              </el-tag>
            </el-tooltip>
          </template>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 四态图例 -->
    <div v-if="result && !result.reason && rows.length" class="legend mt-3">
      <span class="legend-title">图例：</span>
      <el-tag
        v-for="(meta, key) in SCOPE_MODE_META"
        :key="key"
        size="small"
        :type="meta.type"
        effect="light"
        class="mr-2"
      >
        {{ meta.label }}
      </el-tag>
    </div>

    <!-- 空状态 -->
    <el-empty
      v-if="!result || (!result.reason && rows.length === 0)"
      description="无范围权限数据"
    />
  </div>
</template>

<style lang="scss" scoped>
.scope-matrix-panel {
  padding: var(--space-3);

  .legend {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .legend-title {
    margin-right: var(--space-1);
  }
}
</style>
