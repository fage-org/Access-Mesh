/**
 * 用户删除二次确认与错误反馈回归（T-FE-047）：
 * 旧实现点击即删（无确认弹窗）、删除失败 rejection 无人处理零提示——
 * 三个用例分别锁「确认后才发请求 / 取消零请求 / 失败有错误反馈且不刷新」，旧实现下全部失败。
 *
 * 列表加载错误反馈与请求代际 + 写操作一致性（T-FE-051）：
 * 旧实现 loadTable try/finally 无 catch（失败零提示）且无请求代际（迟到响应覆盖新结果）；
 * handleCreate/handleUpdate 无 catch（失败 rejection 上抛弹窗只剩 closeLoading 零提示）；
 * handleToggleStatus 原在 MemberTab SFC 内（不可 import 测试）且失败只显泛文案——
 * 移入 hook 后失败透出后端 error.message（对齐 handleDelete 形态）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockDeleteUser = vi.fn();
const mockGetUserPage = vi.fn();
const mockConfirm = vi.fn();
const mockMessage = vi.fn();
const mockCreateUser = vi.fn();
const mockUpdateUser = vi.fn();
const mockEnableUsers = vi.fn();

// 阻断 hook 模块级链（同 biz-domain hook.spec 范式：api/message/element-plus 全 mock）
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => mockConfirm(...args) }
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));
vi.mock("@/api/user-manage", () => ({
  getUserPage: (...args: unknown[]) => mockGetUserPage(...args),
  createUser: (...args: unknown[]) => mockCreateUser(...args),
  updateUser: (...args: unknown[]) => mockUpdateUser(...args),
  deleteUser: (...args: unknown[]) => mockDeleteUser(...args),
  enableUsers: (...args: unknown[]) => mockEnableUsers(...args)
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

/** 受控 promise：手动 resolve/reject 驱动「A 慢 B 快」并发时序（禁裸 sleep） */
function defer<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 排空微任务 + 一个宏任务周期，让 loadTable 续体确定性跑完 */
function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}

describe("用户删除二次确认与错误反馈（T-FE-047）", () => {
  beforeEach(() => {
    // clearAllMocks 只清调用记录不清 implementation（vitest 源码 mockClear/mockReset 之分）——
    // 各 mock 的默认实现须在此逐项重设，防个别用例设置的 rejection 泄漏给后续用例
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetUserPage.mockResolvedValue({ items: [], total: 0 });
    mockDeleteUser.mockResolvedValue(undefined);
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

describe("列表加载错误反馈与请求代际（T-FE-051 回归锁①②）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetUserPage.mockResolvedValue({ items: [], total: 0 });
    mockDeleteUser.mockResolvedValue(undefined);
    mockCreateUser.mockResolvedValue({ initialPassword: "P@ssw0rd!" });
    mockUpdateUser.mockResolvedValue(undefined);
    mockEnableUsers.mockResolvedValue(undefined);
  });

  it("加载失败：message 提示后端文案 + loading 复位 + 旧数据保留（旧实现 try/finally 无 catch 必失败）", async () => {
    const { loadTable, loading, tableData } = useUserManage();
    mockGetUserPage.mockResolvedValueOnce({ items: [USER], total: 1 });
    await loadTable();
    expect(tableData.value).toEqual([USER]);

    mockGetUserPage.mockRejectedValueOnce(new Error("服务器开小差"));
    // 旧实现 loadTable 无 catch：rejection 上抛、零提示——此 await 在旧实现下失败
    await loadTable();

    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockMessage).toHaveBeenCalledWith("服务器开小差", {
      type: "error"
    });
    expect(loading.value).toBe(false);
    expect(tableData.value).toEqual([USER]); // 旧数据保留，不静默残留也不清空
  });

  it("并发 A(慢)→B(快)→B 回→A 回：表格终态=B（旧实现无代际守卫被 A 覆盖必失败）", async () => {
    type Page = { items: unknown[]; total: number };
    const a = defer<Page>();
    const b = defer<Page>();
    const calls: Array<ReturnType<typeof defer<Page>>> = [a, b];
    let i = 0;
    mockGetUserPage.mockImplementation(() => calls[i++].promise);

    const { loadTable, tableData, loading } = useUserManage();
    loadTable(); // A（慢，返回旧结果）
    loadTable(); // B（快，返回空结果）
    b.resolve({ items: [], total: 0 });
    await flush();
    expect(tableData.value).toEqual([]);

    a.resolve({ items: [USER], total: 1 }); // A 迟到：旧实现覆盖 B
    await flush();
    expect(tableData.value).toEqual([]); // 旧实现下此处为 [USER] → 失败
    expect(loading.value).toBe(false);
  });
});

