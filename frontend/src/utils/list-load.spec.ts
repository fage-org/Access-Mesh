/**
 * 列表加载 composable 回归（T-FE-051）：
 * latest-wins 请求代际守卫（迟到成功/迟到失败均丢弃、副作用仅最新触发）、
 * 失败保留旧数据 + message 提示 + loading 复位 + error 标记、
 * 分页层 total 回写 / onSearch 回第 1 页 / 失败保留数据与 total。
 * 并发时序用受控 promise（defer）确定性表达，禁裸 sleep 余量。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockMessage = vi.fn();

vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));

import { useListLoad, usePagedList } from "./list-load";

/** 受控 promise：测试侧手动 resolve/reject，驱动「A 慢 B 快」等并发时序 */
function defer<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 排空微任务 + 一个宏任务周期，让 load() 的续体确定性跑完 */
function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}

describe("useListLoad（T-FE-051 通用列表层）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("并发 A(慢)→B(快)→B 回→A 回：终态=B，A 被丢弃，loading 复位；load 返回值区分最新/迟到", async () => {
    const a = defer<string[]>();
    const b = defer<string[]>();
    const calls: Array<ReturnType<typeof defer<string[]>>> = [a, b];
    let i = 0;
    const { list, loading, load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher: () => calls[i++].promise
    });

    const pA = load(); // A
    const pB = load(); // B
    b.resolve(["B"]);
    await flush();
    expect(list.value).toEqual(["B"]);
    await expect(pB).resolves.toBe(true); // 最新请求成功

    a.resolve(["A"]); // 迟到响应：静默丢弃
    await flush();
    expect(list.value).toEqual(["B"]);
    await expect(pA).resolves.toBe(false); // 迟到请求被丢弃
    expect(loading.value).toBe(false);
  });

  it("迟到失败丢弃：不弹错、不清数据（旧分叉实现会误报误清）", async () => {
    const a = defer<string[]>();
    const b = defer<string[]>();
    const calls = [a, b];
    let i = 0;
    const { list, loading, load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher: () => calls[i++].promise
    });

    load();
    load();
    b.resolve(["B"]);
    await flush();
    a.reject(new Error("stale A failed"));
    await flush();

    expect(list.value).toEqual(["B"]);
    expect(loading.value).toBe(false);
    expect(mockMessage).not.toHaveBeenCalled();
  });

  it("最新请求失败：提示后端文案、旧数据保留、error 标记、loading 复位", async () => {
    const fetcher = vi
      .fn<() => Promise<string[]>>()
      .mockResolvedValueOnce(["旧数据"])
      .mockRejectedValueOnce(new Error("服务器开小差"));
    const { list, loading, error, load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher
    });

    await load();
    expect(list.value).toEqual(["旧数据"]);

    await load(); // 失败
    expect(mockMessage).toHaveBeenCalledWith("服务器开小差", {
      type: "error"
    });
    expect(list.value).toEqual(["旧数据"]); // 保留旧数据，不清空
    expect(loading.value).toBe(false);
    expect(error.value).toBe("服务器开小差");
  });

  it("非 2xx（axios 形态 response.data.message）：提示后端 body 文案而非 fallback/默认串", async () => {
    const fetcher = vi.fn<() => Promise<string[]>>().mockRejectedValue({
      message: "Request failed with status code 403",
      response: { status: 403, data: { message: "无访问权限" } }
    });
    const { error, load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher
    });

    await load();
    expect(mockMessage).toHaveBeenCalledWith("无访问权限", { type: "error" });
    expect(error.value).toBe("无访问权限");
  });

  it("error 标记在下次加载成功后清除", async () => {
    const fetcher = vi
      .fn<() => Promise<string[]>>()
      .mockRejectedValueOnce("down")
      .mockResolvedValue(["新数据"]);
    const { error, load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher
    });

    await load();
    expect(error.value).toBe("加载测试列表失败");
    await load();
    expect(error.value).toBeNull();
  });

  it("onLoaded 副作用仅最新请求触发（迟到响应不触发）", async () => {
    const a = defer<string[]>();
    const b = defer<string[]>();
    const calls = [a, b];
    let i = 0;
    const onLoaded = vi.fn();
    const { load } = useListLoad<string>({
      errorText: "加载测试列表失败",
      fetcher: () => calls[i++].promise,
      onLoaded
    });

    load();
    load();
    b.resolve(["B"]);
    await flush();
    a.resolve(["A"]);
    await flush();

    expect(onLoaded).toHaveBeenCalledTimes(1);
    expect(onLoaded).toHaveBeenCalledWith(["B"]);
  });
});

