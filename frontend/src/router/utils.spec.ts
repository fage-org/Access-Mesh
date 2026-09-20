/**
 * 空侧栏占位项两态判定回归锁（T-FE-049）。
 * 旧实现：menus 为空恒渲染「菜单加载失败，点击重试」占位项——拉取成功但账号
 * 无菜单（零权限用户）被误导为可重试恢复（重试永远「失败」）。empty 分支断言
 * 在旧实现（恒 retry 占位项）下必红。
 * mock 说明：utils.ts 模块头部 import 链含 router 实例与三个 store hook（均仅
 * 函数内消费），此处 mock 断链——被测对象 resolveSidebarFallback 为纯函数。
 *
 * initRouter 无凭证分支（Q-020 收口）与 refreshSessionCapability single-flight/
 * 代际守卫（Q-016 收口）：mock 全链 + resetModules 动态重导入（single-flight 与
 * 提示窗口为模块级状态，用例间隔离）。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const {
  mockUser,
  mockLogOut,
  mockRefreshUserMenu,
  mockHandleBackendMenus,
  mockHandleWholeMenus,
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
    mockHandleWholeMenus: vi.fn(),
    mockGetToken: vi.fn(),
    mockMessage: vi.fn()
  };
});

vi.mock("./index", () => ({
  router: {
    hasRoute: vi.fn(() => false),
    addRoute: vi.fn(),
    options: { routes: [{ children: [] }] }
  }
}));
vi.mock("@/store/modules/multiTags", () => ({
  useMultiTagsStoreHook: () => ({
    getMultiTagsCache: false,
    handleTags: vi.fn()
  })
}));
vi.mock("@/store/modules/permission", () => ({
  usePermissionStoreHook: () => ({
    handleBackendMenus: mockHandleBackendMenus,
    handleWholeMenus: mockHandleWholeMenus,
    flatteningRoutes: []
  })
}));
vi.mock("@/store/modules/user", () => ({
  useUserStoreHook: () => mockUser
}));
vi.mock("@/layout/types", () => ({ routerArrays: [] }));
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
  userKey: "user-info"
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => mockMessage(...args)
}));

import { resolveSidebarFallback } from "./utils";

describe("resolveSidebarFallback 空侧栏占位两态（T-FE-049）", () => {
  it("拉取失败：可重试占位项「菜单加载失败，点击重试」（既有行为不变）", () => {
    const items = resolveSidebarFallback(true);
    expect(items).toHaveLength(1);
    expect(items[0].meta?.title).toBe("菜单加载失败，点击重试");
    expect(items[0].path).toBe("/menu-retry");
  });

  it("拉取成功但账号无菜单：占位项「当前账号无可用菜单」——旧实现恒 retry 占位必红", () => {
    const items = resolveSidebarFallback(false);
    expect(items).toHaveLength(1);
    expect(items[0].meta?.title).toBe("当前账号无可用菜单");
    expect(items[0].path).toBe("/menu-retry");
  });

  it("占位项不携带未注册路由 name——侧栏 :to 对象 name 优先解析、tagOnClick push({name}) 命中，未注册即 MATCHER_NOT_FOUND（项渲染消失/标签点击失效，claude 外评 P2 回归锁）", () => {
    // EMPTY 项无 name（walk path 分支）；RETRY 项 name=MenuLoadRetry 恰已注册于 remaining.ts。
    // 不变量：侧栏项要么不带 name、要么 name 必须已注册（buildSidebarMenus「不产出 name」同款）
    expect(resolveSidebarFallback(false)[0].name).toBeUndefined();
    expect(resolveSidebarFallback(undefined)[0].name).toBe("MenuLoadRetry");
  });

  it("menuLoadFailed 未定义（store 旧态/恢复前）按失败占位兜底（fail-closed 方向）", () => {
    expect(resolveSidebarFallback(undefined)[0].meta?.title).toBe(
      "菜单加载失败，点击重试"
    );
  });

  it("每次调用返回 cloneDeep 副本，侧栏组件改动不污染常量", () => {
    const a = resolveSidebarFallback(true);
    const b = resolveSidebarFallback(true);
    expect(a[0]).not.toBe(b[0]);
    a[0].meta.title = "mutated";
    expect(resolveSidebarFallback(true)[0].meta?.title).toBe(
      "菜单加载失败，点击重试"
    );
  });
});

describe("initRouter 无凭证分支（Q-020 收口：menu-retry 会话过期重试不再失真）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.menus = [];
    mockUser.menuLoadFailed = false;
  });

  it("锁：本地凭证已无——统一提示+logOut+抛 SessionExpiredError，不构建侧栏（旧实现零动作 return router 必红）", async () => {
    mockGetToken.mockReturnValue(null);
    vi.resetModules();
    const { initRouter } = await import("./utils");

    // name 断言（非 instanceof）：resetModules 后 SessionExpiredError 与本用例模块图不同源
    const err: unknown = await initRouter().catch(e => e);
    expect((err as Error)?.name).toBe("SessionExpiredError"); // 旧实现 resolve router → undefined → 红
    expect((err as Error)?.message).toBe("会话已过期，请重新登录");
    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockMessage).toHaveBeenCalledWith("会话已过期，请重新登录", {
      type: "warning"
    });
    expect(mockLogOut).toHaveBeenCalledTimes(1);
    expect(mockHandleBackendMenus).not.toHaveBeenCalled(); // 旧实现 rebuild 空侧栏占位 → 调用 → 红
  });

  it("锁（外评 P2）：cookie 过期被清+userKey 残留形态（getToken 真值、无 accessToken、本地 expires 已过）同判会话终结——Q-020 条目点名形态（此前仅判 !getToken() 漏判、走刷新分支重弹失真提示必红）", async () => {
    // setUserKey 七字段不含 accessToken：cookie 被浏览器清除后 getToken() 兜底
    // userKey 返回真值对象（expires 已过、accessToken undefined）
    mockGetToken.mockReturnValue({
      refreshToken: "",
      expires: Date.now() - 1000
    });
    vi.resetModules();
    const { initRouter } = await import("./utils");

    const err: unknown = await initRouter().catch(e => e);
    // 已提交实现（仅判 !getToken()）：该形态 truthy → 走刷新分支 resolve router →
    // err=undefined 且无提示无 logOut（三断言红）
    expect((err as Error)?.name).toBe("SessionExpiredError");
    expect(mockMessage).toHaveBeenCalledTimes(1);
    expect(mockLogOut).toHaveBeenCalledTimes(1);
    expect(mockRefreshUserMenu).not.toHaveBeenCalled(); // 不再走注定被短路的刷新分支
  });

  it("有凭证形态不受扰：走重取分支正常构建侧栏（特征锁，防无凭证分支误伤守卫/登录路径）", async () => {
    mockGetToken.mockReturnValue({
      accessToken: "t1",
      expires: Date.now() + 600_000
    });
    mockUser.menus = [];
    mockRefreshUserMenu.mockImplementation(async () => {
      mockUser.menus = [{ path: "/welcome" }];
    });
    vi.resetModules();
    const { initRouter } = await import("./utils");

    const result = await initRouter();
    expect(result).toBeDefined();
    expect(mockLogOut).not.toHaveBeenCalled();
    expect(mockMessage).not.toHaveBeenCalled();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1);
    // 侧栏重建发生即证（刷新入口内 rebuild + initRouter 尾部兜底 rebuild 为
    // T-FE-048 既有幂等形态，不锁具体次数——防实现细节漂移）
    expect(mockHandleBackendMenus).toHaveBeenCalled();
  });
});

describe("refreshSessionCapability single-flight 与代际守卫（Q-016 收口）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser.menus = [{ path: "/welcome" }];
    mockUser.menuLoadFailed = false;
  });

  it("锁：同会话并发两次只发一次 refreshUserMenu，两调用方共享同一在途结果（旧实现两次必红）", async () => {
    mockGetToken.mockReturnValue({ accessToken: "t1", expires: 1 });
    let resolveMenu!: () => void;
    mockRefreshUserMenu.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveMenu = resolve;
        })
    );
    vi.resetModules();
    const { refreshSessionCapability } = await import("./utils");

    const p1 = refreshSessionCapability();
    const p2 = refreshSessionCapability();
    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(1); // 旧实现并发两发 → 2 → 红
    resolveMenu();
    await Promise.all([p1, p2]);
    expect(mockHandleBackendMenus).toHaveBeenCalledTimes(1);
  });

  it("锁：跨会话代际——刷新在途时令牌已换（登出重登），旧响应不重建新会话侧栏（旧实现必红）", async () => {
    mockGetToken.mockReturnValue({ accessToken: "t1", expires: 1 });
    let resolveMenu!: () => void;
    mockRefreshUserMenu.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveMenu = resolve;
        })
    );
    vi.resetModules();
    const { refreshSessionCapability } = await import("./utils");

    const pending = refreshSessionCapability();
    mockGetToken.mockReturnValue({ accessToken: "t2", expires: 1 });
    resolveMenu();
    await pending;

    expect(mockHandleBackendMenus).not.toHaveBeenCalled(); // 旧实现无条件 rebuild → 调用 → 红
  });

  it("同会话完成后在途标记复位：第二次调用重新发起（single-flight 非永久单次）", async () => {
    mockGetToken.mockReturnValue({ accessToken: "t1", expires: 1 });
    mockRefreshUserMenu.mockResolvedValue(undefined);
    vi.resetModules();
    const { refreshSessionCapability } = await import("./utils");

    await refreshSessionCapability();
    await refreshSessionCapability();

    expect(mockRefreshUserMenu).toHaveBeenCalledTimes(2);
    expect(mockHandleBackendMenus).toHaveBeenCalledTimes(2);
  });
});
