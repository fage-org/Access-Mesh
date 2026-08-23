<script setup lang="ts">
import { ref, computed } from "vue";
import { usePermissionQuery } from "./utils/hook";
import SubjectInputBar from "./components/SubjectInputBar.vue";
import ScopeMatrixPanel from "./components/ScopeMatrixPanel.vue";
import ExplainPanel from "./components/ExplainPanel.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { SCOPE_MODE_META, isAllMode } from "./utils/types";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";

defineOptions({ name: "SystemPermissionQuery" });

const { canQuery, tab1, tab2, tab3 } = usePermissionQuery();
const activeTab = ref("effective");

// ========== Tab1 列定义 ==========
const tab1Columns = [
  {
    label: "资源类型",
    prop: "resourceTypeCode",
    width: 120,
    slot: "resourceTypeCode"
  },
  {
    label: "资源编码",
    prop: "resourceCode",
    minWidth: 160,
    slot: "resourceCode"
  },
  {
    label: "资源名称",
    prop: "resourceName",
    minWidth: 140,
    slot: "resourceName"
  },
  {
    label: "操作码",
    prop: "operationCodes",
    minWidth: 160,
    slot: "operationCodes"
  },
  { label: "范围模式", prop: "scopeMode", width: 110, slot: "scopeMode" },
  {
    label: "来源角色",
    prop: "sourceRoles",
    minWidth: 200,
    slot: "sourceRoles"
  },
  {
    label: "来源数",
    prop: "sourceRoleCount",
    width: 90,
    slot: "sourceRoleCount"
  }
];

// ========== Tab1 行内派生展示 ==========
function modeMeta(mode: string) {
  return SCOPE_MODE_META[mode] ?? { label: mode, type: "info" as const };
}

function sourceRoleNames(row: any): string {
  if (!row.sourceRoles?.length) return "-";
  return row.sourceRoles.map((r: any) => r.roleName).join("、");
}

// ========== Tab3 scopeMode 切换：ALL 时禁用 resourceCode/codeType ==========
const tab3IsAll = computed(() => isAllMode(tab3.form.scopeMode));

/** Tab1 行复合 key：resourceTypeCode + codeType + resourceCode/ALL + scopeMode + 排序后 operationCodes
 *  同一四元组可能存在多个操作授权（评审 P2 修复：追加 operationCodes 保证唯一） */
function rowKey(row: any) {
  const ops = (row.operationCodes ?? []).slice().sort().join(",");
  return (
    row.resourceTypeCode +
    ":" +
    (row.codeType ?? "null") +
    ":" +
    (row.resourceCode ?? "ALL") +
    ":" +
    row.scopeMode +
    ":" +
    ops
  );
}
</script>

