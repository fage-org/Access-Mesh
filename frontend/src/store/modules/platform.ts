import { defineStore } from "pinia";
import { store } from "@/store";
import { router } from "@/router";
import { platformLogin, platformLogout, platformMe } from "@/api/platform";
import {
  getPlatformSession,
  setPlatformSession,
  removePlatformSession,
  formatToken
} from "@/utils/auth";

export const usePlatformStore = defineStore("platform-operator", {
  state: () => ({ account: getPlatformSession()?.account ?? null }),
  actions: {
    async login(data: Parameters<typeof platformLogin>[0]) {
      const result = await platformLogin(data);
      this.account = result.account;
      setPlatformSession({
        accessToken: result.accessToken,
        expires: Date.now() + result.expiresIn * 1000,
        account: result.account
      });
      return result.account;
    },
    async refresh() {
      const session = getPlatformSession();
      const account = await platformMe();
      if (session?.accessToken !== getPlatformSession()?.accessToken) return;
      this.account = account;
      setPlatformSession({ ...session, account });
      if (account.forceResetPwd)
        await router.replace("/platform/change-password");
    },
    logout() {
      const session = getPlatformSession();
      if (session)
        void platformLogout(formatToken(session.accessToken)).catch(() =>
          console.warn("平台服务端注销失败，本地凭据已清除")
        );
      removePlatformSession();
      this.account = null;
      void router.push("/platform/login");
    }
  }
});
export function usePlatformStoreHook() {
  return usePlatformStore(store);
}
