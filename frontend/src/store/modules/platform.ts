import { message } from "@/utils/message";
import { assertPlatformSessionCurrent } from "@/utils/platform-session";
import { defineStore } from "pinia";
import { store } from "@/store";
import { router } from "@/router";
import { platformLogin, platformLogout, platformMe } from "@/api/platform";
import {
  getPlatformSession,
  setPlatformSession,
  removePlatformSession
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
        sessionId: result.sessionId,
        expires: Date.now() + result.expiresIn * 1000,
        account: result.account
      });
      this.account = result.account;
      return result.account;
    },
    async refresh() {
      const session = getPlatformSession();
      const account = await platformMe();
      if (session?.sessionId !== getPlatformSession()?.sessionId) return;
      setPlatformSession({ ...session, account });
      this.account = account;
      if (account.forceResetPwd)
        await router.replace("/platform/change-password");
    },
    logout() {
      assertPlatformSessionCurrent();
      const session = getPlatformSession();
      if (session)
        void platformLogout(session.sessionId).catch(() =>
          message("原平台会话：服务器尚未确认退出", { type: "warning" })
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
