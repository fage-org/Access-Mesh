import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import {
  buildBaseline,
  createV2DraftState,
  upsertMainVariant,
  upsertChildVariant,
  removeMainVariant,
  cascadeRemoveChildren,
  generateVariantId,
  normalizeConditionCode,
  permCellKeyStr,
  childPermCellKeyStr
} from "./grant-variant";
import type { V2DraftPermission } from "./v2-types";

function makeMain(
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

function makeItem(
  id: number,
  overrides: Partial<RolePermissionItem> = {}
): RolePermissionItem {
  return {
    id,
    domainCode: "",
    resourceTypeCode: "MENU",
    resourceCode: "sys",
    codeType: "MENU",
    resourceName: "系统管理",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL",
    ...overrides
  };
}

describe("grant-variant", () => {
  it("normalizeConditionCode: '' -> null，null -> null，非空保留", () => {
    expect(normalizeConditionCode("")).toBeNull();
    expect(normalizeConditionCode(null)).toBeNull();
    expect(normalizeConditionCode("C1")).toBe("C1");
  });

  it("generateVariantId: 连续调用唯一", () => {
    const a = generateVariantId();
    const b = generateVariantId();
    expect(a).not.toBe(b);
    expect(typeof a).toBe("string");
  });

  it("buildBaseline: items -> state，main/child maps + 索引正确", () => {
    const items: RolePermissionItem[] = [
      makeItem(1, { operationCode: "VIEW", conditionCode: null }),
      makeItem(2, {
        operationCode: "VIEW",
        conditionCode: "C1",
        id: 2
      }),
      makeItem(3, {
        operationCode: "CREATE",
        dependOn: 1,
        id: 3
      })
    ];
    const state = buildBaseline(items);
    // 2 主权限（同 cell 两分支）+ 1 子权限
    expect(state.mainMap.size).toBe(2);
    expect(state.childMap.size).toBe(1);
    // 同 cell 两分支并存
    const cellKey = permCellKeyStr(makeMain(1));
    expect(state.mainIndex.get(cellKey)?.length).toBe(2);
    // 子权限索引含父变体
    const childKey = childPermCellKeyStr(
      1,
      makeMain(3, { operationCode: "CREATE" })
    );
    expect(state.childIndex.get(childKey)?.length).toBe(1);
  });

  it("buildBaseline: 孤儿子权限抛错（父变体不存在）", () => {
    const items: RolePermissionItem[] = [makeItem(3, { dependOn: 999, id: 3 })];
    expect(() => buildBaseline(items)).toThrow(/孤儿/);
  });

  it("upsert/remove 维护索引（同 cell 多分支并存）", () => {
    const state = createV2DraftState();
    upsertMainVariant(state, makeMain("v1", { conditionCode: null }));
    upsertMainVariant(state, makeMain("v2", { conditionCode: "C1" }));
    const cellKey = permCellKeyStr(makeMain("v1"));
    expect(state.mainIndex.get(cellKey)?.length).toBe(2);
    expect(state.mainMap.size).toBe(2);

    removeMainVariant(state, "v1");
    expect(state.mainMap.size).toBe(1);
    expect(state.mainIndex.get(cellKey)?.length).toBe(1);
    // 删空后索引 key 清除
    removeMainVariant(state, "v2");
    expect(state.mainMap.size).toBe(0);
    expect(state.mainIndex.has(cellKey)).toBe(false);
  });

  it("cascadeRemoveChildren: 仅删该父分支，不影响同 cell 其他父变体子权限", () => {
    const state = createV2DraftState();
    const p1 = makeMain("p1", { conditionCode: null });
    const p2 = makeMain("p2", { conditionCode: "C1" });
    upsertMainVariant(state, p1);
    upsertMainVariant(state, p2);
    const c1 = makeMain("c1", { operationCode: "CREATE", dependOn: "p1" });
    const c2 = makeMain("c2", { operationCode: "CREATE", dependOn: "p2" });
    upsertChildVariant(state, c1);
    upsertChildVariant(state, c2);
    expect(state.childMap.size).toBe(2);

    // 删 p1 -> 仅级联 c1，c2 保留
    const n = cascadeRemoveChildren(state, "p1");
    expect(n).toBe(1);
    expect(state.childMap.has("c1")).toBe(false);
    expect(state.childMap.has("c2")).toBe(true);
  });

  it("upsertChildVariant: dependOn=null 抛错", () => {
    const state = createV2DraftState();
    const bad = makeMain("c1");
    bad.dependOn = null;
    expect(() => upsertChildVariant(state, bad)).toThrow(/dependOn/);
  });

  it("upsertMainVariant: dependOn !== null 抛错", () => {
    const state = createV2DraftState();
    const bad = makeMain("v1");
    bad.dependOn = "p1";
    expect(() => upsertMainVariant(state, bad)).toThrow(/dependOn/);
  });

  it("upsertMainVariant: 同 ID 不同 cell -> 坐标不可变抛错", () => {
    const state = createV2DraftState();
    upsertMainVariant(state, makeMain("v1", { operationCode: "VIEW" }));
    expect(() =>
      upsertMainVariant(state, makeMain("v1", { operationCode: "CREATE" }))
    ).toThrow(/坐标不可变/);
  });

  it("upsertMainVariant: 同 ID 同 cell -> 幂等覆盖", () => {
    const state = createV2DraftState();
    upsertMainVariant(state, makeMain("v1", { canGrant: false }));
    upsertMainVariant(state, makeMain("v1", { canGrant: true }));
    expect(state.mainMap.size).toBe(1);
    expect(state.mainMap.get("v1")!.canGrant).toBe(true);
  });

  it("upsertChildVariant: 父变体不存在 -> 孤儿抛错", () => {
    const state = createV2DraftState();
    const c = makeMain("c1", { dependOn: "p1" });
    expect(() => upsertChildVariant(state, c)).toThrow(/孤儿/);
  });

  it("upsertChildVariant: 同 ID 不同父 -> 坐标/父变体不可变抛错", () => {
    const state = createV2DraftState();
    upsertMainVariant(state, makeMain("p1"));
    upsertMainVariant(state, makeMain("p2", { conditionCode: "C1" }));
    const c1 = makeMain("c1", { operationCode: "CREATE", dependOn: "p1" });
    upsertChildVariant(state, c1);
    // 同 ID 换父 -> 抛错
    const c1b = makeMain("c1", { operationCode: "CREATE", dependOn: "p2" });
    expect(() => upsertChildVariant(state, c1b)).toThrow(/不可变/);
  });
});
