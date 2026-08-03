<script setup lang="ts">
/**
 * 4.1 权限授予页（v3，T-FE-036）。
 * 查看为主：左栏主体树 + 中栏矩阵（el-table-v2 虚拟滚动）+ 右栏变更清单 + 底部保存条；
 * 授予 = 授权弹窗（操作维度 4 步）；子权限/多分支 = 详情层抽屉；统一提交 = apply-grant-plan 单入口。
 * 路由：/perm/grant?subjectType=ROLE|ORG（角色首期；组织二期占位；PERSONAL 预留不挂路由）。
 */
import { computed } from "vue";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_GRANT_PERMS } from "./utils/perms";
import { usePermissionGrant } from "./utils/hook";
import SubjectTreePanel from "./components/SubjectTreePanel.vue";
import GrantMatrixPanel from "./components/GrantMatrixPanel.vue";
import ChangeListPanel from "./components/ChangeListPanel.vue";
import GrantDialog from "./components/GrantDialog.vue";
import PermissionDetailDrawer from "./components/PermissionDetailDrawer.vue";

defineOptions({ name: "PermGrant" });

const {
  subjectType,
  canView,
  canManage,
  canCondition,
  grantStore,
  resourceForest,
  operationDefs,
  conditions,
  depsLoading,
  includeResourceInherit,
  includeOpInherit,
  resourceKeyword,
  hiddenColumnCodes,
  effective,
  sourceChain,
  matrixTypeCodes,
  unionColumns,
  visibleColumns,
  handleSelectSubject,
  confirmDiscardIfDirty,
  subjectTreeRef,
  activeKey,
  selectingKey,
  groupHint,
  onSelectSubject,
  onSelectGroup,
  frozen,
  dialogVisible,
  dialogInitial,
  openGrantDialog,
  handleDialogConfirm,
  drawerVisible,
  drawerTarget,
  drawerRecords,
  openDetail,
  childrenOf,
  handleAddBranch,
  handleUpdateBranch,
  handleDeleteRecord,
  handleAddChild,
  handleReplaceChild,
  handleSaveAll,
  handleRevertAll,
  locateRequest,
  handleLocateChange
} = usePermissionGrant();

const hasSubject = computed(() => grantStore.context != null);

const pageTitle = computed(() =>
  subjectType.value === "ORG" ? "组织 权限授予" : "角色 权限授予"
);

const capabilityTag = computed(() =>
  grantStore.capability === "edit"
    ? { type: "success" as const, text: "可编辑" }
    : { type: "info" as const, text: "只读" }
);

// ========== 矩阵单元格事件 ==========

function onCellGrant(initial: {
  operationCode: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  scopeMode: "INSTANCE" | "ALL";
}) {
  openGrantDialog(initial);
}

function onCellDetail(target: NonNullable<typeof drawerTarget.value>) {
  openDetail(target);
}
</script>

