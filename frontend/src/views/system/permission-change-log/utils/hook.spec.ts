/**
 * 权限变更日志页列表加载收敛回归（T-FE-051）：
 * 本页原为代际守卫（reqSeq）+ 失败清空旧数据的分叉实现，收敛到 usePagedList 后——
 * 1) 代际语义保持（特征锁：旧实现 reqSeq 已有，两实现下均绿）；
 * 2) 失败语义按全仓统一口径改为「保留旧数据 + 提示」（old-fail：旧实现清空旧数据）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetChangeLogList = vi.fn();
const mockMessage = vi.fn();

vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));
vi.mock("@/api/permission-change-log", () => ({
  getChangeLogList: (...args: unknown[]) => mockGetChangeLogList(...args)
}));

import { usePermissionChangeLog } from "./hook";

/** 受控 promise：手动 resolve/reject 驱动并发时序（禁裸 sleep） */
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

describe("权限变更日志页列表加载收敛（T-FE-051）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetChangeLogList.mockResolvedValue({ items: [], total: 0 });
  });

  it("并发 A(慢)→B(快)→B 回→A 回：终态=B（特征锁：本页 reqSeq 先例语义保持）", async () => {
    type Page = { items: Array<{ id: number }>; total: number };
    const a = defer<Page>();
    const b = defer<Page>();
    const calls: Array<ReturnType<typeof defer<Page>>> = [a, b];
    let i = 0;
    mockGetChangeLogList.mockImplementation(() => calls[i++].promise);

    const { loadTable, tableData, loading } = usePermissionChangeLog();
    loadTable();
    loadTable();
    b.resolve({ items: [{ id: 2 }], total: 1 });
    await flush();
    a.resolve({ items: [{ id: 1 }], total: 1 });
    await flush();

    expect(tableData.value).toEqual([{ id: 2 }]);
    expect(loading.value).toBe(false);
  });

  it("加载失败：保留旧数据 + message 提示（old-fail：旧实现清空旧数据）", async () => {
    mockGetChangeLogList
      .mockResolvedValueOnce({ items: [{ id: 1 }], total: 1 })
      .mockRejectedValueOnce(new Error("后端不可用"));
    const { loadTable, tableData } = usePermissionChangeLog();

    await loadTable();
    await loadTable(); // 失败
    expect(mockMessage).toHaveBeenCalledWith("后端不可用", { type: "error" });
    // 旧实现此分支 tableData=[]（清空）——统一口径后保留旧数据
    expect(tableData.value).toEqual([{ id: 1 }]);
  });

  it("迟到失败丢弃：不弹错、不清数据（特征锁：reqSeq 先例语义保持）", async () => {
    type Page = { items: Array<{ id: number }>; total: number };
    const a = defer<Page>();
    const b = defer<Page>();
    const calls: Array<ReturnType<typeof defer<Page>>> = [a, b];
    let i = 0;
    mockGetChangeLogList.mockImplementation(() => calls[i++].promise);

    const { loadTable, tableData } = usePermissionChangeLog();
    loadTable();
    loadTable();
    b.resolve({ items: [{ id: 2 }], total: 1 });
    await flush();
    a.reject(new Error("stale A failed"));
    await flush();

    expect(mockMessage).not.toHaveBeenCalled();
    expect(tableData.value).toEqual([{ id: 2 }]);
  });
});
