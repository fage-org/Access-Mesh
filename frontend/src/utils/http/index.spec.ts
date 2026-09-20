/**
 * http 响应拦截器 403 分支回归锁（T-FE-048 会话权限热刷新）。
 * 锁①：403 触发能力刷新恰好一次（10s 窗口内第二次 403 不再触发）——旧实现
 *      （403 仅由页面层处理，无任何触发）下必红。
 * 锁②：403 自动刷新成功后侧栏同步重建（撤销的菜单项从侧栏消失）——旧实现
 *      （wholeMenus 唯一重建点在 initRouter，仅刷 store）下必红。
 * 附加锁：窗口过期后再 403 可再次触发 / user-menu 自身 403 不触发（刷新入口即
 * 该请求，重发无自愈可能）/ 刷新失败静默维持旧态（不重建侧栏、原请求错误照常
 * reject、无 message 弹窗）/ 401 分支不受扰。
 *
 * 测试形态：真实 http 单例 + 注入 403 adapter（PureHttp.axiosInstance 为 TS 私有
 * static，运行时经 constructor 取得）；store/router 外链全 mock 断链（refreshUserMenu
 * 由 mock 直接改写 mockUser.menus，验证真实 refreshSessionCapability 的「先拉取后
 * 重建侧栏」原子序）。403 窗口为模块级状态，用例间 vi.resetModules() + 动态重导入
 * 取全新模块图隔离；fake timers 用例只 fake Date/setTimeout（微任务链不受影响，
 * 统一用微任务排空等待，不用 setTimeout flush）。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";

const { mockUser, mockLogOut, mockRefreshUserMenu, mockHandleBackendMenus } =
  vi.hoisted(() => {
    const mockUser = {
      menus: [] as Array<{ path: string }>,
      menuLoadFailed: false,
      logOut: vi.fn(),
      refreshUserMenu: vi.fn()
    };
    return {
      mockUser,
      mockLogOut: mockUser.logOut,
      mockRefreshUserMenu: mockUser.refreshUserMenu,
      mockHandleBackendMenus: vi.fn()
    };
  });

vi.mock("@/store/modules/user", () => ({
  useUserStoreHook: () => mockUser
}));
vi.mock("@/store/modules/permission", () => ({
  usePermissionStoreHook: () => ({
    handleBackendMenus: mockHandleBackendMenus
  })
}));
vi.mock("@/store/modules/multiTags", () => ({
  useMultiTagsStoreHook: () => ({})
}));
vi.mock("@/router", () => ({ router: {} }));
vi.mock("@/utils/auth", () => ({
  getToken: () => ({
    accessToken: "token-1",
    refreshToken: "",
    expires: Date.now() + 600_000
  }),
  formatToken: (token: string) => `Bearer ${token}`,
  userKey: "user-info"
}));

/** 每用例经 resetModules 重导入取新实例（403 窗口为模块级状态，用例间必须隔离） */
type HttpModule = typeof import("@/utils/http");
let http: HttpModule["http"];
let CAPABILITY_REFRESH_403_WINDOW_MS: number;

const MENU_A = { path: "/welcome", meta: { title: "首页" } };
const MENU_B = { path: "/system/user", meta: { title: "用户管理" } };

/** 注入按 url 决定状态码的拒绝 adapter（错误形态对齐 axios：response.status + config） */
function installAdapter(statusOf: (url: string) => number) {
  // PureHttp.axiosInstance 为 private static，运行时经 constructor 取得（仅测试）
  const ctor = Object.getPrototypeOf(http).constructor as {
    axiosInstance: AxiosInstance;
  };
  ctor.axiosInstance.defaults.adapter = (
    config: InternalAxiosRequestConfig
  ) => {
    const status = statusOf(config.url ?? "");
    return Promise.reject(
      Object.assign(new Error(`Request failed with status code ${status}`), {
        isAxiosError: true,
        config,
        response: {
          status,
          statusText: "statusText",
          data: { code: status, message: "权限不足" },
          config,
          headers: {}
        }
      })
    );
  };
}

