import { http } from "@/utils/http";
import { unwrap, type PermResult } from "./_envelope";

// ========== 真实登录链路契约（T-FE-041，对齐 AdminAuthController） ==========

/**
 * 首期固定租户：bootstrap（T-ACCESS-020）预置的固定租户 "1"。
 * 多租户选择器为 Phase 3 范围，届时再引入选择 UI。
 */
export const FIXED_TENANT_ID = "1";

/**
 * 首期固定客户端：oauth2_client 种子已有 admin-web（grant_types 含 password）。
 */
export const FIXED_CLIENT_ID = "admin-web";

/** `/auth/captcha` 响应：验证码图片（base64，含 data:image/png;base64, 前缀）+ 一次性 ID */
export type CaptchaResp = {
  captchaId: string;
  image: string;
};

/**
 * `/auth/login` 请求体（对齐后端 LoginReq）。
 * tenantId/clientId 由 api 层固定注入，页面只收集账号/密码/验证码。
 */
export type LoginReq = {
  tenantId: string;
  username: string;
  password: string;
  captchaId: string;
  captchaCode: string;
  clientId: string;
};

/**
 * `/auth/login` 响应（对齐后端 LoginResp）。
 * - `expiresIn` 为秒数，调用方需转换为绝对时间供 `setToken` 使用
 * - 普通 Sa-Token 会话 `refreshToken` 为 null——不接 OAuth2 刷新令牌
 */
export type LoginResp = {
  accessToken: string;
  refreshToken: string | null;
  expiresIn: number;
  tokenType: string;
  userId: number;
  username: string;
  tenantId: number;
  forceResetPwd: boolean;
};

/** 登录页收集的表单字段（api 层补齐 tenantId/clientId 后即为 LoginReq） */
export type LoginFormData = Pick<
  LoginReq,
  "username" | "password" | "captchaId" | "captchaCode"
>;

/**
 * 获取登录验证码（POST /auth/captcha，无参）。
 * <p>
 * 后端运行时强制校验验证码（无开关），页面加载与登录失败后均需调用刷新；
 * 验证码 5 分钟有效且一次性消费。
 */
export const getCaptcha = async (): Promise<CaptchaResp> => {
  const res = await http.request<PermResult<CaptchaResp>>(
    "post",
    "/auth/captcha"
  );
  return unwrap(res);
};

/**
 * 账号密码登录（POST /auth/login）。
 * <p>
 * 保留 PermResult 信封返回，由 store 通过 `unwrap` 解包以走统一异常路径
 * （业务失败 HTTP 200 + code≠200，经 unwrap 抛 RequestError）。
 */
export const login = (data: LoginFormData): Promise<PermResult<LoginResp>> => {
  return http.request<PermResult<LoginResp>>("post", "/auth/login", {
    // 固定 tenantId/clientId 放在展开之后获得最终覆盖权（结构化类型下多余字段无法篡改）
    data: {
      ...data,
      tenantId: FIXED_TENANT_ID,
      clientId: FIXED_CLIENT_ID
    } satisfies LoginReq
  });
};

// ========== 用户菜单（v1.4 双轨并行） ==========

/**
 * `/auth/user-menu` 响应数据（v1.4 双轨并行）。
 * - `menus`：菜单可见性轨道（DIR/MENU 树）
 * - `roles`：用户角色（pure-admin-thin 模板按角色名 string 处理）
 * - `permissions`：按钮权限轨道，形如 `"ORG:CREATE_POSITION"` 的 perm 串
 *
 * 注意：本文件返回 `Promise<PermResult<UserMenuData>>`（保留 PermResult 信封），
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
 * 响应壳为 PermResult<T>（code=200 为成功），由调用方 store.refreshUserMenu
 * 通过 `unwrap` 解包并以 try/catch 处理失败。
 */
export const getUserMenu = () => {
  return http.request<PermResult<UserMenuData>>("post", "/auth/user-menu");
};
