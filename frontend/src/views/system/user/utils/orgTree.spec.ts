import { describe, it, expect } from "vitest";
import { getParentOrgLabel } from "./orgTree";

describe("父组织名称不依赖过滤后的树", () => {
  it.each([
    { parentOrgId: 2, parentOrgName: "市场部", expected: "市场部" },
    { parentOrgId: 21, parentOrgName: "品牌组", expected: "品牌组" },
    { parentOrgId: null, parentOrgName: null, expected: "根组织" },
    { parentOrgId: 0, parentOrgName: null, expected: "根组织" },
    { parentOrgId: 999, parentOrgName: null, expected: "上级不可见" },
    { parentOrgId: 999, parentOrgName: "", expected: "上级不可见" }
  ])("$parentOrgId → $expected", ({ parentOrgId, parentOrgName, expected }) => {
    expect(getParentOrgLabel({ parentOrgId, parentOrgName })).toBe(expected);
  });
});
