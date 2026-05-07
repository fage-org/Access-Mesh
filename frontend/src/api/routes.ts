import { http } from "@/utils/http";

/**
 * 用户菜单响应类型
 * 与后端 UserMenuResp.java 对应
 */
export interface UserMenuResp {
  menus: MenuRouteItem[];
  roles: string[];
  permissions: string[];
}

/**
 * 菜单路由项
 */
export interface MenuRouteItem {
  path: string;
  name: string;
  component: string | null;
  redirect: string | null;
  meta: MetaInfo;
  children: MenuRouteItem[] | null;
}

/**
 * 路由元信息
 */
export interface MetaInfo {
  title: string;
  icon: string | null;
  rank: number | null;
  showLink: boolean | null;
  keepAlive: boolean | null;
  frameSrc: string | null;
  roles: string[] | null;
  auths: string[] | null;
}

type Result = {
  success: boolean;
  data: UserMenuResp;
};

/**
 * 获取用户动态路由菜单
 * 调用后端 /auth/user-menu API
 */
export const getAsyncRoutes = () => {
  return http.request<Result>("post", "/auth/user-menu");
};
