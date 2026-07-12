<script setup lang="ts">
import { ref, onMounted, onUnmounted, provide } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { ElMessageBox } from "element-plus";
import { usePermissionGrant } from "./utils/hook";
import RoleTreePanel from "./components/RoleTreePanel.vue";
import PermissionMatrixPanel from "./components/PermissionMatrixPanel.vue";
import RightPanel from "./components/RightPanel.vue";
import AdditionalSettingDialog from "./components/AdditionalSettingDialog.vue";
import ChildPermissionDrawer from "./components/ChildPermissionDrawer.vue";
import type {
  AdditionalSettingContext,
  ChildPermissionContext
} from "./utils/types";

defineOptions({ name: "PermissionGrant" });

const store = usePermissionGrant();
provide("pgStore", store);

// 弹窗状态（中栏 emit -> index.vue 管理）
const settingVisible = ref(false);
const settingContext = ref<AdditionalSettingContext | null>(null);
const childVisible = ref(false);
const childContext = ref<ChildPermissionContext | null>(null);

function onOpenSetting(ctx: AdditionalSettingContext) {
  settingContext.value = ctx;
  settingVisible.value = true;
}

function onOpenChild(ctx: ChildPermissionContext) {
  childContext.value = ctx;
  childVisible.value = true;
}

onMounted(() => {
  if (store.canView.value) {
    store.loadStaticData();
    store.loadRoleTree();
  }
});

// P1-8：路由导航离开保护（有草稿时拦截 Vue Router 内部导航）
onBeforeRouteLeave(async () => {
  if (store.hasDraft.value) {
    try {
      await ElMessageBox.confirm(
        "当前有未保存的变更，离开将丢弃。是否继续？",
        "离开页面",
        { type: "warning" }
      );
    } catch {
      return false;
    }
  }
});

// P1-8：组件卸载时移除 beforeunload 监听器（避免内存泄漏）
onUnmounted(() => {
  store.cleanup();
});
</script>

<template>
  <div class="permission-grant-page main-content">
    <!-- 无权状态（§10：无 ROLE:VIEW 整页无权，不发请求） -->
    <div v-if="!store.canView.value" class="no-perm">
      <el-empty description="无权限访问（需要 ROLE:VIEW）" />
    </div>

    <template v-else>
      <div class="page-header">
        <span class="page-title">权限授予</span>
        <span class="page-context">
          业务域：<strong>{{ store.currentDomainCode.value }}</strong>
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
      </div>

      <div class="page-grid">
        <!-- 左栏：角色树 -->
        <div class="grid-left">
          <RoleTreePanel />
        </div>

        <!-- 中栏：资源×操作权限树表 -->
        <div class="grid-center">
          <PermissionMatrixPanel
            @open-setting="onOpenSetting"
            @open-child="onOpenChild"
          />
        </div>

        <!-- 右栏：当前权限 + 本次变更 -->
        <div class="grid-right">
          <RightPanel />
        </div>
      </div>
    </template>

    <AdditionalSettingDialog
      v-model="settingVisible"
      :context="settingContext"
    />
    <ChildPermissionDrawer
      v-model="childVisible"
      :context="childContext"
      @open-setting="onOpenSetting"
    />
  </div>
</template>

<style lang="scss" scoped>
.permission-grant-page {
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
</style>
