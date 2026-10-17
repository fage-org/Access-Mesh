import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";

// ========== 真实登录链路契约（T-FE-041，对齐 AdminAuthController） ==========

/** `/api/access/auth/captcha` 响应：验证码图片（base64，含 data:image/png;base64, 前缀）+ 一次性 ID */
export type CaptchaResp = {
  captchaId: string;
  image: string;
};

/**
 * `/api/access/auth/browser-login` 请求体（对齐后端 LoginReq）。
 * tenantCode 由用户填写；普通登录不依赖 OAuth2 客户端。
 */
export type LoginReq = {
  tenantCode: string;
  username: string;
  password: string;
  captchaId: string;
  captchaCode: string;
  clientId?: string;
};

/** Browser metadata. sessionId binds CSRF and request context; it cannot authenticate alone. */
export type BrowserLoginResp = {
  sessionId: string;
  expiresIn: number;
  userId: number;
  username: string;
  tenantId: number;
  forceResetPwd: boolean;
};

/** 租户登录表单。 */
export type LoginFormData = Pick<
  LoginReq,
  "tenantCode" | "username" | "password" | "captchaId" | "captchaCode"
>;

/**
 * 获取登录验证码（POST /api/access/auth/captcha，无参）。
 * <p>
 * 后端运行时强制校验验证码（无开关），页面加载与登录失败后均需调用刷新；
 * 验证码 5 分钟有效且一次性消费。
 */
export const getCaptcha = async (): Promise<CaptchaResp> => {
  const res = await http.request<R<CaptchaResp>>(
    "post",
    "/api/access/auth/captcha"
  );
  return unwrap(res);
};

/**
 * 账号密码登录（POST /api/access/auth/browser-login）。
 * <p>
 * 保留 R 信封返回，由 store 通过 `unwrap` 解包以走统一异常路径
 * （业务失败 HTTP 200 + code≠200，经 unwrap 抛 RequestError）。
 */
export const login = (data: LoginFormData): Promise<R<BrowserLoginResp>> => {
  return http.request<R<BrowserLoginResp>>(
    "post",
    "/api/access/auth/browser-login",
    {
      data: {
        tenantCode: data.tenantCode,
        username: data.username,
        password: data.password,
        captchaId: data.captchaId,
        captchaCode: data.captchaCode
      } satisfies LoginReq
    }
  );
};

/** Attempt to revoke the original Cookie session; never retry after changing accounts. */
export const logout = async (sessionId: string): Promise<void> => {
  const res = await http.request<R<void>>(
    "post",
    "/api/access/auth/browser-logout",
    {
      data: {},
      headers: { "X-AM-Session": sessionId }
    }
  );
  unwrap(res);
};

// ========== 用户菜单（v1.4 双轨并行） ==========

/**
 * `/api/access/auth/user-menu` 响应数据（v1.4 双轨并行）。
 * - `menus`：菜单可见性轨道（DIR/MENU 树）
 * - `roles`：用户角色（pure-admin-thin 模板按角色名 string 处理）
 * - `permissions`：按钮权限轨道，形如 `"ORG:CREATE_POSITION"` 的 perm 串
 *
 * 注意：本文件返回 `Promise<R<UserMenuData>>`（保留 R 信封），
 * 由 store/user.ts 通过 `unwrap` 解包以触发 try/catch 抛错降级。
 */
export type UserMenuData = {
  menus: Array<UserMenuRoute>;
  roles: Array<string>;
  permissions: Array<string>;
};

/**
 * 菜单节点（路由形态）。后端 buildMenuTree 已转为前端友好结构。
 */
export type UserMenuRoute = {
  path: string;
  name?: string;
  component?: string;
  redirect?: string;
  meta?: {
    title?: string;
    icon?: string;
    rank?: number;
    auths?: Array<string>;
    [key: string]: any;
  };
  children?: Array<UserMenuRoute>;
};

/**
 * 登录后获取用户菜单 + 角色 + 按钮权限。
 * <p>
 * 响应壳为 R<T>（code=200 为成功），由调用方 store.refreshUserMenu
 * 通过 `unwrap` 解包并以 try/catch 处理失败。
 */
export const getUserMenu = () => {
  return http.request<R<UserMenuData>>("post", "/api/access/auth/user-menu");
};
