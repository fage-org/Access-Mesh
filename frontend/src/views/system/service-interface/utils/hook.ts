import { computed, onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import {
  createApiMapping,
  getApiMappingList,
  getServiceApis,
  getServiceConfigList,
  removeApiMappings,
  removeServiceConfigs,
  saveServiceConfig,
  syncServiceInterfaces,
  updateApiMapping,
  type ApiMappingResp,
  type ServiceConfigSyncResp
} from "@/api/service-interface";
import type {
  MappingFormData,
  ServiceConfigFormData,
  ServiceSummary
} from "./types";
import type { ServiceConfigSyncReq } from "@/api/service-interface";

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

/**
 * 服务与接口映射页的状态编排。
 *
 * 左栏服务概览通过一次 resource-api-mapping/list 取回映射计数；选中服务的
 * 明细始终走 service-config/apis，既避免为每个服务重复请求，也保持页面对
 * 服务专用接口的真实使用。
 */
export function useServiceInterface() {
  const loading = ref(false);
  const mappingLoading = ref(false);
  const serviceSearch = ref("");
  const selectedServiceCode = ref<string | null>(null);
  const services = ref<ServiceSummary[]>([]);
  const mappings = ref<ApiMappingResp[]>([]);
  const mappingSearch = reactive({
    keyword: "",
    method: "",
    enabled: "all" as "all" | "enabled" | "disabled"
  });

  const visibleServices = computed(() => {
    const keyword = serviceSearch.value.trim().toLowerCase();
    if (!keyword) return services.value;
    return services.value.filter(
      service =>
        service.serviceCode.toLowerCase().includes(keyword) ||
        service.name.toLowerCase().includes(keyword) ||
        (service.basePath || "").toLowerCase().includes(keyword)
    );
  });

  const currentService = computed(
    () =>
      services.value.find(
        service => service.serviceCode === selectedServiceCode.value
      ) || null
  );

  const filteredMappings = computed(() => {
    const keyword = mappingSearch.keyword.trim().toLowerCase();
    return mappings.value.filter(mapping => {
      const matchesKeyword =
        !keyword ||
        mapping.pathPattern.toLowerCase().includes(keyword) ||
        String(mapping.resourceEntityId).includes(keyword);
      const matchesMethod =
        !mappingSearch.method || mapping.httpMethod === mappingSearch.method;
      const matchesEnabled =
        mappingSearch.enabled === "all" ||
        (mappingSearch.enabled === "enabled" && mapping.enabled) ||
        (mappingSearch.enabled === "disabled" && !mapping.enabled);
      return matchesKeyword && matchesMethod && matchesEnabled;
    });
  });

  const enabledMappingCount = computed(
    () => mappings.value.filter(mapping => mapping.enabled).length
  );

  async function loadMappings(serviceCode = selectedServiceCode.value) {
    if (!serviceCode) {
      mappings.value = [];
      return;
    }
    mappingLoading.value = true;
    try {
      const result = await getServiceApis(serviceCode);
      mappings.value = result.items.slice().sort((left, right) => {
        const order = (left.matchOrder ?? 100) - (right.matchOrder ?? 100);
        return order || left.pathPattern.localeCompare(right.pathPattern);
      });
    } catch (error: unknown) {
      message(getErrorMessage(error, "加载接口映射失败"), { type: "error" });
    } finally {
      mappingLoading.value = false;
    }
  }

  async function loadDirectory() {
    loading.value = true;
    try {
      const [serviceResult, mappingResult] = await Promise.all([
        getServiceConfigList(),
        getApiMappingList({})
      ]);
      const mappingStats = new Map<
        string,
        { total: number; enabled: number }
      >();
      for (const mapping of mappingResult.items) {
        const current = mappingStats.get(mapping.serviceCode) || {
          total: 0,
          enabled: 0
        };
        current.total += 1;
        current.enabled += mapping.enabled ? 1 : 0;
        mappingStats.set(mapping.serviceCode, current);
      }
      services.value = serviceResult.items
        .slice()
        .sort((left, right) =>
          left.serviceCode.localeCompare(right.serviceCode)
        )
        .map(service => {
          const stat = mappingStats.get(service.serviceCode);
          return {
            ...service,
            apiCount: stat?.total || 0,
            enabledApiCount: stat?.enabled || 0
          };
        });

      const selectedStillExists = services.value.some(
        service => service.serviceCode === selectedServiceCode.value
      );
      if (!selectedStillExists) {
        selectedServiceCode.value = services.value[0]?.serviceCode || null;
      }
      await loadMappings();
    } catch (error: unknown) {
      message(getErrorMessage(error, "加载服务目录失败"), { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  async function selectService(serviceCode: string) {
    if (serviceCode === selectedServiceCode.value) return;
    selectedServiceCode.value = serviceCode;
    mappingSearch.keyword = "";
    mappingSearch.method = "";
    mappingSearch.enabled = "all";
    await loadMappings(serviceCode);
  }

  function resetMappingFilters() {
    mappingSearch.keyword = "";
    mappingSearch.method = "";
    mappingSearch.enabled = "all";
  }

  async function submitService(
    form: ServiceConfigFormData,
    mode: "create" | "edit"
  ): Promise<boolean> {
    try {
      await saveServiceConfig({
        serviceCode: form.serviceCode,
        name: form.name,
        basePath: form.basePath || null,
        description: form.description || null,
        status: form.status,
        extra: form.extra.trim() || null
      });
      selectedServiceCode.value = form.serviceCode;
      message(mode === "create" ? "服务已登记" : "服务配置已保存", {
        type: "success"
      });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(
        getErrorMessage(
          error,
          mode === "create" ? "登记服务失败" : "保存服务失败"
        ),
        { type: "error" }
      );
      return false;
    }
  }

  async function deleteCurrentService(): Promise<boolean> {
    const service = currentService.value;
    if (!service) return false;
    try {
      await ElMessageBox.confirm(
        `确认删除服务「${service.name}」吗？该服务关联的接口映射也将不再可用。`,
        "删除服务",
        {
          type: "warning",
          confirmButtonText: "删除服务",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return false;
    }
    try {
      await removeServiceConfigs([service.id]);
      message("服务已删除", { type: "success" });
      selectedServiceCode.value = null;
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(getErrorMessage(error, "删除服务失败"), { type: "error" });
      return false;
    }
  }

  async function submitMapping(
    form: MappingFormData,
    mode: "create",
    editing?: ApiMappingResp
  ): Promise<boolean>;
  async function submitMapping(
    form: MappingFormData,
    mode: "edit",
    editing: ApiMappingResp
  ): Promise<boolean>;
  async function submitMapping(
    form: MappingFormData,
    mode: "create" | "edit",
    editing?: ApiMappingResp
  ): Promise<boolean> {
    const serviceCode = selectedServiceCode.value;
    if (!serviceCode || form.resourceEntityId == null) {
      message("请先选择服务并填写资源实体 ID", { type: "warning" });
      return false;
    }
    try {
      if (mode === "create") {
        await createApiMapping({
          resourceId: form.resourceEntityId,
          serviceCode,
          httpMethod: form.httpMethod,
          pathPattern: form.pathPattern,
          matchOrder: form.matchOrder,
          enabled: form.enabled,
          extra: form.extra.trim() || null
        });
      } else if (editing) {
        await updateApiMapping({
          resourceId: editing.resourceEntityId,
          mappingId: editing.id,
          httpMethod: form.httpMethod,
          pathPattern: form.pathPattern,
          matchOrder: form.matchOrder,
          enabled: form.enabled,
          extra: form.extra.trim() || null
        });
      }
      message(mode === "create" ? "接口映射已创建" : "接口映射已保存", {
        type: "success"
      });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(
        getErrorMessage(
          error,
          mode === "create" ? "创建接口映射失败" : "保存接口映射失败"
        ),
        { type: "error" }
      );
      return false;
    }
  }

  async function deleteMapping(mapping: ApiMappingResp): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认移除 ${mapping.httpMethod} ${mapping.pathPattern} 的映射吗？`,
        "移除接口映射",
        {
          type: "warning",
          confirmButtonText: "移除映射",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return false;
    }
    try {
      await removeApiMappings([mapping.id]);
      message("接口映射已移除", { type: "success" });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(getErrorMessage(error, "移除接口映射失败"), { type: "error" });
      return false;
    }
  }

  async function runFullSync(
    request: ServiceConfigSyncReq
  ): Promise<ServiceConfigSyncResp | null> {
    try {
      const result = await syncServiceInterfaces(request);
      await loadDirectory();
      return result;
    } catch (error: unknown) {
      message(getErrorMessage(error, "同步接口失败"), { type: "error" });
      return null;
    }
  }

  onMounted(loadDirectory);

  return {
    loading,
    mappingLoading,
    serviceSearch,
    selectedServiceCode,
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
  };
}
