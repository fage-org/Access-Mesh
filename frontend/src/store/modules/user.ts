import { message } from "@/utils/message";
import { assertTenantSessionCurrent } from "@/utils/tenant-session";
import { oauthReturnTarget } from "@/views/oauth-consent/flow";
import { closeAllDialog } from "@/components/ReDialog/store";
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
  type BrowserLoginResp,
  type UserMenuRoute
} from "@/api/auth";
import { unwrap } from "@/api/_envelope";
import { useMultiTagsStoreHook } from "./multiTags";
import {
  type DataInfo,
  getTenantSession,
  setTenantSession,
  removeTenantSession,
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
    /** 会话门禁状态机（T-FE-056）：迁移唯一入口 refreshUserMenu，见 types.ts 字注视 */
    menuGateStatus: "uninitialized",
    // 是否勾选了登录页的免登录
    isRemembered: false,
    // 登录页的免登录存储几天，默认7天
    loginDay: 7
  }),
  actions: {
    /** Clear this tab's tenant UI only; shared credentials and platform state remain owned by their writers. */
    clearLocalSessionState() {
      this.avatar = "";
      this.nickname = "";
      this.username = "";
      this.roles = [];
      this.permissions = [];
      this.menus = [];
      this.menuLoadFailed = false;
      this.menuGateStatus = "uninitialized";
    },
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
     *  1. 调 `/api/access/auth/browser-login` 拿会话元数据（`unwrap` 解包：业务失败 HTTP 200 + code≠200 抛 RequestError）
     *  2. `expiresIn`（秒）转换为绝对时间后 `setTenantSession` 保存可读元数据（
     *     只保存非认证会话标记，不接 OAuth2 刷新令牌）；`roles: []` 占位使 `setTenantSession`
     *     写入登录用户名至 Pinia/localStorage（roles 由第 3 步填充）
     *  3. 调 `/api/access/auth/user-menu` 拿 menus + roles + permissions，写 Pinia + localStorage
     *  4. 失败语义：第 1 步失败整体 reject；第 3 步 HTTP 401（会话失效，拦截器已清会话
     *     回登录页）同样 reject——不能按登录成功处理；其余异常（网络等）仅记录，仍 resolve
     */
    async loginByUsername(data: LoginFormData): Promise<BrowserLoginResp> {
      const loginData = unwrap(await login(data));
      // Publish one complete identity snapshot; never merge a different tab's metadata into this login.
      setTenantSession({
        sessionId: loginData.sessionId,
        expires: new Date(Date.now() + loginData.expiresIn * 1000),
        username: loginData.username,
        userId: loginData.userId,
        tenantId: loginData.tenantId,
        forceResetPwd: loginData.forceResetPwd ?? false,
        roles: []
      } as DataInfo<Date>);
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
     * 成功后同步写入 Pinia state 与 localStorage（保持 setTenantSession 的 userKey 一致语义）。
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
     *       〔getTenantSession 空，如 401 分支/主动登出已清理〕都不回写——终结后 200 晚到
     *       曾把已清的 Pinia/userKey 重建，codex sol 外评 P2 收口）；</li>
     *   <li>失败丢弃：仅存在另一活会话令牌才吞（新会话无关的失败不污染）——会话已
     *       终结照常置位/上抛（claude 外评 P3 处置：吞掉会话终结型 401 会使手动刷新
     *       假成功、loginByUsername 的 401 硬化分支不可达）。</li>
     * </ul>
     */
    async refreshUserMenu() {
      const session = getTenantSession();
      const fingerprint = session?.sessionId;
      const sessionAlive = () => {
        const current = getTenantSession()?.sessionId;
        return current != null && current === fingerprint;
      };
      const sessionReplaced = () => {
        const current = getTenantSession()?.sessionId;
        return current != null && current !== fingerprint;
      };
      // 门禁状态机（T-FE-056）：仅首次（未 loaded）进入 loading——已 loaded 会话的
      // 刷新期间维持 loaded，守卫继续用旧 path 集判定不等待
      if (this.menuGateStatus !== "loaded") this.menuGateStatus = "loading";
      try {
        const resp = await getUserMenu();
        const { menus, roles, permissions } = unwrap(resp);
        if (!sessionAlive()) return;
        // 门禁状态机（T-FE-056）：成功即 loaded——零权限账号（menus 空集）同样 loaded
        this.menuGateStatus = "loaded";
        this.menuLoadFailed = false;
        this.SET_MENUS(menus ?? []);
        this.SET_ROLES(roles ?? []);
        this.SET_PERMS(permissions ?? []);
        // Keep capabilities attached to the snapshot that initiated this request.
        storageLocal().setItem(userKey, {
          ...session,
          roles: roles ?? [],
          permissions: permissions ?? []
        });
      } catch (err) {
        if (sessionReplaced()) return;
        // 门禁状态机（T-FE-056，状态迁移表）：failed 仅由首次加载失败产生（守卫
        // fail-open 放行）；已 loaded 会话的刷新失败维持 loaded——menuLoadFailed=true
        // 但 menus 保留旧值，守卫继续用旧 path 集判定（门禁不因刷新抖动静默失效）
        if (this.menuGateStatus !== "loaded") this.menuGateStatus = "failed";
        this.menuLoadFailed = true;
        throw err;
      }
    },
    /** Clear local UI immediately; only the original marker is used for the logout attempt.
     * Late responses never clear a newer session. Failure is surfaced without background compensation.
     */
    async logOut(returnTo?: unknown) {
      assertTenantSessionCurrent();
      if (logoutInFlight) return;
      logoutInFlight = true;
      try {
        const token = getTenantSession();
        if (token?.sessionId) {
          logout(token.sessionId).catch(() => {
            console.warn("[logOut] 原会话服务器注销尚未确认");
            message("原租户会话：服务器尚未确认退出", { type: "warning" });
          });
        }
        this.clearLocalSessionState();
        removeTenantSession();
        // 会话终结清全局编辑弹窗（T-FE-068）：ReDialog 挂 App 层（router-view 之外）、
        // dialogStore 为模块级全局——登出不清则旧上下文弹窗浮在登录页，重登后旧对象
        // 仍可被提交；closeAllDialog 直清不触发弹窗回调（终结处置不做保存类副作用）
        closeAllDialog();
        useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
        resetRouter();
        // 续接缺省继承（T-FE-068 拍板 A）：无参调用（响应 401 分支/本地过期短路/
        // initRouter 终结）读当前页 returnTo，与改密成功分支显式传参共用同一窄续接
        // 规则——经 oauthReturnTarget 白名单校验，仅本地 /oauth2/authorize 目标续接；
        // 常规页面 query 无 returnTo，行为不变
        const consentTarget = oauthReturnTarget(
          returnTo ?? router.currentRoute?.value?.query?.returnTo
        );
        router.push(
          consentTarget
            ? { path: "/login", query: { returnTo: consentTarget } }
            : "/login"
        );
      } finally {
        logoutInFlight = false;
      }
    }
  }
});

export function useUserStoreHook() {
  return useUserStore(store);
}