<template>
  <div class="permission-query-page">
    <!-- 整页无权状态：路由框架不消费 meta.auths，页面入口必须立即检查 -->
    <el-result
      v-if="!canQuery"
      icon="warning"
      title="无权限"
      sub-title="您没有权限排查权限，请联系管理员"
    />

    <div v-else class="query-content">
      <el-tabs v-model="activeTab" class="query-tabs">
        <!-- ========== Tab1: 当前有效权限（effective-permissions） ========== -->
        <el-tab-pane label="当前有效权限" name="effective">
          <div class="tab-pane-content">
            <el-form
              :inline="true"
              :model="tab1.form"
              class="search-form-inline"
            >
              <SubjectInputBar
                :target-type="tab1.form.targetType"
                :subject-type-code="tab1.form.subjectTypeCode"
                :subject-external-id="tab1.form.subjectExternalId"
                :role-type-code="tab1.form.roleTypeCode"
                :role-external-id="tab1.form.roleExternalId"
                :domain-code="tab1.form.domainCode"
                @update:target-type="tab1.form.targetType = $event"
                @update:subject-type-code="tab1.form.subjectTypeCode = $event"
                @update:subject-external-id="
                  tab1.form.subjectExternalId = $event
                "
                @update:role-type-code="tab1.form.roleTypeCode = $event"
                @update:role-external-id="tab1.form.roleExternalId = $event"
                @update:domain-code="tab1.form.domainCode = $event"
                @target-type-change="tab1.onTargetTypeChange()"
              />
              <el-form-item label="资源类型" class="mb-0!">
                <el-input
                  v-model="tab1.form.resourceTypeCodes"
                  placeholder="逗号分隔，如 REPORT,DATA"
                  class="w-48!"
                />
              </el-form-item>
              <el-form-item label="操作码" class="mb-0!">
                <el-input
                  v-model="tab1.form.operationCodes"
                  placeholder="逗号分隔，如 DATA_READ"
                  class="w-44!"
                />
              </el-form-item>
              <el-form-item label="资源关键字" class="mb-0!">
                <el-input
                  v-model="tab1.form.resourceKeyword"
                  placeholder="模糊匹配"
                  class="w-36!"
                  @keyup.enter="tab1.onSearch()"
                />
              </el-form-item>
              <el-form-item class="mb-0!">
                <el-button
                  type="primary"
                  :icon="useRenderIcon(Search)"
                  :loading="tab1.loading.value"
                  @click="tab1.onSearch()"
                >
                  查询
                </el-button>
                <el-button
                  :icon="useRenderIcon(Refresh)"
                  @click="tab1.onReset()"
                >
                  重置
                </el-button>
              </el-form-item>
            </el-form>

            <PureTableBar title="" :columns="tab1Columns">
              <template v-slot="{ size, dynamicColumns }">
                <pure-table
                  :row-key="rowKey"
                  align-whole="center"
                  table-layout="auto"
                  :loading="tab1.loading.value"
                  :size="size"
                  :data="tab1.result.value?.items ?? []"
                  :columns="dynamicColumns"
                  :pagination="{
                    currentPage: tab1.form.pageNum,
                    pageSize: tab1.form.pageSize,
                    total: tab1.result.value?.total ?? 0
                  }"
                  @page-size-change="tab1.onPageSizeChange"
                  @page-current-change="tab1.onPageChange"
                >
                  <template #resourceTypeCode="{ row }">
                    <el-tag size="small" effect="plain" class="font-mono">
                      {{ row.resourceTypeCode }}
                    </el-tag>
                  </template>
                  <template #resourceCode="{ row }">
                    <span v-if="row.resourceCode" class="font-mono">{{
                      row.resourceCode
                    }}</span>
                    <el-tag v-else size="small" type="warning" effect="light"
                      >全量</el-tag
                    >
                  </template>
                  <template #resourceName="{ row }">
                    {{ row.resourceName ?? "-" }}
                  </template>
                  <template #operationCodes="{ row }">
                    <el-tag
                      v-for="op in row.operationCodes"
                      :key="op"
                      size="small"
                      type="info"
                      effect="plain"
                      class="mr-1 font-mono"
                    >
                      {{ op }}
                    </el-tag>
                  </template>
                  <template #scopeMode="{ row }">
                    <el-tag
                      size="small"
                      :type="modeMeta(row.scopeMode).type"
                      effect="light"
                    >
                      {{ modeMeta(row.scopeMode).label }}
                    </el-tag>
                  </template>
                  <template #sourceRoles="{ row }">
                    <el-tooltip
                      :content="sourceRoleNames(row)"
                      placement="top"
                      :show-after="200"
                      :disabled="!row.sourceRoles?.length"
                    >
                      <span class="source-cell">{{
                        sourceRoleNames(row)
                      }}</span>
                    </el-tooltip>
                  </template>
                  <template #sourceRoleCount="{ row }">
                    <span>{{ row.sourceRoleCount }}</span>
                    <el-tag
                      v-if="row.sourceRolesTruncated"
                      size="small"
                      type="warning"
                      effect="plain"
                      class="ml-1"
                    >
                      截断
                    </el-tag>
                  </template>
                </pure-table>
              </template>
            </PureTableBar>
          </div>
        </el-tab-pane>

        <!-- ========== Tab2: 范围权限四态（query-scopes，仅 USER） ========== -->
        <el-tab-pane label="范围权限四态" name="scopes">
          <div class="tab-pane-content">
            <el-form
              :inline="true"
              :model="tab2.form"
              class="search-form-inline"
            >
              <!-- 仅 USER 主体（query-scopes 只解析用户） -->
              <el-form-item label="用户类型" class="mb-0!">
                <el-select v-model="tab2.form.subjectTypeCode" class="w-40!">
                  <el-option label="本地用户 LOCAL_USER" value="LOCAL_USER" />
                  <el-option label="普通用户 USER" value="USER" />
                </el-select>
              </el-form-item>
              <el-form-item label="用户标识" class="mb-0!">
                <el-input
                  v-model="tab2.form.subjectExternalId"
                  placeholder="subjectExternalId"
                  class="w-40!"
                />
              </el-form-item>
              <el-form-item label="业务域" class="mb-0!">
                <el-input
                  v-model="tab2.form.domainCode"
                  placeholder="example"
                  class="w-32!"
                />
              </el-form-item>
              <el-form-item label="主资源类型" class="mb-0!">
                <el-input
                  v-model="tab2.form.parentResourceTypeCode"
                  placeholder="REPORT"
                  class="w-32!"
                />
              </el-form-item>
              <el-form-item label="主资源编码" class="mb-0!">
                <el-input
                  v-model="tab2.form.parentResourceCode"
                  placeholder="report:sales"
                  class="w-40!"
                />
              </el-form-item>
              <el-form-item label="主操作" class="mb-0!">
                <el-input
                  v-model="tab2.form.parentOperationCodes"
                  placeholder="逗号分隔"
                  class="w-44!"
                  @keyup.enter="tab2.onSearch()"
                />
              </el-form-item>
              <el-form-item label="范围资源类型" class="mb-0!">
                <el-input
                  v-model="tab2.form.scopeResourceTypeCodes"
                  placeholder="逗号分隔"
                  class="w-36!"
                />
              </el-form-item>
              <el-form-item label="范围操作" class="mb-0!">
                <el-input
                  v-model="tab2.form.scopeOperationCodes"
                  placeholder="逗号分隔"
                  class="w-48!"
                />
              </el-form-item>
              <el-form-item class="mb-0!">
                <el-button
                  type="primary"
                  :icon="useRenderIcon(Search)"
                  :loading="tab2.loading.value"
                  @click="tab2.onSearch()"
                >
                  查询
                </el-button>
                <el-button
                  :icon="useRenderIcon(Refresh)"
                  @click="tab2.onReset()"
                >
                  重置
                </el-button>
              </el-form-item>
            </el-form>

            <ScopeMatrixPanel :result="tab2.result.value" />
          </div>
        </el-tab-pane>

        <!-- ========== Tab3: 单权限解释（explain） ========== -->
        <el-tab-pane label="单权限解释" name="explain">
          <div class="tab-pane-content">
            <el-form
              :inline="true"
              :model="tab3.form"
              class="search-form-inline"
            >
              <SubjectInputBar
                :target-type="tab3.form.targetType"
                :subject-type-code="tab3.form.subjectTypeCode"
                :subject-external-id="tab3.form.subjectExternalId"
                :role-type-code="tab3.form.roleTypeCode"
                :role-external-id="tab3.form.roleExternalId"
                :domain-code="tab3.form.domainCode"
                @update:target-type="tab3.form.targetType = $event"
                @update:subject-type-code="tab3.form.subjectTypeCode = $event"
                @update:subject-external-id="
                  tab3.form.subjectExternalId = $event
                "
                @update:role-type-code="tab3.form.roleTypeCode = $event"
                @update:role-external-id="tab3.form.roleExternalId = $event"
                @update:domain-code="tab3.form.domainCode = $event"
                @target-type-change="tab3.onTargetTypeChange()"
              />
              <el-form-item label="资源类型" class="mb-0!">
                <el-input
                  v-model="tab3.form.resourceTypeCode"
                  placeholder="REPORT"
                  class="w-32!"
                />
              </el-form-item>
              <el-form-item label="资源编码" class="mb-0!">
                <el-input
                  v-model="tab3.form.resourceCode"
                  placeholder="report:sales"
                  :disabled="tab3IsAll"
                  class="w-40!"
                />
              </el-form-item>
              <el-form-item label="编码类型" class="mb-0!">
                <el-input
                  v-model="tab3.form.codeType"
                  placeholder="default"
                  :disabled="tab3IsAll"
                  class="w-28!"
                />
              </el-form-item>
              <el-form-item label="操作码" class="mb-0!">
                <el-input
                  v-model="tab3.form.operationCode"
                  placeholder="DATA_READ"
                  class="w-36!"
                  @keyup.enter="tab3.onSearch()"
                />
              </el-form-item>
              <el-form-item label="范围模式" class="mb-0!">
                <el-select v-model="tab3.form.scopeMode" class="w-32!">
                  <el-option label="实例 INSTANCE" value="INSTANCE" />
                  <el-option label="全量 ALL" value="ALL" />
                </el-select>
              </el-form-item>
              <el-form-item class="mb-0!">
                <el-button
                  type="primary"
                  :icon="useRenderIcon(Search)"
                  :loading="tab3.loading.value"
                  @click="tab3.onSearch()"
                >
                  查询
                </el-button>
                <el-button
                  :icon="useRenderIcon(Refresh)"
                  @click="tab3.onReset()"
                >
                  重置
                </el-button>
              </el-form-item>
            </el-form>

            <ExplainPanel :result="tab3.result.value" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.permission-query-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.query-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.query-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  overflow: hidden;

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow: auto;
  }
}

.tab-pane-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.search-form-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;

  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    padding-right: 4px;
  }
}

.source-cell {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 -> 同级看顺序，再加 tag 提升至 0,2,1 */
div.permission-query-page.main-content {
  margin: var(--space-3);
}
</style>
