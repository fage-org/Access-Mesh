<script setup lang="ts">
import { ref, h, computed } from "vue";
import { useBizDomain } from "./utils/hook";
import BizDomainForm from "./components/BizDomainForm.vue";
import DomainConfigForm from "./components/DomainConfigForm.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { BIZ_DOMAIN_PERMS } from "./utils/perms";
import { configTypeLabel } from "./utils/types";
import type { BizDomainResp } from "@/api/biz-domain";
import type { DomainConfigResp as DomainConfigRow } from "@/api/domain-config";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import AddFill from "~icons/ri/add-circle-line";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Setting from "~icons/ep/setting";

defineOptions({
  name: "SystemBizDomain"
});

const {
  tableData,
  loading,
  searchForm,
  pagination,
  currentDomain,
  loadTable,
  onSearch,
  onReset,
  onPageChange,
  onPageSizeChange,
  selectDomain,
  handleSubmitBizDomain,
  checkGlobalDomainExists,
  handleDeleteBizDomain,
  configData,
  configLoading,
  handleSubmitConfig,
  handleDeleteConfig
} = useBizDomain();

const tableRef = ref();
const configTableRef = ref();
const configSectionRef = ref<HTMLElement | null>(null);

// ========== 权限门控 ==========
// 与 docs/design/frontend/biz-domain.md §权限接线 对齐。
// biz-domain list/detail 门禁 DOMAIN:VIEW；biz-domain 写 + domain-config 子区门禁 SYSTEM_CONFIG:VIEW/MANAGE。
const canView = computed(() => hasPerms(BIZ_DOMAIN_PERMS.DOMAIN_VIEW));
const canManage = computed(() => hasPerms(BIZ_DOMAIN_PERMS.CONFIG_SAVE));
const canViewConfig = computed(() => hasPerms(BIZ_DOMAIN_PERMS.CONFIG_VIEW));

// ========== 主表列定义 ==========
const columns = [
  { label: "域编码", prop: "code", minWidth: 140, slot: "code" },
  { label: "域名称", prop: "name", minWidth: 140 },
  { label: "描述", prop: "description", minWidth: 200 },
  { label: "创建时间", prop: "createdAt", width: 170 },
  {
    label: "操作",
    prop: "operation",
    width: 200,
    fixed: "right" as const,
    slot: "operation"
  }
];

// ========== 子表列定义（纯 el-table） ==========
const configColumns = [
  { label: "配置类型", prop: "configType", minWidth: 180, slot: "configType" },
  { label: "配置值", prop: "extra", minWidth: 280, slot: "extra" },
  { label: "更新时间", prop: "updatedAt", width: 170 },
  {
    label: "操作",
    prop: "operation",
    width: 140,
    fixed: "right" as const,
    slot: "configOperation"
  }
];

// ========== 主表：业务域新建/编辑弹窗 ==========
/** 弹窗打开序号守卫（codex 评审 P3-1，2026-09-09 用户定案序列号修法）：
 *  create 态 await 全局域预查期间用户可能点击其他弹窗入口（编辑/再次新增），
 *  过期打开在预查返回后丢弃——dialogStore.push 无取消机制，慢网下会叠弹窗。 */
let bizDomainFormOpenSeq = 0;

async function openBizDomainForm(mode: "create" | "edit", row?: BizDomainResp) {
  const seq = ++bizDomainFormOpenSeq;
  // T-PERM-046：create 态预查全局域存在性（开关禁用+提示；查询失败按不存在，后端 20057 兜底）
  const globalExists =
    mode === "create" ? await checkGlobalDomainExists() : false;
  // await 期间有更新的弹窗打开请求（任何模式）→ 本次为过期请求，放弃打开
  if (seq !== bizDomainFormOpenSeq) return;
  let formRef: any = null;
  addDialog({
    title: mode === "edit" ? "编辑业务域" : "新增业务域",
    width: "520px",
    contentRenderer: () =>
      h(BizDomainForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        initialData: mode === "edit" ? row : null,
        globalExists
      }),
    beforeSure: async (done: Function, { closeLoading }: any) => {
      if (!formRef) {
        done();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) {
        closeLoading();
        return;
      }
      const formData = formRef.getFormData();
      // edit 以业务键 code 定位（T-PERM-026 收口，原内部主键 id 退役）
      const ok = await handleSubmitBizDomain(
        mode,
        formData,
        mode === "edit" ? row?.code : undefined
      );
      if (ok) done();
      else closeLoading();
    }
  });
}

