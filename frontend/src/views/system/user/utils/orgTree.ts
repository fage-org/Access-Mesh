import type { OrgTreeNode } from "@/api/user-manage";

/**
 * 递归查找父组织名称（T-FE-050 自 index.vue 提取）。
 * 未找到（parentId 为空或树中不存在）返回 null——纯查找形态与 PositionTab 同款；
 * 「根组织」「未知」等展示文案由调用点决定（index.vue 两处调用先判 parentOrgId 再 ?? "未知"）。
 */
export function findParentOrgName(
  nodes: OrgTreeNode[],
  parentId: number | null
): string | null {
  if (!parentId) return null;
  for (const node of nodes) {
    if (node.id === parentId) return node.orgName;
    if (node.children?.length) {
      const found = findParentOrgName(node.children, parentId);
      if (found) return found;
    }
  }
  return null;
}
