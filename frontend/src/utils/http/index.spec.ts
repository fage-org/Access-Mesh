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
 * token 过期短路与双分支统一提示（T-FE-054，2026-09-20 四项拍板）：本地过期
 * 请求短路不发（reject 与提示同文案 Error）+ 并发窗口去重只弹一次 + 401 响应
 * 同提示（双分支统一，共用 10s 去重窗口）——旧实现（无令牌放行+零提示）下必红；
 * 白名单不做过期判定 / 未过期正常附加头为既有行为特征锁。
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

const {
  mockUser,
  mockLogOut,
  mockRefreshUserMenu,
  mockHandleBackendMenus,
  mockGetToken,
  mockMessage
} = vi.hoisted(() => {
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
    mockHandleBackendMenus: vi.fn(),
    mockGetToken: vi.fn(),
    mockMessage: vi.fn()
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
  getToken: (...args: unknown[]) => mockGetToken(...args),
  // isSessionTerminated 真实语义镜像（T-FE-054 外评 P2 单源判据的 mock 面）
  isSessionTerminated: (data?: unknown) => {
    const d = (data ?? mockGetToken()) as
      | { expires?: number | string }
      | null
      | undefined;
    if (!d) return true;
    return parseInt(String(d.expires)) - Date.now() <= 0;
  },
  formatToken: (token: string) => `Bearer ${token}`,
  userKey: "user-info"
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));

/** 每用例经 resetModules 重导入取新实例（403 窗口为模块级状态，用例间必须隔离） */
type HttpModule = typeof import("@/utils/http");
let http: HttpModule["http"];
let CAPABILITY_REFRESH_403_WINDOW_MS: number;
let SESSION_EXPIRED_NOTIFY_WINDOW_MS: number;

const MENU_A = { path: "/welcome", meta: { title: "首页" } };
const MENU_B = { path: "/system/user", meta: { title: "用户管理" } };

/** adapter 实际收到的请求 url 序列——短路断言「零请求发出」的事实来源 */
const adapterCalls: string[] = [];

