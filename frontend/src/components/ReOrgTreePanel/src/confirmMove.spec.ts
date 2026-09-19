/**
 * 组织跨层级移动确认判定回归（T-FE-052，定案④）：确认文案含组织名/目标名与
 * 后果句（组织资源父链镜像）；取消返回 false（调用方重拉树恢复）；parentId 未
 * 变化不弹确认直接放行。旧实现（面板直接 emit node-move 无确认）下无此模块——
 * 本 spec 以模块缺失红起（old-fail 形态=新模块契约锁）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockConfirm = vi.fn();

vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => mockConfirm(...args) }
}));

import { confirmOrgMoveIfNeeded } from "./confirmMove";

describe("confirmOrgMoveIfNeeded（T-FE-052 定案④）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
  });

  it("parentId 变化：弹确认（含组织名/目标名/后果句），确认返回 true", async () => {
    const ok = await confirmOrgMoveIfNeeded(
      { orgName: "研发部", parentOrgId: 1 },
      { id: 2, orgName: "事业群" }
    );

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    const text = mockConfirm.mock.calls[0][0] as string;
    expect(text).toContain("研发部");
    expect(text).toContain("事业群");
    expect(text).toContain("组织层级变化");
    expect(ok).toBe(true);
  });

  it("取消：返回 false（调用方据此重拉树恢复 el-tree 已移动的节点）", async () => {
    mockConfirm.mockRejectedValue("cancel");
    const ok = await confirmOrgMoveIfNeeded(
      { orgName: "研发部", parentOrgId: 1 },
      { id: 2, orgName: "事业群" }
    );

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(ok).toBe(false);
  });

  it("parentId 未变化（已是目标子节点）：不弹确认直接放行（防御分支，现 inner-only 不可达）", async () => {
    const ok = await confirmOrgMoveIfNeeded(
      { orgName: "研发部", parentOrgId: 2 },
      { id: 2, orgName: "事业群" }
    );

    expect(mockConfirm).not.toHaveBeenCalled();
    expect(ok).toBe(true);
  });
});