// ========== 主表：删除业务域 ==========
function onDeleteBizDomain(row: BizDomainResp) {
  handleDeleteBizDomain([row.id]);
}

// ========== 主表：选中域 → 滚动到子表 ==========
// canViewConfig=false（无 SYSTEM_CONFIG:VIEW）时只选中域、不发 /list 请求，避免可避免的 403。
function onConfigLink(row: BizDomainResp) {
  selectDomain(row, canViewConfig.value);
  // 滚动到子表区
  configSectionRef.value?.scrollIntoView({
    behavior: "smooth",
    block: "start"
  });
}

// ========== 子表：域配置新建/编辑弹窗（save upsert） ==========
function openDomainConfigForm(mode: "create" | "edit", row?: DomainConfigRow) {
  if (!currentDomain.value) return;
  let formRef: any = null;
  addDialog({
    title:
      mode === "edit"
        ? `编辑配置（${currentDomain.value.code}）`
        : `新增配置（${currentDomain.value.code}）`,
    width: "520px",
    contentRenderer: () =>
      h(DomainConfigForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        domainCode: currentDomain.value!.code,
        initialData: mode === "edit" ? row : null
      }),
    beforeSure: async (done: Function, { closeLoading }: any) => {
      if (!formRef) {
        done();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) {
        closeLoading();
        return;
      }
      const formData = formRef.getFormData();
      const ok = await handleSubmitConfig(formData);
      if (ok) done();
      else closeLoading();
    }
  });
}

// ========== 子表：删除域配置 ==========
function onDeleteDomainConfig(row: DomainConfigRow) {
  handleDeleteConfig([row.id]);
}
</script>

