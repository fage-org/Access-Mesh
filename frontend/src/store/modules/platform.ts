import { assertPlatformSessionCurrent } from "@/utils/platform-session";
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
    clearLocalSessionState() {
      this.account = null;
    },
    async login(data: Parameters<typeof platformLogin>[0]) {
      const result = await platformLogin(data);
      setPlatformSession({
        accessToken: result.accessToken,
        expires: Date.now() + result.expiresIn * 1000,
        account: result.account
      });
      this.account = result.account;
      return result.account;
    },
    async refresh() {
      const session = getPlatformSession();
      const account = await platformMe();
      if (session?.accessToken !== getPlatformSession()?.accessToken) return;
      setPlatformSession({ ...session, account });
      this.account = account;
      if (account.forceResetPwd)
        await router.replace("/platform/change-password");
    },
    logout() {
      assertPlatformSessionCurrent();
      const session = getPlatformSession();
      if (session)
        void platformLogout(formatToken(session.accessToken)).catch(() =>
          console.warn("平台服务端注销失败，本地凭据已清除")
        );
      removePlatformSession();
      this.clearLocalSessionState();
      void router.push("/platform/login");
    }
  }
});
export function usePlatformStoreHook() {
  return usePlatformStore(store);
}
