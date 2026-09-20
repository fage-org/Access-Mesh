import { describe, expect, it } from "vitest";
import { resolveGrantEntryLabel } from "./grant-entry";

describe("resolveGrantEntryLabel（T-FE-055 授予入口文案二分）", () => {
  it("有 ROLE:MANAGE → 授予语义文案（现状值不变）", () => {
    expect(resolveGrantEntryLabel(true)).toBe("权限授予");
  });

  it("仅 ROLE:VIEW → 查看语义文案（2026-09-20 拍板「查看权限」）", () => {
    expect(resolveGrantEntryLabel(false)).toBe("查看权限");
  });
});
