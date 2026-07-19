<script setup lang="ts">
import { onMounted, provide } from "vue";
import { usePermissionGrantV2 } from "./utils/hook";
import RoleTreePanel from "./components/RoleTreePanel.vue";
import PermissionMatrixPanel from "./components/PermissionMatrixPanel.vue";
import SavePreviewSheet from "./components/SavePreviewSheet.vue";

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

        <!-- 右栏：变更流（T-FE-028 实现，SAVE_PREVIEW 警告区联动） -->
        <div class="grid-right">
          <div class="placeholder">
            <el-empty description="变更流将在 T-FE-028 实现" :image-size="80" />
          </div>
        </div>
      </div>

      <!-- saving 顶部进度条（T-FE-034：indeterminate） -->
      <div v-if="store.saving.value" class="saving-bar" />

      <!-- 底部保存栏（T-FE-034：SAVE_PREVIEW/失败/stale 详细视图由 SavePreviewSheet 承载） -->
      <div
        v-if="store.currentRole.value && !store.readonly.value"
        class="save-bar"
      >
        <span class="save-status">
          <span
            v-if="store.savePhase.value === 'SAVE_OUTCOME_UNKNOWN'"
            class="status-unknown"
          >
            保存结果核对中…
          </span>
          <span
            v-else-if="store.failedChildren.value.length > 0"
            class="status-warning"
          >
            {{ store.failedChildren.value.length }} 项子权限待重试
          </span>
          <span v-else-if="store.baselineStale.value" class="status-danger">
            权限事实已过期
          </span>
        </span>
        <span class="save-actions">
          <el-button
            size="small"
            :disabled="
              !store.hasDraft.value ||
              store.saving.value ||
              store.saveOutcomeUnknown.value ||
              store.baselineStale.value
            "
            :loading="store.saving.value"
            type="primary"
            @click="store.requestSave"
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

      <!-- 保存前总览 / 失败恢复 sheet（T-FE-034，interaction §4.5） -->
      <SavePreviewSheet />
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

.saving-bar {
  flex-shrink: 0;
  width: 100%;
  height: 3px;
  overflow: hidden;
  background: var(--el-color-primary-light-8);

  &::after {
    display: block;
    width: 40%;
    height: 100%;
    content: "";
    background: var(--el-color-primary);
    animation: saving-progress 1.2s ease-in-out infinite;
  }
}

@keyframes saving-progress {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(350%);
  }
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

.save-status {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
}

.status-unknown {
  color: var(--el-color-info);
}

.status-warning {
  color: var(--el-color-warning);
}

.status-danger {
  color: var(--el-color-danger);
}

.save-actions {
  display: inline-flex;
  gap: var(--space-2);
  margin-left: auto;
}
</style>
