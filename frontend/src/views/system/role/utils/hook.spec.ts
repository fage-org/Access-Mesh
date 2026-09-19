/**
 * 角色禁用二次确认与跨层级拖拽确认回归（T-FE-052，定案④）：
 * 旧实现 handleToggleStatus 无确认直接禁用、handleNodeDrop 直接 moveRole——
 * old-fail 用例在旧实现下失败；启用不确认/同父排序不确认/非法移动回滚为特征锁
 * （旧实现同行为，锁防回退）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetRoleTree = vi.fn();
const mockUpdateRole = vi.fn();
const mockMoveRole = vi.fn();
const mockConfirm = vi.fn();
const mockMessage = vi.fn();

// 阻断 hook 模块级链（同 user hook.spec 范式：api/message/element-plus 全 mock；
// MANAGEABLE_ROLE_TYPES 须随 mock 提供——./types 顶部从本模块导入消费）
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => mockConfirm(...args) }
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));
vi.mock("@/api/role-manage", () => ({
  MANAGEABLE_ROLE_TYPES: ["BASIC_ROLE"],
  getRoleTree: (...args: unknown[]) => mockGetRoleTree(...args),
  createRole: vi.fn(),
  updateRole: (...args: unknown[]) => mockUpdateRole(...args),
  moveRole: (...args: unknown[]) => mockMoveRole(...args),
  removeRoles: vi.fn()
}));

import { useRoleManage } from "./hook";
import type { RoleTreeNode } from "@/api/role-manage";

function node(partial: Partial<RoleTreeNode>): RoleTreeNode {
  return {
    id: 0,
    parentId: null,
    roleTypeCode: "BASIC_ROLE",
    name: "",
    externalId: null,
    status: 1,
    sortOrder: 0,
    children: [],
    ...partial
  };
}

/** 树形态：角色A(1) ─┬─ 子角色C(3)、子角色D(4)；角色B(2) 顶层 */
const TREE: RoleTreeNode[] = [
  node({
    id: 1,
    name: "角色A",
    children: [
      node({ id: 3, parentId: 1, name: "子角色C" }),
      node({ id: 4, parentId: 1, name: "子角色D" })
    ]
  }),
  node({ id: 2, name: "角色B" })
];

/** 递归查找：未命中返回 null（空 children 也走完循环），顶层 mustFind 兜底报错 */
function findIn(nodes: RoleTreeNode[], id: number): RoleTreeNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const hit = n.children ? findIn(n.children, id) : null;
    if (hit) return hit;
  }
  return null;
}

function mustFind(id: number): RoleTreeNode {
  const hit = findIn(TREE, id);
  if (!hit) throw new Error(`node ${id} not found`);
  return hit;
}

describe("角色禁用二次确认（T-FE-052 定案④）", () => {
  beforeEach(() => {
    // clearAllMocks 只清调用记录不清 implementation——默认实现逐项重设防跨用例泄漏
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetRoleTree.mockResolvedValue(TREE);
    mockUpdateRole.mockResolvedValue(undefined);
  });

  it("禁用先弹确认（含角色名与定案钉死短语），确认后 updateRole、状态回写、成功提示（旧实现无确认必失败）", async () => {
    const target = node({ id: 3, name: "子角色C", status: 1 });
    const { handleToggleStatus } = useRoleManage();
    await handleToggleStatus(target);

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(mockConfirm.mock.calls[0][0]).toContain("子角色C");
    // 定案④钉死短语——被删/改写时此断言红
    expect(mockConfirm.mock.calls[0][0]).toContain(
      "全部持有者立即失去该角色权限"
    );

    expect(mockUpdateRole).toHaveBeenCalledWith({ roleId: 3, status: 0 });
    expect(target.status).toBe(0);
    expect(mockMessage).toHaveBeenCalledWith("禁用成功", { type: "success" });
  });

  it("禁用取消：零请求、零提示、状态不变（旧实现无确认照发 updateRole 必失败）", async () => {
    mockConfirm.mockRejectedValue("cancel");
    const target = node({ id: 3, name: "子角色C", status: 1 });
    const { handleToggleStatus } = useRoleManage();
    await handleToggleStatus(target);

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(mockUpdateRole).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
    expect(target.status).toBe(1);
  });

  it("启用不弹确认直接执行（特征锁：启用为安全方向，对齐用户页仅停用确认先例）", async () => {
    const target = node({ id: 3, name: "子角色C", status: 0 });
    const { handleToggleStatus } = useRoleManage();
    await handleToggleStatus(target);

    expect(mockConfirm).not.toHaveBeenCalled();
    expect(mockUpdateRole).toHaveBeenCalledWith({ roleId: 3, status: 1 });
    expect(target.status).toBe(1);
    expect(mockMessage).toHaveBeenCalledWith("启用成功", { type: "success" });
  });
});

