import { describe, expect, it } from "vitest";
import {
  resourceDeclaration,
  updateResourceDeclaration
} from "./resource-association";

describe("资源关系声明", () => {
  it.each([
    "bad json",
    "[]",
    '{"associationMode":null}',
    '{"associationMode":"FUTURE"}'
  ])("未知声明不可当普通资源：%s", extra => {
    expect(resourceDeclaration(extra).mode).toBe("UNKNOWN");
  });
  it("未声明保持普通类型，编辑一个字段保留所有者与来源", () => {
    expect(resourceDeclaration(null).mode).toBe("NONE");
    const before = {
      grantOriginRole: { roleTypeCode: "BASIC_ROLE", roleExternalId: "owner" },
      managedMode: "SYNC",
      syncSourceService: "example"
    };
    const after = JSON.parse(
      updateResourceDeclaration(
        JSON.stringify(before),
        "associationMode",
        "EXCLUSIVE"
      )
    );
    expect(after).toEqual({ ...before, associationMode: "EXCLUSIVE" });
    expect(resourceDeclaration(JSON.stringify(after))).toMatchObject({
      mode: "EXCLUSIVE",
      managedMode: "SYNC",
      source: "example"
    });
  });
});
