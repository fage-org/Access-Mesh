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
  type LoginResult,
  type RefreshTokenResult,
  type LoginRequest,
  login,
  refreshToken as refreshTokenApi,
  logout as logoutApi
} from "@/api/admin/auth";
import { useMultiTagsStoreHook } from "./multiTags";
import { type DataInfo, setToken, removeToken, userKey } from "@/utils/auth";
import { useTenantStoreHook } from "@/store/modules/tenant";
import { stopTokenRefreshScheduler } from "@/utils/http/tokenRefreshScheduler";

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
    /** 存储是否勾选了登录页的免登录 */
    SET_ISREMEMBERED(bool: boolean) {
      this.isRemembered = bool;
    },
    /** 设置登录页的免登录存储几天 */
    SET_LOGINDAY(value: number) {
      this.loginDay = Number(value);
    },
    /** 登入 */
    async loginByUsername(data: LoginRequest) {
      return new Promise<LoginResult>((resolve, reject) => {
        login(data)
          .then(res => {
            if (res?.success) {
              // 转换 expires 格式
              const tokenData = {
                ...res.data,
                expires: new Date(res.data.expires)
              };
              setToken(tokenData);

              // 更新租户状态
              useTenantStoreHook().SET_CURRENT_TENANT(res.data.tenantId);
            }
            resolve(res);
          })
          .catch(error => {
            reject(error);
          });
      });
    },
    /** 登出 */
    async logOut() {
      // 1. 停止定时刷新调度器
      stopTokenRefreshScheduler();

      // 2. 调用后端登出接口
      try {
        await logoutApi();
      } catch {
        // 后端登出失败不影响前端登出流程
        // 静默处理，避免阻塞
      }

      // 3. 清除前端 Token
      removeToken();

      // 4. 清除用户状态
      this.username = "";
      this.nickname = "";
      this.avatar = "";
      this.roles = [];
      this.permissions = [];

      // 5. 重置标签页和路由
      useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
      resetRouter();

      // 6. 跳转登录页
      router.push("/login");
    },
    /** 刷新`token` */
    async handRefreshToken(data: { refreshToken: string }) {
      return new Promise<RefreshTokenResult>((resolve, reject) => {
        refreshTokenApi(data)
          .then(res => {
            if (res?.success) {
              // 转换 expires 格式
              const tokenData = {
                ...res.data,
                expires: new Date(res.data.expires)
              };
              setToken(tokenData);
              resolve(res);
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
