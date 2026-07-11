<script setup lang="ts">
import { computed, h, ref } from "vue";
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
  type ServiceConfigFormData
} from "./utils/types";
import type {
  ApiMappingResp,
  ServiceConfigSyncReq
} from "@/api/service-interface";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Refresh from "~icons/ep/refresh";
import Connection from "~icons/ep/connection";
import Switch from "~icons/ep/switch";
import Search from "~icons/ep/search";

defineOptions({ name: "SystemServiceInterface" });

const {
  loading,
  mappingLoading,
  serviceSearch,
  services,
  visibleServices,
  currentService,
  mappings,
  filteredMappings,
  mappingSearch,
  enabledMappingCount,
  loadDirectory,
  selectService,
  resetMappingFilters,
  submitService,
  deleteCurrentService,
  submitMapping,
  deleteMapping,
  runFullSync
} = useServiceInterface();

const tableRef = ref();

const canView = computed(() => hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_VIEW));
const canManageService = computed(() =>
  hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_MANAGE)
);
const canSync = computed(() => hasPerms(SERVICE_INTERFACE_PERMS.SERVICE_SYNC));
const canManageMapping = computed(() =>
  hasPerms(SERVICE_INTERFACE_PERMS.MAPPING_MANAGE)
);

const selectedApiTotal = computed(() => mappings.value.length);
const disabledMappingCount = computed(
  () => selectedApiTotal.value - enabledMappingCount.value
);

