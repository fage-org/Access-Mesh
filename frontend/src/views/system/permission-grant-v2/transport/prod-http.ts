/**
 * V2 transport prod HTTP 实现。
 *
 * 复用 @/api/permission-grant 的 5 个 role-resource-permission/* 函数（走标准
 * http client 打真实后端 canonical URL）。生产/staging 无 vite-plugin-fake-server，
 * http client 直连后端；adapt 逻辑（scopeAll/scopeMode 双字段容错、domainCode
 * 补齐、capability 防御 default）由 @/api/permission-grant 统一承载，V2 不重复。
 */
import {
  getRolePermissionList,
  saveRolePermission,
  getChildPermissions,
  addChildPermission,
  removeChildPermission
} from "@/api/permission-grant";
import type { V2GrantTransport } from "./types";

export function createProdHttpTransport(): V2GrantTransport {
  return {
    getRolePermissionList,
    saveRolePermission,
    getChildPermissions,
    addChildPermission,
    removeChildPermission
  };
}
