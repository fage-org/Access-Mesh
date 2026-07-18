import { describe, it, expect } from "vitest";
import { aggregateCell } from "./aggregate";
import type { V2DraftPermission } from "./v2-types";

function makePerm(
  variantId: number | string,
  overrides: Partial<V2DraftPermission> = {}
): V2DraftPermission {
  return {
    domainCode: "",
    resourceTypeCode: "MENU",
    scopeMode: "INSTANCE",
    resourceCode: "sys",
    codeType: "MENU",
    operationCode: "VIEW",
    variantId,
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL",
    resourceName: "系统管理",
    ...overrides
  };
}

describe("aggregate", () => {
  it("含无条件分支 -> DIRECT", () => {
    const draft = [makePerm(1, { conditionCode: null })];
    const r = aggregateCell(draft, draft);
    expect(r.effective).toBe("DIRECT");
    expect(r.draftChange).toBeNull();
  });

  it("仅有条件分支 -> CONDITIONAL", () => {
    const draft = [makePerm(1, { conditionCode: "C1" })];
    const r = aggregateCell(draft, draft);
    expect(r.effective).toBe("CONDITIONAL");
  });

  it("含无条件 + 条件分支 -> DIRECT（无条件优先）", () => {
    const draft = [
      makePerm(1, { conditionCode: "C1" }),
      makePerm(2, { conditionCode: null })
    ];
    const r = aggregateCell(draft, draft);
    expect(r.effective).toBe("DIRECT");
  });

  it("无分支 + baseline 非空 -> UNAUTHORIZED + REMOVE（全分支待移除）", () => {
    const baseline = [makePerm(1)];
    const r = aggregateCell([], baseline);
    expect(r.effective).toBe("UNAUTHORIZED");
    expect(r.draftChange).toBe("REMOVE");
  });

  it("无分支 + baseline 空 -> UNAUTHORIZED + null", () => {
    const r = aggregateCell([], []);
    expect(r.effective).toBe("UNAUTHORIZED");
    expect(r.draftChange).toBeNull();
  });

  it("有新增变体 -> ADD", () => {
    const baseline = [makePerm(1, { conditionCode: "C1" })];
    const draft = [
      makePerm(1, { conditionCode: "C1" }),
      makePerm("v-new", { conditionCode: null })
    ];
    const r = aggregateCell(draft, baseline);
    expect(r.effective).toBe("DIRECT");
    expect(r.draftChange).toBe("ADD");
  });

  it("属性变更 -> MODIFY", () => {
    const baseline = [makePerm(1, { canGrant: false })];
    const draft = [makePerm(1, { canGrant: true })];
    const r = aggregateCell(draft, baseline);
    expect(r.effective).toBe("DIRECT");
    expect(r.draftChange).toBe("MODIFY");
  });

  it("ADD 优先级 > REMOVE（同 cell 有新增 + 有移除）", () => {
    const baseline = [
      makePerm(1, { conditionCode: "C1" }),
      makePerm(2, { conditionCode: "C2" })
    ];
    // draft 只留 v1（v2 待移除）+ 新增 v-new
    const draft = [
      makePerm(1, { conditionCode: "C1" }),
      makePerm("v-new", { conditionCode: null })
    ];
    const r = aggregateCell(draft, baseline);
    expect(r.draftChange).toBe("ADD");
  });

  it("部分移除 -> REMOVE（baseline 有变体不在 draft）", () => {
    const baseline = [
      makePerm(1, { conditionCode: "C1" }),
      makePerm(2, { conditionCode: "C2" })
    ];
    const draft = [makePerm(1, { conditionCode: "C1" })];
    const r = aggregateCell(draft, baseline);
    expect(r.draftChange).toBe("REMOVE");
  });

  it("部分移除 + 修改 -> REMOVE（ADD>REMOVE>MODIFY，无 ADD 时 REMOVE 优先）", () => {
    const baseline = [
      makePerm(1, { canGrant: false, conditionCode: "C1" }),
      makePerm(2, { conditionCode: "C2" })
    ];
    const draft = [makePerm(1, { canGrant: true, conditionCode: "C1" })];
    const r = aggregateCell(draft, baseline);
    expect(r.draftChange).toBe("REMOVE");
  });

  it("ADD + REMOVE + MODIFY 同时存在 -> ADD（最高优先级）", () => {
    const baseline = [
      makePerm(1, { canGrant: false, conditionCode: "C1" }),
      makePerm(2, { conditionCode: "C2" })
    ];
    const draft = [
      makePerm(1, { canGrant: true, conditionCode: "C1" }),
      makePerm("v-new", { conditionCode: null })
    ];
    const r = aggregateCell(draft, baseline);
    expect(r.draftChange).toBe("ADD");
  });
});