describe("角色树拖拽跨层级确认（T-FE-052 定案④）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetRoleTree.mockResolvedValue(TREE);
    mockUpdateRole.mockResolvedValue(undefined);
    mockMoveRole.mockResolvedValue(undefined);
  });

  it("inner 跨父：弹确认（含原/新上级名与后果），取消零 move 请求并重拉树恢复（旧实现无确认必失败）", async () => {
    mockConfirm.mockRejectedValue("cancel");
    const { handleNodeDrop, loadTree } = useRoleManage();
    await loadTree(); // 预载树：from-label 在 hook 内部 roleTree 中解析（生产面拖拽必在已渲染树上）
    await handleNodeDrop({ data: mustFind(3) }, { data: mustFind(2) }, "inner");

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    const text = mockConfirm.mock.calls[0][0] as string;
    expect(text).toContain("子角色C");
    expect(text).toContain("角色A"); // 原上级
    expect(text).toContain("角色B"); // 新上级
    // 旧实现无确认直接 moveRole——以下两断言在旧实现下失败
    expect(mockMoveRole).not.toHaveBeenCalled();
    expect(mockGetRoleTree).toHaveBeenCalledTimes(2); // 预载 1 + 取消重拉恢复 1
    expect(mockMessage).not.toHaveBeenCalled();
  });

  it("after 跨父移至顶层：确认后 moveRole(parentId=null)、成功提示、重拉树（旧实现无确认必失败）", async () => {
    const { handleNodeDrop, loadTree } = useRoleManage();
    await loadTree();
    await handleNodeDrop({ data: mustFind(3) }, { data: mustFind(2) }, "after");

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    const text = mockConfirm.mock.calls[0][0] as string;
    expect(text).toContain("顶层"); // 新上级=顶层
    expect(mockMoveRole).toHaveBeenCalledWith({ roleId: 3, parentId: null });
    expect(mockMessage).toHaveBeenCalledWith("移动成功", { type: "success" });
    expect(mockGetRoleTree).toHaveBeenCalledTimes(2); // 预载 1 + 移动后同步 parentId 重拉 1
  });

  it("同父排序：不弹确认直接 moveRole（特征锁：定案④同级排序直接生效）", async () => {
    const { handleNodeDrop, loadTree } = useRoleManage();
    await loadTree();
    await handleNodeDrop(
      { data: mustFind(4) },
      { data: mustFind(3) },
      "before"
    );

    expect(mockConfirm).not.toHaveBeenCalled();
    expect(mockMoveRole).toHaveBeenCalledWith({ roleId: 4, parentId: 1 });
  });

  it("只读类型拖拽：警告提示+重拉树+零 move+不弹确认（特征锁：现状回滚行为保持）", async () => {
    const readonly = node({ id: 10, roleTypeCode: "ORG", name: "组织角色" });
    const { handleNodeDrop, loadTree } = useRoleManage();
    await loadTree();
    await handleNodeDrop({ data: readonly }, { data: mustFind(2) }, "inner");

    expect(mockMessage).toHaveBeenCalledWith(
      "组织/岗位/个人角色由同步生成，不可移动",
      { type: "warning" }
    );
    expect(mockMoveRole).not.toHaveBeenCalled();
    expect(mockConfirm).not.toHaveBeenCalled();
    expect(mockGetRoleTree).toHaveBeenCalledTimes(2); // 预载 1 + 回滚重拉 1
  });

  it("跨类型拖拽：警告提示+重拉树+零 move+不弹确认（特征锁：非法移动先于确认拦截）", async () => {
    const foreign = node({ id: 11, roleTypeCode: "BASIC_ROLE", name: "X" });
    const orgTarget = node({ id: 12, roleTypeCode: "ORG", name: "组织角色" });
    const { handleNodeDrop, loadTree } = useRoleManage();
    await loadTree();
    await handleNodeDrop({ data: foreign }, { data: orgTarget }, "inner");

    expect(mockMessage).toHaveBeenCalledWith("不允许跨角色类型移动", {
      type: "warning"
    });
    expect(mockMoveRole).not.toHaveBeenCalled();
    expect(mockConfirm).not.toHaveBeenCalled();
    expect(mockGetRoleTree).toHaveBeenCalledTimes(2); // 预载 1 + 回滚重拉 1
  });
});