<template>
  <div class="perm-grant-page">
    <!-- 无矩阵查看权限（ROLE:VIEW）→ 整页占位（§10 无权降级） -->
    <el-result
      v-if="!canView"
      icon="warning"
      title="无权限"
      sub-title="您没有权限查看权限授予页（需要 ROLE:VIEW），请联系管理员"
    />

    <template v-else>
      <!-- 头部：标题 + 主体提示 + 能力标签 -->
      <div class="page-header">
        <span class="page-title">{{ pageTitle }}</span>
        <el-tag size="small" :type="capabilityTag.type" effect="plain">
          {{ capabilityTag.text }}
        </el-tag>
        <span v-if="grantStore.context" class="subject-hint">
          当前主体：{{ grantStore.context.displayName }}（{{
            grantStore.context.roleTypeCode
          }}
          · {{ grantStore.context.roleExternalId }}）
          <template v-if="grantStore.context.fromGroupRoleName">
            · 来自分组角色「{{ grantStore.context.fromGroupRoleName }}」
          </template>
        </span>
        <span v-else class="subject-hint muted">未选择主体</span>
      </div>

      <!-- 三栏：主体树 / 矩阵 / 变更清单 -->
      <div class="page-body">
        <aside class="subject-col">
          <SubjectTreePanel
            ref="subjectTreeRef"
            :subject-type="subjectType"
            :active-key="activeKey"
            :selecting-key="selectingKey"
            :disabled="grantStore.isSaving"
            @request-select="onSelectSubject"
            @request-select-group="onSelectGroup"
          />
        </aside>

        <main class="matrix-col">
          <GrantMatrixPanel
            v-model:keyword="resourceKeyword"
            v-model:include-resource-inherit="includeResourceInherit"
            v-model:include-op-inherit="includeOpInherit"
            v-model:hidden-column-codes="hiddenColumnCodes"
            :source-chain="sourceChain"
            :resource-forest="resourceForest"
            :matrix-type-codes="matrixTypeCodes"
            :visible-columns="visibleColumns"
            :union-columns="unionColumns"
            :mark-info="effective.markInfo"
            :capability="grantStore.capability"
            :loading="depsLoading || grantStore.baselineLoading"
            :has-subject="hasSubject"
            :group-hint="groupHint"
            :locate-request="locateRequest"
            @cell-detail="onCellDetail"
            @cell-grant="onCellGrant"
            @grant="openGrantDialog()"
          />
        </main>

        <aside
          v-if="grantStore.isDirty || grantStore.submit.kind === 'saveFailed'"
          class="change-col"
        >
          <ChangeListPanel
            :changes="grantStore.changes"
            :disabled="frozen"
            :submit="grantStore.submit"
            @locate="handleLocateChange"
            @revert="change => grantStore.revertChange(change.changeId)"
          />
        </aside>
      </div>

      <!-- 底部固定条：放弃全部 / 保存全部 (N) -->
      <div
        v-if="
          hasSubject &&
          (grantStore.isDirty || grantStore.submit.kind !== 'idle')
        "
        class="page-footer"
      >
        <span
          v-if="grantStore.submit.kind === 'saveFailed'"
          class="footer-status failed"
        >
          {{ grantStore.submit.message }}
        </span>
        <span v-else-if="grantStore.isDirty" class="footer-status">
          {{ grantStore.changeCount }} 条未保存变更
        </span>
        <div class="footer-actions">
          <el-button
            :disabled="!grantStore.isDirty || frozen"
            @click="handleRevertAll"
          >
            放弃全部
          </el-button>
          <el-button
            v-if="canManage && grantStore.capability === 'edit'"
            type="primary"
            :loading="grantStore.isSaving"
            :disabled="!grantStore.isDirty || frozen"
            @click="handleSaveAll"
          >
            保存全部（{{ grantStore.changeCount }}）
          </el-button>
        </div>
      </div>

      <!-- 授权弹窗（操作维度 4 步，方案二全量语义） -->
      <GrantDialog
        v-model="dialogVisible"
        :initial="dialogInitial"
        :operations="operationDefs"
        :resource-forest="resourceForest"
        :conditions="conditions"
        :records="effective.mains"
        :can-condition="canCondition"
        @confirm="handleDialogConfirm"
      />

      <!-- 详情层抽屉（多分支 / 子权限） -->
      <PermissionDetailDrawer
        v-model="drawerVisible"
        :target="drawerTarget"
        :records="drawerRecords"
        :children-provider="childrenOf"
        :conditions="conditions"
        :operations="operationDefs"
        :resource-forest="resourceForest"
        :can-manage="canManage && grantStore.capability === 'edit' && !frozen"
        :can-condition="canCondition"
        :undefined-bits-by-record="sourceChain.undefinedBitsByRecord"
        @add-branch="handleAddBranch"
        @update-branch="handleUpdateBranch"
        @delete-record="handleDeleteRecord"
        @add-child="handleAddChild"
        @replace-child="handleReplaceChild"
      />
    </template>
  </div>
</template>

<style lang="scss" scoped>
.perm-grant-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--header-offset, 84px));

  .page-header {
    display: flex;
    gap: var(--space-2);
    align-items: center;
    padding: var(--space-2) var(--space-3);
    border-bottom: 1px solid var(--el-border-color-lighter);

    .page-title {
      font-size: 16px;
      font-weight: 600;
    }

    .subject-hint {
      font-size: 12px;
      color: var(--el-text-color-secondary);

      &.muted {
        color: var(--el-text-color-placeholder);
      }
    }
  }

  .page-body {
    display: flex;
    flex: 1;
    min-height: 0;

    .subject-col {
      flex-shrink: 0;
      width: 240px;
      border-right: 1px solid var(--el-border-color-lighter);
    }

    .matrix-col {
      flex: 1;
      min-width: 0;
    }

    .change-col {
      flex-shrink: 0;
      width: 300px;
      border-left: 1px solid var(--el-border-color-lighter);
    }
  }

  .page-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-2) var(--space-3);
    background: var(--el-bg-color);
    border-top: 1px solid var(--el-border-color-lighter);
    box-shadow: 0 -2px 8px rgb(0 0 0 / 4%);

    .footer-status {
      font-size: 13px;
      color: var(--el-text-color-secondary);

      &.failed {
        color: var(--el-color-danger);
      }
    }
  }
}
</style>
