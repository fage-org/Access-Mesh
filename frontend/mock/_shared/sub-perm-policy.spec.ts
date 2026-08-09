import { describe, expect, it } from "vitest";
import {
  mockSubPermissionAllows,
  parseMockSubPermissionPolicy,
  type MockSubPermissionConfig
} from "./sub-perm-policy";

const existing = (extra: string | null): MockSubPermissionConfig => ({
  exists: true,
  extra
});

describe("parseMockSubPermissionPolicy", () => {
  it.each([
    [undefined, "CONFIG_MISSING"],
    [{ exists: false, extra: null }, "CONFIG_MISSING"],
    [existing("  "), "CONFIG_EMPTY"],
    [existing("{"), "CONFIG_INVALID"],
    [existing('[{"parent_type":"","child_types":[]}]'), "CONFIG_INVALID"],
    [
      existing('[{"parent_type":"MENU","child_types":["BUTTON"]}]'),
      "PARENT_NOT_CONFIGURED"
    ],
    [existing('[{"parent_type":"API","child_types":[]}]'), "CHILD_TYPES_EMPTY"]
  ] as const)("ALLOW_NONE reason=%s → %s", (config, reason) => {
    expect(parseMockSubPermissionPolicy(config, "API")).toEqual({
      mode: "ALLOW_NONE",
      reason,
      allowedChildResourceTypeCodes: []
    });
  });

  it("支持顶层与嵌套通配", () => {
    expect(parseMockSubPermissionPolicy(existing("*"), "DATA").mode).toBe(
      "ALLOW_ALL"
    );
    expect(
      parseMockSubPermissionPolicy(
        existing('[{"parent_type":"DATA","child_types":["*"]}]'),
        "data"
      ).mode
    ).toBe("ALLOW_ALL");
  });

  it("多匹配项按大小写不敏感并集去重，保留首次配置原文", () => {
    const policy = parseMockSubPermissionPolicy(
      existing(
        '[{"parent_type":"MENU","child_types":["BUTTON","data"]},{"parent_type":"menu","child_types":["button","REPORT"]}]'
      ),
      "MENU"
    );
    expect(policy).toEqual({
      mode: "ALLOW_LIST",
      reason: null,
      allowedChildResourceTypeCodes: ["BUTTON", "data", "REPORT"]
    });
    expect(mockSubPermissionAllows(policy, "button")).toBe(true);
    expect(mockSubPermissionAllows(policy, "API")).toBe(false);
  });
});
