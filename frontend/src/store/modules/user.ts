import { defineStore } from "pinia";
import {
  type userType,
  store,
  router,
  resetRouter,
  routerArrays,
  storageLocal
} from "../utils";
import {
  login,
  logout,
  getUserMenu,
  type LoginFormData,
  type LoginResp,
  type UserMenuRoute
} from "@/api/auth";
import { unwrap } from "@/api/_envelope";
import { useMultiTagsStoreHook } from "./multiTags";
import {
  type DataInfo,
  formatToken,
  getToken,
  setToken,
  removeToken,
  userKey
} from "@/utils/auth";

/** 登出串行防抖（T-FE-045）：登出进行中重复触发（连点/拦截器程序化调用）直接短路，同一登出动作只发一次 POST /logout */
let logoutInFlight = false;

export const useUserStore = defineStore("pure-user", {
  state: (): userType => ({
    // 头像
    avatar: storageLocal().getItem<DataInfo<number>>(userKey)?.avatar ?? "",
    // 用户名
    username: storageLocal().getItem<DataInfo<number>>(userKey)?.username ?? "",
    // 昵称
    nickname: storageLocal().getItem<DataInfo<number>>(userKey)?.nickname ?? "",
    // 页面级别权限
    roles: storageLocal().getItem<DataInfo<number>>(userKey)?.roles ?? [],
    // 按钮级别权限
    permissions:
      storageLocal().getItem<DataInfo<number>>(userKey)?.permissions ?? [],
    /** 后端下发的菜单树（v1.4 双轨并行：菜单可见性轨道，登录后由 /api/access/auth/user-menu 填充） */
    menus: [],
    /** 最近一次 /user-menu 拉取是否失败（T-FE-049，区分「加载失败」与「成功但无菜单」两态空侧栏） */
    menuLoadFailed: false,
    // 是否勾选了登录页的免登录
    isRemembered: false,
    // 登录页的免登录存储几天，默认7天
    loginDay: 7
  }),
  actions: {
    /** 存储头像 */
    SET_AVATAR(avatar: string) {
      this.avatar = avatar;
    },
    /** 存储用户名 */
    SET_USERNAME(username: string) {
      this.username = username;
    },
    /** 存储昵称 */
    SET_NICKNAME(nickname: string) {
      this.nickname = nickname;
    },
    /** 存储角色 */
    SET_ROLES(roles: Array<string>) {
      this.roles = roles;
    },
    /** 存储按钮级别权限 */
    SET_PERMS(permissions: Array<string>) {
      this.permissions = permissions;
    },
    /** 存储后端下发的菜单树（v1.4） */
    SET_MENUS(menus: Array<UserMenuRoute>) {
      this.menus = menus;
    },
    /** 存储是否勾选了登录页的免登录 */
    SET_ISREMEMBERED(bool: boolean) {
      this.isRemembered = bool;
    },
    /** 设置登录页的免登录存储几天 */
    SET_LOGINDAY(value: number) {
      this.loginDay = Number(value);
    },
    /**
     * 登入（T-FE-041 真实链路，对齐 R 信封）。
     * <p>
     * 流程（v1.4 双轨并行）：
     *  1. 调 `/api/access/auth/login` 拿令牌（`unwrap` 解包：业务失败 HTTP 200 + code≠200 抛 RequestError）
     *  2. `expiresIn`（秒）转换为绝对时间后 `setToken` 写 cookie（普通 Sa-Token 会话
     *     `refreshToken` 为 null，不接 OAuth2 刷新令牌）；`roles: []` 占位使 `setToken`
     *     走传入分支——登录用户名立即落位 Pinia/localStorage（roles 由第 3 步填充）
     *  3. 调 `/api/access/auth/user-menu` 拿 menus + roles + permissions，写 Pinia + localStorage
     *  4. 失败语义：第 1 步失败整体 reject；第 3 步 HTTP 401（会话失效，拦截器已清会话
     *     回登录页）同样 reject——不能按登录成功处理；其余异常（网络等）仅记录，仍 resolve
     */
    async loginByUsername(data: LoginFormData): Promise<LoginResp> {
      const loginData = unwrap(await login(data));
      setToken({
        accessToken: loginData.accessToken,
        refreshToken: loginData.refreshToken ?? "",
        expires: new Date(Date.now() + loginData.expiresIn * 1000),
        username: loginData.username,
        roles: []
      } as DataInfo<Date>);
      // 强制改密闭环（T-FE-046）：LoginResp 的 userId（自助改密请求入参）与
      // forceResetPwd（路由守卫阻断标记）随登录写入 userKey——与登录主体绑定、
      // 跨标签共享（sessionStorage 每标签独立不合格）；改密成功由页面调
      // clearForceResetPwdFlag 置 false，登出 removeToken 整体清除
      const stored = storageLocal().getItem<DataInfo<number>>(userKey) ?? {
        refreshToken: "",
        expires: 0
      };
      storageLocal().setItem(userKey, {
        ...stored,
        userId: loginData.userId,
        forceResetPwd: loginData.forceResetPwd ?? false
      });
      try {
        await this.refreshUserMenu();
      } catch (err) {
        const status = (err as { response?: { status?: number } })?.response
          ?.status;
        if (status === 401) {
          // 会话已失效（响应拦截器已 logOut 跳登录页），登录不按成功收尾
          throw err;
        }
        // 不阻断登录流程；前端可在路由守卫层做兜底（如重定向到错误页）
        console.warn(
          "[loginByUsername] failed to load /api/access/auth/user-menu",
          err
        );
      }
      return loginData;
    },
    /**
     * 拉取用户菜单与权限（v1.4 双轨并行的下发入口）。
     * <p>
     * 独立 action 暴露便于：① 登录时调用；② 权限变更回调时手动重拉。
     * 成功后同步写入 Pinia state 与 localStorage（保持 setToken 的 userKey 一致语义）。
     * <p>
     * 响应壳为 R<T>（code=200 为成功）；通过 `unwrap` 解包，
     * 非 200 / 网络异常会抛出 Error，由调用方 try/catch（loginByUsername 已接住降级，仅 console.warn 不阻断）。
     * <p>
     * 成败同步维护 `menuLoadFailed`（T-FE-049）：调用方据它与 menus.length 区分
     * 「加载失败」（占位可重试）与「拉取成功但账号无菜单」（占位提示联系管理员）两态。
     * <p>
     * 会话代际守卫（Q-016 收口，2026-09-20 拍板）：发起时记当前令牌指纹，响应/失败
     * 回写前分别比对——
     * <ul>
     *   <li>成功回写：当前仍存在且令牌即本会话才写（会话已换〔登出重登〕或已终结
     *       〔getToken 空，如 401 分支/主动登出已清理〕都不回写——终结后 200 晚到
     *       曾把已清的 Pinia/userKey 重建，codex sol 外评 P2 收口）；</li>
     *   <li>失败丢弃：仅存在另一活会话令牌才吞（新会话无关的失败不污染）——会话已
     *       终结照常置位/上抛（claude 外评 P3 处置：吞掉会话终结型 401 会使手动刷新
     *       假成功、loginByUsername 的 401 硬化分支不可达）。</li>
     * </ul>
     */
    async refreshUserMenu() {
      const fingerprint = getToken()?.accessToken;
      const sessionAlive = () => {
        const current = getToken()?.accessToken;
        return current != null && current === fingerprint;
      };
      const sessionReplaced = () => {
        const current = getToken()?.accessToken;
        return current != null && current !== fingerprint;
      };
      try {
        const resp = await getUserMenu();
        const { menus, roles, permissions } = unwrap(resp);
        if (!sessionAlive()) return;
        this.menuLoadFailed = false;
        this.SET_MENUS(menus ?? []);
        this.SET_ROLES(roles ?? []);
        this.SET_PERMS(permissions ?? []);
        // 与 setToken 写入 userKey 的 schema 对齐，刷新页面时仍可从 storage 恢复
        const stored =
          storageLocal().getItem<DataInfo<Date | number>>(userKey) ??
          ({} as any);
        storageLocal().setItem(userKey, {
          ...stored,
          roles: roles ?? [],
          permissions: permissions ?? []
        });
      } catch (err) {
        if (sessionReplaced()) return;
        this.menuLoadFailed = true;
        throw err;
      }
    },
    /**
     * 登出（T-FE-045 真注销；Q-016 收口后注销改 fire-and-forget，2026-09-20 拍板）。
     * <p>
     * 1. 持有令牌时先发出 `POST /api/access/auth/logout` 注销服务端会话（显式携当前 token：
     *    本地 `expires` 已到期路径触发的登出，令牌在服务端可能仍存活，注销不能空转）。
     *    注销请求 fire-and-forget：发出即不等完成——后端黑洞时本地清理与跳转不再被
     *    推迟（原 await 形态最长挂 10s），窗口内新登录的凭据也不再被旧清理链清除；
     *    失败仅 console.warn 不弹错（浏览器凭证已清，残留服务端会话按 Sa-Token TTL 过期）。
     * 2. 本地清理无条件立即执行：清 Pinia → removeToken → multiTags 重置 → resetRouter → 跳 /login。
     *    登出完成后重复触发时 getToken() 已无令牌，不再发请求，仅幂等清理+跳转。
     * 3. logoutInFlight 标记在 fire-and-forget 后无实际拦截窗口（函数体无 await 挂起点，
     *    同步栈内执行完毕即复位，恒不命中）——保留是防御未来重新引入 await 点时的重入；
     *    重复触发（连点/拦截器程序化调用）的实际防护由 getToken() 无令牌幂等分支承担。
     */
    async logOut() {
      if (logoutInFlight) return;
      logoutInFlight = true;
      try {
        const token = getToken();
        if (token?.accessToken) {
          logout(formatToken(token.accessToken)).catch(err => {
            console.warn("[logOut] 服务端注销失败（不阻断本地清理）", err);
          });
        }
        this.username = "";
        this.roles = [];
        this.permissions = [];
        this.menus = [];
        this.menuLoadFailed = false;
        removeToken();
        useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
        resetRouter();
        router.push("/login");
      } finally {
        logoutInFlight = false;
      }
    }
  }
});

export function useUserStoreHook() {
  return useUserStore(store);
}
