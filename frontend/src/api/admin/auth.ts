import { http } from "@/utils/http";

// ========== 类型定义 ==========

export interface LoginResult {
  success: boolean;
  data: {
    accessToken: string;
    refreshToken: string;
    expires: string; // 格式: "xxxx/xx/xx xx:xx:xx"
    userId: number;
    username: string;
    nickname: string;
    avatar: string;
    roles: Array<string>;
    permissions: Array<string>;
    tenantId: number;
  };
}

export interface RefreshTokenResult {
  success: boolean;
  data: {
    accessToken: string;
    refreshToken: string;
    expires: string;
  };
}

export interface LoginRequest {
  username: string;
  password: string;
  tenantId: number;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// ========== API 函数 ==========

/** 登录 */
export const login = (data: LoginRequest) => {
  return http.request<LoginResult>("post", "/admin/api/auth/login", { data });
};

/** 刷新 Token */
export const refreshToken = (data: RefreshTokenRequest) => {
  return http.request<RefreshTokenResult>(
    "post",
    "/admin/api/auth/refresh-token",
    { data }
  );
};

/** 登出 */
export const logout = () => {
  return http.request("post", "/admin/api/auth/logout", { data: {} });
};
