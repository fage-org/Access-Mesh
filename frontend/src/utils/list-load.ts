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
 * 上下文绑定（T-FE-059，F011）：contextKey 声明列表数据的归属上下文（选中服务/类型/
 * 组织等）——为不同上下文取数时发起即清空旧数据（旧上下文数据不得在新上下文下可写），
 * 切换后失败保持空集；同上下文刷新失败保留旧数据。clear() 供调用方置空上下文时使用，
 * 同时作废在途请求。预清空先例（biz-domain selectDomain / MappingForm 切类型）达同语义。
 *
 * 先例（收敛源）：permission-change-log reqSeq / biz-domain configReqSeq /
 * service-interface MappingForm treeRequestSeq / 授予页 matrixToken（矩阵面，形态不同不收敛）。
 */
import { computed, reactive, ref } from "vue";
import type { Ref } from "vue";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";

/** 「从未成功加载/已清空」上下文哨兵（与任何 contextKey 返回值不同构，含 null/undefined） */
const NO_CONTEXT = Symbol("list-load:no-context");

export interface ListLoadOptions<T> {
  /** 取数（调用方闭包内完成排序/映射等变换，返回最终列表） */
  fetcher: () => Promise<T[]>;
  /** 失败提示 fallback（后端 body message 优先） */
  errorText: string;
  /** 数据回写后的页面副作用（仅最新请求触发；迟到响应不触发） */
  onLoaded?: (items: T[]) => void;
  /** 上下文键（T-FE-059）：列表数据归属的稳定标识（选中服务/类型/组织等）。
   *  提供后：为不同上下文取数时发起即清空旧数据——旧上下文数据不得在新上下文下可写，
   *  切换后失败保持空集（F011）；同上下文刷新失败仍保留旧数据（不制造额外损失）。 */
  contextKey?: () => unknown;
  /** 数据因上下文切换被清空或 clear() 时的副作用（如分页层重置 total） */
  onClear?: () => void;
}

export function useListLoad<T>(opts: ListLoadOptions<T>) {
  const list = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  /** 最近一次失败文案（null=无错误）；失败保留旧数据，与空态（成功且空）区分 */
  const error = ref<string | null>(null);

  let reqSeq = 0;
  /** 当前数据归属的上下文（NO_CONTEXT=从未成功加载或已清空；contextKey 缺省形态恒为 NO_CONTEXT） */
  let loadedContext: unknown = NO_CONTEXT;
  /** 曾成功加载过：区分「null 上下文已加载」与「从未加载」——null 是合法上下文值（如全组织视图） */
  let hasLoadedContext = false;

  /** 清空数据并作废在途请求（T-FE-059：调用方置空/切换上下文时使用，迟到响应不回写） */
  function clear() {
    reqSeq++;
    list.value = [];
    error.value = null;
    loading.value = false;
    loadedContext = NO_CONTEXT;
    hasLoadedContext = false;
    opts.onClear?.();
  }

  /** 加载：latest-wins。返回 true=最新请求成功回写；false=失败（已提示）或迟到被丢弃 */
  async function load(): Promise<boolean> {
    const seq = ++reqSeq;
    // 请求上下文与 fetcher 同刻捕获（fetcher 同步段读取选择值，二者一致）；
    // null 不作哨兵归一（null 可以是合法上下文值，如用户页「全组织」视图）
    const ctx = opts.contextKey ? opts.contextKey() : NO_CONTEXT;
    // 已有其他上下文的数据且为新上下文取数：立即清空（旧上下文数据不得在新上下文下
    // 可写；切换后失败不回填旧数据）。从未成功加载或已清空时无数据可清、不触发 onClear。
    if (opts.contextKey && hasLoadedContext && !Object.is(loadedContext, ctx)) {
      list.value = [];
      loadedContext = NO_CONTEXT;
      hasLoadedContext = false;
      opts.onClear?.();
    }
    loading.value = true;
    error.value = null;
    try {
      let items: T[];
      try {
        items = await opts.fetcher();
      } catch (e) {
        // 仅取数失败判「加载失败」——onLoaded 回调异常发生在数据回写之后，不属本类
        // （claude 外评 P3：回调抛错若走本分支会误弹加载失败并暴露内部错误串）
        if (seq !== reqSeq) return false;
        error.value = toErrorMessage(e, opts.errorText);
        message(error.value, { type: "error" });
        // 同上下文刷新失败保留旧数据（比清空友好）；上下文切换已在上文清空
        return false;
      }
      // 过期请求静默丢弃（用户已发起更新的请求），含 onLoaded 副作用
      if (seq !== reqSeq) return false;
      list.value = items;
      loadedContext = ctx;
      hasLoadedContext = true;
      try {
        opts.onLoaded?.(items);
      } catch (e) {
        // 回调属页面侧代码且数据已回写：告警留痕，不冒充加载失败
        // （total 等回调内写入面可能半提交，由回调自身保证）
        console.warn("[list-load] onLoaded 回调异常:", e);
      }
      return true;
    } finally {
      // 仅最新请求复位 loading，迟到请求不干扰
      if (seq === reqSeq) loading.value = false;
    }
  }

  return { list, loading, error, load, clear };
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
  /** 上下文键（T-FE-059）：透传列表层——切换上下文清空时 total/page 同步复位 */
  contextKey?: () => unknown;
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
    },
    // 上下文切换清空：total/page 与空表格同步复位（T-FE-059）
    contextKey: opts.contextKey,
    onClear: () => {
      pagination.total = 0;
      pagination.page = 1;
    }
  });

  /** 表格数据=最新成功请求的 items（复合结果解包） */
  const tableData = computed(() => latestPage.value[0]?.items ?? []);

  function loadTable() {
    return load();
  }

  // 翻页回调返回 load() 的 promise——`await onSearch()` 等「加载完成」语义成立
  // （claude 外评存量观察②：不返回时 await 在取数发出后即返回）
  function onSearch() {
    pagination.page = 1;
    return load();
  }

  function onPageChange(page: number) {
    pagination.page = page;
    return load();
  }

  function onPageSizeChange(size: number) {
    pagination.size = size;
    pagination.page = 1;
    return load();
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
