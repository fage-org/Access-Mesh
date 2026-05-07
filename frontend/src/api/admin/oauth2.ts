import Axios from "axios";
import { http } from "@/utils/http";

// ========== 类型定义 ==========

/**
 * OAuth2 授权码请求参数
 * GET /oauth2/authorize
 */
export interface AuthorizationCodeRequest {
  response_type: "code";
  client_id: string;
  redirect_uri: string;
  scope?: string;
  state: string;
  code_challenge: string;
  code_challenge_method: "S256";
  tenant_id: number;
}

/**
 * OAuth2 Token 交换请求参数
 * POST /oauth2/token
 */
export interface TokenExchangeRequest {
  grant_type: "authorization_code";
  code: string;
  redirect_uri: string;
  client_id: string;
  code_verifier: string;
}

/**
 * OAuth2 Token 响应
 */
export interface OAuth2TokenResult {
  access_token: string;
  refresh_token: string;
  expires_in: number; // 秒数
  token_type: "Bearer";
  scope?: string;
  user_info?: {
    userId: number;
    username: string;
    nickname: string;
    avatar: string;
    roles: Array<string>;
    permissions: Array<string>;
  };
}

/**
 * OAuth2 Token 响应包装
 */
export interface OAuth2TokenResponse {
  code: number;
  message: string;
  data: OAuth2TokenResult;
}

/**
 * 验证码响应
 */
export interface CaptchaResult {
  success: boolean;
  data: {
    captchaId: string;
    captchaImage: string; // Base64 图片
  };
}

// ========== API 函数 ==========

/**
 * 获取验证码
 */
export const getCaptcha = (tenantId: number) => {
  return http.request<CaptchaResult>("post", "/admin/api/auth/captcha", {
    data: { tenantId }
  });
};

/**
 * 请求授权码(GET 请求,直接重定向)
 * 前端不直接调用,通过 window.location.href 触发
 */
export function buildAuthorizationUrl(
  params: AuthorizationCodeRequest
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  const query = new URLSearchParams({
    response_type: params.response_type,
    client_id: params.client_id,
    redirect_uri: params.redirect_uri,
    state: params.state,
    code_challenge: params.code_challenge,
    code_challenge_method: params.code_challenge_method,
    tenant_id: String(params.tenant_id)
  });

  if (params.scope) {
    query.append("scope", params.scope);
  }

  return `${baseUrl}/oauth2/authorize?${query.toString()}`;
}

/**
 * 交换 Token(POST application/x-www-form-urlencoded)
 * 注意: 不使用 http.request,直接 axios.post
 */
export const exchangeToken = async (params: TokenExchangeRequest) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  const formData = new URLSearchParams({
    grant_type: params.grant_type,
    code: params.code,
    redirect_uri: params.redirect_uri,
    client_id: params.client_id,
    code_verifier: params.code_verifier
  });

  const response = await Axios.post<OAuth2TokenResponse>(
    `${baseUrl}/oauth2/token`,
    formData,
    {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      }
    }
  );

  return response.data;
};

/**
 * 刷新 Token(POST JSON)
 * 后端端点: POST /auth/oauth2/refresh
 * PKCE 公开客户端: 仅需要 clientId + refreshToken (无 clientSecret)
 * 注意: refreshToken 为一次性使用,刷新后返回新的 refreshToken
 */
export const refreshToken = async (refreshTokenValue: string) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;

  const response = await Axios.post<OAuth2TokenResponse>(
    `${baseUrl}/auth/oauth2/refresh`,
    {
      clientId: import.meta.env.VITE_OAUTH2_CLIENT_ID,
      refreshToken: refreshTokenValue
    },
    {
      headers: {
        "Content-Type": "application/json"
      }
    }
  );

  return response.data;
};
