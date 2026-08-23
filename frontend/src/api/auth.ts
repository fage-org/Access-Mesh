import { http } from "@/utils/http";
import type { PermResult } from "./_envelope";

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
 * 真后端响应壳为 PermResult<T>（code=200 为成功），不能再依赖旧的 `success` 字段。
 * 本函数保留信封返回，调用方 store.refreshUserMenu 通过 `unwrap` 解包并以 try/catch 处理失败。
 *
 * @see docs/design/org-user-permission-contract.md v1.4
 */
export const getUserMenu = () => {
  return http.request<PermResult<UserMenuData>>("post", "/auth/user-menu");
};