const columns = [
  { label: "方法", prop: "httpMethod", width: 72, slot: "httpMethod" },
  { label: "Gateway 路径", prop: "pathPattern", minWidth: 200, slot: "path" },
  { label: "资源实体", prop: "resourceEntityId", width: 92, slot: "resource" },
  { label: "顺序", prop: "matchOrder", width: 58 },
  { label: "状态", prop: "enabled", width: 70, slot: "enabled" },
  { label: "更新时间", prop: "updatedAt", width: 138, slot: "updatedAt" },
  {
    label: "操作",
    prop: "operation",
    width: 96,
    fixed: "right" as const,
    slot: "operation"
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

function openServiceForm(mode: "create" | "edit") {
  const initialData = mode === "edit" ? currentService.value : null;
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
    <header class="page-intro">
      <div>
        <div class="intro-eyebrow"><span /> ROUTE CONTROL</div>
        <h1>服务与接口映射</h1>
        <p>
          按服务维护 Gateway
          的接口鉴权边界：先登记服务，再用完整清单同步，必要时补充手工映射。
        </p>
      </div>
      <div class="intro-actions">
        <el-button
          :icon="useRenderIcon(Refresh)"
          :loading="loading"
          @click="loadDirectory"
        >
          刷新目录
        </el-button>
        <el-button
          v-if="canManageService"
          type="primary"
          :icon="useRenderIcon(AddFill)"
          @click="openServiceForm('create')"
        >
          登记服务
        </el-button>
      </div>
    </header>

    <el-empty
      v-if="!canView"
      description="你没有查看服务与接口映射的权限"
      class="permission-empty"
    />

    <section v-else class="workspace" :class="{ 'is-loading': loading }">
      <aside class="service-rail" aria-label="接入服务">
        <div class="rail-heading">
          <div>
            <span class="rail-kicker">SERVICE INVENTORY</span>
            <strong>接入服务</strong>
          </div>
          <el-badge :value="services.length" type="primary" />
        </div>
        <el-input
          v-model="serviceSearch"
          placeholder="筛选名称、编码或路径"
          clearable
          :prefix-icon="useRenderIcon(Search)"
          class="service-search"
        />
        <div class="service-list">
          <button
            v-for="service in visibleServices"
            :key="service.id"
            class="service-card"
            :class="{
              selected: currentService?.serviceCode === service.serviceCode,
              disabled: service.status === 0
            }"
            type="button"
            @click="selectService(service.serviceCode)"
          >
            <span class="service-card__signal" aria-hidden="true" />
            <span class="service-card__main">
              <span class="service-card__title">{{ service.name }}</span>
              <span class="service-card__code">{{ service.serviceCode }}</span>
            </span>
            <span class="service-card__meta">
              <span>{{ service.basePath || "/" }}</span>
              <span
                >{{ service.enabledApiCount }}/{{
                  service.apiCount
                }}
                条有效</span
              >
            </span>
          </button>
          <el-empty
            v-if="visibleServices.length === 0"
            :image-size="76"
            description="没有匹配的服务"
          />
        </div>
        <div class="rail-footnote">
          <span class="route-line" />
          接口映射使用 OR 语义：任一关联资源授权通过即可放行。
        </div>
      </aside>

      <main v-if="currentService" class="mapping-stage">
        <section class="service-focus">
          <div class="focus-copy">
            <div class="focus-title-line">
              <span class="focus-code">{{ currentService.serviceCode }}</span>
              <el-tag
                :type="currentService.status === 1 ? 'success' : 'info'"
                effect="light"
                size="small"
              >
                {{ currentService.status === 1 ? "服务启用" : "服务停用" }}
              </el-tag>
            </div>
            <h2>{{ currentService.name }}</h2>
            <p>{{ currentService.description || "尚未填写服务说明" }}</p>
          </div>
          <div class="focus-actions">
            <el-button
              v-if="canManageService"
              :icon="useRenderIcon(EditPen)"
              @click="openServiceForm('edit')"
            >
              编辑服务
            </el-button>
            <el-button
              v-if="canManageService"
              type="danger"
              plain
              :icon="useRenderIcon(Delete)"
              @click="deleteCurrentService"
            >
              删除服务
            </el-button>
          </div>
        </section>

        <section class="signal-strip" aria-label="当前服务接口概览">
          <div class="signal-tile">
            <span>基础路径</span>
            <strong class="font-mono">{{
              currentService.basePath || "/"
            }}</strong>
          </div>
          <div class="signal-tile">
            <span>已登记接口</span>
            <strong>{{ selectedApiTotal }}</strong>
          </div>
          <div class="signal-tile is-positive">
            <span>有效映射</span>
            <strong>{{ enabledMappingCount }}</strong>
          </div>
          <div
            class="signal-tile"
            :class="{ 'is-muted': disabledMappingCount === 0 }"
          >
            <span>已停用</span>
            <strong>{{ disabledMappingCount }}</strong>
          </div>
        </section>

        <el-alert class="sync-reminder" type="info" :closable="false" show-icon>
          <template #title>同步清单只接受 FULL 模式</template>
          <template #default>
            同步仅清理当前服务自动维护的缺失接口；手工映射由映射表单独维护。
          </template>
        </el-alert>

        <section class="mapping-panel">
          <div class="panel-toolbar">
            <div class="toolbar-title">
              <span class="toolbar-index">API MAP</span>
              <strong>接口资源映射</strong>
              <span class="toolbar-count"
                >{{ filteredMappings.length }} 条</span
              >
            </div>
            <div class="toolbar-actions">
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
            </div>
          </div>

          <div class="filter-row">
            <el-input
              v-model="mappingSearch.keyword"
              placeholder="搜索 Gateway 路径或资源 ID"
              clearable
              :prefix-icon="useRenderIcon(Search)"
              class="path-filter"
            />
            <el-select
              v-model="mappingSearch.method"
              placeholder="全部方法"
              clearable
            >
              <el-option
                v-for="item in HTTP_METHOD_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select v-model="mappingSearch.enabled" class="status-filter">
              <el-option label="全部状态" value="all" />
              <el-option label="仅有效" value="enabled" />
              <el-option label="仅停用" value="disabled" />
            </el-select>
            <el-button link type="primary" @click="resetMappingFilters"
              >重置筛选</el-button
            >
          </div>

          <pure-table
            ref="tableRef"
            row-key="id"
            align-whole="center"
            table-layout="auto"
            :loading="mappingLoading"
            :data="filteredMappings"
            :columns="columns"
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
                :show-after="350"
              >
                <span class="path-cell">{{ row.pathPattern }}</span>
              </el-tooltip>
            </template>
            <template #resource="{ row }">
              <span class="resource-cell">#{{ row.resourceEntityId }}</span>
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
                row.updatedAt || row.createdAt || "—"
              }}</span>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canManageMapping"
                class="reset-margin"
                link
                type="primary"
                @click="openMappingForm('edit', row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canManageMapping"
                class="reset-margin"
                link
                type="danger"
                @click="deleteMapping(row)"
              >
                移除
              </el-button>
              <span v-if="!canManageMapping" class="no-operation">只读</span>
            </template>
            <template #empty>
              <el-empty
                :image-size="90"
                description="尚无接口映射，可同步服务清单或手工新增"
              />
            </template>
          </pure-table>
        </section>
      </main>

      <main v-else class="empty-stage">
        <el-empty description="尚未登记服务">
          <el-button
            v-if="canManageService"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openServiceForm('create')"
          >
            登记第一个服务
          </el-button>
        </el-empty>
      </main>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.service-interface-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  height: 100%;
  overflow: hidden;
  color: var(--el-text-color-primary);
}

