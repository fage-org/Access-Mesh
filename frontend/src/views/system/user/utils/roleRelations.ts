import type { UserRoleItem } from "@/api/user-manage";

/** 同一角色的不同关系是独立记录；JSON 元组避免业务键中的分隔符碰撞。 */
export function roleRelationKey(role: UserRoleItem): string {
  return JSON.stringify([
    role.roleTypeCode,
    role.roleExternalId,
    role.relationId
  ]);
}

export function withoutRoleRelation(
  roles: UserRoleItem[],
  removed: UserRoleItem
): UserRoleItem[] {
  const key = roleRelationKey(removed);
  return roles.filter(role => roleRelationKey(role) !== key);
}