describe("usePagedList（T-FE-051 分页层）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("并发 A(慢)→B(快)：tableData 与 total 终态均取 B（B 回第 2 页场景）", async () => {
    const a = defer<{ items: string[]; total: number }>();
    const b = defer<{ items: string[]; total: number }>();
    const calls = [a, b];
    let i = 0;
    const { tableData, loading, pagination, loadTable } = usePagedList<string>({
      errorText: "加载测试分页失败",
      fetcher: () => calls[i++].promise
    });

    loadTable(); // A
    loadTable(); // B
    b.resolve({ items: ["B"], total: 42 });
    await flush();
    a.resolve({ items: ["A"], total: 7 });
    await flush();

    expect(tableData.value).toEqual(["B"]);
    expect(pagination.total).toBe(42);
    expect(loading.value).toBe(false);
  });

  it("同栈双 resolve（B 回后 A 立即 resolve、中间零 flush）：表格与 total 均取 B——total 随请求身份走 await 链（双轨评审 P3-1：共享 lastTotal 形态下 total=A 必失败）", async () => {
    type Page = { items: string[]; total: number };
    const a = defer<Page>();
    const b = defer<Page>();
    const calls: Array<ReturnType<typeof defer<Page>>> = [a, b];
    let i = 0;
    const { tableData, pagination, loadTable } = usePagedList<string>({
      errorText: "加载测试分页失败",
      fetcher: () => calls[i++].promise
    });

    const pA = loadTable(); // A（慢）
    const pB = loadTable(); // B（快）
    b.resolve({ items: ["B"], total: 42 });
    a.resolve({ items: ["A"], total: 7 }); // 同一同步栈、无宏任务边界
    await flush();

    expect(tableData.value).toEqual(["B"]);
    expect(pagination.total).toBe(42); // 共享变量形态下此处=7（A 的 total）→ 失败
    await expect(pB).resolves.toBe(true);
    await expect(pA).resolves.toBe(false);
  });

  it("onSearch 回第 1 页；翻页回调透传页码/页大小并回 1", async () => {
    const fetcher = vi
      .fn<
        (
          page: number,
          size: number
        ) => Promise<{ items: string[]; total: number }>
      >()
      .mockResolvedValue({ items: [], total: 0 });
    const { pagination, onSearch, onPageChange, onPageSizeChange } =
      usePagedList<string>({ errorText: "e", fetcher });

    pagination.page = 3;
    await onSearch();
    expect(fetcher).toHaveBeenLastCalledWith(1, 15); // initialSize 默认 15

    await onPageChange(5);
    expect(fetcher).toHaveBeenLastCalledWith(5, 15);

    await onPageSizeChange(50);
    expect(fetcher).toHaveBeenLastCalledWith(1, 50);
    expect(pagination.size).toBe(50);
  });

  it("加载失败：旧数据与旧 total 保留、loading 复位（成功后 total 才更新）", async () => {
    const fetcher = vi
      .fn<
        (
          page: number,
          size: number
        ) => Promise<{ items: string[]; total: number }>
      >()
      .mockResolvedValueOnce({ items: ["旧数据"], total: 30 })
      .mockRejectedValueOnce(new Error("第二次失败"));
    const { tableData, loading, pagination, loadTable } = usePagedList<string>({
      errorText: "加载测试分页失败",
      fetcher
    });

    await loadTable();
    await loadTable(); // 失败
    expect(tableData.value).toEqual(["旧数据"]);
    expect(pagination.total).toBe(30);
    expect(loading.value).toBe(false);
    expect(mockMessage).toHaveBeenCalledWith("第二次失败", { type: "error" });
  });
});