/** 注入按 url 决定状态码的拒绝 adapter（错误形态对齐 axios：response.status + config） */
function installAdapter(statusOf: (url: string) => number) {
  adapterCalls.length = 0;
  // PureHttp.axiosInstance 为 private static，运行时经 constructor 取得（仅测试）
  const ctor = Object.getPrototypeOf(http).constructor as {
    axiosInstance: AxiosInstance;
  };
  ctor.axiosInstance.defaults.adapter = (
    config: InternalAxiosRequestConfig
  ) => {
    adapterCalls.push(config.url ?? "");
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
  // 会话默认未过期（T-FE-054 短路用例各自覆盖为过期形态）
  mockGetToken.mockReset().mockReturnValue({
    accessToken: "token-1",
    refreshToken: "",
    expires: Date.now() + 600_000
  });
  // 会话初态：侧栏两菜单（A/B）——锁②撤销断言的前置
  mockUser.menus = [MENU_A, MENU_B];
  mockUser.menuLoadFailed = false;
  vi.resetModules();
  ({ http, CAPABILITY_REFRESH_403_WINDOW_MS } = await import("@/utils/http"));
  ({ SESSION_EXPIRED_NOTIFY_WINDOW_MS } = await import(
    "@/utils/session-expired"
  ));
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

describe("token 过期短路与双分支统一提示（T-FE-054）", () => {
  const EXPIRED_TOKEN = {
    accessToken: "token-1",
    refreshToken: "",
    expires: Date.now() - 1000
  };

  it("锁①：本地过期请求短路不发——零 adapter 调用、reject SessionExpiredError（同文案）、logOut 触发（旧实现无令牌放行+resolve 必红）", async () => {
    mockGetToken.mockReturnValue(EXPIRED_TOKEN);
    installAdapter(() => 403); // 若发出必经 adapter（任意状态码都证明发出）
    const rejection = await http
      .post("/api/access/type-definition/list")
      .catch(e => e);
    await drainMicrotasks();
    expect((rejection as Error).message).toBe("会话已过期，请重新登录");
    // SessionExpiredError 形态（claude 外评 P3 处置）：授予页 classifySaveError 按
    // name 识别为「未发出可重试」非「结果未知」——旧实现（普通 Error）下 name 断言红
    expect((rejection as Error).name).toBe("SessionExpiredError");
    // 旧实现：请求无令牌放行 → adapter 收到 → 计数 1（此处必红）
    expect(adapterCalls).toHaveLength(0);
    expect(mockLogOut).toHaveBeenCalledTimes(1);
  });

  it("锁②：并发多请求同刻过期——统一提示只弹一次（窗口去重），全部 reject（旧实现无提示必红）", async () => {
    mockGetToken.mockReturnValue(EXPIRED_TOKEN);
    installAdapter(() => 403);
    const results = await Promise.allSettled([
      http.post("/api/access/a"),
      http.post("/api/access/b"),
      http.post("/api/access/c")
    ]);
    expect(results.every(r => r.status === "rejected")).toBe(true);
    expect(mockMessage).toHaveBeenCalledTimes(1); // 旧实现 0 次 → 红
    expect(mockMessage).toHaveBeenCalledWith("会话已过期，请重新登录", {
      type: "warning"
    });
    // logOut 触发即证（mock 层无防抖语义；真实 store 的 logoutInFlight 在
    // fire-and-forget 后恒不命中，重复触发防护由 getToken 无令牌幂等分支承担）
    expect(mockLogOut).toHaveBeenCalled();
  });

  it("锁③：401 响应弹「会话已过期」（双分支统一，旧实现无提示必红）+ logOut", async () => {
    installAdapter(() => 401);
    await expectRejected("/api/access/x");
    await drainMicrotasks();
    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockMessage).toHaveBeenCalledWith("会话已过期，请重新登录", {
      type: "warning"
    });
    expect(mockLogOut).toHaveBeenCalledTimes(1);
  });

  it("锁（外评 P3）：主动登出后在途请求的 401 不弹「会话已过期」——令牌已清（getToken 空）不提示，仍 logOut 幂等清理（旧实现无条件提示必红）", async () => {
    installAdapter(() => 401);
    // 模拟主动登出已完成本地清理（logOut removeToken 后 getToken() 为空）
    mockGetToken.mockReturnValue(null);
    await expectRejected("/api/access/in-flight");
    await drainMicrotasks();
    expect(mockMessage).not.toHaveBeenCalled(); // 旧实现弹「会话已过期」误导 → 红
    expect(mockLogOut).toHaveBeenCalledTimes(1); // 幂等清理照常
  });

  it("提示窗口跨分支共用去重：401 与短路混合 10s 内只弹一次，窗口过期可再弹", async () => {
    vi.useFakeTimers({ toFake: ["Date", "setTimeout", "clearTimeout"] });
    installAdapter(url => (url.includes("auth401") ? 401 : 403));
    // 路径 2：token 未过期，请求发出收 401 → 提示①
    await expectRejected("/api/access/auth401-a");
    await drainMicrotasks();
    expect(mockMessage).toHaveBeenCalledTimes(1);
    // 路径 1：同窗口内本地过期短路 → 不再弹
    mockGetToken.mockReturnValue(EXPIRED_TOKEN);
    await expect(http.post("/api/access/short-circuit")).rejects.toThrow();
    await drainMicrotasks();
    expect(mockMessage).toHaveBeenCalledTimes(1);
    // 窗口过期：可再弹
    vi.setSystemTime(Date.now() + SESSION_EXPIRED_NOTIFY_WINDOW_MS + 1);
    mockGetToken.mockReturnValue({
      accessToken: "token-1",
      refreshToken: "",
      expires: Date.now() + 600_000
    });
    await expectRejected("/api/access/auth401-b");
    await drainMicrotasks();
    expect(mockMessage).toHaveBeenCalledTimes(2);
  });

  it("白名单不做过期判定：captcha 过期 token 照常放行发出（错误形态=adapter 拒绝非短路，既有行为特征锁）", async () => {
    mockGetToken.mockReturnValue(EXPIRED_TOKEN);
    installAdapter(() => 403);
    await expect(http.post("/api/access/auth/captcha")).rejects.toThrow(
      "Request failed with status code 403"
    );
    expect(adapterCalls).toHaveLength(1);
    expect(mockLogOut).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
  });

  it("未过期 token 正常附加头发出（特征锁，防短路误伤正常路径）", async () => {
    installAdapter(() => 403);
    await expectRejected("/api/access/normal");
    expect(adapterCalls).toHaveLength(1);
    expect(mockLogOut).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
  });
});
