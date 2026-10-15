import { ref, type Ref } from "vue";
import type { OrgTreeNode } from "@/api/user-manage";
import { relocateSelectionAfterReload } from "./relocate";

export interface OrgTreeModelOptions {
  /** 取树（configId=请求时选中配置；null=后端默认树语义，orgType/operationCode 由闭包补齐） */
  fetchTree: (configId: number | null) => Promise<OrgTreeNode[]>;
  /** 组织域变化通知（配置切换预清空/重定位 keep/clear 统一出口——消费方右侧列表据此换域） */
  orgChange: (orgId: number | null) => void;
  /** 树加载失败提示（迟到失败不提示） */
  loadError: () => void;
}

export interface OrgTreeModel {
  /** 最新成功回写的树（迟到响应被代际丢弃不回写） */
  tree: Ref<OrgTreeNode[]>;
  selectedConfigId: Ref<number | null>;
  selectedOrgId: Ref<number | null>;
  selectedOrgName: Ref<string>;
  /** 选中组织身份回写（node-click 路径） */
  selectOrg: (node: OrgTreeNode) => void;
  clearOrg: () => void;
  /** latest-wins 树加载（挂载/编辑保存后刷新）：迟到成功/迟到失败均静默丢弃（含重定位与 org-change 副作用）；返回 true=本次为最新代际 */
  loadTree: () => Promise<boolean>;
  /** 配置切换：换配置身份+清选中，立即清空旧树并通知消费方，再发起新加载 */
  switchConfig: (configId: number) => Promise<boolean>;
}

/**
 * 组织树面板的树/选中生命周期（T-FE-068 自 SFC 抽出，形态对齐 oauth-consent/model.ts）。
 *
 * 竞态守卫（useListLoad 同款 latest-wins 代际）：
 * - loadTree 发起时领票（seq++），await 归来先验票——旧配置请求的迟到成功不得
 *   覆写新配置树，迟到失败不得清树/弹错（探针实证：20→30 连续切换、30 先回 20
 *   后到时旧实现目录与选中被旧配置覆盖，右侧用户列表随 org-change 错域）；
 * - switchConfig 跨配置发起即预清空：旧配置目录/选中不得在新配置在途窗口内继续
 *   可见可用——清树 + org-change(null) 通知消费方，同刻作废在途。
 */
export function createOrgTreeModel(
  initialConfigId: number | null,
  options: OrgTreeModelOptions
): OrgTreeModel {
  const tree = ref<OrgTreeNode[]>([]);
  const selectedConfigId = ref<number | null>(initialConfigId);
  const selectedOrgId = ref<number | null>(null);
  const selectedOrgName = ref("");
  /** 请求代际：仅最新一次 loadTree 的结果可回写树/选中/事件 */
  let seq = 0;

  function selectOrg(node: OrgTreeNode) {
    selectedOrgId.value = node.id;
    selectedOrgName.value = node.orgName;
    options.orgChange(node.id);
  }

  function clearOrg() {
    selectedOrgId.value = null;
    selectedOrgName.value = "";
    options.orgChange(null);
  }

  async function loadTree(): Promise<boolean> {
    const ticket = ++seq;
    const configId = selectedConfigId.value;
    let nodes: OrgTreeNode[];
    try {
      nodes = await options.fetchTree(configId);
    } catch {
      if (ticket !== seq) return false; // 迟到失败静默丢弃（新请求已发起）
      tree.value = [];
      options.loadError();
      return false;
    }
    if (ticket !== seq) return false; // 迟到成功静默丢弃（用户已切换配置/再次发起）
    tree.value = nodes;
    // 刷新后按既有选中重定位：节点仍在树中以服务端新快照重发 org-change；消失走清空链路
    const relocation = relocateSelectionAfterReload(nodes, selectedOrgId.value);
    if (relocation.kind === "keep") {
      selectedOrgName.value = relocation.node.orgName;
      options.orgChange(relocation.node.id);
    } else if (relocation.kind === "clear") {
      clearOrg();
    }
    return true;
  }

  async function switchConfig(configId: number): Promise<boolean> {
    selectedConfigId.value = configId;
    selectedOrgId.value = null;
    selectedOrgName.value = "";
    // 跨配置发起即清旧树并通知消费方；同刻作废在途（迟到响应不再回写旧配置树）
    seq++;
    tree.value = [];
    options.orgChange(null);
    return loadTree();
  }

  return {
    tree,
    selectedConfigId,
    selectedOrgId,
    selectedOrgName,
    selectOrg,
    clearOrg,
    loadTree,
    switchConfig
  };
}
