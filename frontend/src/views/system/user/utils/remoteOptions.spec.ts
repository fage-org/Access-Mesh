import { describe, expect, it, vi } from "vitest";
import { mergeSelected, useRemotePagedOptions } from "./remoteOptions";

describe("useRemotePagedOptions", () => {
  it("search 换词回到第 1 页并携带 keyword/pageSize 取数", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue({ items: [1], total: 41, hasNext: true });
    const opts = useRemotePagedOptions<number>(fetcher);

    opts.pageNum.value = 3;
    opts.search("华东");

    expect(fetcher).toHaveBeenCalledWith({
      keyword: "华东",
      pageNum: 1,
      pageSize: 20
    });
  });

  it("翻页受 hasNext 与第 1 页边界约束", async () => {
    const fetcher = vi.fn();
    const opts = useRemotePagedOptions<number>(fetcher, { pageSize: 20 });
    opts.hasNext.value = false;

    opts.nextPage();
    expect(opts.pageNum.value).toBe(1);

    opts.hasNext.value = true;
    opts.nextPage();
    expect(opts.pageNum.value).toBe(2);

    opts.prevPage();
    expect(opts.pageNum.value).toBe(1);

    opts.prevPage();
    expect(opts.pageNum.value).toBe(1);
  });

  it("旧请求结果不回写：换词后慢的旧响应被代际废弃", async () => {
    let resolveOld: (v: {
      items: number[];
      total: number;
      hasNext: boolean;
    }) => void = () => {};
    const oldPromise = new Promise<{
      items: number[];
      total: number;
      hasNext: boolean;
    }>(r => {
      resolveOld = r;
    });
    const fetcher = vi
      .fn()
      .mockImplementationOnce(() => oldPromise)
      .mockImplementationOnce(() =>
        Promise.resolve({ items: [9], total: 1, hasNext: false })
      );
    const opts = useRemotePagedOptions<number>(fetcher);

    void opts.load();
    opts.search("新词");
    await vi.waitFor(() => expect(opts.items.value).toEqual([9]));

    resolveOld({ items: [1, 2, 3], total: 99, hasNext: true });
    await vi.waitFor(() => expect(opts.loading.value).toBe(false));
    expect(opts.items.value).toEqual([9]);
    expect(opts.total.value).toBe(1);
    expect(opts.hasNext.value).toBe(false);
  });

  it("失败不清空当前页数据（反馈走 onError 回调）；reset 作废在途请求", async () => {
    const onError = vi.fn();
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce({ items: [1], total: 1, hasNext: false })
      .mockRejectedValueOnce(new Error("boom"));
    const opts = useRemotePagedOptions<number>(fetcher, { onError });

    await opts.load();
    expect(opts.items.value).toEqual([1]);

    await opts.load();
    expect(onError).toHaveBeenCalledWith(expect.any(Error));
    expect(opts.items.value).toEqual([1]);

    opts.reset();
    expect(opts.items.value).toEqual([]);
    expect(opts.pageNum.value).toBe(1);
    expect(opts.keyword.value).toBe("");
  });
});

describe("mergeSelected", () => {
  const keyOf = (n: number) => String(n);

  it("已选不在当前页的补尾，页内重复不重复渲染", () => {
    const selected = new Map([
      ["2", 2],
      ["5", 5]
    ]);
    expect(mergeSelected([1, 2, 3], selected, keyOf)).toEqual([1, 2, 3, 5]);
  });

  it("已选为空时保持当前页原样", () => {
    expect(mergeSelected([1, 2], new Map(), keyOf)).toEqual([1, 2]);
  });
});
