<script setup lang="ts">
import { computed, h, ref } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { message } from "@/utils/message";
import ServiceForm from "./components/ServiceForm.vue";
import MappingForm from "./components/MappingForm.vue";
import SyncForm from "./components/SyncForm.vue";
import { useServiceInterface } from "./utils/hook";
import { SERVICE_INTERFACE_PERMS } from "./utils/perms";
import {
  HTTP_METHOD_OPTIONS,
  methodTagType,
  type MappingFormData,
  type ServiceConfigFormData,
  type ServiceSummary
} from "./utils/types";
import type {
  ApiMappingResp,
  ServiceConfigSyncReq
} from "@/api/service-interface";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Connection from "~icons/ep/connection";
import Switch from "~icons/ep/switch";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";

defineOptions({ name: "SystemServiceInterface" });

const {
  loading,
  mappingLoading,
  serviceSearch,
  visibleServices,
  currentService,
  filteredMappings,
  mappingSearch,
  selectService,
  resetMappingFilters,
  submitService,
  deleteService,
  submitMapping,
  deleteMapping,
  runFullSync,
  refreshMappings,
  loadDirectory
} = useServiceInterface();

const mappingTableRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/service-interface-mapping.md §权限接线 对齐。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canView = computed(() => hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_VIEW));
const canManageService = computed(() =>
  hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_MANAGE)
);
const canSync = computed(() => hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_SYNC));
const canManageMapping = computed(() =>
  hasPerms(SERVICE_INTERFACE_PERMS.MAPPING_MANAGE)
);

// ========== 子表列定义（接口映射） ==========
const mappingColumns = [
  { label: "方法", prop: "httpMethod", width: 72, slot: "httpMethod" },
  {
    label: "Gateway 路径",
    prop: "pathPattern",
    minWidth: 200,
    slot: "path"
  },
  {
    label: "资源实体",
    prop: "resourceEntityId",
    width: 92,
    slot: "resource"
  },
  { label: "顺序", prop: "matchOrder", width: 58 },
  { label: "状态", prop: "enabled", width: 70, slot: "enabled" },
  { label: "更新时间", prop: "updatedAt", width: 138, slot: "updatedAt" },
  {
    label: "操作",
    prop: "operation",
    width: 96,
    fixed: "right" as const,
    slot: "mappingOperation"
  }
];

type DialogForm<T> = {
  validate: () => Promise<boolean>;
  getFormData: () => T;
};

function getDialogForm<T>(element: unknown): DialogForm<T> | null {
  if (
    element &&
    typeof element === "object" &&
    "validate" in element &&
    "getFormData" in element &&
    typeof element.validate === "function" &&
    typeof element.getFormData === "function"
  ) {
    return element as DialogForm<T>;
  }
  return null;
}

