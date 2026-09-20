/**
 * 登录链路 store 单测（T-FE-041 评审修复回归保护）。
 * 覆盖 loginByUsername 四分支：
 *  1. 成功：expiresIn 秒 → 绝对时间供 setToken；username/roles 占位立即落位；菜单数据填充（含 userKey 持久化）
 *  2. 业务失败（HTTP 200 + code≠200）：unwrap 抛 RequestError，整体 reject
 *  3. user-menu HTTP 401（会话失效）：不降级，reject
 *  4. user-menu 普通异常（网络等）：降级，仍 resolve
 *
 * logOut 真注销（T-FE-045）四锁 → T-FE-054/Q-016 收口后口径：调用序（先发起 POST 注销
 * 后清本地，fire-and-forget 不等完成）/ 服务端失败仍清理（告警在微任务后排空再断言）/
 * fire-and-forget 主锁（注销在途黑洞时本地清理与跳转已完成，注销完成后不再补清理）/
 * 登出完成后重复触发零请求——旧实现（不调接口 / await 注销挂起）下必红。
 *
 * refreshUserMenu 会话代际守卫（Q-016 跨会话变体收口）：旧会话刷新在途时登出重登，
 * 旧响应（成功/失败）不回写新会话——旧实现覆盖 menus/权限串/menuLoadFailed 必红。
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
    // setToken 为 mock 不写真存储——getToken 须反映登录后持令牌的真实链路
    //（refreshUserMenu 代际守卫发起/回写均读 getToken，sol P2 修复后终结形态不回写）
    mockGetToken.mockReturnValue({
      accessToken: "token-1",
      expires: 1,
      refreshToken: ""
    });
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

describe("强制改密标记写入（T-FE-046：LoginResp userId/forceResetPwd 随登录入 userKey）", () => {
  it("forceResetPwd=true 登录：userId 与阻断标记写入 userKey（跨标签共享存储，守卫阻断的标记源）——旧实现不写必红", async () => {
    mockLogin.mockResolvedValue({
      code: 200,
      message: "ok",
      data: { ...LOGIN_RESP, forceResetPwd: true }
    });
    mockGetUserMenu.mockResolvedValue(MENU_RESP);

    await useUserStore().loginByUsername({
      username: "admin",
      password: "Admin@2026",
      captchaId: "id",
      captchaCode: "1234"
    });

    expect(mockStorage.setItem).toHaveBeenCalledWith(
      "user-info",
      expect.objectContaining({ userId: 1, forceResetPwd: true })
    );
  });

  it("forceResetPwd=false 登录：覆盖旧会话残留标记（登录覆盖生命周期）——旧实现不写必红", async () => {
    // 模拟 userKey 残留上一阻断会话标记。先取 store 实例使 state 初始化的
    // 5 次 getItem（avatar/username/nickname/roles/permissions）消化默认 null，
    // 两个 mockReturnValueOnce 精确落在 loginByUsername 链路读点（标记写入 +
    // refreshUserMenu spread）——双轨评审代码轨 P3 修复：此前 Once 队列被
    // state 初始化吞噬，stale 路径未被真正测到
    const user = useUserStore();
    const staleUserKey = {
      username: "prev",
      roles: [],
      forceResetPwd: true,
      userId: 9
    };
    mockStorage.getItem
      .mockReturnValueOnce(staleUserKey)
      .mockReturnValueOnce(staleUserKey);
    mockLogin.mockResolvedValue({
      code: 200,
      message: "ok",
      data: { ...LOGIN_RESP, forceResetPwd: false, userId: 2 }
    });
    mockGetUserMenu.mockResolvedValue(MENU_RESP);

    await user.loginByUsername({
      username: "other",
      password: "Admin@2026",
      captchaId: "id",
      captchaCode: "1234"
    });

    // stale 里的 forceResetPwd:true 被登录响应覆盖为 false（残留不外溢）
    expect(mockStorage.setItem).toHaveBeenCalledWith(
      "user-info",
      expect.objectContaining({ userId: 2, forceResetPwd: false })
    );
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
    // 会话恒在（代际守卫发起/回写读 getToken；undefined 指纹会被判终结不回写）
    mockGetToken.mockReturnValue({
      accessToken: "token-1",
      expires: 1,
      refreshToken: ""
    });
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

describe("logOut 真注销（T-FE-045；注销 fire-and-forget 化 T-FE-054/Q-016 收口）", () => {
  const TOKEN = { accessToken: "token-1", expires: 1, refreshToken: "" };

  /** 微任务排空（fire-and-forget 的注销 catch 在微任务后，断言前排空） */
  async function drainMicrotasks(times = 5) {
    for (let i = 0; i < times; i++) await Promise.resolve();
  }

  it("调用序：先发起 POST 注销（fire-and-forget 不等完成）再清本地——removeToken/resetRouter/push 依次在后，Pinia 清空", async () => {
    mockGetToken.mockReturnValue(TOKEN);
    mockLogout.mockResolvedValue(undefined);

    await useUserStore().logOut();

    expect(mockLogout).toHaveBeenCalledTimes(1);
    // 注销请求显式携带当前 token（formatToken 构造 Authorization 头；旧实现不调接口，此断言必红）
    expect(mockLogout).toHaveBeenCalledWith("Bearer token-1");
    // 调用序锁：注销请求发起先于本地清理，清理先于路由重置与跳转
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
    await drainMicrotasks();

    expect(warn).toHaveBeenCalled();
    expect(mockRemoveToken).toHaveBeenCalledTimes(1);
    expect(mockResetRouter).toHaveBeenCalledTimes(1);
    expect(mockRouterPush).toHaveBeenCalledWith("/login");
    warn.mockRestore();
  });

  it("fire-and-forget 主锁：注销在途黑洞时 logOut 已完成本地清理与跳转（旧实现 await 挂起必红），注销完成后不再补清理——窗口内新登录凭据不被旧清理链清除的时序等价", async () => {
    mockGetToken.mockReturnValue(TOKEN);
    let resolveLogout!: () => void;
    mockLogout.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveLogout = resolve;
        })
    );

    let settled = false;
    const first = useUserStore()
      .logOut()
      .finally(() => (settled = true));
    // 断言失败（红跑态）也必须收尾：注销 defer 不 resolve 会把 logoutInFlight 残留为
    // true，污染后续用例（旧实现下断言红即中断）——try/finally 保证状态复位
    try {
      await drainMicrotasks(10);

      // 注销仍 pending：logOut 已 settled 且清理链已全部完成——旧实现（await 注销）
      // 下 first 挂起 settled=false、removeToken 未调用，两断言必红
      expect(settled).toBe(true);
      expect(mockRemoveToken).toHaveBeenCalledTimes(1);
      expect(mockResetRouter).toHaveBeenCalledTimes(1);
      expect(mockRouterPush).toHaveBeenCalledWith("/login");
    } finally {
      resolveLogout();
      await first.catch(() => {});
      await drainMicrotasks();
    }
    // 注销完成不触发补清理：旧清理链再无机会清掉窗口内新登录的凭据
    expect(mockRemoveToken).toHaveBeenCalledTimes(1);
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