/** 微任务排空（fake timers 下 setTimeout 不可用；链路全微任务，20 跳足够） */
async function drainMicrotasks() {
  for (let i = 0; i < 20; i++) await Promise.resolve();
}

async function expectRejected(url: string) {
  await expect(http.post(url)).rejects.toThrow();
}

beforeEach(async () => {
  vi.clearAllMocks();
  mockRefreshUserMenu.mockReset().mockResolvedValue(undefined);
  // 会话初态：侧栏两菜单（A/B）——锁②撤销断言的前置
  mockUser.menus = [MENU_A, MENU_B];
  mockUser.menuLoadFailed = false;
  vi.resetModules();
  ({ http, CAPABILITY_REFRESH_403_WINDOW_MS } = await import("@/utils/http"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("http 403 触发会话能力刷新（T-FE-048）", () => {
  it("锁①：403 触发能力刷新恰好一次——窗口内第二次 403 不再触发（旧实现无任何触发必红）", async () => {
    installAdapter(() => 403);
    await expectRejected("/api/access/type-definition/list");
    await drainMicrotasks();
    await expectRejected("/api/access/resource-entity/tree");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1);
  });

  it("锁②：403 自动刷新成功后侧栏同步重建——撤销的菜单项从侧栏消失（旧实现仅 initRouter 重建、仅刷 store 必红）", async () => {
    // 管理员撤销 B：刷新后 user-menu 只返回 A
    mockRefreshUserMenu.mockImplementation(async () => {
      mockUser.menus = [MENU_A];
    });
    installAdapter(() => 403);
    await expectRejected("/api/access/some/api");
    await drainMicrotasks();
    expect(mockHandleBackendMenus).toHaveBeenCalledTimes(1);
    const sidebar = mockHandleBackendMenus.mock.calls[0][0];
    expect(sidebar.map((m: { path: string }) => m.path)).toEqual(["/welcome"]);
  });

  it("锁②边界：刷新后零菜单（全撤销）侧栏落「当前账号无可用菜单」占位而非旧菜单残留", async () => {
    mockRefreshUserMenu.mockImplementation(async () => {
      mockUser.menus = [];
      mockUser.menuLoadFailed = false;
    });
    installAdapter(() => 403);
    await expectRejected("/api/access/some/api");
    await drainMicrotasks();
    const sidebar = mockHandleBackendMenus.mock.calls[0][0];
    expect(sidebar).toHaveLength(1);
    expect(sidebar[0].meta?.title).toBe("当前账号无可用菜单");
    expect(sidebar[0].path).toBe("/menu-retry");
  });

  it("窗口过期后再 403 可再次触发（窗口=10s 起算于触发时刻，非永久单次）", async () => {
    vi.useFakeTimers({ toFake: ["Date", "setTimeout", "clearTimeout"] });
    installAdapter(() => 403);
    await expectRejected("/api/access/a");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1);
    // 窗口内第二个 403：不触发
    await expectRejected("/api/access/b");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1);
    // 窗口过期：再次触发
    vi.setSystemTime(Date.now() + CAPABILITY_REFRESH_403_WINDOW_MS + 1);
    await expectRejected("/api/access/c");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(2);
  });

  it("user-menu 自身 403 不触发刷新（刷新入口即该请求，其 403 下重发无自愈可能）", async () => {
    installAdapter(() => 403);
    await expectRejected("/api/access/auth/user-menu");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).not.toHaveBeenCalled();
  });

  it("刷新失败静默：侧栏不重建维持旧态、原请求错误照常 reject、无 message 弹窗（手动入口的显式反馈口径与本通道区分）", async () => {
    mockRefreshUserMenu.mockRejectedValue(new Error("network down"));
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    installAdapter(() => 403);
    await expectRejected("/api/access/x");
    await drainMicrotasks();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1);
    expect(mockHandleBackendMenus).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });

  it("401 分支不受扰：仍走 logOut，不触发能力刷新", async () => {
    installAdapter(() => 401);
    await expectRejected("/api/access/x");
    await drainMicrotasks();
    expect(mockLogOut).toHaveBeenCalledTimes(1);
    expect(mockRefreshUserMenu).not.toHaveBeenCalled();
  });
});
