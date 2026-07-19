<script setup lang="ts">
import { onMounted, provide } from "vue";
import { usePermissionGrantV2 } from "./utils/hook";
import RoleTreePanel from "./components/RoleTreePanel.vue";
import PermissionMatrixPanel from "./components/PermissionMatrixPanel.vue";

defineOptions({ name: "PermissionGrantV2" });

const store = usePermissionGrantV2();
provide("pgV2Store", store);

onMounted(() => {
  // D1 门控：无 ROLE:VIEW 整页无权不发请求
  if (store.canView.value) {
    store.loadRoleTree();
  }
});
</script>

<template>
  <div class="permission-grant-v2-page main-content">
    <!-- 无权状态（D1：无 ROLE:VIEW 整页无权，不发请求） -->
    <div v-if="!store.canView.value" class="no-perm">
      <el-empty description="无权限访问（需要 ROLE:VIEW）" />
    </div>

    <template v-else>
      <!-- 上下文条：业务域 / 当前角色 / 只读标记 / 只读原因 -->
      <div class="page-header">
        <span class="page-title">权限授予 V2</span>
        <span class="page-context">
          业务域：<strong>{{
            store.currentRole.value
              ? store.currentDomainCode.value || "全局域"
              : "（未选择）"
          }}</strong>
        </span>
        <span v-if="store.currentRole.value" class="page-context">
          当前角色：<strong>{{ store.currentRole.value.roleName }}</strong>
          <el-tag
            v-if="store.readonly.value"
            size="small"
            type="warning"
            effect="plain"
            >只读</el-tag
          >
        </span>
        <span
          v-if="store.readonly.value && store.readonlyReason.value"
          class="readonly-reason"
        >
          {{ store.readonlyReason.value }}
        </span>
        <span v-if="store.loadingContext.value" class="loading-hint">
          加载中…
        </span>
      </div>

      <div class="page-grid">
        <!-- 左栏：角色树 -->
        <div class="grid-left">
          <RoleTreePanel />
        </div>

        <!-- 中栏：资源×操作矩阵（T-FE-031） -->
        <div class="grid-center">
          <PermissionMatrixPanel />
        </div>

        <!-- 右栏：变更流（T-FE-034 保存前总览联动实现） -->
        <div class="grid-right">
          <div class="placeholder">
            <el-empty description="变更流将在 T-FE-034 实现" :image-size="80" />
          </div>
        </div>
      </div>

      <!-- 底部保存栏（T-FE-033 两步保存 + 失败提示）：
           SAVE_PREVIEW/fetchBaseline+reconcile/完整离开保护留 T-FE-034 -->
      <div
        v-if="store.currentRole.value && !store.readonly.value"
        class="save-bar"
      >
        <span v-if="store.saveError.value" class="save-error">
          {{ store.saveError.value }}
        </span>
        <span v-if="store.failedChildren.value.length > 0" class="failed-hint">
          {{ store.failedChildren.value.length }} 项子权限保存失败
          <el-button
            size="small"
            type="warning"
            :disabled="store.saving.value || store.baselineStale.value"
            @click="store.retryFailedChildren"
          >
            重试失败项
          </el-button>
        </span>
        <span v-if="store.baselineStale.value" class="stale-hint">
          权限事实已过期
          <el-button size="small" @click="store.reloadBaseline">
            重新加载
          </el-button>
        </span>
        <span class="save-actions">
          <el-button
            size="small"
            :disabled="!store.hasDraft.value || store.saving.value"
            :loading="store.saving.value"
            type="primary"
            @click="store.saveAll"
          >
            保存{{
              store.hasDraft.value
                ? `（${store.allDiff.value.length} 项变更）`
                : ""
            }}
          </el-button>
          <el-button
            v-if="store.hasDraft.value"
            size="small"
            :disabled="store.saving.value"
            @click="store.discardAll"
          >
            放弃全部
          </el-button>
        </span>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.permission-grant-v2-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--header-offset));
  overflow: hidden;
}

.no-perm {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.page-header {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-context {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);

  strong {
    color: var(--el-text-color-primary);
  }
}

.readonly-reason {
  font-size: 12px;
  color: var(--el-color-warning);
}

.loading-hint {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.page-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(200px, 240px) 1fr minmax(300px, 360px);
  gap: 1px;
  min-height: 0;
  background: var(--el-border-color-lighter);
}

.grid-left,
.grid-center,
.grid-right {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
}

.grid-left {
  border-right: 1px solid var(--el-border-color-lighter);
}

.grid-right {
  border-left: 1px solid var(--el-border-color-lighter);
}

.placeholder {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.save-bar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  justify-content: flex-end;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);
}

.save-error {
  font-size: 12px;
  color: var(--el-color-danger);
}

.failed-hint,
.stale-hint {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
  font-size: 12px;
}

.failed-hint {
  color: var(--el-color-warning);
}

.stale-hint {
  color: var(--el-color-danger);
}

.save-actions {
  display: inline-flex;
  gap: var(--space-2);
  margin-left: auto;
}
</style>
