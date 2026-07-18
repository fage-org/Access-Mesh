<script setup lang="ts">
import { onMounted, provide } from "vue";
import { usePermissionGrantV2 } from "./utils/hook";
import RoleTreePanel from "./components/RoleTreePanel.vue";

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

        <!-- 中栏：资源×操作矩阵（T-FE-031 实现） -->
        <div class="grid-center">
          <div class="placeholder">
            <el-empty
              description="权限矩阵将在 T-FE-031 实现"
              :image-size="80"
            />
          </div>
        </div>

        <!-- 右栏：变更流（T-FE-032 实现） -->
        <div class="grid-right">
          <div class="placeholder">
            <el-empty description="变更流将在 T-FE-032 实现" :image-size="80" />
          </div>
        </div>
      </div>

      <!-- 底部保存栏骨架（T-FE-034 实现保存语义）：
           未选角色或只读时不显示保存动作；已选且可管理时按钮保持禁用 -->
      <div
        v-if="store.currentRole.value && !store.readonly.value"
        class="save-bar"
      >
        <el-button type="primary" disabled>保存（T-FE-034 实现）</el-button>
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
  gap: var(--space-2);
  justify-content: flex-end;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
