import { ref, reactive, computed, onMounted } from "vue";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { toErrorMessage } from "@/api/_envelope";
import { useListLoad } from "@/utils/list-load";
import {
  getDependencyList,
  type ResourceDependencyResp
} from "@/api/resource-dependency";
import {
  getResourceTree,
  getOperationList,
  type ResourceTreeNode,
  type OperationPermissionResp
} from "@/api/resource-operation";
import { RESOURCE_DEPENDENCY_PERMS } from "./perms";
import { hasBit } from "@/utils/bit-ops";

/**
 * 资源依赖页组合式逻辑。
 *
 * 引用数据映射（Resp 已随 T-PERM-031 补静态字段，映射保留为冗余快路径）：
 * - resourceMap：getResourceTree 扁平化 -> id->{name, resourceTypeCode, code, codeType}
 * - operationList：getOperationList 全量操作；bitsToOpNames(bits, typeCode) 按资源类型
 *   过滤，hasBit（BigInt）位与拆解（P1 修复：typeCode 隔离跨类型同 bit 误匹配，
 *   BigInt 避免 32 位截断）
 *
 * 资源业务键用于只读循环检查；声明发布由所属服务完成。
 */
export function useResourceDependency() {
  // ========== 权限门控（computed：会话热刷新后即时重算——T-FE-055 复评同族收敛，
  // 全仓其余页面门控均为 computed 形态，本页原 setup 一次性取值系孤例） ==========
  const canView = computed(() =>
    hasPerms(RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_VIEW)
  );
  // ========== 列表状态 ==========
  // T-FE-051：列表加载收敛 useListLoad（latest-wins 代际 + 失败提示保留旧数据——
  // 原实现失败清空列表，按全仓统一口径改为保留）；权限门禁留在包装层
  const {
    list,
    loading,
    load: loadListCore
  } = useListLoad<ResourceDependencyResp>({
    errorText: "加载依赖列表失败",
    fetcher: async () => (await getDependencyList({})).items
  });
  const search = reactive({ keyword: "" });

  // ========== 引用数据 ==========
  const resourceMap = ref<Map<number, ResourceTreeNode>>(new Map());
  const resourceList = ref<ResourceTreeNode[]>([]);
  const operationList = ref<OperationPermissionResp[]>([]);
  const resourceTypeOptions = ref<Array<{ value: string; label: string }>>([]);

  // ========== 映射辅助 ==========

  /** 资源 ID -> 名称（缺失回退 #id） */
  function resolveResourceName(id: number | null | undefined): string {
    if (id == null) return "—";
    return resourceMap.value.get(id)?.name ?? `#${id}`;
  }

  /** 资源 ID -> 资源类型编码 */
  function resolveResourceTypeCode(
    id: number | null | undefined
  ): string | null {
    if (id == null) return null;
    return resourceMap.value.get(id)?.resourceTypeCode ?? null;
  }

  /** 操作位 -> 操作名称展示（null=任意，空=-）。
   *  P2 修复：直接从经类型过滤的操作对象取 name，避免 find(o => o.code === c)
   *  跨类型同名 code 误匹配（同一 code 在不同资源类型下名称可能不同）。 */
  function bitsToOpNames(
    bits: number | string | null,
    typeCode: string | null
  ): string {
    if (bits == null) return "任意";
    const names: string[] = [];
    for (const op of operationList.value) {
      if (op.binaryBit == null) continue;
      if (op.resourceTypeCode !== typeCode) continue;
      if (hasBit(bits, op.binaryBit)) names.push(op.name);
    }
    return names.length === 0 ? "-" : names.join("、");
  }

  // ========== 列表加载 ==========

  async function loadList() {
    if (!canView.value) return;
    return loadListCore();
  }

  // ========== 引用数据加载 ==========

  /** 递归扁平化资源树 */
  function flattenTree(
    node: ResourceTreeNode,
    map: Map<number, ResourceTreeNode>
  ) {
    map.set(node.id, node);
    if (node.children) {
      for (const child of node.children) flattenTree(child, map);
    }
  }

  async function loadRefData() {
    try {
      const [treeRes, opRes] = await Promise.all([
        getResourceTree({}),
        getOperationList({})
      ]);
      // 资源映射
      const map = new Map<number, ResourceTreeNode>();
      for (const item of treeRes.items) {
        if (item.root) flattenTree(item.root, map);
      }
      resourceMap.value = map;
      resourceList.value = Array.from(map.values()).sort((a, b) => a.id - b.id);
      // 操作映射
      operationList.value = opRes.items;
      const typeNameMap = new Map<string, string>();
      for (const op of opRes.items) {
        if (op.resourceTypeCode != null && op.resourceTypeName) {
          typeNameMap.set(op.resourceTypeCode, op.resourceTypeName);
        }
      }
      // 资源类型选项：从 resourceMap distinct，中文名从 operationList 补
      const typeSet = new Set<string>();
      for (const node of map.values()) {
        typeSet.add(node.resourceTypeCode);
      }
      resourceTypeOptions.value = Array.from(typeSet)
        .sort()
        .map(code => ({ value: code, label: typeNameMap.get(code) ?? code }));
    } catch (e) {
      message(toErrorMessage(e, "加载引用数据失败"), { type: "error" });
    }
  }

  // ========== 过滤 ==========

  const filteredList = computed(() => {
    const kw = search.keyword.trim().toLowerCase();
    if (!kw) return list.value;
    return list.value.filter(d => {
      const sourceName = (
        d.sourceResourceName || resolveResourceName(d.resourceEntityId)
      ).toLowerCase();
      const targetName = (
        d.targetResourceName || resolveResourceName(d.dependsOnResourceEntityId)
      ).toLowerCase();
      const desc = (d.description ?? "").toLowerCase();
      return (
        sourceName.includes(kw) ||
        targetName.includes(kw) ||
        d.sourceResourceCode.toLowerCase().includes(kw) ||
        d.depResourceCode.toLowerCase().includes(kw) ||
        desc.includes(kw)
      );
    });
  });

  function resetFilters() {
    search.keyword = "";
  }

  // ========== 循环检测 ==========
  // 环检测交互由 CycleCheckDialog 直调 checkDependencyCycle API（业务键经自身资源映射构造），
  // hook 不再重复封装（原 checkCycle 死导出已随外评清扫删除）。

  onMounted(() => {
    loadList();
    loadRefData();
  });

  return {
    canView,
    list,
    loading,
    search,
    filteredList,
    resetFilters,
    loadList,
    // 引用数据
    resourceMap,
    resourceList,
    operationList,
    resourceTypeOptions,
    // 映射辅助
    resolveResourceName,
    resolveResourceTypeCode,
    bitsToOpNames
  };
}
