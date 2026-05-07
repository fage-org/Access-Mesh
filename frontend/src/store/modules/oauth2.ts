import { defineStore } from "pinia";
import { store } from "../utils";
import { generateCodeVerifier, generateCodeChallenge } from "@/utils/pkce";

export interface OAuth2State {
  codeVerifier: string;
  codeChallenge: string;
  state: string; // CSRF 防护参数
  authorizationCode: string;
}

export const useOAuth2Store = defineStore("oauth2", {
  state: (): OAuth2State => ({
    codeVerifier: "",
    codeChallenge: "",
    state: "",
    authorizationCode: ""
  }),
  actions: {
    SET_CODE_VERIFIER(verifier: string) {
      this.codeVerifier = verifier;
    },
    SET_CODE_CHALLENGE(challenge: string) {
      this.codeChallenge = challenge;
    },
    SET_STATE(state: string) {
      this.state = state;
    },
    SET_AUTHORIZATION_CODE(code: string) {
      this.authorizationCode = code;
    },
    CLEAR() {
      this.codeVerifier = "";
      this.codeChallenge = "";
      this.state = "";
      this.authorizationCode = "";
    },
    // 初始化 OAuth2 参数
    async initOAuth2Params() {
      const verifier = generateCodeVerifier();
      const challenge = await generateCodeChallenge(verifier);
      const state = this.generateState();

      this.SET_CODE_VERIFIER(verifier);
      this.SET_CODE_CHALLENGE(challenge);
      this.SET_STATE(state);
    },
    // 生成 state 参数(UUID)
    generateState(): string {
      return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(
        /[xy]/g,
        function (c) {
          const r = (Math.random() * 16) | 0;
          const v = c === "x" ? r : (r & 0x3) | 0x8;
          return v.toString(16);
        }
      );
    }
  }
});

export function useOAuth2StoreHook() {
  return useOAuth2Store(store);
}
