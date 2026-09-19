/**
 * 路由守卫 beforeEach 回归锁（T-FE-053）。
 * 旧实现两处贯穿分支（roles 403 / VITE_HIDE_HOME 404）调 next 后不 return，
 * 会继续走到 toCorrectRoute 二次 next（触发面=零：全仓无路由声明 meta.roles、
 * .env VITE_HIDE_HOME=false，外评据此降级为卫生修）。2026-09-19 拍板：
 * 删 roles 死分支（本仓路由权限=后端 menus 派生，见 beforeEach 头注）+
 * 全部 next() 站点统一「每 next 必 return」。
 * mock 说明：index.ts 模块加载即 createRouter+注册守卫（依赖 Layout SFC 树与
 * 两 store 循环引用，此前从未被测试加载），此处 mock createRouter 捕获守卫
 * 回调、按模块路径断环（storageLocal 用内存 Map 替 localStorage——node 环境
 * 无 DOM）；被测对象=守卫纯逻辑，matched 留空避开 document.title 写点。
 * VITE_HIDE_HOME 分支（import.meta.env 模块级读取）测试环境不可达，其 return
 * 与 404 分支同机械形态，由双轨评审覆盖。
 */
import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";

const { fakeRouter, memStorage } = vi.hoisted(() => ({
  fakeRouter: {
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    addRoute: vi.fn(),
    clearRoutes: vi.fn(),
    push: vi.fn(),
    options: { routes: [] as unknown[] }
  },
  memStorage: new Map<string, string>()
}));

vi.mock("vue-router", async importOriginal => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return { ...actual, createRouter: vi.fn(() => fakeRouter) };
});
vi.mock("./utils", () => ({
  ascending: (v: unknown[]) => v,
  getTopMenu: vi.fn(),
  initRouter: vi
    .fn()
    .mockResolvedValue({ options: { routes: [{ children: [] }] } }),
  getHistoryMode: () => "hash",
  findRouteByPath: vi.fn(),
  handleAliveRoute: vi.fn(),
  formatTwoStageRoutes: (v: unknown) => v,
  formatFlatteningRoutes: (v: unknown) => v,
  // 旧实现（删 roles 死分支前）从此处 import isOneOfArray——红跑对旧码需可执行；
  // 与真实实现的已知分歧：真实版对非数组 b 入参宽容返回 true，此处返回 false
  //（红跑用例输入恒携带 roles 数组，分歧不可达；新实现已不消费该函数）
  isOneOfArray: (a?: string[], b: string[] = []) =>
    a ? a.some(x => b.includes(x)) : true
}));
vi.mock("./modules/remaining", () => ({
  default: [{ path: "/login" }, { path: "/menu-retry" }, { path: "/redirect" }]
}));
vi.mock("@/store/modules/multiTags", () => ({
  useMultiTagsStoreHook: () => ({
    getMultiTagsCache: false,
    handleTags: vi.fn()
  })
}));
vi.mock("@/store/modules/permission", () => ({
  usePermissionStoreHook: () => ({ wholeMenus: [], clearAllCachePage: vi.fn() })
}));
vi.mock("@/utils/auth", () => ({
  userKey: "user-info",
  multipleTabsKey: "multiple-tabs",
  removeToken: vi.fn(),
  getToken: vi.fn(),
  setToken: vi.fn()
}));
vi.mock("js-cookie", () => ({ default: { get: vi.fn() } }));
vi.mock("@/utils/progress", () => ({
  default: { start: vi.fn(), done: vi.fn() }
}));
vi.mock("@pureadmin/utils", () => ({
  isUrl: (v?: string) => typeof v === "string" && /^https?:\/\//.test(v),
  openLink: vi.fn(),
  cloneDeep: <T>(v: T): T => JSON.parse(JSON.stringify(v)),
  isAllEmpty: (...args: unknown[]) =>
    args.every(a => a === undefined || a === null || a === ""),
  storageLocal: () => ({
    getItem: (key: string) =>
      memStorage.has(key) ? JSON.parse(memStorage.get(key) as string) : null,
    setItem: (key: string, value: unknown) =>
      memStorage.set(key, JSON.stringify(value)),
    removeItem: (key: string) => memStorage.delete(key)
  })
}));

