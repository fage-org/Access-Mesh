import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 菜单树节点 */
export interface MenuNode {
  id: number;
  parentId?: number;
  name: string;
  type: number; // 0=目录, 1=菜单, 2=按钮
  path?: string;
  component?: string;
  permCode?: string;
  icon?: string;
  sort: number;
  visible: number; // 0=隐藏, 1=显示
  status: number; // 0=停用, 1=启用
  createdAt?: string;
  updatedAt?: string;
  children?: Array<MenuNode>;
}

/** 菜单查询 */
export interface MenuQuery {
  name?: string;
  type?: number;
  status?: number;
}

/** 创建菜单请求 */
export interface MenuCreateRequest {
  parentId?: number;
  name: string;
  type: number;
  path?: string;
  component?: string;
  permCode?: string;
  icon?: string;
  sort?: number;
  visible?: number;
  status?: number;
}

/** 更新菜单请求 */
export interface MenuUpdateRequest {
  id: number;
  parentId?: number;
  name?: string;
  type?: number;
  path?: string;
  component?: string;
  permCode?: string;
  icon?: string;
  sort?: number;
  visible?: number;
  status?: number;
}

/** 菜单树响应 */
export interface MenuTreeResult {
  success: boolean;
  data: Array<MenuNode>;
}

/** 菜单详情响应 */
export interface MenuDetailResult {
  success: boolean;
  data: MenuNode;
}

/** 菜单操作响应 */
export interface MenuActionResult {
  success: boolean;
  data?: number;
}

// ========== API 函数 ==========

/** 获取菜单树 */
export const getMenuTree = (data?: MenuQuery) => {
  return http.request<MenuTreeResult>("post", "/admin/api/menu/tree", {
    data: data || {}
  });
};

/** 获取菜单详情 */
export const getMenuDetail = (data: { id: number }) => {
  return http.request<MenuDetailResult>("post", "/admin/api/menu/detail", {
    data
  });
};

/** 创建菜单 */
export const createMenu = (data: MenuCreateRequest) => {
  return http.request<MenuActionResult>("post", "/admin/api/menu/create", {
    data
  });
};

/** 更新菜单 */
export const updateMenu = (data: MenuUpdateRequest) => {
  return http.request<MenuActionResult>("post", "/admin/api/menu/update", {
    data
  });
};

/** 删除菜单 */
export const deleteMenu = (data: { id: number }) => {
  return http.request<MenuActionResult>("post", "/admin/api/menu/delete", {
    data
  });
};
