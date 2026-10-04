import type { OrgTreeNode } from "@/api/user-manage";

/**
 * 树刷新后的选中重定位判定（外部评审 P2 处置，2026-10-04）：
 * loadTree 只替换树数据不重发选中事件，消费方（user/index.vue 的
 * selectedOrg 等）会一直持有旧快照——T-FE-061 起授予入口按
 * selectedOrg.status 禁用，「停用→编辑启用→保存」后按钮仍禁用，
 * 违背「恢复启用后可选择」定案（permission-grant.md §1.1）。
 *
 * keep=节点仍在原始树中：以服务端新快照重发 org-change（消费方
 * 按新树重查详情，不重复加载成员表——orgId 值不变 watch 不触发）；
 * clear=节点消失（被删/被过滤/树配置切换裁剪）：走既有清空链路；
 * none=无选中（挂载初次加载/配置切换已清空）：不通知。
 */
export type RelocateResult =
  | { kind: "keep"; node: OrgTreeNode }
  | { kind: "clear" }
  | { kind: "none" };

export function relocateSelectionAfterReload(
  nodes: OrgTreeNode[],
  selectedOrgId: number | null
): RelocateResult {
  if (selectedOrgId == null) {
    return { kind: "none" };
  }
  const node = findOrgById(nodes, selectedOrgId);
  return node ? { kind: "keep", node } : { kind: "clear" };
}

function findOrgById(
  nodes: OrgTreeNode[],
  id: number
): OrgTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const found = findOrgById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}
