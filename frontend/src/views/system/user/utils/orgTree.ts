import type { OrgTreeNode } from "@/api/user-manage";

/** 父名由服务端提供；真实根与父级不可见分开显示，不依赖当前树筛选。 */
export function getParentOrgLabel(
  node?: Pick<OrgTreeNode, "parentOrgId" | "parentOrgName">
): string {
  if (!node?.parentOrgId) return "根组织";
  return node.parentOrgName?.trim() || "上级不可见";
}
