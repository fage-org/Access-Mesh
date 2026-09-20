/**
 * menu-retry 重载编排回归锁（T-FE-056 外评处置，2026-09-20 用户拍板：不预清、
 * 改用会话能力刷新入口强制重取）。锁的核心不变量：重取失败时 menus/门禁集合/
 * 侧栏全部保留旧态（预清+回滚旧实现下，请求在途与回滚半程存在门禁空集窗口、
 * 401 后还会把旧菜单写回已登出的 store）——主锁在预清形态实现下必红。mock 形态
 * 对齐 user.spec.ts（真 user store/Pinia + mock 能力刷新入口/auth/session-expired
 * 与 store 工具桶断环）；侧栏「失败不触碰 wholeMenus」由 T-FE-048 能力刷新入口
 * 既有锁覆盖（utils/http spec），页面层不重复锁；menu-retry.vue 三态消息消费属
 * SFC 接线（无组件测试既定边界）。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

const {
  mockRefreshCapability,
  mockIsSessionTerminated,
  mockNotifyExpired,
  mockStorage,
  mockRouterPush,
  mockResetRouter
} = vi.hoisted(() => ({
  mockRefreshCapability: vi.fn(),
  mockIsSessionTerminated: vi.fn(),
  mockNotifyExpired: vi.fn(),
  mockStorage: {
    getItem: vi.fn(() => null),
    setItem: vi.fn(),
    removeItem: vi.fn()
  },
  mockRouterPush: vi.fn(),
  mockResetRouter: vi.fn()
}));
vi.mock("@/router/utils", () => ({
  refreshSessionCapability: mockRefreshCapability
}));
vi.mock("@/utils/auth", () => ({
  isSessionTerminated: mockIsSessionTerminated,
  getToken: vi.fn(),
  setToken: vi.fn(),
  removeToken: vi.fn(),
  userKey: "user-info"
}));
vi.mock("@/utils/session-expired", () => ({
  notifySessionExpiredOnce: mockNotifyExpired,
  SessionExpiredError: class SessionExpiredError extends Error {}
}));
vi.mock("@/store/utils", () => ({
  store: createPinia(),
  router: { push: mockRouterPush },
  resetRouter: mockResetRouter,
  routerArrays: [],
  constantMenus: [],
  storageLocal: () => mockStorage,
  responsiveStorageNameSpace: () => "responsive-"
}));

import { reloadSessionMenus } from "./menu-retry-reload";
import { useUserStoreHook } from "@/store/modules/user";
import { SessionExpiredError } from "@/utils/session-expired";

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  mockIsSessionTerminated.mockReturnValue(false);
});

describe("menu-retry 重载编排（T-FE-056 外评处置：能力刷新入口形态）", () => {
  it("主锁（old-fail）：重取在途期间 menus 保留旧值（门禁集合不塌缩，守卫现算读点此刻仍命中）——预清形态实现（SET_MENUS([]) 后等刷新）下在途 menus=[] 必红", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([
      { path: "/welcome" },
      { path: "/system/user" }
    ] as never);
    let resolveRefresh!: () => void;
    mockRefreshCapability.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveRefresh = resolve;
        })
    );
    const pending = reloadSessionMenus();
    // 在途窗口：门禁每次导航现算 collectMenuPaths 的数据源此刻必须仍是旧菜单
    expect(userStore.menus).toEqual([
      { path: "/welcome" },
      { path: "/system/user" }
    ]);
    resolveRefresh();
    expect(await pending).toBe("recovered");
  });

  it("重取失败（会话仍活）：menus 保留旧值 + still-failed——能力刷新入口原样抛出、不触碰 store", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([
      { path: "/welcome" },
      { path: "/system/user" }
    ] as never);
    mockRefreshCapability.mockRejectedValue(new Error("network down"));

    const outcome = await reloadSessionMenus();

    expect(outcome).toBe("still-failed");
    expect(userStore.menus).toEqual([
      { path: "/welcome" },
      { path: "/system/user" }
    ]);
  });

  it("重取成功：menus 非空 → recovered（空与否按刷新后事实判定，不回滚不预清）", async () => {
    const userStore = useUserStoreHook();
    mockRefreshCapability.mockImplementation(async () => {
      useUserStoreHook().SET_MENUS([
        { path: "/welcome" },
        { path: "/system/role" }
      ] as never);
    });

    const outcome = await reloadSessionMenus();

    expect(outcome).toBe("recovered");
    expect(userStore.menus).toEqual([
      { path: "/welcome" },
      { path: "/system/role" }
    ]);
  });

  it("拉取成功但空（权限全撤/零权限新事实）：still-empty（成功路径事实不被旧快照覆盖）", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([{ path: "/welcome" }] as never);
    // 入口成功路径回写空 menus（权限全撤的新事实）
    mockRefreshCapability.mockImplementation(async () => {
      useUserStoreHook().SET_MENUS([] as never);
    });

    const outcome = await reloadSessionMenus();

    expect(outcome).toBe("still-empty");
    // 编排层不恢复旧快照（回滚形态实现下此处 menus=[/welcome] 必红）
    expect(userStore.menus).toEqual([]);
  });

  it("会话终结前置判：点击重试时凭证已无 → 提示+logOut+抛 SessionExpiredError（Q-020，不发刷新请求）", async () => {
    mockIsSessionTerminated.mockReturnValue(true);

    await expect(reloadSessionMenus()).rejects.toThrow(SessionExpiredError);
    expect(mockNotifyExpired).toHaveBeenCalledTimes(1);
    expect(mockRefreshCapability).not.toHaveBeenCalled();
    // logOut 已执行（Pinia 清空 + 跳登录）
    expect(mockRouterPush).toHaveBeenCalledWith("/login");
  });

  it("401 后置判：拉取失败且会话已终结（拦截器已 logOut）→ 抛 SessionExpiredError 不弹失真业务提示——预清+回滚旧实现下该形态回滚旧菜单并返回 still-failed 必红", async () => {
    // 401 形态：刷新抛错后 isSessionTerminated 翻真（令牌已被 http 拦截器 logOut
    // 清除——本 spec 对 http 断链，以判据 mock 表达同一时序）
    mockRefreshCapability.mockRejectedValue(new Error("401"));
    mockIsSessionTerminated
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);

    // 抛 SessionExpiredError 由页面 catch 留痕（不弹「菜单加载仍失败」失真提示）；
    // 回滚形态实现下此处 resolve "still-failed"（不抛）必红
    await expect(reloadSessionMenus()).rejects.toThrow(SessionExpiredError);
  });
});
