/**
 * 组织跨层级移动确认判定回归（T-FE-052，定案④）：确认文案含组织名/目标名与
 * 后果句（组织资源父链镜像）；取消返回 false（拖拽面调用方重拉树恢复/表单面
 * 保持弹窗打开）；parentId 未变化不弹确认直接放行；target.id=null 表达移至
 * 顶层（表单面「设为顶层」，claude 外评 P3 处置扩展）。旧实现（面板直接 emit
 * node-move 无确认、表单换父直接提交）下无此模块——本 spec 以模块缺失红起
 * （old-fail 形态=新模块契约锁）。
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

  it("target.id=null（移至顶层，表单面「设为顶层」）：弹确认含「移至顶层」，确认返回 true（claude 外评 P3 处置扩展）", async () => {
    const ok = await confirmOrgMoveIfNeeded(
      { orgName: "研发部", parentOrgId: 1 },
      { id: null, orgName: "" }
    );

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    const text = mockConfirm.mock.calls[0][0] as string;
    expect(text).toContain("研发部");
    expect(text).toContain("移至顶层");
    expect(text).not.toContain("移入「"); // 顶层形态不得拼出空名目标
    expect(ok).toBe(true);
  });

  it("parentId 未变化（拖拽面防御分支不可达；表单面=仅改名等未换父提交）：不弹确认直接放行", async () => {
    const ok = await confirmOrgMoveIfNeeded(
      { orgName: "研发部", parentOrgId: 2 },
      { id: 2, orgName: "事业群" }
    );

    expect(mockConfirm).not.toHaveBeenCalled();
    expect(ok).toBe(true);
  });
});
