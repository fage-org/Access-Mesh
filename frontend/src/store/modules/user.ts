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
  type UserResult,
  type RefreshTokenResult,
  getLogin,
  refreshTokenApi
} from "@/api/user";
import { getUserMenu, type UserMenuRoute } from "@/api/auth";
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
     * 登入。
     * <p>
     * 流程（v1.4 双轨并行）：
     *  1. 调 `/login` 拿 token + 基础用户信息（`setToken` 写 cookie）
     *  2. 调 `/auth/user-menu` 拿 menus + roles + permissions，写 Pinia + localStorage
     *  3. 任一步失败：第 1 步失败整体 reject；第 2 步失败仅记录，仍 resolve（避免登录被菜单接口卡住）
     */
    async loginByUsername(data) {
      return new Promise<UserResult>((resolve, reject) => {
        getLogin(data)
          .then(loginResp => {
            if (!loginResp?.success) {
              resolve(loginResp);
              return;
            }
            // 第 1 步：写入 token + 基础信息
            setToken(loginResp.data);
            // 第 2 步：拉取菜单与权限
            this.refreshUserMenu()
              .catch(err => {
                // 不阻断登录流程；前端可在路由守卫层做兜底（如重定向到错误页）
                console.warn(
                  "[loginByUsername] failed to load /auth/user-menu",
                  err
                );
              })
              .finally(() => resolve(loginResp));
          })
          .catch(error => {
            reject(error);
          });
      });
    },
    /**
     * 拉取用户菜单与权限（v1.4 双轨并行的下发入口）。
     * <p>
     * 独立 action 暴露便于：① 登录时调用；② token 刷新后或权限变更回调时手动重拉。
     * 成功后同步写入 Pinia state 与 localStorage（保持 setToken 的 userKey 一致语义）。
     * <p>
     * 后端响应壳为 PermResult<T>（code=200 为成功）；通过 `unwrap` 解包，
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
    },
    /** 刷新`token` */
    async handRefreshToken(data) {
      return new Promise<RefreshTokenResult>((resolve, reject) => {
        refreshTokenApi(data)
          .then(data => {
            if (data) {
              setToken(data.data);
              resolve(data);
            }
          })
          .catch(error => {
            reject(error);
          });
      });
    }
  }
});

export function useUserStoreHook() {
  return useUserStore(store);
}
