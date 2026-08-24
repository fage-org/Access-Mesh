/**
 * 登录链路 store 单测（T-FE-041 评审修复回归保护）。
 * 覆盖 loginByUsername 四分支：
 *  1. 成功：expiresIn 秒 → 绝对时间供 setToken；username/roles 占位立即落位；菜单数据填充（含 userKey 持久化）
 *  2. 业务失败（HTTP 200 + code≠200）：unwrap 抛 RequestError，整体 reject
 *  3. user-menu HTTP 401（会话失效）：不降级，reject
 *  4. user-menu 普通异常（网络等）：降级，仍 resolve
 *
 * 边界说明：401 的"清会话回登录页"副作用在 http 响应拦截器（utils/http/index.ts），
 * 本 spec 在 store 层 mock API 直接抛错，不覆盖拦截器内部（不为 10 行拦截器逻辑
 * 搭 axios 测试基建，该路径由真实环境 E2E 覆盖——避免过度设计）。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { RequestError } from "@/api/_envelope";
import type { PermResult } from "@/api/_envelope";

// mock API 层（阻断 http；login/getUserMenu 返回值由用例控制）
const { mockLogin, mockGetUserMenu } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockGetUserMenu: vi.fn()
}));
vi.mock("@/api/auth", () => ({
  login: mockLogin,
  getUserMenu: mockGetUserMenu,
  FIXED_TENANT_ID: "1",
  FIXED_CLIENT_ID: "admin-web"
}));

// mock setToken/removeToken（真实实现依赖 Cookie/localStorage/Pinia 链，node 环境不可用）
const { mockSetToken } = vi.hoisted(() => ({ mockSetToken: vi.fn() }));
vi.mock("@/utils/auth", () => ({
  setToken: mockSetToken,
  removeToken: vi.fn(),
  userKey: "user-info"
}));

// mock store 工具桶（阻断 router/storageLocal；store 用真 Pinia 实例）
// storageLocal 返回共享实例，用例可断言 userKey 持久化写入（评审修复：覆盖 localStorage 副作用）
const { mockStorage } = vi.hoisted(() => ({
  mockStorage: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn()
  }
}));
vi.mock("../utils", () => ({
  store: createPinia(),
  router: { push: vi.fn() },
  resetRouter: vi.fn(),
  routerArrays: [],
  storageLocal: () => mockStorage
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

const MENU_RESP: PermResult<{
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