describe("写操作与启停 catch+message 一致性（T-FE-051）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetUserPage.mockResolvedValue({ items: [], total: 0 });
    mockDeleteUser.mockResolvedValue(undefined);
    mockCreateUser.mockResolvedValue({ initialPassword: "P@ssw0rd!" });
    mockUpdateUser.mockResolvedValue(undefined);
    mockEnableUsers.mockResolvedValue(undefined);
  });

  it("创建失败：message 透后端文案、返回 null、不刷新列表（旧实现无 catch 上抛必失败）", async () => {
    mockCreateUser.mockRejectedValue(new Error("用户名已存在"));
    const { handleCreate } = useUserManage();
    // 旧实现 handleCreate 无 catch：rejection 上抛——此 await 在旧实现下失败
    const result = await handleCreate({
      username: "zhang",
      name: "张三"
    });

    expect(result).toBeNull();
    expect(mockMessage).toHaveBeenCalledWith("用户名已存在", { type: "error" });
    expect(mockGetUserPage).not.toHaveBeenCalled(); // 失败不刷新
  });

  it("创建成功：返回结果（initialPassword 供弹窗展示）并刷新列表", async () => {
    const { handleCreate } = useUserManage();
    const result = await handleCreate({ username: "zhang", name: "张三" });

    expect(result?.initialPassword).toBe("P@ssw0rd!");
    expect(mockGetUserPage).toHaveBeenCalledTimes(1);
  });

  it("更新失败：message 透后端文案、返回 false、不刷新列表（旧实现无 catch 上抛必失败）", async () => {
    mockUpdateUser.mockRejectedValue(new Error("邮箱已被占用"));
    const { handleUpdate } = useUserManage();
    // 旧实现 handleUpdate 无 catch：rejection 上抛——此 await 在旧实现下失败
    const ok = await handleUpdate({ id: 7, name: "张三" }, null);

    expect(ok).toBe(false);
    expect(mockMessage).toHaveBeenCalledWith("邮箱已被占用", {
      type: "error"
    });
    expect(mockGetUserPage).not.toHaveBeenCalled();
  });

  it("停用取消：恢复开关状态、零启停请求、零提示", async () => {
    mockConfirm.mockRejectedValue("cancel");
    const row = { ...USER, status: 0 }; // switch 已拨到停用位
    const { handleToggleStatus } = useUserManage();
    await handleToggleStatus(row, 0);

    expect(row.status).toBe(1); // 取消时恢复
    expect(mockEnableUsers).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
  });

  it("停用确认成功：发 enableUsers、行状态回写、提示成功", async () => {
    const row = { ...USER, status: 0 };
    const { handleToggleStatus } = useUserManage();
    await handleToggleStatus(row, 0);

    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(mockEnableUsers).toHaveBeenCalledWith({ ids: [7], status: 0 });
    expect(row.status).toBe(0);
    expect(mockMessage).toHaveBeenCalledWith("停用成功", { type: "success" });
  });

  it("启停失败：message 透后端文案而非泛文案「停用失败」（旧实现只显泛文案必失败）+ 开关回滚", async () => {
    mockEnableUsers.mockRejectedValue(new Error("不能停用当前登录用户"));
    const row = { ...USER, status: 0 };
    const { handleToggleStatus } = useUserManage();
    await handleToggleStatus(row, 0);

    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockMessage).toHaveBeenCalledWith("不能停用当前登录用户", {
      type: "error"
    });
    expect(mockMessage).not.toHaveBeenCalledWith("停用失败", {
      type: "error"
    });
    expect(row.status).toBe(1); // 失败回滚
  });
});

