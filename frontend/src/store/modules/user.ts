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
  getUserMenu,
  type LoginFormData,
  type LoginResp,
  type UserMenuRoute
} from "@/api/auth";
import { unwrap } from "@/api/_envelope";
import { useMultiTagsStoreHook } from "./multiTags";
import { type DataInfo, setToken, removeToken, userKey } from "@/utils/auth";

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
    /** 后端下发的菜单树（v1.4 双轨并行：菜单可见性轨道，登录后由 /auth/user-menu 填充） */
    menus: [],
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
     * 登入（T-FE-041 真实链路，对齐 PermResult 信封）。
     * <p>
     * 流程（v1.4 双轨并行）：
     *  1. 调 `/auth/login` 拿令牌（`unwrap` 解包：业务失败 HTTP 200 + code≠200 抛 RequestError）
     *  2. `expiresIn`（秒）转换为绝对时间后 `setToken` 写 cookie（普通 Sa-Token 会话
     *     `refreshToken` 为 null，不接 OAuth2 刷新令牌）；`roles: []` 占位使 `setToken`
     *     走传入分支——登录用户名立即落位 Pinia/localStorage（roles 由第 3 步填充）
     *  3. 调 `/auth/user-menu` 拿 menus + roles + permissions，写 Pinia + localStorage
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
        console.warn("[loginByUsername] failed to load /auth/user-menu", err);
      }
      return loginData;
    },
    /**
     * 拉取用户菜单与权限（v1.4 双轨并行的下发入口）。
     * <p>
     * 独立 action 暴露便于：① 登录时调用；② 权限变更回调时手动重拉。
     * 成功后同步写入 Pinia state 与 localStorage（保持 setToken 的 userKey 一致语义）。
     * <p>
     * 响应壳为 PermResult<T>（code=200 为成功）；通过 `unwrap` 解包，
     * 非 200 / 网络异常会抛出 Error，由调用方 try/catch（loginByUsername 已接住降级，仅 console.warn 不阻断）。
     */
    async refreshUserMenu() {
      const resp = await getUserMenu();
      const { menus, roles, permissions } = unwrap(resp);
      this.SET_MENUS(menus ?? []);
      this.SET_ROLES(roles ?? []);
      this.SET_PERMS(permissions ?? []);
      // 与 setToken 写入 userKey 的 schema 对齐，刷新页面时仍可从 storage 恢复
      const stored =
        storageLocal().getItem<DataInfo<Date | number>>(userKey) ?? ({} as any);
      storageLocal().setItem(userKey, {
        ...stored,
        roles: roles ?? [],
        permissions: permissions ?? []
      });
    },
    /** 前端登出（不调用接口） */
    logOut() {
      this.username = "";
      this.roles = [];
      this.permissions = [];
      this.menus = [];
      removeToken();
      useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
      resetRouter();
      router.push("/login");
    }
  }
});

export function useUserStoreHook() {
  return useUserStore(store);
}