import Cookies from "js-cookie";
import { removeToken, userKey } from "@/utils/auth";
import { initRouter } from "./utils";
import { router } from "./index";

type GuardFn = (to: any, from: any, next: (...args: any[]) => void) => void;
const guardRegistrations = (
  (router as unknown as { beforeEach: Mock }).beforeEach.mock
    .calls as unknown as GuardFn[]
).length;
const guard = (
  (router as unknown as { beforeEach: Mock }).beforeEach.mock
    .calls[0] as GuardFn[]
)[0];

/** 构造守卫入参 to：matched 留空（不触 document.title），其余按用例覆盖 */
function makeTo(overrides: Record<string, unknown> = {}) {
  return {
    path: "/system/user",
    fullPath: "/system/user",
    name: "SystemUser",
    meta: {},
    matched: [],
    ...overrides
  };
}
const fromNamed = { name: "Welcome", fullPath: "/" };
/** js-cookie get 为重载签名（vi.mocked 取无参重载致 mockReturnValue 类型不合），放宽为 Mock */
const cookiesGet = Cookies.get as unknown as Mock;

/** 已登录态：多标签 cookie 在 + localStorage 落用户信息（守卫双条件）；stored 覆盖 userKey 附加字段（T-FE-046 forceResetPwd/userId） */
function loginAs(
  roles: string[] = ["BASIC_ROLE"],
  stored: Record<string, unknown> = {}
) {
  memStorage.set(
    userKey,
    JSON.stringify({ username: "admin", roles, permissions: [], ...stored })
  );
  // 守卫仅读 multipleTabsKey 一处，固定返回真值即可（js-cookie get 重载签名
  // 与 mockImplementation 不兼容，用 mockReturnValue 表达同一语义）
  cookiesGet.mockReturnValue("1");
}
/** 未登录态：无 cookie、无用户信息 */
function loginOutState() {
  memStorage.delete(userKey);
  cookiesGet.mockReturnValue(undefined);
}

beforeEach(() => {
  vi.clearAllMocks();
  memStorage.clear();
  cookiesGet.mockReturnValue(undefined);
});

