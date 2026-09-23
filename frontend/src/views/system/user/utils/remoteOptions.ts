import { ref } from "vue";
import type { Ref } from "vue";

/** 分页结果形态（对齐 PageResp 消费面：items/total/hasNext） */
export interface RemotePagedResult<T> {
  items: T[];
  total: number;
  hasNext: boolean;
}

export interface RemotePagedQuery {
  keyword: string;
  pageNum: number;
  pageSize: number;
}

/**
 * el-select 远程搜索 + 下拉内翻页的选项装载状态（T-FE-058）。
 *
 * - keyword 变更回到第 1 页再取数（新词旧页码结果无意义）；
 * - 请求代际守卫：换词/翻页后，在途旧请求的成功/失败结果一律不回写；
 * - 已选项跨换词/翻页保留由调用方维护 selectedMap，展示经 {@link mergeSelected}
 *   把已选但不在当前页的选项补回下拉（避免 tag 退化为裸 value）。
 */
export function useRemotePagedOptions<T>(
  fetcher: (q: RemotePagedQuery) => Promise<RemotePagedResult<T>>,
  opts?: { pageSize?: number; onError?: (e: unknown) => void }
) {
  const pageSize = opts?.pageSize ?? 20;
  const keyword = ref("");
  const pageNum = ref(1);
  const items: Ref<T[]> = ref([]);
  const total = ref(0);
  const hasNext = ref(false);
  const loading = ref(false);
  let seq = 0;

  async function load() {
    const s = ++seq;
    loading.value = true;
    try {
      const res = await fetcher({
        keyword: keyword.value,
        pageNum: pageNum.value,
        pageSize
      });
      if (s !== seq) return;
      items.value = res.items;
      total.value = res.total;
      hasNext.value = res.hasNext;
    } catch (e) {
      if (s !== seq) return;
      // 失败保留当前页数据；反馈经 onError 回调由调用方 toast（不设内部错误态）。
      // rethrow 供翻页调用方回滚页码（页码先行更新，失败须退回与保留数据一致）
      opts?.onError?.(e);
      throw e;
    } finally {
      if (s === seq) loading.value = false;
    }
  }

  /** 远程搜词（remote-method 入参；清空输入收到 "" 同样回第 1 页重查；失败页码已为 1 无需回滚） */
  function search(kw: string) {
    keyword.value = kw ?? "";
    pageNum.value = 1;
    void load().catch(() => undefined);
  }

  /** 翻页：页码先行更新，失败回滚到与保留数据一致的页（codex 外评 P2）；
   *  回滚仅在该请求仍是最新代际时执行（失败后有新请求接管页码时不覆写） */
  function prevPage() {
    if (pageNum.value > 1) {
      const prev = pageNum.value;
      const mySeq = seq + 1;
      pageNum.value--;
      void load().catch(() => {
        if (seq === mySeq) pageNum.value = prev;
      });
    }
  }

  function nextPage() {
    if (hasNext.value) {
      const prev = pageNum.value;
      const mySeq = seq + 1;
      pageNum.value++;
      void load().catch(() => {
        if (seq === mySeq) pageNum.value = prev;
      });
    }
  }

  /** 弹窗重开/上下文切换：复位全部状态并清空在途请求的回写资格 */
  function reset() {
    seq++;
    keyword.value = "";
    pageNum.value = 1;
    items.value = [];
    total.value = 0;
    hasNext.value = false;
    loading.value = false;
  }

  return {
    keyword,
    pageNum,
    items,
    total,
    hasNext,
    loading,
    load,
    search,
    prevPage,
    nextPage,
    reset
  };
}

/**
 * 当前页选项 ∪ 已选项（按 key 去重，已选不在当前页的补在尾部）——
 * el-select 的 option 列表渲染源，保证已选 tag 始终有 label 可显示。
 */
export function mergeSelected<T, K>(
  pageItems: T[],
  selected: Map<K, T>,
  keyOf: (t: T) => K
): T[] {
  const out = [...pageItems];
  const seen = new Set(pageItems.map(keyOf));
  for (const t of selected.values()) {
    if (!seen.has(keyOf(t))) {
      out.push(t);
      seen.add(keyOf(t));
    }
  }
  return out;
}
