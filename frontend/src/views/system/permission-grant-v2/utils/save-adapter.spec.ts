import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import { RequestError } from "@/api/_envelope";
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  V2DraftPermission,
  V2GrantTaskSnapshot,
  V2FailedChildOp
} from "./v2-types";
import { buildBaseline } from "./grant-variant";
import {
  toAddItem,
  toUpdateItem,
  computeMainDiff,
  computeChildDiff,
  matchSaveResponse,
  classifySaveError,
  reconcileIdentities,
  deriveSavePhase
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

  describe("classifySaveError (Q1 明确拒绝白名单)", () => {
    it("RequestError kind=business -> business", () => {
      const e = new RequestError("业务拒绝", {
        appCode: 4001,
        kind: "business"
      });
      expect(classifySaveError(e)).toBe("business");
    });
    it("RequestError kind=timeout -> unknown", () => {
      expect(
        classifySaveError(new RequestError("超时", { kind: "timeout" }))
      ).toBe("unknown");
    });
    it("RequestError kind=network -> unknown", () => {
      expect(
        classifySaveError(new RequestError("断网", { kind: "network" }))
      ).toBe("unknown");
    });
    it("axios ECONNABORTED -> unknown", () => {
      expect(classifySaveError({ code: "ECONNABORTED" })).toBe("unknown");
    });
    it("axios ERR_NETWORK -> unknown", () => {
      expect(classifySaveError({ code: "ERR_NETWORK" })).toBe("unknown");
    });
    it("axios ERR_CANCELED -> unknown", () => {
      expect(classifySaveError({ code: "ERR_CANCELED" })).toBe("unknown");
    });
    it("axios 500 -> unknown", () => {
      expect(classifySaveError({ response: { status: 500 } })).toBe("unknown");
    });
    it("axios 503 -> unknown", () => {
      expect(classifySaveError({ response: { status: 503 } })).toBe("unknown");
    });
    it("axios 408 -> unknown", () => {
      expect(classifySaveError({ response: { status: 408 } })).toBe("unknown");
    });
    it("axios 400 -> business", () => {
      expect(classifySaveError({ response: { status: 400 } })).toBe("business");
    });
    it("axios 409 -> business", () => {
      expect(classifySaveError({ response: { status: 409 } })).toBe("business");
    });
    it("axios 401 -> business", () => {
      expect(classifySaveError({ response: { status: 401 } })).toBe("business");
    });
    it("axios 422 -> business", () => {
      expect(classifySaveError({ response: { status: 422 } })).toBe("business");
    });
    it("axios 404 -> unknown（白名单外默认未知）", () => {
      expect(classifySaveError({ response: { status: 404 } })).toBe("unknown");
    });
    it("axios 405 -> unknown（白名单外默认未知）", () => {
      expect(classifySaveError({ response: { status: 405 } })).toBe("unknown");
    });
    it("有 code 无 response -> unknown", () => {
      expect(classifySaveError({ code: "ERR_SOMETHING" })).toBe("unknown");
    });
    it("普通对象无 code/response -> unknown", () => {
      expect(classifySaveError({ foo: "bar" })).toBe("unknown");
    });
    it("undefined -> unknown", () => {
      expect(classifySaveError(undefined)).toBe("unknown");
    });
  });

  describe("reconcileIdentities (Q2 身份合并 + Q3 settled 剔除)", () => {
    const menuCell: PermCellKey = {
      domainCode: "",
      resourceTypeCode: "MENU",
      scopeMode: "INSTANCE",
      resourceCode: "sys",
      codeType: "MENU",
      operationCode: "VIEW"
    };
    const buttonCell: PermCellKey = {
      domainCode: "",
      resourceTypeCode: "BUTTON",
      scopeMode: "INSTANCE",
      resourceCode: "btn",
      codeType: "BUTTON",
      operationCode: "VIEW"
    };

    function grantTask(
      uuid: string,
      cell: PermCellKey,
      conditionCode: string | null = null
    ): V2GrantTaskSnapshot {
      return {
        taskId: `t-${uuid}`,
        domainCode: "",
        roleExternalId: "role-1",
        roleTypeCode: "ROLE",
        resourceTypeCode: cell.resourceTypeCode,
        scopeMode: cell.scopeMode,
        intent: "grant",
        operationCodes: [cell.operationCode],
        resources: [
          {
            resourceCode: cell.resourceCode,
            codeType: cell.codeType,
            resourceName: null
          }
        ],
        commands: [
          {
            kind: "grant",
            cell,
            proposedVariantId: uuid,
            conditionCode,
            canGrant: false,
            resourceName: null,
            keepDirectWhenAllCovered: false
          }
        ],
        createdAt: 0
      };
    }

    function childGrantTask(
      uuid: string,
      parentVariantId: string | number
    ): V2GrantTaskSnapshot {
      return {
        taskId: `t-${uuid}`,
        domainCode: "",
        roleExternalId: "role-1",
        roleTypeCode: "ROLE",
        resourceTypeCode: "BUTTON",
        scopeMode: "INSTANCE",
        intent: "grant",
        operationCodes: ["VIEW"],
        resources: [
          { resourceCode: "btn", codeType: "BUTTON", resourceName: null }
        ],
        commands: [
          {
            kind: "child-grant",
            parentVariantId,
            cell: buttonCell,
            proposedVariantId: uuid,
            conditionCode: null,
            canGrant: false,
            resourceName: null
          }
        ],
        createdAt: 0
      };
    }

    it("主权限 UUID 已落库 -> 重绑 + 清理 settled command（noChange）", () => {
      const task = grantTask("tmp-1", menuCell);
      const nextBaseline = buildBaseline([mainItem(9001)]);
      const result = reconcileIdentities(nextBaseline, [task], []);
      expect(result.ok).toBe(true);
      expect(result.mainRebind.get("tmp-1")).toBe(9001);
      // 已落库 -> replay noChange -> command 清理 -> 任务删
      expect(result.tasks).toHaveLength(0);
    });

    it("主权限 UUID 未落库 -> 保留 UUID（仍为 add）", () => {
      const task = grantTask("tmp-1", menuCell);
      const nextBaseline = buildBaseline([]);
      const result = reconcileIdentities(nextBaseline, [task], []);
      expect(result.ok).toBe(true);
      expect(result.mainRebind.has("tmp-1")).toBe(false);
      expect(result.tasks).toHaveLength(1);
      expect(result.tasks[0].commands[0]).toMatchObject({
        kind: "grant",
        proposedVariantId: "tmp-1"
      });
    });

    it("子权限 UUID 已落库 -> 重绑 proposedVariantId（父为 baseline number）", () => {
      const nextBaseline = buildBaseline([
        mainItem(9001),
        childItem(9101, 9001)
      ]);
      const task = childGrantTask("tmp-child", 9001);
      const result = reconcileIdentities(nextBaseline, [task], []);
      expect(result.ok).toBe(true);
      expect(result.childRebind.get("tmp-child")).toBe(9101);
      // 已落库 -> command 清理 -> 任务删
      expect(result.tasks).toHaveLength(0);
    });

    it("子权限父 UUID 已落库 -> parentVariantId 重绑为服务端 id", () => {
      // 父 UUID tmp-parent 已落库为 9001，子权限未落库
      const nextBaseline = buildBaseline([mainItem(9001)]);
      const parentTask = grantTask("tmp-parent", menuCell);
      const childTask = childGrantTask("tmp-child", "tmp-parent");
      const result = reconcileIdentities(
        nextBaseline,
        [parentTask, childTask],
        []
      );
      expect(result.ok).toBe(true);
      expect(result.mainRebind.get("tmp-parent")).toBe(9001);
      // 子 task parentVariantId 应重绑为 9001
      const childCmd = result.tasks
        .flatMap(t => t.commands)
        .find(c => c.kind === "child-grant") as Extract<
        V2GrantTaskSnapshot["commands"][number],
        { kind: "child-grant" }
      >;
      expect(childCmd.parentVariantId).toBe(9001);
    });

    it("failedChildren add 已落库 -> 剔除", () => {
      const nextBaseline = buildBaseline([
        mainItem(9001),
        childItem(9101, 9001)
      ]);
      const failedChild: V2FailedChildOp = {
        op: "add",
        child: {
          ...makeDraft({
            variantId: "tmp-child",
            dependOn: 9001,
            resourceTypeCode: "BUTTON",
            resourceCode: "btn",
            codeType: "BUTTON"
          })
        },
        childKey: "x",
        parentVariantId: 9001
      };
      const result = reconcileIdentities(nextBaseline, [], [failedChild]);
      expect(result.ok).toBe(true);
      expect(result.failedChildren).toHaveLength(0);
    });

    it("failedChildren remove 已删 -> 剔除", () => {
      const nextBaseline = buildBaseline([mainItem(9001)]); // 子权限 9101 已不在
      const failedChild: V2FailedChildOp = {
        op: "remove",
        child: {
          ...makeDraft({
            variantId: 9101,
            dependOn: 9001,
            resourceTypeCode: "BUTTON",
            resourceCode: "btn",
            codeType: "BUTTON"
          })
        },
        childKey: "x",
        parentVariantId: 9001
      };
      const result = reconcileIdentities(nextBaseline, [], [failedChild]);
      expect(result.ok).toBe(true);
      expect(result.failedChildren).toHaveLength(0);
    });

    it("failedChildren add 未落库 -> 保留", () => {
      const nextBaseline = buildBaseline([mainItem(9001)]); // 无子权限
      const failedChild: V2FailedChildOp = {
        op: "add",
        child: {
          ...makeDraft({
            variantId: "tmp-child",
            dependOn: 9001,
            resourceTypeCode: "BUTTON",
            resourceCode: "btn",
            codeType: "BUTTON"
          })
        },
        childKey: "x",
        parentVariantId: 9001
      };
      const result = reconcileIdentities(nextBaseline, [], [failedChild]);
      expect(result.ok).toBe(true);
      expect(result.failedChildren).toHaveLength(1);
      expect(result.failedChildren[0].child.variantId).toBe("tmp-child");
    });

    it("多匹配（baseline 同 cell+condition 两条）-> ok=false", () => {
      const task = grantTask("tmp-1", menuCell);
      const nextBaseline = buildBaseline([mainItem(9001), mainItem(9002)]);
      const result = reconcileIdentities(nextBaseline, [task], []);
      expect(result.ok).toBe(false);
    });

    it("非一一映射（两 UUID 抢同一 baseline id）-> ok=false", () => {
      const task1 = grantTask("tmp-1", menuCell);
      const task2 = grantTask("tmp-2", menuCell);
      const nextBaseline = buildBaseline([mainItem(9001)]);
      const result = reconcileIdentities(nextBaseline, [task1, task2], []);
      expect(result.ok).toBe(false);
    });
  });

  describe("deriveSavePhase (状态转换，验收第11条)", () => {
    const base = {
      saving: false,
      savePreview: false,
      saveOutcomeUnknown: false,
      baselineStale: false,
      failedChildrenCount: 0,
      mainFailed: false,
      hasDraft: false
    };
    it("无草稿 -> CLEAN", () => {
      expect(deriveSavePhase(base)).toBe("CLEAN");
    });
    it("有草稿 -> DIRTY", () => {
      expect(deriveSavePhase({ ...base, hasDraft: true })).toBe("DIRTY");
    });
    it("mainFailed -> SAVE_FAILED_MAIN（不靠 saveError 推断）", () => {
      expect(
        deriveSavePhase({ ...base, mainFailed: true, hasDraft: true })
      ).toBe("SAVE_FAILED_MAIN");
    });
    it("mainFailed + failedChildren -> SAVE_FAILED_MAIN（主失败优先，不被子失败遮蔽）", () => {
      expect(
        deriveSavePhase({
          ...base,
          mainFailed: true,
          failedChildrenCount: 3,
          hasDraft: true
        })
      ).toBe("SAVE_FAILED_MAIN");
    });
    it("failedChildren 非空 -> SAVE_FAILED_CHILD", () => {
      expect(
        deriveSavePhase({ ...base, failedChildrenCount: 2, hasDraft: true })
      ).toBe("SAVE_FAILED_CHILD");
    });
    it("baselineStale -> STALE", () => {
      expect(deriveSavePhase({ ...base, baselineStale: true })).toBe("STALE");
    });
    it("baselineStale + failedChildren -> STALE_WITH_CHILD_FAILURE（组合态）", () => {
      expect(
        deriveSavePhase({
          ...base,
          baselineStale: true,
          failedChildrenCount: 3
        })
      ).toBe("STALE_WITH_CHILD_FAILURE");
    });
    it("saveOutcomeUnknown -> SAVE_OUTCOME_UNKNOWN", () => {
      expect(deriveSavePhase({ ...base, saveOutcomeUnknown: true })).toBe(
        "SAVE_OUTCOME_UNKNOWN"
      );
    });
    it("savePreview -> SAVE_PREVIEW", () => {
      expect(
        deriveSavePhase({ ...base, savePreview: true, hasDraft: true })
      ).toBe("SAVE_PREVIEW");
    });
    it("saving -> SAVING（最高优先级）", () => {
      expect(
        deriveSavePhase({
          ...base,
          saving: true,
          savePreview: true,
          saveOutcomeUnknown: true,
          baselineStale: true,
          failedChildrenCount: 1,
          mainFailed: true,
          hasDraft: true
        })
      ).toBe("SAVING");
    });
    it("reconcile 后仍有 diff 不判 MAIN_FAILED（mainFailed=false -> DIRTY）", () => {
      // 模拟 handleSaveOutcomeUnknown reconcile 成功后仍有未落库项：
      // saveOutcomeUnknown 已清，mainFailed 未设，hasDraft=true -> DIRTY 续传
      expect(deriveSavePhase({ ...base, hasDraft: true })).toBe("DIRTY");
    });
  });
});