.page-intro {
  display: flex;
  gap: var(--space-6);
  align-items: flex-end;
  justify-content: space-between;
  padding: var(--space-1) var(--space-1) 0;

  h1 {
    margin: var(--space-1) 0 var(--space-2);
    font-size: calc(var(--el-font-size-extra-large) + var(--space-1));
    font-weight: 680;
    line-height: 1.1;
    color: var(--el-text-color-primary);
    letter-spacing: -0.035em;
  }

  p {
    max-width: 42rem;
    margin: 0;
    font-size: var(--el-font-size-small);
    line-height: 1.6;
    color: var(--el-text-color-secondary);
  }
}

.intro-eyebrow,
.rail-kicker,
.toolbar-index {
  font-size: var(--el-font-size-extra-small);
  font-weight: 700;
  color: var(--el-color-success);
  letter-spacing: 0.13em;
}

.intro-eyebrow {
  display: inline-flex;
  gap: var(--space-2);
  align-items: center;

  span {
    width: var(--space-2);
    height: var(--space-2);
    background: var(--el-color-success);
    border-radius: var(--radius-full);
    box-shadow: 0 0 0 var(--space-1) var(--el-color-success-light-8);
  }
}

.intro-actions,
.focus-actions,
.toolbar-actions,
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.permission-empty,
.empty-stage {
  flex: 1;
  min-height: 0;
  background: var(--el-bg-color);
  border: var(--el-border-width) solid var(--el-border-color);
  border-radius: var(--radius-xl);
}

.workspace {
  display: grid;
  flex: 1;
  grid-template-columns:
    minmax(
      var(--sidebar-max-width),
      calc(var(--sidebar-max-width) + var(--space-10))
    )
    minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: var(--el-border-width) solid var(--el-border-color);
  border-radius: var(--radius-xl);
  box-shadow: var(--el-box-shadow-light);
}

.service-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--space-5) var(--space-4) var(--space-3);
  background: var(--el-fill-color-light);
  border-right: var(--el-border-width) solid var(--el-border-color);
  box-shadow: inset 0 var(--space-1) 0 0 var(--el-color-success-light-8);
}

.rail-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding: 0 var(--space-1) var(--space-3);

  strong {
    display: block;
    margin-top: var(--space-1);
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-primary);
    letter-spacing: -0.015em;
  }
}

.service-search {
  margin-bottom: var(--space-3);
}

.service-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 0;
  padding: var(--el-border-width) var(--space-1) var(--space-2);
  overflow: auto;
}

.service-card {
  position: relative;
  display: grid;
  grid-template-columns: var(--space-2) minmax(0, 1fr);
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3) var(--space-2);
  overflow: hidden;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: var(--el-border-width) solid transparent;
  border-radius: var(--radius-lg);
  transition:
    background 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;

  &:hover {
    background: var(--el-bg-color-overlay);
    border-color: var(--el-border-color);
    transform: translateX(var(--space-1));
  }

  &:focus-visible {
    outline: var(--el-border-width) solid var(--el-color-primary);
    outline-offset: var(--space-1);
  }

  &.selected {
    background: var(--el-bg-color);
    border-color: var(--el-color-success-light-5);
    box-shadow: var(--el-box-shadow-light);

    &::after {
      position: absolute;
      top: 0;
      right: 0;
      bottom: 0;
      width: var(--space-1);
      content: "";
      background: var(--el-color-success);
    }
  }

  &.disabled {
    opacity: 0.62;
  }
}

.service-card__signal {
  width: var(--space-2);
  height: var(--space-2);
  margin-top: var(--space-1);
  background: var(--el-color-success);
  border-radius: var(--radius-full);
  box-shadow: 0 0 0 var(--space-1) var(--el-color-success-light-8);

  .disabled & {
    background: var(--el-color-info);
    box-shadow: 0 0 0 var(--space-1) var(--el-color-info-light-8);
  }
}

.service-card__main,
.service-card__meta {
  display: grid;
  min-width: 0;
}

.service-card__title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--el-font-size-small);
  font-weight: 650;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.service-card__code,
.service-card__meta {
  font-family: var(--el-font-family);
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}

.service-card__code {
  margin-top: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: nowrap;
}

.service-card__meta {
  grid-template-columns: minmax(0, 1fr) auto;
  grid-column: 2;
  gap: var(--space-2);
  margin-top: var(--space-2);

  span:first-child {
    overflow: hidden;
    text-overflow: ellipsis;
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    white-space: nowrap;
  }
}

.rail-footnote {
  display: grid;
  grid-template-columns: var(--space-6) 1fr;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-1) 0;
  font-size: var(--el-font-size-extra-small);
  line-height: 1.45;
  color: var(--el-text-color-secondary);
  border-top: var(--el-border-width) solid var(--el-border-color);
}