// ========== 服务登记/编辑弹窗 ==========
function openServiceForm(mode: "create" | "edit", row?: ServiceSummary) {
  const initialData = mode === "edit" ? row : null;
  if (mode === "edit" && !initialData) return;
  let formRef: DialogForm<ServiceConfigFormData> | null = null;
  addDialog({
    title:
      mode === "create"
        ? "登记接入服务"
        : `编辑服务 · ${initialData?.serviceCode}`,
    width: "560px",
    contentRenderer: () =>
      h(ServiceForm, {
        ref: element => {
          formRef = getDialogForm<ServiceConfigFormData>(element);
        },
        mode,
        initialData
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitService(
        formRef.getFormData() as ServiceConfigFormData,
        mode
      );
      if (saved) done();
      else closeLoading();
    }
  });
}

function onDeleteService(row: ServiceSummary) {
  deleteService(row);
}

// ========== 接口映射新增/编辑弹窗 ==========
function openMappingForm(mode: "create" | "edit", row?: ApiMappingResp) {
  const service = currentService.value;
  if (!service || (mode === "edit" && !row)) return;
  let formRef: DialogForm<MappingFormData> | null = null;
  addDialog({
    title:
      mode === "create"
        ? `新增接口映射 · ${service.serviceCode}`
        : `编辑接口映射 · ${row?.httpMethod} ${row?.pathPattern}`,
    width: "580px",
    contentRenderer: () =>
      h(MappingForm, {
        ref: element => {
          formRef = getDialogForm<MappingFormData>(element);
        },
        mode,
        serviceCode: service.serviceCode,
        initialData: row || null
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved =
        mode === "create"
          ? await submitMapping(formRef.getFormData() as MappingFormData, mode)
          : await submitMapping(
              formRef.getFormData() as MappingFormData,
              mode,
              row as ApiMappingResp
            );
      if (saved) done();
      else closeLoading();
    }
  });
}

// ========== 同步接口弹窗 ==========
function openSyncForm() {
  const service = currentService.value;
  if (!service) return;
  let formRef: DialogForm<ServiceConfigSyncReq | null> | null = null;
  addDialog({
    title: `同步接口清单 · ${service.serviceCode}`,
    width: "720px",
    contentRenderer: () =>
      h(SyncForm, {
        ref: element => {
          formRef = getDialogForm<ServiceConfigSyncReq | null>(element);
        },
        service
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const request = formRef.getFormData() as ServiceConfigSyncReq | null;
      if (!request) {
        closeLoading();
        return;
      }
      const result = await runFullSync(request);
      if (!result) {
        closeLoading();
        return;
      }
      message(
        `同步完成：新增 ${result.createdMappings}，更新 ${result.updatedMappings}，清理 ${result.deletedMappings} 条映射`,
        { type: "success", duration: 5000 }
      );
      done();
    }
  });
}
</script>

<template>
  <div class="service-interface-page">
    <el-empty
      v-if="!canView"
      description="你没有查看服务与接口映射的权限"
      class="permission-empty"
    />
    <template v-else>
      <!-- ========== 左侧：服务列表 ========== -->
      <aside class="service-panel">
        <div class="panel-header">
          <div class="panel-title">
            服务
            <span class="panel-count">{{ visibleServices.length }}</span>
          </div>
          <div class="panel-header-actions">
            <el-button
              size="small"
              :icon="useRenderIcon(Refresh)"
              :loading="loading"
              title="刷新目录"
              @click="loadDirectory"
            />
            <el-button
              v-if="canManageService"
              size="small"
              type="primary"
              plain
              :icon="useRenderIcon(AddFill)"
              @click="openServiceForm('create')"
            >
              登记
            </el-button>
          </div>
        </div>
        <div class="panel-search">
          <el-input
            v-model="serviceSearch"
            placeholder="编码 / 名称 / 路径"
            clearable
            size="small"
            :prefix-icon="useRenderIcon(Search)"
          />
        </div>
        <div v-loading="loading" class="service-list">
          <div
            v-for="service in visibleServices"
            :key="service.serviceCode"
            class="service-item"
            :class="{
              active: currentService?.serviceCode === service.serviceCode,
              disabled: service.status === 0
            }"
            @click="selectService(service.serviceCode)"
          >
            <div class="service-item__head">
              <span class="service-item__name">{{ service.name }}</span>
              <span
                class="service-item__dot"
                :class="service.status === 1 ? 'is-on' : 'is-off'"
              />
            </div>
            <div class="service-item__sub">
              <span class="font-mono">{{ service.serviceCode }}</span>
              <span class="service-item__count"
                >{{ service.enabledApiCount }}/{{ service.apiCount }}</span
              >
            </div>
          </div>
          <el-empty
            v-if="visibleServices.length === 0"
            :image-size="48"
            description="暂无服务"
          />
        </div>
      </aside>

      <!-- ========== 右侧：接口映射 ========== -->
      <section class="mapping-area">
        <template v-if="currentService">
          <!-- 选中服务信息条 -->
          <div class="service-info-bar">
            <div class="service-info">
              <span class="font-mono text-sm">{{
                currentService.serviceCode
              }}</span>
              <span class="service-name">{{ currentService.name }}</span>
              <el-tag
                :type="currentService.status === 1 ? 'success' : 'info'"
                size="small"
                effect="light"
              >
                {{ currentService.status === 1 ? "启用" : "停用" }}
              </el-tag>
              <span class="service-path">
                基础路径
                <span class="font-mono">{{
                  currentService.basePath || "/"
                }}</span>
              </span>
            </div>
            <div class="service-actions">
              <el-button
                v-if="canManageService"
                size="small"
                :icon="useRenderIcon(EditPen)"
                @click="openServiceForm('edit', currentService)"
              >
                编辑服务
              </el-button>
              <el-button
                v-if="canManageService"
                size="small"
                type="danger"
                plain
                :icon="useRenderIcon(Delete)"
                @click="onDeleteService(currentService)"
              >
                删除服务
              </el-button>
            </div>
          </div>

          <!-- 接口映射表 -->
          <div class="table-wrap">
            <PureTableBar
              title="接口映射"
              :columns="mappingColumns"
              @refresh="refreshMappings"
            >
              <template #title>
                <el-form :inline="true" class="search-form-inline">
                  <el-form-item label="路径/资源" class="mb-0!">
                    <el-input
                      v-model="mappingSearch.keyword"
                      placeholder="Gateway 路径或资源 ID"
                      clearable
                      class="w-56!"
                      :prefix-icon="useRenderIcon(Search)"
                    />
                  </el-form-item>
                  <el-form-item label="方法" class="mb-0!">
                    <el-select
                      v-model="mappingSearch.method"
                      placeholder="全部"
                      clearable
                      class="w-28!"
                    >
                      <el-option
                        v-for="item in HTTP_METHOD_OPTIONS"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="状态" class="mb-0!">
                    <el-select v-model="mappingSearch.enabled" class="w-28!">
                      <el-option label="全部" value="all" />
                      <el-option label="有效" value="enabled" />
                      <el-option label="停用" value="disabled" />
                    </el-select>
                  </el-form-item>
                  <el-form-item class="mb-0!">
                    <el-button link type="primary" @click="resetMappingFilters">
                      重置
                    </el-button>
                  </el-form-item>
                </el-form>
              </template>
              <template #buttons>
                <el-button
                  v-if="canSync"
                  type="warning"
                  plain
                  :icon="useRenderIcon(Switch)"
                  @click="openSyncForm"
                >
                  同步接口
                </el-button>
                <el-button
                  v-if="canManageMapping"
                  type="primary"
                  :icon="useRenderIcon(Connection)"
                  @click="openMappingForm('create')"
                >
                  新增映射
                </el-button>
              </template>
              <template v-slot="{ size, dynamicColumns }">
                <pure-table
                  ref="mappingTableRef"
                  row-key="id"
                  align-whole="center"
                  table-layout="auto"
                  :loading="mappingLoading"
                  :size="size"
                  :data="filteredMappings"
                  :columns="dynamicColumns"
                  :header-cell-style="{
                    background: 'var(--el-fill-color-light)',
                    color: 'var(--el-text-color-primary)'
                  }"
                >
                  <template #httpMethod="{ row }">
                    <el-tag
                      size="small"
                      effect="light"
                      class="method-tag"
                      :type="methodTagType(row.httpMethod)"
                    >
                      {{ row.httpMethod }}
                    </el-tag>
                  </template>
                  <template #path="{ row }">
                    <el-tooltip
                      :content="row.pathPattern"
                      placement="top"
                      :show-after="300"
                    >
                      <span class="path-cell font-mono">{{
                        row.pathPattern
                      }}</span>
                    </el-tooltip>
                  </template>
                  <template #resource="{ row }">
                    <span class="resource-cell"
                      >#{{ row.resourceEntityId }}</span
                    >
                  </template>
                  <template #enabled="{ row }">
                    <el-tag
                      :type="row.enabled ? 'success' : 'info'"
                      size="small"
                      effect="plain"
                    >
                      {{ row.enabled ? "有效" : "停用" }}
                    </el-tag>
                  </template>
                  <template #updatedAt="{ row }">
                    <span class="time-cell">{{
                      row.updatedAt || row.createdAt || "-"
                    }}</span>
                  </template>
                  <template #mappingOperation="{ row }">
                    <el-button
                      v-if="canManageMapping"
                      class="reset-margin"
                      link
                      type="primary"
                      :size="size"
                      @click="openMappingForm('edit', row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-if="canManageMapping"
                      class="reset-margin"
                      link
                      type="danger"
                      :size="size"
                      @click="deleteMapping(row)"
                    >
                      移除
                    </el-button>
                    <span
                      v-if="!canManageMapping"
                      class="text-sm text-gray-400"
                    >
                      -
                    </span>
                  </template>
                  <template #empty>
                    <el-empty
                      :image-size="60"
                      description="暂无接口映射，可同步服务清单或手工新增"
                    />
                  </template>
                </pure-table>
              </template>
            </PureTableBar>
          </div>
        </template>

        <el-empty
          v-else
          description="请先在左侧选择服务"
          class="mapping-empty"
        />
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@media (width <= 48rem) {
  .service-interface-page {
    grid-template-rows: auto 1fr;
    grid-template-columns: 1fr;
  }

  .service-panel {
    max-height: 14rem;
  }
}

.service-interface-page {
  display: grid;
  grid-template-columns: minmax(180px, 240px) 1fr;
  gap: var(--space-2);
  height: calc(100vh - var(--header-offset));
  overflow: hidden;
}

.permission-empty {
  grid-column: 1 / -1;
  place-self: center center;
}

/* ========== 左侧服务面板 ========== */
.service-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.panel-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-header-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
  align-items: center;
}

