/**
 * 路由级 UX 门禁判定纯函数锁（T-FE-056）。
 * 与 index.spec.ts（守卫行为锁）分工：本文件锁 gate.ts 对**真实** remaining.ts
 * 的白名单收集完整性（顶层全量 path + /redirect children 参数路由前缀化 + 公共
 * 错误页显式登记）与 menus 树 path 集递归收集——mock 形态的守卫级覆盖见锁③。
 * mock 说明：gate → user store → store/utils → @/router 循环边以轻量 router 对象
 * 断链（真 index.ts 顶层 createRouter 依赖 VITE_ROUTER_HISTORY/DOM，node 环境不可
 * 加载）；remaining.ts 保持真实数据（白名单完整性的被测对象）；user store 为
 * Pinia 内存实例直写。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

vi.mock("@/router", () => ({
  router: { push: vi.fn(), options: { routes: [] } },
  resetRouter: vi.fn(),
  constantMenus: []
}));

import { isPublicRoute, collectMenuPaths, isRouteAllowed } from "./gate";
import { useUserStoreHook } from "@/store/modules/user";

describe("路由级 UX 门禁纯函数（T-FE-056）", () => {
  beforeEach(() => {
    const userStore = useUserStoreHook();
    userStore.menuGateStatus = "loaded";
    userStore.menus = [];
    userStore.permissions = [];
  });

  describe("isPublicRoute：remaining.ts 全量 + 公共错误页显式登记", () => {
    it("remaining.ts 顶层全部 path 自动纳入（逐项——新增公共路由漏自动收集时逐项红）", () => {
      for (const path of [
        "/login",
        "/access-denied",
        "/server-error",
        "/menu-retry",
        "/change-password",
        "/redirect"
      ]) {
        expect(isPublicRoute(path), path).toBe(true);
      }
    });

    it("/redirect/:path(.*) children 参数路由前缀化——标签刷新形态 /redirect/<任意路径> 放行（漏配即拦死全角色标签刷新）", () => {
      expect(isPublicRoute("/redirect/system/user")).toBe(true);
      expect(isPublicRoute("/redirect/system/role/list")).toBe(true);
    });

    it("前缀不误命中：/redirectxxx（无尾斜杠）与 /redirect2/** 非公共路由——前缀推导含尾斜杠（trim 斜杠/无斜杠变体把白名单误放大至免门禁免等待）实现下必红", () => {
      expect(isPublicRoute("/redirectxxx")).toBe(false);
      expect(isPublicRoute("/redirect2/system/user")).toBe(false);
    });

    it("公共错误页显式登记（error.ts 模块路由不进 remaining 自动收集——漏登记则守卫 404 重定向与页面异常跳转自环）", () => {
      for (const path of ["/error/403", "/error/404", "/error/500"]) {
        expect(isPublicRoute(path), path).toBe(true);
      }
    });

    it("业务路由不在白名单", () => {
      expect(isPublicRoute("/system/user")).toBe(false);
      expect(isPublicRoute("/perm/grant")).toBe(false);
      expect(isPublicRoute("/welcome")).toBe(false);
    });
  });

  describe("collectMenuPaths：后端 menus 树递归收集", () => {
    it("父/子/孙三层与空树/缺字段宽容", () => {
      const paths = collectMenuPaths([
        {
          path: "/system",
          children: [
            {
              path: "/system/user",
              children: [{ path: "/system/user/detail" }]
            },
            { path: "/system/role" }
          ]
        },
        { path: "/welcome" }
      ] as Parameters<typeof collectMenuPaths>[0]);
      expect(paths).toEqual(
        new Set([
          "/system",
          "/system/user",
          "/system/user/detail",
          "/system/role",
          "/welcome"
        ])
      );
      expect(collectMenuPaths([])).toEqual(new Set());
    });
  });

  describe("isRouteAllowed：三源判定（白名单 ∨ menus 集 ∨ 显式映射）", () => {
    it("menus 集内放行、集外拦截", () => {
      const userStore = useUserStoreHook();
      userStore.menus = [{ path: "/welcome" }] as never;
      expect(isRouteAllowed("/welcome")).toBe(true);
      expect(isRouteAllowed("/system/user")).toBe(false);
    });

    it("显式动作路由映射：/perm/grant 持 ROLE:VIEW 放行、不持拦截——纯 menus 白名单实现下本用例必红", () => {
      const userStore = useUserStoreHook();
      userStore.permissions = ["ROLE:VIEW"];
      expect(isRouteAllowed("/perm/grant")).toBe(true);
      userStore.permissions = [];
      expect(isRouteAllowed("/perm/grant")).toBe(false);
    });
  });
});