.route-line {
  height: var(--el-border-width);
  margin-top: var(--space-2);
  background: linear-gradient(90deg, var(--el-color-success), transparent);
}

.mapping-stage {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: var(--space-5) var(--space-6) var(--space-6);
  overflow: auto;
}

.service-focus {
  display: flex;
  gap: var(--space-5);
  align-items: flex-start;
  justify-content: space-between;
}

.focus-title-line {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.focus-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--el-font-size-extra-small);
  font-weight: 650;
  color: var(--el-color-success);
}

.focus-copy {
  h2 {
    margin: var(--space-2) 0 var(--space-1);
    font-size: var(--el-font-size-extra-large);
    font-weight: 680;
    color: var(--el-text-color-primary);
    letter-spacing: -0.03em;
  }

  p {
    max-width: 38rem;
    margin: 0;
    font-size: var(--el-font-size-small);
    line-height: 1.55;
    color: var(--el-text-color-secondary);
  }
}

.signal-strip {
  display: grid;
  grid-template-columns: minmax(9rem, 1.55fr) repeat(3, minmax(7rem, 1fr));
  margin-top: var(--space-5);
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: var(--el-border-width) solid var(--el-border-color);
  border-radius: var(--radius-lg);
}

.signal-tile {
  display: grid;
  gap: var(--space-1);
  align-content: center;
  min-height: 4rem;
  padding: var(--space-3) var(--space-4);
  border-right: var(--el-border-width) solid var(--el-border-color);

  &:last-child {
    border-right: 0;
  }

  span {
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  strong {
    font-size: var(--el-font-size-extra-large);
    font-weight: 680;
    color: var(--el-text-color-primary);
    letter-spacing: -0.035em;
  }

  &.is-positive strong {
    color: var(--el-color-success);
  }

  &.is-muted strong {
    color: var(--el-text-color-secondary);
  }
}

.sync-reminder {
  margin-top: var(--space-4);
}

.mapping-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin-top: var(--space-4);
  overflow: hidden;
  border: var(--el-border-width) solid var(--el-border-color);
  border-radius: var(--radius-md);
}

.panel-toolbar {
  display: flex;
  gap: var(--space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  background: var(--el-bg-color);
  border-bottom: var(--el-border-width) solid var(--el-border-color);
}

.toolbar-title {
  display: flex;
  gap: var(--space-2);
  align-items: baseline;

  strong {
    font-size: var(--el-font-size-base);
    color: var(--el-text-color-primary);
  }
}

.toolbar-count {
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}

.filter-row {
  padding: var(--space-2) var(--space-4);
  background: var(--el-fill-color-light);
  border-bottom: var(--el-border-width) solid var(--el-border-color);

  .el-select {
    flex: 0 1 8rem;
    min-width: 0;
  }

  .status-filter {
    flex-basis: 7rem;
  }
}

.path-filter {
  flex: 1 1 18rem;
  width: auto;
}

.mapping-panel :deep(.pure-table) {
  flex: 1;
}

.mapping-panel :deep(.el-table) {
  min-height: 0;
}

.method-tag {
  justify-content: center;
  min-width: calc(var(--space-12) + var(--space-2));
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 650;
}

.path-cell,
.resource-cell,
.time-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--el-font-size-extra-small);
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

.resource-cell {
  color: var(--el-text-color-secondary);
}

.time-cell,
.no-operation {
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}

@media (width <= 64rem) {
  .workspace {
    grid-template-columns: var(--sidebar-max-width) minmax(0, 1fr);
  }

  .mapping-stage {
    padding: var(--space-5);
  }

  .signal-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .signal-tile:nth-child(2) {
    border-right: 0;
  }

  .signal-tile:nth-child(-n + 2) {
    border-bottom: var(--el-border-width) solid var(--el-border-color);
  }
}

@media (width <= 48rem) {
  .service-interface-page {
    overflow: auto;
  }

  .page-intro,
  .service-focus,
  .panel-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .workspace {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: visible;
  }

  .service-rail {
    min-height: 0;
    border-right: 0;
    border-bottom: var(--el-border-width) solid var(--el-border-color);
  }

  .service-list {
    max-height: 12rem;
  }

  .mapping-stage {
    overflow: visible;
  }

  .focus-actions,
  .toolbar-actions {
    width: 100%;
  }

  .filter-row .path-filter,
  .filter-row .el-select,
  .filter-row .status-filter {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .service-card {
    transition: none;
  }
}
</style>

<style>
div.service-interface-page.main-content {
  margin: var(--space-3);
}
</style>
