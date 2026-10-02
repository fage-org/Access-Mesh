import { ROLE_TYPE_CODE, type RoleTypeCode } from "@/api/role-manage";

/**
 * 用户详情面板「分配功能角色」候选类型（T-PERM-097 外评处置，2026-10-02）。
 * GROUP_ROLE 为绑定拒绝类型（后端 assign/batch-assign/sync/full-sync 四入口
 * 20022，契约 §10.5）：组角色不能配权限，候选装载它只会产出恒失败选项，
 * 自候选收窄。PERSONAL 保留可分配——勿套用角色管理页 MANAGEABLE_ROLE_TYPES
 * （仅 BASIC_ROLE，会误删 PERSONAL）。存量展示/撤销不受影响：面板
 * otherRoles 展示列表与 revoke 通道仍接纳 GROUP_ROLE 存量持有行（清理通道不动）。
 */
export const ASSIGNABLE_ROLE_TYPE_CODES: RoleTypeCode[] = [
  ROLE_TYPE_CODE.BASIC_ROLE,
  ROLE_TYPE_CODE.PERSONAL
];
