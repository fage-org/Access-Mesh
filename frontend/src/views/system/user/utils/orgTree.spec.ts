import { describe, it, expect } from "vitest";
import type { OrgTreeNode } from "@/api/user-manage";
import { findParentOrgName } from "./orgTree";

/** 构造树节点（缺省字段填中性值——测试只关心 id/orgName/parentOrgId/children） */
function node(
  id: number,
  orgName: string,
  parentOrgId: number | null,
  children: OrgTreeNode[] = []
): OrgTreeNode {
  return {
    id,
    orgName,
    code: `ORG-${id}`,
    parentOrgId,
    orgType: 1,
    status: 1,
    sort: 0,
    children
  };
}

/**
 * 两根两枝树：研发部（首位根，含前端组/后端组）+ 市场部（后位根，含品牌组）。
 * 目标父组织凡位于「后位兄弟子树」即命中旧 bug：首个子树递归失败返回
 * 「未知」（truthy 哨兵）被当成功值上抛，后续兄弟子树永不遍历。
 */
const TREE: OrgTreeNode[] = [
  node(1, "研发部", null, [node(11, "前端组", 1), node(12, "后端组", 1)]),
  node(2, "市场部", null, [node(21, "品牌组", 2)])
];

describe("findParentOrgName（T-FE-050：未找到不剪枝，兄弟子树继续遍历）", () => {
  it("父组织是后位根节点——返回其名称（旧实现钻入首根子树后整体返回「未知」）", () => {
    expect(findParentOrgName(TREE, 2)).toBe("市场部");
  });

  it("父组织在后位根的子树内——返回其名称（旧实现同因返回「未知」）", () => {
    expect(findParentOrgName(TREE, 21)).toBe("品牌组");
  });

  it("父组织在首根自身/首根子树——新旧实现都应命中（回归保护）", () => {
    expect(findParentOrgName(TREE, 1)).toBe("研发部");
    expect(findParentOrgName(TREE, 11)).toBe("前端组");
  });

  it("parentId 为 null——返回 null（「根组织」文案由调用点决定；旧实现返回「根组织」）", () => {
    expect(findParentOrgName(TREE, null)).toBeNull();
  });

  it("树中不存在该 id——返回 null（「未知」文案由调用点决定；旧实现返回「未知」）", () => {
    expect(findParentOrgName(TREE, 999)).toBeNull();
  });
});
