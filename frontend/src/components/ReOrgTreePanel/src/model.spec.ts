/**
 * 组织树面板模型竞态回归锁（T-FE-068 配置乱序批）。
 * 场景（外部持续审查探针实证，2026-10-14）：组织树配置 20→30 连续切换、30 的响应
 * 先回 20 的迟到后到——旧实现 loadTree/onConfigChange 在 await 后无条件回写树并
 * emit org-change，目录与选中被旧配置覆盖，右侧用户列表随 org-change 错域。
 *
 * 锁①：旧配置请求的迟到成功不覆写新配置树（旧实现直接覆盖必红）。
 * 锁②：跨配置发起即清旧树并通知消费方（org-change null）——旧配置目录/选中
 *      不得在新配置在途窗口内继续可见可用（旧实现静默保留必红）。
 * 锁③：旧配置请求的迟到失败不清树、不弹错（旧实现清空+弹错必红）。
 * 特征锁：重定位 keep/clear 语义保持（刷新后选中节点在→以新快照重发 org-change；
 *         节点消失→清空+null——T-FE-061 relocate 既有语义不因抽出丢失）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { defer } from "@/test-support/async";
import type { OrgTreeNode } from "@/api/user-manage";
import { createOrgTreeModel } from "./model";

function node(id: number, orgName?: string, children: OrgTreeNode[] = []) {
  return {
    id,
    orgName: orgName ?? `org-${id}`,
    code: `code-${id}`,
    parentOrgId: null,
    orgType: 1,
    status: 1,
    sort: id,
    children
  } as OrgTreeNode;
}

function createHarness(initialConfigId: number | null = null) {
  const fetchTree = vi.fn();
  const orgChange = vi.fn();
  const loadError = vi.fn();
  const model = createOrgTreeModel(initialConfigId, {
    fetchTree,
    orgChange,
    loadError
  });
  return { model, fetchTree, orgChange, loadError };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("组织树模型竞态守卫（T-FE-068）", () => {
  it("锁①：旧配置请求迟到成功不覆写新配置树——20→30 切换、30 先回 20 后到，树与选中保持 30（旧实现被旧配置覆盖必红）", async () => {
    const { model, fetchTree, orgChange } = createHarness(20);
    const d20 = defer<OrgTreeNode[]>();
    const d30 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d20.promise);
    fetchTree.mockImplementationOnce(() => d30.promise);

    const first = model.loadTree(); // 配置 20 在途
    const second = model.switchConfig(30); // 切配置：清选中+发起新请求
    expect(fetchTree).toHaveBeenNthCalledWith(1, 20);
    expect(fetchTree).toHaveBeenNthCalledWith(2, 30);

    d30.resolve([node(31)]); // 30 先回
    await second;
    expect(model.tree.value).toEqual([node(31)]);

    d20.resolve([node(21)]); // 20 迟到后到
    await first;
    expect(model.tree.value).toEqual([node(31)]); // 旧实现：覆写为 [node(21)] → 红
    expect(orgChange).not.toHaveBeenCalledWith(21); // 旧配置的 org-change 不得再发
  });

  it("锁②：跨配置发起即清旧树并通知消费方——switchConfig 同步段清树+org-change(null)，在途窗口内旧配置目录不可见（旧实现静默保留必红）", async () => {
    const { model, fetchTree, orgChange } = createHarness(20);
    const d20 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d20.promise);
    const initial = model.loadTree();
    d20.resolve([node(1)]);
    await initial;
    expect(model.tree.value).toEqual([node(1)]);

    const d30 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d30.promise);
    // 同步段断言：发起即清空旧树并通知（不等待响应）
    const switched = model.switchConfig(30);
    expect(model.tree.value).toEqual([]); // 旧实现：旧树滞留至响应回来 → 红
    expect(orgChange).toHaveBeenLastCalledWith(null); // 消费方右侧列表随即换域/清空
    expect(model.selectedOrgId.value).toBeNull();

    d30.resolve([node(31)]);
    await switched;
    expect(model.tree.value).toEqual([node(31)]);
  });

  it("锁③：旧配置请求迟到失败不清树、不弹错——30 已成功回写后 20 迟到 reject，树保持 30（旧实现清空+弹错必红）", async () => {
    const { model, fetchTree, orgChange, loadError } = createHarness(20);
    const d20 = defer<OrgTreeNode[]>();
    const d30 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d20.promise);
    fetchTree.mockImplementationOnce(() => d30.promise);

    const first = model.loadTree();
    const second = model.switchConfig(30);
    d30.resolve([node(31)]);
    await second;
    expect(model.tree.value).toEqual([node(31)]);
    const callsAfterSwitchSuccess = orgChange.mock.calls.length;

    d20.reject(new Error("timeout"));
    await first.catch(() => {});
    expect(model.tree.value).toEqual([node(31)]); // 旧实现：清空为 [] → 红
    expect(loadError).not.toHaveBeenCalled(); // 旧实现：误弹「组织树加载失败」→ 红
    // 迟到失败零新增事件（switchConfig 预清空的 org-change(null) 是合法前置，
    // 此处锁的是迟到失败本身不触发清空/重定位通知）
    expect(orgChange.mock.calls.length).toBe(callsAfterSwitchSuccess);
  });

  it("特征锁：重定位 keep——刷新后选中节点仍在树中，以新快照重发 org-change 且选中名刷新（T-FE-061 语义保持）", async () => {
    const { model, fetchTree, orgChange } = createHarness(20);
    const d1 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d1.promise);
    const first = model.loadTree();
    d1.resolve([node(1, "old-name")]);
    await first;
    model.selectOrg(node(1, "old-name"));

    orgChange.mockClear();
    const d2 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d2.promise);
    const second = model.loadTree();
    d2.resolve([node(1, "new-name")]);
    await second;
    expect(orgChange).toHaveBeenCalledTimes(1);
    expect(orgChange).toHaveBeenCalledWith(1);
    expect(model.selectedOrgName.value).toBe("new-name");
    expect(model.selectedOrgId.value).toBe(1);
  });

  it("特征锁：重定位 clear——刷新后选中节点消失（被删/被配置裁剪），清空选中并通知 null（T-FE-061 语义保持）", async () => {
    const { model, fetchTree, orgChange } = createHarness(20);
    const d1 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d1.promise);
    const first = model.loadTree();
    d1.resolve([node(1), node(2)]);
    await first;
    model.selectOrg(node(2));

    orgChange.mockClear();
    const d2 = defer<OrgTreeNode[]>();
    fetchTree.mockImplementationOnce(() => d2.promise);
    const second = model.loadTree();
    d2.resolve([node(1)]); // 节点 2 消失
    await second;
    expect(orgChange).toHaveBeenCalledWith(null);
    expect(model.selectedOrgId.value).toBeNull();
    expect(model.selectedOrgName.value).toBe("");
  });
});
