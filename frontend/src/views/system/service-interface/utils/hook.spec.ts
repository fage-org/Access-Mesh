/**
 * 服务与接口映射页共享列表上下文回归（T-FE-059 / F011）：
 * 切服务后旧服务的映射行不得在新服务标题下残留可写——切换取数发起即清空、失败保持空集；
 * 编辑/删除提交前核对行所属服务与当前选中一致，不一致拒绝且不发货。
 * 旧实现（无上下文绑定、无提交守卫）：切 B 失败保留 A 行、编辑/删除照发 A 的 ID——
 * 本组用例在旧实现下必失败。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetServiceApis = vi.fn();
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
  saveServiceConfig: vi.fn(),
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