describe("路由守卫 beforeEach（T-FE-053）", () => {
  it("守卫单注册：模块加载期 beforeEach 恰注册一次（T-FE-056 复用架子若新增注册，防用例静默只测第一个；计数于 clearAllMocks 前的加载期捕获）", () => {
    expect(guardRegistrations).toBe(1);
  });

  it('路由声明 meta.roles 也不再拦 403、正常放行——本仓路由权限=后端 menus 派生（旧实现 next({path:"/error/403"})+贯穿二次 next，断言必红）', () => {
    loginAs(["BASIC_ROLE"]);
    const next = vi.fn();
    guard(makeTo({ meta: { roles: ["admin"] } }), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });

  it("已登录访问白名单 /login：next 弹回当前页 _from.fullPath（既有行为特征锁）", () => {
    loginAs();
    const next = vi.fn();
    guard(makeTo({ path: "/login", fullPath: "/login" }), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith("/");
  });

  it("已登录访问普通路由：next() 无参放行恰一次（每 next 必 return 纪律）", () => {
    loginAs();
    const next = vi.fn();
    guard(makeTo(), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });

  it("F5 刷新（无 _from.name 且 wholeMenus 空）：同步 next() 放行一次，initRouter 异步补路由不再追加 next", async () => {
    loginAs();
    const next = vi.fn();
    guard(makeTo(), { name: undefined, fullPath: "/" }, next);
    expect(next).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(initRouter).toHaveBeenCalled());
    // then 回调只补路由/handleTags，不得再走 next（放行已由同步分支完成）
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });

  it('未登录访问非白名单：removeToken + next({path:"/login"}) 恰一次（既有行为特征锁）', () => {
    loginOutState();
    const next = vi.fn();
    guard(makeTo(), fromNamed, next);
    expect(removeToken).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith({ path: "/login" });
  });

  it("未登录访问 /login：直接 next() 放行、不清令牌（既有行为特征锁）", () => {
    loginOutState();
    const next = vi.fn();
    guard(makeTo({ path: "/login", fullPath: "/login" }), fromNamed, next);
    expect(removeToken).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });
});

describe("路由守卫 forceResetPwd 阻断（T-FE-046）", () => {
  /** 阻断态：登录标记 forceResetPwd=true（userKey，跨标签共享 localStorage） */
  function loginAsForceReset() {
    loginAs(["BASIC_ROLE"], { forceResetPwd: true, userId: 1 });
  }

  it("阻断：forceResetPwd=true 访问业务路由 redirect /change-password 恰一次——旧实现（无阻断）next() 无参放行必红", () => {
    loginAsForceReset();
    const next = vi.fn();
    guard(makeTo(), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith({ path: "/change-password" });
  });

  it("放行面：/change-password、公共错误页（/error/403 代表）与未标记用户不受阻断", () => {
    loginAsForceReset();
    const nextChange = vi.fn();
    guard(
      makeTo({ path: "/change-password", fullPath: "/change-password" }),
      fromNamed,
      nextChange
    );
    expect(nextChange).toHaveBeenCalledTimes(1);
    expect(nextChange).toHaveBeenCalledWith();

    const nextError = vi.fn();
    guard(
      makeTo({ path: "/error/403", fullPath: "/error/403" }),
      fromNamed,
      nextError
    );
    expect(nextError).toHaveBeenCalledTimes(1);
    expect(nextError).toHaveBeenCalledWith();

    // 正常登录态（forceResetPwd 缺省）业务路由照常放行——阻断闸门仅对标记开
    loginAs();
    const nextNormal = vi.fn();
    guard(makeTo(), fromNamed, nextNormal);
    expect(nextNormal).toHaveBeenCalledTimes(1);
    expect(nextNormal).toHaveBeenCalledWith();
  });

  it("阻断态访问 /login：不被改密页重定向（放行清单内），走既有白名单弹回 _from——非阻断用户行为不变", () => {
    loginAsForceReset();
    const next = vi.fn();
    guard(makeTo({ path: "/login", fullPath: "/login" }), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith("/");
  });

  it("新开标签同被阻断：守卫每次导航重读共享存储（无模块级状态），同 storage 连续两次导航均 redirect 改密页——旧实现必红", () => {
    loginAsForceReset();
    const nextFirst = vi.fn();
    guard(makeTo(), fromNamed, nextFirst);
    const nextSecond = vi.fn();
    guard(makeTo(), fromNamed, nextSecond);
    expect(nextFirst).toHaveBeenCalledWith({ path: "/change-password" });
    expect(nextSecond).toHaveBeenCalledWith({ path: "/change-password" });
  });

  it("redirect 族不放行：真实标签刷新形态 /redirect/<path> 与裸 /redirect 均 redirect 改密页——裸路径命中 Layout 父记录会把侧栏壳放给阻断人群（双轨评审 P2 修复锁，修前清单含 /redirect 必红）", () => {
    loginAsForceReset();
    const nextFresh = vi.fn();
    guard(
      makeTo({
        path: "/redirect/system/user",
        fullPath: "/redirect/system/user"
      }),
      fromNamed,
      nextFresh
    );
    expect(nextFresh).toHaveBeenCalledWith({ path: "/change-password" });

    const nextBare = vi.fn();
    guard(
      makeTo({ path: "/redirect", fullPath: "/redirect" }),
      fromNamed,
      nextBare
    );
    expect(nextBare).toHaveBeenCalledWith({ path: "/change-password" });
  });

  it("改密成功后放行：标记置 false（clearForceResetPwdFlag 的存储效果）后业务路由 next() 无参放行", () => {
    loginAsForceReset();
    // 改密成功 = 共享存储中标记翻转为 false（另一标签同 storage 重读即解除阻断）
    loginAs(["BASIC_ROLE"], { forceResetPwd: false, userId: 1 });
    const next = vi.fn();
    guard(makeTo(), fromNamed, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
  });
});