<template>
  <div class="biz-domain-page">
    <!-- ========== 上区：BizDomain 主表 ========== -->
    <div class="table-wrap main-table-wrap">
      <PureTableBar title="业务域" :columns="columns" @refresh="onSearch">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="关键字" class="mb-0!">
              <el-input
                v-model="searchForm.keyword"
                placeholder="请输入域编码/名称/描述"
                clearable
                class="w-50!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item class="mb-0!">
              <el-button
                type="primary"
                :icon="useRenderIcon(Search)"
                :loading="loading"
                @click="onSearch"
              >
                搜索
              </el-button>
              <el-button :icon="useRenderIcon(Refresh)" @click="onReset()">
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </template>
        <template #buttons>
          <el-button
            v-if="canManage"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openBizDomainForm('create')"
          >
            新增业务域
          </el-button>
        </template>
        <template v-slot="{ size, dynamicColumns }">
          <pure-table
            ref="tableRef"
            row-key="id"
            align-whole="center"
            table-layout="auto"
            :loading="loading"
            :size="size"
            :data="tableData"
            :columns="dynamicColumns"
            :pagination="{
              currentPage: pagination.page,
              pageSize: pagination.size,
              total: pagination.total
            }"
            :header-cell-style="{
              background: 'var(--el-fill-color-light)',
              color: 'var(--el-text-color-primary)'
            }"
            @page-size-change="onPageSizeChange"
            @page-current-change="onPageChange"
          >
            <template #code="{ row }">
              <span class="font-mono text-sm">{{ row.code }}</span>
              <!-- 全域名标识（Resp.global，T-PERM-026 起 Resp 返回） -->
              <el-tag
                v-if="row.global"
                size="small"
                effect="plain"
                class="ml-2"
              >
                全局
              </el-tag>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canManage"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(EditPen)"
                @click="openBizDomainForm('edit', row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canManage"
                class="reset-margin"
                link
                type="danger"
                :size="size"
                :disabled="row.global === true"
                :icon="useRenderIcon(Delete)"
                @click="onDeleteBizDomain(row)"
              >
                删除
              </el-button>
              <el-button
                v-if="canViewConfig"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(Setting)"
                @click="onConfigLink(row)"
              >
                配置
              </el-button>
              <span
                v-if="!canManage && !canViewConfig"
                class="text-sm text-gray-400"
              >
                —
              </span>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>

    <!-- ========== 下区：DomainConfig 子表（选中域后展示） ========== -->
    <div ref="configSectionRef" class="config-section">
      <div class="config-header">
        <span class="config-title">
          {{
            currentDomain
              ? `${currentDomain.name}（${currentDomain.code}）的域配置`
              : "域配置"
          }}
        </span>
        <el-button
          v-if="canManage && currentDomain"
          type="primary"
          size="small"
          :icon="useRenderIcon(AddFill)"
          @click="openDomainConfigForm('create')"
        >
          新增配置
        </el-button>
      </div>

      <el-empty
        v-if="!currentDomain"
        description="请点击上方业务域行的「配置」按钮，查看该域的配置项"
        :image-size="60"
      />
      <el-empty
        v-else-if="!canViewConfig"
        description="无权查看域配置（缺少 SYSTEM_CONFIG:VIEW 权限）"
        :image-size="60"
      />
      <pure-table
        v-else
        ref="configTableRef"
        row-key="id"
        align-whole="center"
        table-layout="auto"
        :loading="configLoading"
        size="small"
        :data="configData"
        :columns="configColumns"
        :header-cell-style="{
          background: 'var(--el-fill-color-light)',
          color: 'var(--el-text-color-primary)'
        }"
      >
        <template #configType="{ row }">
          <el-tag size="small" effect="plain" class="font-mono">
            {{ row.configType }}
          </el-tag>
          <span class="ml-2 text-gray-500 text-xs">
            {{ configTypeLabel(row.configType) }}
          </span>
        </template>
        <template #extra="{ row }">
          <el-tooltip :content="row.extra" placement="top" :show-after="300">
            <span class="extra-cell font-mono text-xs">
              {{ row.extra }}
            </span>
          </el-tooltip>
        </template>
        <template #configOperation="{ row }">
          <el-button
            v-if="canManage"
            class="reset-margin"
            link
            type="primary"
            size="small"
            :icon="useRenderIcon(EditPen)"
            @click="openDomainConfigForm('edit', row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canManage"
            class="reset-margin"
            link
            type="danger"
            size="small"
            :icon="useRenderIcon(Delete)"
            @click="onDeleteDomainConfig(row)"
          >
            删除
          </el-button>
          <span v-if="!canManage" class="text-sm text-gray-400"> — </span>
        </template>
      </pure-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.biz-domain-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
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

.table-wrap {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.main-table-wrap {
  flex: 1;
}

/* PureTableBar 内部布局 */
.table-wrap :deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
}

.table-wrap :deep(.el-scrollbar__wrap) {
  height: 100%;
}

.table-wrap :deep(.el-scrollbar__view) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 让 pure-table 填充剩余空间 */
.table-wrap :deep(.pure-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

/* 表格内容区滚动 */
.table-wrap :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

.table-wrap :deep(.el-table__body-wrapper) {
  max-height: calc(100vh - var(--table-offset));
  overflow-y: auto;
}

/* 下区：域配置子表 */
.config-section {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  padding: var(--space-3);
  margin-top: var(--space-3);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.config-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* extra 单元格截断 */
.extra-cell {
  display: inline-block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 → 同级看顺序，再加 tag 提升至 0,2,1 */
div.biz-domain-page.main-content {
  margin: var(--space-3);
}
</style>
