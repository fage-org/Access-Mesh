import { computed, onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import { useListLoad } from "@/utils/list-load";
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

/**
 * 服务与接口映射页的状态编排。
 *
 * 左栏服务概览通过一次 resource-api-mapping/list 取回映射计数；选中服务的
 * 明细始终走 service-config/apis，既避免为每个服务重复请求，也保持页面对
 * 服务专用接口的真实使用。
 *
 * T-FE-051：服务目录 / 接口明细两路列表加载收敛到 useListLoad（latest-wins 代际 +
 * 失败提示保留旧数据）；错误文案统一走 _envelope toErrorMessage（原本页局部
 * getErrorMessage 副本退役——非 2xx 优先消费后端 body message）。
 */
export function useServiceInterface() {
  const serviceSearch = ref("");
  const selectedServiceCode = ref<string | null>(null);
  const mappingSearch = reactive({
    keyword: "",
    method: "",
    enabled: "all" as "all" | "enabled" | "disabled"
  });

  // ========== 接口映射明细（选中服务） ==========
  // 本次加载的目标服务（loadMappings 带参调用时由包装层写入，fetcher 闭包读取）
  let mappingsTargetCode: string | null = null;

  const {
    list: mappings,
    loading: mappingLoading,
    load: loadMappingsCore,
    clear: clearMappings
  } = useListLoad<ApiMappingResp>({
    errorText: "加载接口映射失败",
    // T-FE-059：明细数据归属选中服务——切服务取数发起即清空旧服务行（失败不回填），
    // 旧服务行不得在新服务标题下可写（F011）
    contextKey: () => mappingsTargetCode,
    fetcher: async () => {
      const result = await getServiceApis(mappingsTargetCode!);
      return result.items.slice().sort((left, right) => {
        const order = (left.matchOrder ?? 100) - (right.matchOrder ?? 100);
        return order || left.pathPattern.localeCompare(right.pathPattern);
      });
    }
  });

  async function loadMappings(serviceCode = selectedServiceCode.value) {
    if (!serviceCode) {
      // 置空选中：清空并作废在途请求（T-FE-059，迟到响应不回写）
      clearMappings();
      return;
    }
    mappingsTargetCode = serviceCode;
    return loadMappingsCore();
  }

  // ========== 服务目录（左栏概览 + 映射计数） ==========
  const {
    list: services,
    loading,
    load: loadDirectoryCore
  } = useListLoad<ServiceSummary>({
    errorText: "加载服务目录失败",
    fetcher: async () => {
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
      return serviceResult.items
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
    }
  });

  /** 刷新服务目录；成功后级联——选中服务若已不存在则回退首项，并刷新其接口映射明细 */
  async function loadDirectory() {
    const ok = await loadDirectoryCore();
    if (!ok) return; // 失败/迟到：useListLoad 已提示，不级联
    const selectedStillExists = services.value.some(
      service => service.serviceCode === selectedServiceCode.value
    );
    if (!selectedStillExists) {
      selectedServiceCode.value = services.value[0]?.serviceCode || null;
    }
    await loadMappings();
  }

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
        (mapping.resourceCode || "").toLowerCase().includes(keyword) ||
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

  /** original（T-API-004，编辑态必传）：编辑行原值——三字段清空公式（原值非空且表单清空
   *  → xxxClear）的判定基准；JSON null 无法区分「未传」与「清空」，公式对齐 role/resource
   *  页 extraClear 先例。extraClear 语义=撤销 syncTypes 同步白名单（后端 fail-closed）。 */
  async function submitService(
    form: ServiceConfigFormData,
    mode: "create" | "edit",
    original?: ServiceSummary
  ): Promise<boolean> {
    try {
      await saveServiceConfig({
        serviceCode: form.serviceCode,
        name: form.name,
        basePath: form.basePath || null,
        description: form.description || null,
        status: form.status,
        extra: form.extra.trim() || null,
        basePathClear: original?.basePath != null && !form.basePath,
        descriptionClear: original?.description != null && !form.description,
        extraClear: original?.extra != null && !form.extra.trim()
      });
      selectedServiceCode.value = form.serviceCode;
      message(mode === "create" ? "服务已登记" : "服务配置已保存", {
        type: "success"
      });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(
        toErrorMessage(
          error,
          mode === "create" ? "登记服务失败" : "保存服务失败"
        ),
        { type: "error" }
      );
      return false;
    }
  }

  async function deleteService(service: ServiceSummary): Promise<boolean> {
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
      if (selectedServiceCode.value === service.serviceCode) {
        selectedServiceCode.value = null;
      }
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(toErrorMessage(error, "删除服务失败"), { type: "error" });
      return false;
    }
  }

  async function submitMapping(
    form: MappingFormData,
    mode: "create",
    editing: undefined,
    openedAtServiceCode: string
  ): Promise<boolean>;
  async function submitMapping(
    form: MappingFormData,
    mode: "edit",
    editing: ApiMappingResp,
    openedAtServiceCode: string
  ): Promise<boolean>;
  async function submitMapping(
    form: MappingFormData,
    mode: "create" | "edit",
    editing?: ApiMappingResp,
    openedAtServiceCode = ""
  ): Promise<boolean> {
    const serviceCode = selectedServiceCode.value;
    if (!serviceCode || form.resourceEntityId == null) {
      message("请先选择服务并填写资源实体 ID", { type: "warning" });
      return false;
    }
    // 弹窗打开时服务快照与提交时选中不一致即拒绝（T-FE-059，双轨评审处置：
    // 新增弹窗标题绑定打开时服务，create 分支提交目标取当前选中——快照与现值分裂面）
    if (openedAtServiceCode !== serviceCode) {
      message("该弹窗目标服务与当前选中不一致，请刷新后重试", {
        type: "warning"
      });
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
        // 保存时核对仍属于预期上下文（T-FE-059）：行所属服务与当前选中不一致即拒绝——
        // 覆盖「弹窗存续期间页面上下文变化」（如后退键离开再回）的窄路径
        if (editing.serviceCode !== serviceCode) {
          message("该映射不属于当前选中服务，请刷新后重试", {
            type: "warning"
          });
          return false;
        }
        await updateApiMapping({
          resourceId: editing.resourceEntityId,
          mappingId: editing.id,
          httpMethod: form.httpMethod,
          pathPattern: form.pathPattern,
          matchOrder: form.matchOrder,
          enabled: form.enabled,
          extra: form.extra.trim() || null,
          // 显式清空（T-API-004）：原 extra 非空且表单清空 → 清空标志（role/resource 同款公式）
          extraClear: editing.extra != null && !form.extra.trim()
        });
      }
      message(mode === "create" ? "接口映射已创建" : "接口映射已保存", {
        type: "success"
      });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(
        toErrorMessage(
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
      // 保存时核对仍属于预期上下文（T-FE-059）：确认框期间页面上下文可能已变
      if (mapping.serviceCode !== selectedServiceCode.value) {
        message("该映射不属于当前选中服务，请刷新后重试", {
          type: "warning"
        });
        return false;
      }
      await removeApiMappings([mapping.id]);
      message("接口映射已移除", { type: "success" });
      await loadDirectory();
      return true;
    } catch (error: unknown) {
      message(toErrorMessage(error, "移除接口映射失败"), { type: "error" });
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
      message(toErrorMessage(error, "同步接口失败"), { type: "error" });
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
    deleteService,
    submitMapping,
    deleteMapping,
    runFullSync,
    refreshMappings: loadMappings
  };
}
