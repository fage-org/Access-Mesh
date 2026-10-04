/**
 * 树刷新后选中重定位回归（外部评审 P2 处置，2026-10-04）：loadTree 只换
 * 树数据不重发选中事件，消费方 selectedOrg 持旧快照——停用组织经信息卡
 * 编辑启用并保存后，T-FE-061 的授予入口按旧 status 持续禁用，违背
 * 「恢复启用后可选择」定案。旧实现（loadTree 无重定位）下无此模块——
 * 本 spec 以模块缺失红起（old-fail 形态=新模块契约锁，confirmMove 先例）。
 */
import { describe, it, expect } from "vitest";

import { relocateSelectionAfterReload } from "./relocate";
import type { OrgTreeNode } from "@/api/user-manage";

function node(
  id: number,
  orgName: string,
  children: OrgTreeNode[] = []
): OrgTreeNode {
  return {
    id,
    orgName,
    code: `ORG-${id}`,
    parentOrgId: null,
    orgType: 1,
    status: 1,
    sort: 0,
    children
  };
}

describe("relocateSelectionAfterReload（评审 P2：刷新后按选中重定位）", () => {
  it("选中节点仍在树中（含嵌套）：keep 且返回服务端新快照（新 status/父名由此到达消费方）", () => {
    const reloaded: OrgTreeNode[] = [
      node(1, "总部", [
        node(2, "研发部", [node(3, "后端组", [])]),
        node(4, "产品部", [])
      ])
    ];
    const result = relocateSelectionAfterReload(reloaded, 3);

    expect(result).toEqual({ kind: "keep", node: reloaded[0]!.children![0]!.children![0] });
  });

  it("选中节点从新树消失（被删/被过滤/树配置切换裁剪）：clear（消费方走既有清空链路）", () => {
    const reloaded: OrgTreeNode[] = [node(1, "总部", [])];
    const result = relocateSelectionAfterReload(reloaded, 99);

    expect(result).toEqual({ kind: "clear" });
  });

  it("无选中（挂载初次加载/配置切换已清空）：none 不通知（不触发消费方副作用）", () => {
    const result = relocateSelectionAfterReload([node(1, "总部", [])], null);

    expect(result).toEqual({ kind: "none" });
  });
});
