/**
 * 登录链路 store 单测（T-FE-041 评审修复回归保护）。
 * 覆盖 loginByUsername 四分支：
 *  1. 成功：expiresIn 秒 → 绝对时间供 setToken；username/roles 占位立即落位；菜单数据填充（含 userKey 持久化）
 *  2. 业务失败（HTTP 200 + code≠200）：unwrap 抛 RequestError，整体 reject
 *  3. user-menu HTTP 401（会话失效）：不降级，reject
 *  4. user-menu 普通异常（网络等）：降级，仍 resolve
 *
 * logOut 真注销（T-FE-045）四锁：调用序（先 POST 注销后清本地）/ 服务端失败仍清理 /
 * 登出进行中短路（同一动作只发一次）/ 登出完成后重复触发零请求——旧实现（不调接口）下必红。
 *
 * menuLoadFailed 状态维护（T-FE-049）：区分「/user-menu 拉取失败」与「拉取成功但账号
 * 无菜单」两态空侧栏——旧实现无该状态（undefined）下三用例必红。
 *
 * 边界说明：401 的"清会话回登录页"副作用在 http 响应拦截器（utils/http/index.ts），
 * 本 spec 在 store 层 mock API 直接抛错，不覆盖拦截器内部（不为 10 行拦截器逻辑
 * 搭 axios 测试基建，该路径由真实环境 E2E 覆盖——避免过度设计）。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { RequestError } from "@/api/_envelope";
import type { R } from "@/api/_envelope";

// mock API 层（阻断 http；login/getUserMenu/logout 返回值由用例控制）
const { mockLogin, mockGetUserMenu, mockLogout } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockGetUserMenu: vi.fn(),
  mockLogout: vi.fn()
}));
vi.mock("@/api/auth", () => ({
  login: mockLogin,
  getUserMenu: mockGetUserMenu,
  logout: mockLogout,
  FIXED_TENANT_ID: "1",
  FIXED_CLIENT_ID: "admin-web"
}));

// mock setToken/getToken/removeToken（真实实现依赖 Cookie/localStorage/Pinia 链，node 环境不可用）
const { mockSetToken, mockGetToken, mockRemoveToken } = vi.hoisted(() => ({
  mockSetToken: vi.fn(),
  mockGetToken: vi.fn(),
  mockRemoveToken: vi.fn()
}));
vi.mock("@/utils/auth", () => ({
  setToken: mockSetToken,
  getToken: mockGetToken,
  removeToken: mockRemoveToken,
  formatToken: (t: string) => "Bearer " + t,
  userKey: "user-info"
}));

// mock store 工具桶（阻断 router/storageLocal；store 用真 Pinia 实例）
// storageLocal 返回共享实例，用例可断言 userKey 持久化写入（评审修复：覆盖 localStorage 副作用）
// responsiveStorageNameSpace 供 multiTags state 初始化（logOut 触发 handleTags 链）
const { mockStorage, mockRouterPush, mockResetRouter } = vi.hoisted(() => ({
  mockStorage: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn()
  },
  mockRouterPush: vi.fn(),
  mockResetRouter: vi.fn()
}));
vi.mock("../utils", () => ({
  store: createPinia(),
  router: { push: mockRouterPush },
  resetRouter: mockResetRouter,
  routerArrays: [],
  constantMenus: [],
  storageLocal: () => mockStorage,
  responsiveStorageNameSpace: () => "responsive-"
}));

import { useUserStore } from "./user";

const LOGIN_RESP = {
  accessToken: "token-1",
  refreshToken: null,
  expiresIn: 7200,
  tokenType: "Bearer",
  userId: 1,
  username: "admin",
  tenantId: 1,
  forceResetPwd: false
};

const MENU_RESP: R<{
  menus: Array<{ path: string; name?: string }>;
  roles: string[];
  permissions: string[];
}> = {
  code: 200,
  message: "ok",
  data: {
    menus: [{ path: "/welcome", name: "Welcome" }],
    roles: ["Bootstrap Admin"],
    permissions: ["ROLE:VIEW"]
  }
};

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

describe("loginByUsername 真实链路（T-FE-041）", () => {
  it("成功：expiresIn 转绝对时间写入 setToken，username 与 roles 占位落位，菜单填充角色权限", async () => {
    mockLogin.mockResolvedValue({ code: 200, message: "ok", data: LOGIN_RESP });
    mockGetUserMenu.mockResolvedValue(MENU_RESP);
    const before = Date.now();

    const result = await useUserStore().loginByUsername({
      username: "admin",
      password: "Admin@2026",
      captchaId: "id",
      captchaCode: "1234"
    });

    expect(result).toEqual(LOGIN_RESP);
    expect(mockSetToken).toHaveBeenCalledTimes(1);
    const arg = mockSetToken.mock.calls[0][0];
    // expiresIn（秒）→ 绝对时间（容差 2s）
    const expires = new Date(arg.expires).getTime();
    expect(expires).toBeGreaterThanOrEqual(before + 7200 * 1000);
    expect(expires).toBeLessThanOrEqual(before + 7200 * 1000 + 2000);
    // Sa-Token 无刷新令牌 → 空串；username 立即落位 + roles 占位（使 setToken 走传入分支）
    expect(arg.accessToken).toBe("token-1");
    expect(arg.refreshToken).toBe("");
    expect(arg.username).toBe("admin");
    expect(arg.roles).toEqual([]);
    // 菜单数据写入 store（评审修复：menus 用非空夹具并断言，防止 SET_MENUS 删除后测试仍绿）
    const user = useUserStore();
    expect(user.roles).toEqual(["Bootstrap Admin"]);
    expect(user.permissions).toEqual(["ROLE:VIEW"]);
    expect(user.menus).toEqual([{ path: "/welcome", name: "Welcome" }]);
    // userKey 持久化：refreshUserMenu 与 setToken 写入的 storage 保持一致语义
    expect(mockStorage.setItem).toHaveBeenCalledWith(
      "user-info",
      expect.objectContaining({
        roles: ["Bootstrap Admin"],
        permissions: ["ROLE:VIEW"]
      })
    );
  });

  it("业务失败（code≠200）：unwrap 抛 RequestError，整体 reject，不写令牌", async () => {
    mockLogin.mockResolvedValue({
      code: 10901,
      message: "验证码错误",
      data: null
    });

    await expect(
      useUserStore().loginByUsername({
        username: "admin",
        password: "x",
        captchaId: "id",
        captchaCode: "0000"
      })
    ).rejects.toThrow(RequestError);
    expect(mockSetToken).not.toHaveBeenCalled();
    expect(mockGetUserMenu).not.toHaveBeenCalled();
  });

  it("user-menu HTTP 401（会话失效）：不降级，登录按失败 reject", async () => {
    mockLogin.mockResolvedValue({ code: 200, message: "ok", data: LOGIN_RESP });
    const axios401 = Object.assign(
      new Error("Request failed with status code 401"),
      {
        response: { status: 401 }
      }
    );
    mockGetUserMenu.mockRejectedValue(axios401);

    await expect(
      useUserStore().loginByUsername({
        username: "admin",
        password: "x",
        captchaId: "id",
        captchaCode: "1234"
      })
    ).rejects.toThrow("Request failed with status code 401");
    // 令牌已写入（登录本身成功），但会话失效由拦截器 logOut 清理，此处只验证不吞错
  });

  it("user-menu 普通异常（网络等）：降级 console.warn，仍 resolve 登录结果", async () => {
    mockLogin.mockResolvedValue({ code: 200, message: "ok", data: LOGIN_RESP });
    mockGetUserMenu.mockRejectedValue(new Error("network down"));
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

    const result = await useUserStore().loginByUsername({
      username: "admin",
      password: "x",
      captchaId: "id",
      captchaCode: "1234"
    });

    expect(result).toEqual(LOGIN_RESP);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});

describe("menuLoadFailed 状态维护（T-FE-049：空侧栏两态区分的事实来源）", () => {
  it("refreshUserMenu 失败：置 menuLoadFailed=true 且原样 rethrow（语义不变）——旧实现无状态必红", async () => {
    mockGetUserMenu.mockRejectedValue(new Error("network down"));

    const user = useUserStore();
    await expect(user.refreshUserMenu()).rejects.toThrow("network down");
    expect(user.menuLoadFailed).toBe(true);
  });

  it("失败后重拉成功：翻转为 false 并填充 menus（拉取成功但空菜单=合法形态，非失败）", async () => {
    const user = useUserStore();
    mockGetUserMenu.mockRejectedValueOnce(new Error("network down"));
    await expect(user.refreshUserMenu()).rejects.toThrow();
    expect(user.menuLoadFailed).toBe(true);

    mockGetUserMenu.mockResolvedValue({
      code: 200,
      message: "ok",
      data: { menus: [], roles: [], permissions: [] }
    });
    await user.refreshUserMenu();
    // 拉取成功即使 menus 为空（零权限账号）也不是失败——占位项走「无可用菜单」分支
    expect(user.menuLoadFailed).toBe(false);
    expect(user.menus).toEqual([]);
  });

  it("logOut 清理会话时同步重置 menuLoadFailed，登出后状态不残留", async () => {
    const user = useUserStore();
    mockGetUserMenu.mockRejectedValue(new Error("network down"));
    await expect(user.refreshUserMenu()).rejects.toThrow();
    expect(user.menuLoadFailed).toBe(true);

    mockGetToken.mockReturnValue({
      accessToken: "token-1",
      expires: 1,
      refreshToken: ""
    });
    mockLogout.mockResolvedValue(undefined);
    await user.logOut();

    expect(user.menuLoadFailed).toBe(false);
  });
});

describe("logOut 真注销（T-FE-045：服务端注销优先、本地清理无条件）", () => {
  const TOKEN = { accessToken: "token-1", expires: 1, refreshToken: "" };

  it("调用序：先 POST 注销（显式传当前 accessToken）再清本地——removeToken/resetRouter/push 依次在后，Pinia 清空", async () => {
    mockGetToken.mockReturnValue(TOKEN);
    mockLogout.mockResolvedValue(undefined);

    await useUserStore().logOut();

    expect(mockLogout).toHaveBeenCalledTimes(1);
    // 注销请求显式携带当前 token（formatToken 构造 Authorization 头；旧实现不调接口，此断言必红）
    expect(mockLogout).toHaveBeenCalledWith("Bearer token-1");
    // 调用序锁：注销请求先于本地清理，清理先于路由重置与跳转
    expect(mockLogout.mock.invocationCallOrder[0]).toBeLessThan(
      mockRemoveToken.mock.invocationCallOrder[0]
    );
    expect(mockRemoveToken.mock.invocationCallOrder[0]).toBeLessThan(
      mockResetRouter.mock.invocationCallOrder[0]
    );
    expect(mockResetRouter.mock.invocationCallOrder[0]).toBeLessThan(
      mockRouterPush.mock.invocationCallOrder[0]
    );
    expect(mockRouterPush).toHaveBeenCalledWith("/login");
    // Pinia 状态清空
    const user = useUserStore();
    expect(user.username).toBe("");
    expect(user.roles).toEqual([]);
    expect(user.permissions).toEqual([]);
    expect(user.menus).toEqual([]);
  });

  it("服务端注销失败（网络/后端异常）：console.warn 不弹错，本地清理与跳登录无条件完成", async () => {
    mockGetToken.mockReturnValue(TOKEN);
    mockLogout.mockRejectedValue(new Error("network down"));
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

    await useUserStore().logOut();

    expect(warn).toHaveBeenCalled();
    expect(mockRemoveToken).toHaveBeenCalledTimes(1);
    expect(mockResetRouter).toHaveBeenCalledTimes(1);
    expect(mockRouterPush).toHaveBeenCalledWith("/login");
    warn.mockRestore();
  });

  it("登出进行中重复触发直接短路：同一登出动作只发一次 POST /logout，短路方不触发清理", async () => {
    mockGetToken.mockReturnValue(TOKEN);
    let resolveLogout!: () => void;
    mockLogout.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveLogout = resolve;
        })
    );

    const first = useUserStore().logOut();
    await useUserStore().logOut(); // 注销请求在途 → 短路

    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(mockRemoveToken).not.toHaveBeenCalled();

    resolveLogout();
    await first;

    expect(mockRemoveToken).toHaveBeenCalledTimes(1);
    expect(mockRouterPush).toHaveBeenCalledWith("/login");
  });

  it("登出完成后重复触发不再发请求：本地已清（getToken 无令牌）即幂等清理+直接跳登录", async () => {
    mockGetToken
      .mockReturnValueOnce(TOKEN) // 第一次登出：持令牌
      .mockReturnValueOnce(null); // 第二次触发：removeToken 后已无令牌
    mockLogout.mockResolvedValue(undefined);

    await useUserStore().logOut();
    await useUserStore().logOut();

    expect(mockLogout).toHaveBeenCalledTimes(1); // 第二次零请求
    expect(mockRemoveToken).toHaveBeenCalledTimes(2); // 幂等清理照常
    expect(mockRouterPush).toHaveBeenCalledTimes(2); // 直接跳登录
  });
});
