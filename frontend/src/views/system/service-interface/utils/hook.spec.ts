/**
 * 服务与接口映射页共享列表上下文回归（T-FE-059 / F011）：
 * 切服务后旧服务的映射行不得在新服务标题下残留可写——切换取数发起即清空、失败保持空集；
 * 编辑/删除提交前核对行所属服务与当前选中一致，不一致拒绝且不发货。
 * 旧实现（无上下文绑定、无提交守卫）：切 B 失败保留 A 行、编辑/删除照发 A 的 ID——
 * 本组用例在旧实现下必失败。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetServiceApis = vi.fn();
const mockSaveServiceConfig = vi.fn();
const mockUpdateApiMapping = vi.fn();
const mockRemoveApiMappings = vi.fn();
const mockCreateApiMapping = vi.fn();

// 阻断 hook 模块级链（同 biz-domain hook.spec 范式：element-plus/message/api 全 mock）
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) }
}));
vi.mock("@/utils/message", () => ({ message: vi.fn() }));
vi.mock("@/api/service-interface", () => ({
  getServiceApis: (...args: unknown[]) => mockGetServiceApis(...args),
  getServiceConfigList: vi.fn().mockResolvedValue({ items: [] }),
  getApiMappingList: vi.fn().mockResolvedValue({ items: [] }),
  createApiMapping: (...args: unknown[]) => mockCreateApiMapping(...args),
  updateApiMapping: (...args: unknown[]) => mockUpdateApiMapping(...args),
  removeApiMappings: (...args: unknown[]) => mockRemoveApiMappings(...args),
  removeServiceConfigs: vi.fn(),
  saveServiceConfig: (...args: unknown[]) => mockSaveServiceConfig(...args),
  syncServiceInterfaces: vi.fn()
}));

import { useServiceInterface } from "./hook";

/** 排空微任务 + 一个宏任务周期（fetcher 包装多一跳微任务，T-FE-051 同款） */
function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}

function mappingOf(id: number, serviceCode: string) {
  return {
    id,
    resourceEntityId: 100 + id,
    serviceCode,
    httpMethod: "GET",
    pathPattern: `/p${id}`,
    matchOrder: 100,
    enabled: true
  } as any;
}

const EDIT_FORM = {
  resourceEntityId: 101,
  httpMethod: "GET",
  pathPattern: "/x",
  matchOrder: 100,
  enabled: true,
  extra: ""
} as any;

describe("服务与接口映射页共享列表上下文（T-FE-059 / F011）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("切服务 B 加载失败：mappings 清空，B 标题下不残留 A 行（旧实现保留必失败）", async () => {
    mockGetServiceApis
      .mockResolvedValueOnce({ items: [mappingOf(11, "svc-a")] })
      .mockRejectedValueOnce(new Error("svc-b 接口加载失败"));
    const hook = useServiceInterface();

    await hook.selectService("svc-a");
    expect(hook.mappings.value).toHaveLength(1);

    await hook.selectService("svc-b");
    expect(hook.mappings.value).toEqual([]);
  });

  it("选中服务置空清空后，在途旧请求晚归不回写（旧实现回写 A 行必失败）", async () => {
    const pending: Array<(v: any) => void> = [];
    mockGetServiceApis.mockImplementation(
      () => new Promise(resolve => pending.push(resolve))
    );
    const hook = useServiceInterface();

    hook.selectedServiceCode.value = "svc-a";
    void hook.refreshMappings();
    hook.selectedServiceCode.value = null;
    await hook.refreshMappings(); // 选中置空：清空并作废在途

    pending[0]({ items: [mappingOf(11, "svc-a")] });
    await flush();
    expect(hook.mappings.value).toEqual([]);
  });

  it("编辑提交：行所属服务 ≠ 当前选中 → 拒绝且不发货（旧实现照发必失败）", async () => {
    mockUpdateApiMapping.mockResolvedValue(undefined);
    const hook = useServiceInterface();
    hook.selectedServiceCode.value = "svc-b";

    const ok = await hook.submitMapping(
      EDIT_FORM,
      "edit",
      mappingOf(11, "svc-a"),
      "svc-a"
    );

    expect(ok).toBe(false);
    expect(mockUpdateApiMapping).not.toHaveBeenCalled();
  });

  it("删除提交：行所属服务 ≠ 当前选中 → 拒绝且不发货（旧实现照删必失败）", async () => {
    mockRemoveApiMappings.mockResolvedValue(undefined);
    const hook = useServiceInterface();
    hook.selectedServiceCode.value = "svc-b";

    await hook.deleteMapping(mappingOf(11, "svc-a"));

    expect(mockRemoveApiMappings).not.toHaveBeenCalled();
  });

  it("新增弹窗打开时服务 ≠ 提交时选中 → 拒绝且不发货（双轨评审处置：标题快照与提交目标分裂）", async () => {
    mockCreateApiMapping.mockResolvedValue(undefined);
    const hook = useServiceInterface();
    hook.selectedServiceCode.value = "svc-b";

    const ok = await hook.submitMapping(
      EDIT_FORM,
      "create",
      undefined,
      "svc-a"
    );

    expect(ok).toBe(false);
    expect(mockCreateApiMapping).not.toHaveBeenCalled();
  });
});