.panel-title {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-count {
  padding: 0 var(--space-1);
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 10px;
}

.panel-search {
  flex-shrink: 0;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.service-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  padding: var(--space-1);
  overflow: auto;
}

.service-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  border-radius: 4px;
  transition: background 160ms ease;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.active {
    background: var(--el-color-primary-light-9);
    box-shadow: inset 2px 0 0 var(--el-color-primary);
  }

  &.disabled {
    opacity: 0.6;
  }
}

.service-item__head {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
}

.service-item__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.service-item__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-on {
    background: var(--el-color-success);
  }

  &.is-off {
    background: var(--el-color-info);
  }
}

.service-item__sub {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.service-item__count {
  flex-shrink: 0;
}

/* ========== 右侧映射区 ========== */
.mapping-area {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.service-info-bar {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.service-info {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.service-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.service-path {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.service-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
}

.table-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
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

.table-wrap :deep(.pure-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-wrap :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

.mapping-empty {
  flex: 1;
}

.method-tag {
  justify-content: center;
  min-width: calc(var(--space-12) + var(--space-2));
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 650;
}

.path-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.resource-cell,
.time-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 -> 同级看顺序，再加 tag 提升至 0,2,1 */
div.service-interface-page.main-content {
  margin: var(--space-3);
}
</style>