describe("共享列表上下文与可写对象绑定（T-FE-059 / F011 同模式）", () => {
  /** 用户行（orgs 标注所属组织，成员表按选中组织子树过滤） */
  function userOf(id: number, orgIds: number[]) {
    return {
      ...USER,
      id,
      orgs: orgIds.map(orgId => ({
        orgId,
        orgName: `org${orgId}`,
        isPrimary: orgIds[0] === orgId
      }))
    } as any;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockConfirm.mockResolvedValue(undefined);
    mockGetUserPage.mockResolvedValue({ items: [], total: 0 });
  });

  it("切组织 B 加载失败：成员表清空、total 归零（旧实现保留 A 组织用户行必失败）", async () => {
    mockGetUserPage
      .mockResolvedValueOnce({ items: [userOf(1, [1])], total: 1 })
      .mockRejectedValueOnce(new Error("组织 2 成员加载失败"));
    const { selectedOrgId, tableData, pagination, onSearch } = useUserManage();

    selectedOrgId.value = 1;
    await onSearch();
    expect(tableData.value).toHaveLength(1);

    selectedOrgId.value = 2;
    await onSearch(); // 失败

    expect(tableData.value).toEqual([]);
    expect(pagination.total).toBe(0);
  });

  it("全组织(null)已加载 → 切组织失败：成员表清空（旧实现哨兵归一残留全组织行必失败）", async () => {
    mockGetUserPage
      .mockResolvedValueOnce({
        items: [userOf(1, [1]), userOf(2, [2])],
        total: 2
      })
      .mockRejectedValueOnce(new Error("组织 5 成员加载失败"));
    const { selectedOrgId, tableData, onSearch } = useUserManage();

    await onSearch(); // 初始 null=全组织视图，成功加载
    expect(tableData.value).toHaveLength(2);

    selectedOrgId.value = 5;
    await onSearch(); // 失败

    expect(tableData.value).toEqual([]);
  });

  it("handleUpdate：打开时组织 ≠ 当前选中 → 拒绝且不发货（旧实现照发必失败）", async () => {
    mockUpdateUser.mockResolvedValue(undefined);
    const { selectedOrgId, handleUpdate } = useUserManage();

    selectedOrgId.value = 2;
    const ok = await handleUpdate({ id: 1, name: "x" }, 1);

    expect(ok).toBe(false);
    expect(mockUpdateUser).not.toHaveBeenCalled();
  });

  it("handleDelete：确认框期间切换组织 → 确认后拒绝且不发货（旧实现照删必失败）", async () => {
    mockDeleteUser.mockResolvedValue(undefined);
    const { selectedOrgId, handleDelete } = useUserManage();
    selectedOrgId.value = 1;
    // 确认框打开期间组织被切换（如浏览器后退后重进落回别的组织）
    mockConfirm.mockImplementation(async () => {
      selectedOrgId.value = 2;
    });

    await handleDelete(userOf(1, [1]));

    expect(mockDeleteUser).not.toHaveBeenCalled();
  });

  it("handleToggleStatus：确认框期间切换组织 → 确认后拒绝且不发货、开关回滚（旧实现照发必失败）", async () => {
    mockEnableUsers.mockResolvedValue(undefined);
    const { selectedOrgId, handleToggleStatus } = useUserManage();
    selectedOrgId.value = 1;
    mockConfirm.mockImplementation(async () => {
      selectedOrgId.value = 2;
    });
    const row = { ...userOf(1, [1]), status: 1 };

    await handleToggleStatus(row, 0);

    expect(mockEnableUsers).not.toHaveBeenCalled();
    expect(row.status).toBe(1); // 拒绝时回滚开关
  });
});
