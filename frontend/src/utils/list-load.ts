/**
 * 列表加载 composable（T-FE-051）：全仓列表 hook 统一接线点。
 *
 * 两层：
 * - useListLoad——通用列表加载（分页与否皆可）：latest-wins 请求代际守卫
 *   （迟到成功/迟到失败均静默丢弃，副作用仅最新请求触发）+ 失败 message 提示
 *   （后端 body 文案优先，见 @/api/_envelope toErrorMessage）+ 失败保留旧数据
 *   （比清空友好；error 态与空态——成功且 items 空——区分）+ loading 仅最新请求复位。
 * - usePagedList——分页表格层：在 useListLoad 上叠 pagination 与翻页回调，
 *   total 仅随最新成功请求回写。
 *
 * 先例（收敛源）：permission-change-log reqSeq / biz-domain configReqSeq /
 * service-interface MappingForm treeRequestSeq / 授予页 matrixToken（矩阵面，形态不同不收敛）。
 */
import { computed, reactive, ref } from "vue";
import type { Ref } from "vue";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";

export interface ListLoadOptions<T> {
  /** 取数（调用方闭包内完成排序/映射等变换，返回最终列表） */
  fetcher: () => Promise<T[]>;
  /** 失败提示 fallback（后端 body message 优先） */
  errorText: string;
  /** 数据回写后的页面副作用（仅最新请求触发；迟到响应不触发） */
  onLoaded?: (items: T[]) => void;
}

export function useListLoad<T>(opts: ListLoadOptions<T>) {
  const list = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  /** 最近一次失败文案（null=无错误）；失败保留旧数据，与空态（成功且空）区分 */
  const error = ref<string | null>(null);

  let reqSeq = 0;

  /** 加载：latest-wins。返回 true=最新请求成功回写；false=失败（已提示）或迟到被丢弃 */
  async function load(): Promise<boolean> {
    const seq = ++reqSeq;
    loading.value = true;
    error.value = null;
    try {
      const items = await opts.fetcher();
      // 过期请求静默丢弃（用户已发起更新的请求），含 onLoaded 副作用
      if (seq !== reqSeq) return false;
      list.value = items;
      opts.onLoaded?.(items);
      return true;
    } catch (e) {
      // 过期失败同样丢弃
      if (seq !== reqSeq) return false;
      error.value = toErrorMessage(e, opts.errorText);
      message(error.value, { type: "error" });
      // 失败保留旧数据（比清空友好）
      return false;
    } finally {
      // 仅最新请求复位 loading，迟到请求不干扰
      if (seq === reqSeq) loading.value = false;
    }
  }

  return { list, loading, error, load };
}

export interface PagedListOptions<T> {
  /** 分页取数（page/size 由 composable 传入，searchForm 等经闭包读取最新值） */
  fetcher: (
    page: number,
    size: number
  ) => Promise<{ items: T[]; total: number }>;
  /** 失败提示 fallback */
  errorText: string;
  /** 初始页大小（默认 15，对齐各页现行值） */
  initialSize?: number;
}

export function usePagedList<T>(opts: PagedListOptions<T>) {
  const pagination = reactive({
    page: 1,
    size: opts.initialSize ?? 15,
    total: 0
  });

  // 复合结果通道（双轨评审 P3-1）：total 随请求自身结果走 await 链——若经共享变量暂存，
  // 同栈双 resolve（b.resolve 后紧跟 a.resolve、中间无宏任务边界）时后到的包装续体会
  // 覆写共享值，最新请求通过代际判定后读到旧 total（表格=B 而 total=A）。
  // 复合结果以单元素数组形态复用 useListLoad 的 T[] 通道，tableData 经 computed 解包。
  const {
    list: latestPage,
    loading,
    error,
    load
  } = useListLoad<{ items: T[]; total: number }>({
    errorText: opts.errorText,
    fetcher: async () => [await opts.fetcher(pagination.page, pagination.size)],
    // total 仅随最新成功请求回写（迟到响应不回写；失败保留旧 total 与旧数据一致）
    onLoaded: ([latest]) => {
      pagination.total = latest.total;
    }
  });

  /** 表格数据=最新成功请求的 items（复合结果解包） */
  const tableData = computed(() => latestPage.value[0]?.items ?? []);

  function loadTable() {
    return load();
  }

  function onSearch() {
    pagination.page = 1;
    load();
  }

  function onPageChange(page: number) {
    pagination.page = page;
    load();
  }

  function onPageSizeChange(size: number) {
    pagination.size = size;
    pagination.page = 1;
    load();
  }

  return {
    tableData,
    loading,
    error,
    pagination,
    loadTable,
    onSearch,
    onPageChange,
    onPageSizeChange
  };
}
