<script setup lang="ts">
/**
 * 4.1 权限授予页（v3，T-FE-036）。
 * 查看为主：左栏主体树 + 中栏矩阵（el-table-v2 虚拟滚动）+ 右栏变更清单 + 底部保存条；
 * 授予/撤销、已有授权属性与子权限配置 = 授权弹窗本地事务；授权记录/子权限现状 = 详情层只读展示；统一提交 = apply-grant-plan 单入口。
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
  grantStore,
  allResourceForest,
  allOperationDefs,
  resourceForest,
  operationDefs,
  conditions,
  depsLoading,
  typeCandidates,
  currentTypeCode,
  subjectPermissionTypes,
  matrixLoading,
  includeResourceInherit,
  includeOpInherit,
  resourceKeyword,
  hiddenColumnCodes,
  effective,
  sourceChain,
  unionColumns,
  visibleColumns,
  handleSelectSubject,
  handleSwitchType,
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
  handleSaveAll,
  handleRevertAll,
  locateRequest,
  handleLocateChange
} = usePermissionGrant();

const hasSubject = computed(() => grantStore.context != null);

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
    <div v-if="!canView" class="page-empty-card">
      <el-result
        icon="warning"
        title="无权限"
        sub-title="您没有权限查看权限授予页（需要 ROLE:VIEW），请联系管理员"
      />
    </div>

    <template v-else>
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
            :type-candidates="typeCandidates"
            :current-type-code="currentTypeCode"
            :permission-type-codes="subjectPermissionTypes"
            :visible-columns="visibleColumns"
            :union-columns="unionColumns"
            :mark-info="effective.markInfo"
            :capability="grantStore.capability"
            :matrix-loading="matrixLoading"
            :has-subject="hasSubject"
            :subject-name="grantStore.context?.displayName ?? null"
            :group-hint="groupHint"
            :locate-request="locateRequest"
            @switch-type="handleSwitchType"
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
      <footer
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
          <span class="status-dot" />{{ grantStore.submit.message }}
        </span>
        <span v-else-if="grantStore.isDirty" class="footer-status">
          <span class="status-dot" />{{ grantStore.changeCount }} 条未保存变更
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
      </footer>

      <!-- 授权弹窗（操作 + 资源树单屏编辑，勾选授权/取消撤销） -->
      <GrantDialog
        v-model="dialogVisible"
        :initial="dialogInitial"
        :operations="operationDefs"
        :resource-forest="resourceForest"
        :all-operations="allOperationDefs"
        :all-resource-forest="allResourceForest"
        :conditions="conditions"
        :records="effective.mains"
        :baseline="grantStore.baseline"
        :draft-changes="grantStore.changes"
        :subject-key="
          grantStore.context ?? { roleTypeCode: '', roleExternalId: '' }
        "
        @confirm="handleDialogConfirm"
      />

      <!-- 权限详情仅展示授权记录与子权限；所有变更统一在授权弹窗完成。 -->
      <PermissionDetailDrawer
        v-model="drawerVisible"
        :target="drawerTarget"
        :records="drawerRecords"
        :children-provider="childrenOf"
        :conditions="conditions"
        :undefined-bits-by-record="sourceChain.undefinedBitsByRecord"
      />
    </template>
  </div>
</template>

<style lang="scss" scoped>
/* ==========================================================
   简约商务风（页面私有，不污染其他页面）
   - 画布：浅灰 --el-bg-color-page，面板：白色卡片
   - 层级：1px 浅边框 + 轻阴影 + --radius-lg 圆角
   - 全部使用 Element Plus CSS 变量，dark 模式自动适配
   ========================================================== */
.perm-grant-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  height: calc(100vh - var(--header-offset, 84px));
  padding: var(--space-3);
  background: var(--el-bg-color-page);

  /* 无权限占位：居中白卡片 */
  .page-empty-card {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--radius-lg);
  }

  /* ---------- 三栏：白卡片，间距分隔 ---------- */
  .page-body {
    display: flex;
    flex: 1;
    gap: var(--space-3);
    min-height: 0;

    .subject-col {
      flex-shrink: 0;
      width: 252px;
    }

    .matrix-col {
      flex: 1;
      min-width: 0;
    }

    .change-col {
      flex-shrink: 0;
      width: 312px;
    }
  }

  /* ---------- 底部保存条（卡片化） ---------- */
  .page-footer {
    display: flex;
    flex-shrink: 0;
    gap: var(--space-4);
    align-items: center;
    justify-content: space-between;
    padding: var(--space-3) var(--space-5);
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: var(--radius-lg);
    box-shadow: 0 -2px 12px rgb(15 23 42 / 5%);

    .footer-status {
      display: inline-flex;
      gap: var(--space-2);
      align-items: center;
      font-size: 13px;
      color: var(--el-text-color-secondary);

      .status-dot {
        width: 6px;
        height: 6px;
        background: var(--el-color-warning);
        border-radius: var(--radius-full);
      }

      &.failed {
        color: var(--el-color-danger);

        .status-dot {
          background: var(--el-color-danger);
        }
      }
    }

    .footer-actions {
      display: flex;
      gap: var(--space-2);
    }
  }
}
</style>
