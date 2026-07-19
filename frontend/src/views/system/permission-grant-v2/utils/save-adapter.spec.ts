import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import type { V2DraftPermission } from "./v2-types";
import { buildBaseline } from "./grant-variant";
import {
  toAddItem,
  toUpdateItem,
  computeMainDiff,
  computeChildDiff,
  matchSaveResponse
} from "./save-adapter";

function makeDraft(
  overrides: Partial<V2DraftPermission> = {}
): V2DraftPermission {
  return {
    domainCode: "",
    resourceTypeCode: "MENU",
    scopeMode: "INSTANCE",
    resourceCode: "sys",
    codeType: "MENU",
    operationCode: "VIEW",
    variantId: 100,
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL",
    resourceName: "系统管理",
    ...overrides
  };
}

function mainItem(
  id: number,
  overrides: Partial<RolePermissionItem> = {}
): RolePermissionItem {
  return {
    id,
    domainCode: "",
    resourceTypeCode: "MENU",
    resourceCode: "sys",
    codeType: "MENU",
    resourceName: "a",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL",
    ...overrides
  };
}

function childItem(id: number, parentId: number): RolePermissionItem {
  return {
    id,
    domainCode: "",
    resourceTypeCode: "BUTTON",
    resourceCode: "btn",
    codeType: "BUTTON",
    resourceName: "b",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: parentId,
    grantSource: "MANUAL"
  };
}

describe("save-adapter", () => {
  describe("toAddItem (条件 wire)", () => {
    it('无条件 -> conditionCode=""', () => {
      expect(toAddItem(makeDraft({ conditionCode: null })).conditionCode).toBe(
        ""
      );
    });
    it("有条件 -> conditionCode=具体值", () => {
      expect(toAddItem(makeDraft({ conditionCode: "A" })).conditionCode).toBe(
        "A"
      );
    });
    it("INSTANCE 带资源码", () => {
      const item = toAddItem(
        makeDraft({ resourceCode: "sys", codeType: "MENU" })
      );
      expect(item.resourceCode).toBe("sys");
      expect(item.codeType).toBe("MENU");
    });
    it("ALL 不带资源码", () => {
      const item = toAddItem(
        makeDraft({ scopeMode: "ALL", resourceCode: null, codeType: null })
      );
      expect(item.resourceCode).toBeUndefined();
      expect(item.codeType).toBeUndefined();
    });
  });

  describe("toUpdateItem (条件 wire 精确规则)", () => {
    it("条件未变化 -> null（不更新）", () => {
      const draft = makeDraft({ variantId: 1, conditionCode: "A" });
      const base = makeDraft({ variantId: 1, conditionCode: "A" });
      expect(toUpdateItem(draft, base).conditionCode).toBe(null);
    });
    it('从有条件清为无条件 -> ""', () => {
      const draft = makeDraft({ variantId: 1, conditionCode: null });
      const base = makeDraft({ variantId: 1, conditionCode: "A" });
      expect(toUpdateItem(draft, base).conditionCode).toBe("");
    });
    it("新增/更换条件 -> 具体值", () => {
      const draft = makeDraft({ variantId: 1, conditionCode: "B" });
      const base = makeDraft({ variantId: 1, conditionCode: "A" });
      expect(toUpdateItem(draft, base).conditionCode).toBe("B");
    });
    it("无条件->无条件（未变化）-> null", () => {
      const draft = makeDraft({ variantId: 1, conditionCode: null });
      const base = makeDraft({ variantId: 1, conditionCode: null });
      expect(toUpdateItem(draft, base).conditionCode).toBe(null);
    });
    it("canGrant 总是发 draft 值", () => {
      const draft = makeDraft({ variantId: 1, canGrant: true });
      const base = makeDraft({ variantId: 1, canGrant: false });
      expect(toUpdateItem(draft, base).canGrant).toBe(true);
    });
  });

  describe("computeMainDiff", () => {
    it("add/update/remove 分类", () => {
      const base = buildBaseline([
        mainItem(1, { operationCode: "VIEW" }),
        mainItem(2, { operationCode: "CREATE" })
      ]);
      const draft = new Map([
        [1, makeDraft({ variantId: 1, operationCode: "VIEW", canGrant: true })],
        [3, makeDraft({ variantId: 3, operationCode: "DELETE" })]
      ]);
      const diff = computeMainDiff(draft, base);
      expect(diff.map(d => d.type).sort()).toEqual(["add", "remove", "update"]);
      expect(diff.find(d => d.type === "remove")?.variantId).toBe(2);
      expect(diff.find(d => d.type === "add")?.variantId).toBe(3);
    });
  });

  describe("computeChildDiff 级联过滤", () => {
    it("父变体在 mainRemove -> 子 remove 跳过", () => {
      const base = buildBaseline([mainItem(1), childItem(101, 1)]);
      const diff = computeChildDiff(new Map(), base, new Set([1]));
      expect(diff.length).toBe(0);
    });
    it("父变体不在 mainRemove -> 子 remove 进入 diff", () => {
      const base = buildBaseline([mainItem(1), childItem(101, 1)]);
      const diff = computeChildDiff(new Map(), base, new Set());
      expect(diff.length).toBe(1);
      expect(diff[0].type).toBe("remove");
      expect(diff[0].variantId).toBe(101);
    });
    it("子 add 带父变体", () => {
      const base = buildBaseline([mainItem(1)]);
      const childDraft = new Map([
        [
          "c1",
          {
            ...makeDraft({
              variantId: "c1",
              resourceTypeCode: "BUTTON",
              resourceCode: "btn",
              codeType: "BUTTON",
              dependOn: 1
            })
          }
        ]
      ]);
      const diff = computeChildDiff(childDraft, base, new Set());
      expect(diff.length).toBe(1);
      expect(diff[0].type).toBe("add");
      expect(diff[0].parentVariantId).toBe(1);
    });
  });

  describe("matchSaveResponse", () => {
    it("按 PermCellKey+condition 匹配得服务端 id", () => {
      const draftAdd = makeDraft({ variantId: "tmp-1", conditionCode: "A" });
      const saveItems = [
        mainItem(9001, { conditionCode: "A" }),
        mainItem(9002, { conditionCode: null })
      ];
      const map = matchSaveResponse(saveItems, [
        {
          type: "add",
          variantId: "tmp-1",
          perm: draftAdd,
          before: null,
          isChild: false,
          parentVariantId: null
        }
      ]);
      expect(map.get("tmp-1")).toBe(9001);
    });
    it("同 cell 不同 condition 不误匹配", () => {
      const draftAdd = makeDraft({ variantId: "tmp-1", conditionCode: "B" });
      const saveItems = [mainItem(9001, { conditionCode: "A" })];
      const map = matchSaveResponse(saveItems, [
        {
          type: "add",
          variantId: "tmp-1",
          perm: draftAdd,
          before: null,
          isChild: false,
          parentVariantId: null
        }
      ]);
      expect(map.get("tmp-1")).toBeUndefined();
    });
  });
});
