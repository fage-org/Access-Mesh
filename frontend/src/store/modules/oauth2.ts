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
    // 生成 state 参数(UUID v4 格式,使用 crypto.getRandomValues)
    generateState(): string {
      const array = new Uint8Array(16);
      crypto.getRandomValues(array);
      // UUID v4: 设置版本位和变体位
      array[6] = (array[6] & 0x0f) | 0x40; // 版本 4
      array[8] = (array[8] & 0x3f) | 0x80; // 变体 RFC 4122
      // 转换为 UUID 格式字符串
      const hex = Array.from(array)
        .map(b => b.toString(16).padStart(2, "0"))
        .join("");
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }
  }
});

export function useOAuth2StoreHook() {
  return useOAuth2Store(store);
}
