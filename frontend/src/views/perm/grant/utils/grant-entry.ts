/**
 * 授予入口按钮文案（T-FE-055，2026-09-20 用户拍板）。
 *
 * 三处跳转 /perm/grant 的入口按钮（角色管理页详情卡 / 组织信息卡 / 岗位行操作）
 * 门禁统一为 ROLE:VIEW（授予页矩阵查看门禁，permission-grant.md §10 轨道 2）——
 * 仅持 ROLE:VIEW 的用户可进入授予页但 capability='view'（只读矩阵，无授权动作）。
 * 入口文案按是否另持 ROLE:MANAGE 二分，与授予页 capability 判定同源
 * （hook.ts canManage = hasPerms(PERMISSION_GRANT_PERMS.ROLE_MANAGE)），
 * 避免文案承诺用户做不到的事（「权限授予」对只读用户失真）。
 */
export function resolveGrantEntryLabel(canManage: boolean): string {
  return canManage ? "权限授予" : "查看权限";
}