describe("服务/映射显式清空公式（T-API-004，U006 拍板）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("服务编辑：原值非空且表单清空 → 三个 xxxClear=true 随载荷发出（旧实现不发标志必失败）", async () => {
    mockSaveServiceConfig.mockResolvedValue({});
    const hook = useServiceInterface();

    const ok = await hook.submitService(
      {
        serviceCode: "svc-a",
        name: "服务A",
        basePath: "",
        description: "",
        status: 1,
        extra: "   "
      } as any,
      "edit",
      {
        serviceCode: "svc-a",
        name: "服务A",
        basePath: "/svc-a",
        description: "旧描述",
        extra: '{"syncTypes":{}}',
        apiCount: 0,
        enabledApiCount: 0
      } as any
    );

    expect(ok).toBe(true);
    expect(mockSaveServiceConfig).toHaveBeenCalledWith(
      expect.objectContaining({
        basePath: null,
        description: null,
        extra: null,
        basePathClear: true,
        descriptionClear: true,
        extraClear: true
      })
    );
  });

  it("服务编辑：字段填了新值 → 清空标志全 false（冲突由后端兜底拒绝）", async () => {
    mockSaveServiceConfig.mockResolvedValue({});
    const hook = useServiceInterface();

    await hook.submitService(
      {
        serviceCode: "svc-a",
        name: "服务A",
        basePath: "/new",
        description: "新描述",
        status: 1,
        extra: "{}"
      } as any,
      "edit",
      {
        serviceCode: "svc-a",
        basePath: "/svc-a",
        description: "旧描述",
        extra: null,
        apiCount: 0,
        enabledApiCount: 0
      } as any
    );

    expect(mockSaveServiceConfig).toHaveBeenCalledWith(
      expect.objectContaining({
        basePathClear: false,
        descriptionClear: false,
        extraClear: false
      })
    );
  });

  it("映射编辑：原 extra 非空且表单清空 → extraClear=true（旧实现不发标志必失败）", async () => {
    mockUpdateApiMapping.mockResolvedValue(undefined);
    const hook = useServiceInterface();
    hook.selectedServiceCode.value = "svc-a";

    const editing = { ...mappingOf(11, "svc-a"), extra: '{"k":1}' };
    const ok = await hook.submitMapping(
      { ...EDIT_FORM, extra: "" },
      "edit",
      editing,
      "svc-a"
    );

    expect(ok).toBe(true);
    expect(mockUpdateApiMapping).toHaveBeenCalledWith(
      expect.objectContaining({ extra: null, extraClear: true })
    );
  });

  it("映射编辑：原 extra 为空 → 清空表单不产 extraClear（无可清值）", async () => {
    mockUpdateApiMapping.mockResolvedValue(undefined);
    const hook = useServiceInterface();
    hook.selectedServiceCode.value = "svc-a";

    const ok = await hook.submitMapping(
      { ...EDIT_FORM, extra: "" },
      "edit",
      mappingOf(11, "svc-a"),
      "svc-a"
    );

    expect(ok).toBe(true);
    expect(mockUpdateApiMapping).toHaveBeenCalledWith(
      expect.objectContaining({ extra: null, extraClear: false })
    );
  });
});

describe("服务目录与映射计数独立收果（T-ACCESS-055：有限管理员视角 B 缺口）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // loadDirectory 成功后级联 loadMappings（getServiceApis）——统一给空集防挂起
    mockGetServiceApis.mockResolvedValue({ items: [] });
  });

  it("映射全量 list 403（零可见实例，与 service-config/list 同源门禁）时服务目录独立加载、计数降级 0（旧实现 Promise.all 连坐必失败；实例准入后该组合真实链路仅并发改权/瞬时故障可观测，作降级分支特征锁）", async () => {
    const { getServiceConfigList, getApiMappingList } = await import(
      "@/api/service-interface"
    );
    (getServiceConfigList as any).mockResolvedValue({
      items: [{ serviceCode: "access-service", name: "a" } as any]
    });
    (getApiMappingList as any).mockRejectedValue(
      new Error("403 权限不足：resource-api-mapping/list 零可见实例")
    );

    const hook = useServiceInterface();
    await hook.loadDirectory();

    expect(hook.services.value).toHaveLength(1);
    expect(hook.services.value[0].serviceCode).toBe("access-service");
    expect(hook.services.value[0].apiCount).toBe(0);
    expect(hook.services.value[0].enabledApiCount).toBe(0);
  });

  it("双路都成功时计数照常聚合（allSettled 改造不改变正常路径）", async () => {
    const { getServiceConfigList, getApiMappingList } = await import(
      "@/api/service-interface"
    );
    (getServiceConfigList as any).mockResolvedValue({
      items: [{ serviceCode: "svc-a", name: "a" } as any]
    });
    (getApiMappingList as any).mockResolvedValue({
      items: [
        { serviceCode: "svc-a", enabled: true },
        { serviceCode: "svc-a", enabled: false }
      ] as any[]
    });

    const hook = useServiceInterface();
    await hook.loadDirectory();

    expect(hook.services.value[0].apiCount).toBe(2);
    expect(hook.services.value[0].enabledApiCount).toBe(1);
  });

  it("服务目录自身失败仍按 useListLoad 失败语义提示（不因映射失败分支吞掉主路错误）", async () => {
    const { getServiceConfigList, getApiMappingList } = await import(
      "@/api/service-interface"
    );
    (getServiceConfigList as any).mockRejectedValue(
      new Error("服务目录加载失败")
    );
    (getApiMappingList as any).mockResolvedValue({ items: [] });

    const hook = useServiceInterface();
    await hook.loadDirectory();

    expect(hook.services.value).toEqual([]);
  });
});