describe("refreshUserMenu 会话代际守卫（Q-016 收口：旧会话响应不污染新会话）", () => {
  it("旧会话刷新在途时登出重登：旧成功响应不回写新会话（menus/menuLoadFailed 维持新会话态）——旧实现覆盖必红", async () => {
    const user = useUserStore();
    user.SET_MENUS([{ path: "/new-session" } as any]);
    user.menuLoadFailed = true; // 新会话的既有状态
    mockGetToken.mockReturnValue({
      accessToken: "token-old",
      expires: 1,
      refreshToken: ""
    });
    let resolveMenu!: (v: unknown) => void;
    mockGetUserMenu.mockImplementation(
      () =>
        new Promise(resolve => {
          resolveMenu = resolve;
        })
    );

    const pending = user.refreshUserMenu();
    // 刷新在途：旧会话登出、新登录（令牌已换）
    mockGetToken.mockReturnValue({
      accessToken: "token-new",
      expires: 1,
      refreshToken: ""
    });
    resolveMenu(MENU_RESP);
    await pending;

    // 旧实现：旧响应照写 → menus 被覆盖为 MENU_RESP、menuLoadFailed 置 false（双红）
    expect(user.menus).toEqual([{ path: "/new-session" }]);
    expect(user.menuLoadFailed).toBe(true);
    // localStorage userKey 回写同被守卫拦截：旧会话的 roles/permissions 不落新会话存储
    expect(mockStorage.setItem).not.toHaveBeenCalled();
  });

  it("旧会话的失败不污染新会话：menuLoadFailed 不置位、错误不上抛（新会话无关的失败被吞）——旧实现置位+上抛必红", async () => {
    const user = useUserStore();
    user.menuLoadFailed = false;
    mockGetToken.mockReturnValue({
      accessToken: "token-old",
      expires: 1,
      refreshToken: ""
    });
    let rejectMenu!: (e: unknown) => void;
    mockGetUserMenu.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectMenu = reject;
        })
    );

    const pending = user.refreshUserMenu();
    mockGetToken.mockReturnValue({
      accessToken: "token-new",
      expires: 1,
      refreshToken: ""
    });
    rejectMenu(new Error("old session network down"));
    await pending; // 旧实现：错误上抛，此处即 throw（用例红）

    expect(user.menuLoadFailed).toBe(false); // 旧实现置 true → 红
  });

  it("锁（外评 P3）：会话终结型失败不吞——401 分支已 logOut 清令牌（getToken 空）时照常置位+上抛（旧守卫 undefined!==fingerprint 误拦吞掉，手动刷新假成功/登录 401 硬化不可达，必红）", async () => {
    const user = useUserStore();
    user.menuLoadFailed = false;
    mockGetToken.mockReturnValue({
      accessToken: "token-1",
      expires: 1,
      refreshToken: ""
    });
    let rejectMenu!: (e: unknown) => void;
    mockGetUserMenu.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectMenu = reject;
        })
    );

    const pending = user.refreshUserMenu();
    // 401 到达：响应拦截器已先 logOut（同步清令牌）——getToken() 变 null，
    // 与「跨会话（另一活会话令牌）」语义不同，不得拦
    mockGetToken.mockReturnValue(null);
    rejectMenu(
      Object.assign(new Error("Request failed with status code 401"), {
        response: { status: 401 }
      })
    );

    // 已提交实现（getToken 空也拦）：静默 return resolve、menuLoadFailed 不置位（双红）
    await expect(pending).rejects.toThrow();
    expect(user.menuLoadFailed).toBe(true);
  });

  it("锁（sol 外评 P2）：会话终结后的成功响应不回写——刷新在途时登出/401 已清令牌（getToken 空），200 晚到不得重建 Pinia/userKey（旧判据终结不拦回写，三断言必红）", async () => {
    const user = useUserStore();
    user.SET_MENUS([]);
    user.menuLoadFailed = true; // 终结前的既有状态
    mockGetToken.mockReturnValueOnce({
      accessToken: "token-1",
      expires: 1,
      refreshToken: ""
    });
    let resolveMenu!: (v: unknown) => void;
    mockGetUserMenu.mockImplementation(
      () =>
        new Promise(resolve => {
          resolveMenu = resolve;
        })
    );

    const pending = user.refreshUserMenu();
    // 刷新在途：主动登出/401 分支已同步清令牌（getToken 变 null）
    mockGetToken.mockReturnValue(null);
    resolveMenu(MENU_RESP);
    await pending;

    // 已提交实现（sessionReplaced 对终结恒 false → 回写）：menus 被 MENU_RESP 覆盖、
    // menuLoadFailed 置 false、残缺 userKey 重建（setItem 调用）——三断言红
    expect(user.menus).toEqual([]);
    expect(user.menuLoadFailed).toBe(true);
    expect(mockStorage.setItem).not.toHaveBeenCalled();
  });
});
