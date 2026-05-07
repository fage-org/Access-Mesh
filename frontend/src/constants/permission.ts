/**
 * 权限码常量定义
 * 格式: {模块}:{资源}:{操作}
 */

// ========== 用户管理权限码 ==========

/** 用户管理模块 */
export const SYS_USER_VIEW = "sys:user:view";
export const SYS_USER_CREATE = "sys:user:create";
export const SYS_USER_UPDATE = "sys:user:update";
export const SYS_USER_DELETE = "sys:user:delete";
export const SYS_USER_ENABLE = "sys:user:enable";
export const SYS_USER_RESET_PASSWORD = "sys:user:reset-password";
export const SYS_USER_ASSIGN_ROLE = "sys:user:assign-role";
export const SYS_USER_CONFIG_ORG = "sys:user:config-org";

// ========== 组织管理权限码 ==========

export const SYS_ORG_VIEW = "sys:org:view";
export const SYS_ORG_CREATE = "sys:org:create";
export const SYS_ORG_UPDATE = "sys:org:update";
export const SYS_ORG_DELETE = "sys:org:delete";

// ========== 菜单管理权限码 ==========

export const SYS_MENU_VIEW = "sys:menu:view";
export const SYS_MENU_CREATE = "sys:menu:create";
export const SYS_MENU_UPDATE = "sys:menu:update";
export const SYS_MENU_DELETE = "sys:menu:delete";

// ========== 角色管理权限码 ==========

export const SYS_ROLE_VIEW = "sys:role:view";
export const SYS_ROLE_CREATE = "sys:role:create";
export const SYS_ROLE_UPDATE = "sys:role:update";
export const SYS_ROLE_DELETE = "sys:role:delete";
export const SYS_ROLE_ASSIGN_PERM = "sys:role:assign-perm";

// ========== 权限中心权限码 ==========

export const PERM_RESOURCE_VIEW = "perm:resource:view";
export const PERM_RESOURCE_CREATE = "perm:resource:create";
export const PERM_RESOURCE_UPDATE = "perm:resource:update";
export const PERM_RESOURCE_DELETE = "perm:resource:delete";

export const PERM_SERVICE_VIEW = "perm:service:view";
export const PERM_SERVICE_CREATE = "perm:service:create";
export const PERM_SERVICE_UPDATE = "perm:service:update";
export const PERM_SERVICE_DELETE = "perm:service:delete";
export const PERM_SERVICE_SYNC = "perm:service:sync";

export const PERM_VIEW = "perm:view";
export const PERM_EXPLAIN = "perm:explain";

// ========== 权限码映射对象(便于批量使用) ==========

export const PERM_CODES = {
  // 用户管理
  SYS_USER_VIEW,
  SYS_USER_CREATE,
  SYS_USER_UPDATE,
  SYS_USER_DELETE,
  SYS_USER_ENABLE,
  SYS_USER_RESET_PASSWORD,
  SYS_USER_ASSIGN_ROLE,
  SYS_USER_CONFIG_ORG,
  // 组织管理
  SYS_ORG_VIEW,
  SYS_ORG_CREATE,
  SYS_ORG_UPDATE,
  SYS_ORG_DELETE,
  // 菜单管理
  SYS_MENU_VIEW,
  SYS_MENU_CREATE,
  SYS_MENU_UPDATE,
  SYS_MENU_DELETE,
  // 角色管理
  SYS_ROLE_VIEW,
  SYS_ROLE_CREATE,
  SYS_ROLE_UPDATE,
  SYS_ROLE_DELETE,
  SYS_ROLE_ASSIGN_PERM,
  // 权限中心
  PERM_RESOURCE_VIEW,
  PERM_RESOURCE_CREATE,
  PERM_RESOURCE_UPDATE,
  PERM_RESOURCE_DELETE,
  PERM_SERVICE_VIEW,
  PERM_SERVICE_CREATE,
  PERM_SERVICE_UPDATE,
  PERM_SERVICE_DELETE,
  PERM_SERVICE_SYNC,
  PERM_VIEW,
  PERM_EXPLAIN
};
