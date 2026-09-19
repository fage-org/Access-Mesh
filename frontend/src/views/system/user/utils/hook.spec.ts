/**
 * 用户删除二次确认与错误反馈回归（T-FE-047）：
 * 旧实现点击即删（无确认弹窗）、删除失败 rejection 无人处理零提示——
 * 三个用例分别锁「确认后才发请求 / 取消零请求 / 失败有错误反馈且不刷新」，旧实现下全部失败。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockDeleteUser = vi.fn();
const mockGetUserPage = vi.fn();
const mockConfirm = vi.fn();
const mockMessage = vi.fn();

// 阻断 hook 模块级链（同 biz-domain hook.spec 范式：api/message/element-plus 全 mock）
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => mockConfirm(...args) }
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));
vi.mock("@/api/user-manage", () => ({
  getUserPage: (...args: unknown[]) => mockGetUserPage(...args),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: (...args: unknown[]) => mockDeleteUser(...args)
}));

import { useUserManage } from "./hook";

const USER = {
  id: 7,
  username: "zhang",
  name: "张三",
  phone: null,
  email: null,
  status: 1,
  orgs: [],
  createdAt: "2026-09-19 00:00:00"
} as any;

describe("用户删除二次确认与错误反馈（T-FE-047）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetUserPage.mockResolvedValue({ items: [], total: 0 });
  });

  it("确认后才删除：先弹确认（含用户名与后果），确认后发 deleteUser、提示成功、刷新列表", async () => {
    const { handleDelete } = useUserManage();
    await handleDelete(USER);

    // 旧实现无确认弹窗直接删除——此断言在旧实现下失败
    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(mockConfirm.mock.calls[0][0]).toContain("张三");
    expect(mockConfirm.mock.calls[0][0]).toContain("无法登录");

    expect(mockDeleteUser).toHaveBeenCalledTimes(1);
    expect(mockDeleteUser).toHaveBeenCalledWith([7]);
    expect(mockMessage).toHaveBeenCalledWith("用户已删除", { type: "success" });
    // 成功后 loadTable 刷新
    expect(mockGetUserPage).toHaveBeenCalledTimes(1);
  });

  it("取消确认：零删除请求、零提示、不刷新列表", async () => {
    mockConfirm.mockRejectedValue("cancel");
    const { handleDelete } = useUserManage();
    await handleDelete(USER);

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    // 旧实现无确认环节，deleteUser 照发——此断言在旧实现下失败
    expect(mockDeleteUser).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
    expect(mockGetUserPage).not.toHaveBeenCalled();
  });

  it("删除失败：透出后端 message 错误提示，不弹成功、不刷新列表", async () => {
    mockDeleteUser.mockRejectedValue(new Error("不能删除当前登录用户"));
    const { handleDelete } = useUserManage();
    // 旧实现无 catch：rejection 直接上抛——此 await 在旧实现下失败
    await handleDelete(USER);

    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockMessage).toHaveBeenCalledWith("不能删除当前登录用户", {
      type: "error"
    });
    expect(mockGetUserPage).not.toHaveBeenCalled();
  });
});
