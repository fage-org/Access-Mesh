import { describe, expect, it } from "vitest";
import type { UserRoleItem } from "@/api/user-manage";
import { roleRelationKey, withoutRoleRelation } from "./roleRelations";

const direct: UserRoleItem = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "report-reader",
  roleName: "报表阅读",
  roleTypeLabel: "基础角色",
  targetType: "BASIC_ROLE",
  relationId: null,
  relationOrgName: null,
  validFrom: null,
  validTo: null
};
const related = { ...direct, relationId: 200, validTo: "2090-01-01T00:00:00" };

describe("功能角色独立关系", () => {
  it("同角色多关系在列表中具有不同身份", () => {
    expect(roleRelationKey(direct)).not.toBe(roleRelationKey(related));
  });
  it.each([
    [direct, related],
    [related, direct]
  ])("只移除所选关系并保留另一个来源", (removed, kept) => {
    expect(withoutRoleRelation([direct, related], removed)).toEqual([kept]);
  });
});
