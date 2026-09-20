/**
 * menu-retry 重载编排回归锁（T-FE-056 双轨评审 P3-1 处置，2026-09-20 用户拍板直接修）。
 * 锁的核心不变量：预清重取失败后回滚旧菜单集——否则刷新失败时状态机维持 loaded
 * （user store 迁移表）而 menus 已被调用方预清，门禁集合塌缩为仅公共白名单、业务路由
 * 全拦 403 形成锁死（违背「门禁失效最坏结果=回到现状」）。主锁在旧实现（预清不回滚）
 * 下必红。mock 形态对齐 user.spec.ts（真 user store/Pinia + mock initRouter 与
 * store 工具桶断环）；menu-retry.vue 三态消息消费属 SFC 接线（无组件测试既定边界）。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

const { mockInitRouter, mockStorage, mockRouterPush, mockResetRouter } =
  vi.hoisted(() => ({
    mockInitRouter: vi.fn(),
    mockStorage: {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn()
    },
    mockRouterPush: vi.fn(),
    mockResetRouter: vi.fn()
  }));
vi.mock("@/router/utils", () => ({ initRouter: mockInitRouter }));
vi.mock("@/store/utils", () => ({
  store: createPinia(),
  router: { push: mockRouterPush },
  resetRouter: mockResetRouter,
  routerArrays: [],
  constantMenus: [],
  storageLocal: () => mockStorage,
  responsiveStorageNameSpace: () => "responsive-"
}));

import { reloadMenusWithRollback } from "./menu-retry-reload";
import { useUserStoreHook } from "@/store/modules/user";

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

describe("menu-retry 重载编排（T-FE-056 P3-1 处置）", () => {
  it("主锁（old-fail）：有菜单会话重取失败 → 回滚旧菜单集（门禁 path 集恢复）——旧实现（预清不回滚）下 menus=[] 必红", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([
      { path: "/welcome" },
      { path: "/system/user" }
    ] as never);
    // initRouter 重取失败形态：menuLoadFailed 置位、menus 维持预清后的空值
    mockInitRouter.mockImplementation(async () => {
      const store = useUserStoreHook();
      store.menuLoadFailed = true;
    });

    const outcome = await reloadMenusWithRollback();

    expect(outcome).toBe("still-failed");
    expect(userStore.menuLoadFailed).toBe(true);
    expect(userStore.menus).toEqual([
      { path: "/welcome" },
      { path: "/system/user" }
    ]);
  });

  it("重取成功：menus 为新值不回滚（recovered）", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([{ path: "/welcome" }] as never);
    mockInitRouter.mockImplementation(async () => {
      const store = useUserStoreHook();
      store.menuLoadFailed = false;
      store.SET_MENUS([
        { path: "/welcome" },
        { path: "/system/role" }
      ] as never);
    });

    const outcome = await reloadMenusWithRollback();

    expect(outcome).toBe("recovered");
    expect(userStore.menus).toEqual([
      { path: "/welcome" },
      { path: "/system/role" }
    ]);
  });

  it("拉取成功但空（权限全撤/零权限新事实）：不回滚旧快照——成功路径的事实覆盖旧值（still-empty）", async () => {
    const userStore = useUserStoreHook();
    userStore.SET_MENUS([{ path: "/welcome" }] as never);
    mockInitRouter.mockImplementation(async () => {
      const store = useUserStoreHook();
      store.menuLoadFailed = false;
    });

    const outcome = await reloadMenusWithRollback();

    expect(outcome).toBe("still-empty");
    expect(userStore.menus).toEqual([]);
  });

  it("本就无菜单会话（首载失败占位形态）重取仍失败：空快照回滚为无操作（仍空、仍 still-failed）", async () => {
    const userStore = useUserStoreHook();
    mockInitRouter.mockImplementation(async () => {
      const store = useUserStoreHook();
      store.menuLoadFailed = true;
    });

    const outcome = await reloadMenusWithRollback();

    expect(outcome).toBe("still-failed");
    expect(userStore.menus).toEqual([]);
  });
});
