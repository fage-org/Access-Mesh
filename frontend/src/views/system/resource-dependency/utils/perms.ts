import { PERMISSION_CODE } from "@/constants/access";
/** 依赖页面只读门禁；服务发布不使用用户权限码。 */
export const RESOURCE_DEPENDENCY_PERMS = {
  RESOURCE_DEPENDENCY_VIEW: PERMISSION_CODE.DEPENDENCY_VIEW
} as const;
export const RESOURCE_DEPENDENCY_PERM_LIST = Object.values(
  RESOURCE_DEPENDENCY_PERMS
);

/** 只读角色与路由共享同一 VIEW 权限集合。 */
export const RESOURCE_DEPENDENCY_VIEW_PERMS = [
  RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_VIEW
] as const;
