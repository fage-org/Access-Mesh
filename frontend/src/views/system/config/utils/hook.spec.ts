/**
 * 系统配置页列表加载代际与错误文案回归（T-FE-051）：
 * 旧实现无请求代际守卫（快速搜索 A→B 时慢 A 迟到覆盖 B 的结果）；
 * 非 2xx 失败旧实现只显 fallback（axios 默认串/e.message 均无后端 body 文案）——
 * 统一错误文案来源后优先透出后端 body message。
 * 既有失败提示行为（catch+message）用特征锁（旧新均绿），不造旧实现下天然为绿的假 old-fail。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetSystemConfigList = vi.fn();
const mockMessage = vi.fn();

vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));
vi.mock("@/api/system-config", () => ({
  getSystemConfigList: (...args: unknown[]) => mockGetSystemConfigList(...args),
  saveSystemConfig: vi.fn()
}));

import { useSystemConfig } from "./hook";

/** 受控 promise：手动 resolve/reject 驱动「A 慢 B 快」并发时序（禁裸 sleep） */
function defer<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}

describe("系统配置页列表加载（T-FE-051）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetSystemConfigList.mockResolvedValue({ items: [], total: 0 });
  });

  it("并发 A(慢)→B(快)→B 回→A 回：表格终态=B（旧实现无代际守卫被 A 覆盖必失败）", async () => {
    type Page = { items: Array<{ configKey: string }>; total: number };
    const a = defer<Page>();
    const b = defer<Page>();
    const calls: Array<ReturnType<typeof defer<Page>>> = [a, b];
    let i = 0;
    mockGetSystemConfigList.mockImplementation(() => calls[i++].promise);

    const { loadTable, tableData, loading } = useSystemConfig();
    loadTable(); // A：关键词搜索「a」的慢响应
    loadTable(); // B：用户改为「b」的快响应
    b.resolve({ items: [{ configKey: "b-1" }], total: 1 });
    await flush();
    expect(tableData.value).toEqual([{ configKey: "b-1" }]);

    a.resolve({ items: [{ configKey: "a-1" }], total: 1 }); // A 迟到：旧实现覆盖 B
    await flush();
    expect(tableData.value).toEqual([{ configKey: "b-1" }]); // 旧实现下此处为 a-1 → 失败
    expect(loading.value).toBe(false);
  });

  it("加载失败：message 提示 + loading 复位 + 旧数据保留（特征锁：既有 catch 行为）", async () => {
    mockGetSystemConfigList
      .mockResolvedValueOnce({ items: [{ configKey: "old" }], total: 1 })
      .mockRejectedValueOnce(new Error("后端不可用"));
    const { loadTable, tableData, loading } = useSystemConfig();

    await loadTable();
    await loadTable(); // 失败
    expect(mockMessage).toHaveBeenCalledWith("后端不可用", { type: "error" });
    expect(tableData.value).toEqual([{ configKey: "old" }]);
    expect(loading.value).toBe(false);
  });

  it("非 2xx 失败：透出后端 body message（旧实现只显 fallback 必失败）", async () => {
    mockGetSystemConfigList.mockRejectedValueOnce({
      message: "Request failed with status code 403",
      response: { status: 403, data: { message: "无访问权限" } }
    });
    const { loadTable } = useSystemConfig();

    await loadTable();
    expect(mockMessage).toHaveBeenCalledWith("无访问权限", { type: "error" });
  });
});
