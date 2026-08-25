/**
 * 主体树构造纯函数（评审问题 5：递归保留真实 children + 虚拟容器装 extra-roles）。
 *
 * 从 filterVisibleTree 提取为独立模块，便于单测；不依赖响应式/this。
 * 设计依据：docs/design/frontend/permission-grant.md §1.1（角色入口两类型 + 分组展开）。
 */
import type { RoleTreeNode } from "@/api/role-manage";
import type { SubjectTreeNode } from "./types";

/**
 * 角色树过滤：T-PERM-043 后仅保留 BASIC_ROLE（GROUP_ROLE 写入口已删除，主体树不展示，
 * 存量节点整棵裁掉；ORG/POSITION/PERSONAL 不属角色入口，§1.1）。
 * 递归保留真实 children（评审问题 5）。
 * ROOT 为分组根容器，透明下钻不入结果。
 * buildExtraContainer 等 GROUP_ROLE 展开代码保留（不可达），待 role_inclusion 立项恢复。
 */
export function filterVisibleTree(nodes: RoleTreeNode[]): SubjectTreeNode[] {
  const result: SubjectTreeNode[] = [];
  for (const node of nodes) {
    if (node.roleTypeCode === "ROOT") {
      result.push(...filterVisibleTree(node.children ?? []));
      continue;
    }
    if (node.roleTypeCode !== "BASIC_ROLE") {
      continue;
    }
    result.push({
      key: `role:${node.externalId ?? node.id}`,
      kind: "ROLE",
      roleTypeCode: node.roleTypeCode,
      externalId: node.externalId,
      name: node.name,
      status: node.status,
      children: filterVisibleTree(node.children ?? [])
    });
  }
  return result;
}

/**
 * 构造"关联基础角色"虚拟容器（GROUP_ROLE 展开时装 extra-roles，评审问题 5）。
 * 异步加载完成后由组件层追加到 GROUP_ROLE 节点 children 末尾，不覆盖真实 children。
 * 容器自身不可选（kind=EXTRA_CONTAINER），其子节点 kind=EXTRA_ROLE。
 */
export function buildExtraContainer(
  parentExternalId: string,
  parentName: string,
  basics: Array<{ externalId: string; name: string }>
): SubjectTreeNode {
  return {
    key: `extra-container:role:${parentExternalId}`,
    kind: "EXTRA_CONTAINER",
    roleTypeCode: "GROUP_ROLE",
    externalId: null,
    name: "关联基础角色",
    status: 1,
    children: basics.map(b => ({
      key: `role:${b.externalId}@${parentExternalId}`,
      kind: "EXTRA_ROLE" as const,
      roleTypeCode: "BASIC_ROLE",
      externalId: b.externalId,
      name: b.name,
      status: 1,
      expandedFromGroup: true,
      groupRoleName: parentName,
      children: []
    }))
  };
}

// ========== 刷新后动作决策（评审问题 6） ==========

export type RefreshAction =
  | { kind: "preset"; externalId: string }
  | { kind: "syncName"; displayName: string }
  | { kind: "clearSubject" }
  | { kind: "none" };

/**
 * 树刷新后动作决策（问题 6：一次性入口指令优先 + 当前主体处理）。
 * 纯函数，便于单测；执行侧（findNode/preselect/syncDisplayName/resetAll）在 hook.refreshAndPreset。
 *
 * 优先级：
 * 1. query.roleExternalId 存在 -> preset（一次性入口指令，找到节点 requestSelect）
 * 2. 当前主体 context 存在 + 角色仍在树中 -> syncName（同步改名，不重载 baseline）
 * 3. 当前主体 context 存在 + 角色已不在树中 -> clearSubject（清空主体 + 提示）
 * 4. 无 query 无 context -> none
 */
export function decideRefreshAction(args: {
  queryExternalId: string | string[] | null | undefined;
  context: { roleExternalId: string; roleTypeCode: string } | null;
  /** 当前主体角色在刷新后树中的节点（无 query 时才查）；null = 已删除 */
  node: { name: string } | null;
}): RefreshAction {
  const { queryExternalId, context, node } = args;
  if (typeof queryExternalId === "string" && queryExternalId) {
    return { kind: "preset", externalId: queryExternalId };
  }
  if (context) {
    return node
      ? { kind: "syncName", displayName: node.name }
      : { kind: "clearSubject" };
  }
  return { kind: "none" };
}
