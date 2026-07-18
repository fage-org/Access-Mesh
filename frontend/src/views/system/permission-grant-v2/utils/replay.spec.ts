import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import type { PermCellKey } from "@/utils/permission-grant-types";
import {
  buildBaseline,
  createV2DraftState,
  permCellKeyStr
} from "./grant-variant";
import { replayGrantTasks } from "./replay";
import type {
  V2GrantTaskSnapshot,
  VariantCommand,
  V2DraftPermission
} from "./v2-types";

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

function grantCmd(
  proposedVariantId: string,
  overrides: Partial<Extract<VariantCommand, { kind: "grant" }>> = {}
): Extract<VariantCommand, { kind: "grant" }> {
  return {
    kind: "grant",
    cell: {
      domainCode: "",
      resourceTypeCode: "MENU",
      scopeMode: "INSTANCE",
      resourceCode: "sys",
      codeType: "MENU",
      operationCode: "VIEW"
    },
    proposedVariantId,
    conditionCode: null,
    canGrant: false,
    resourceName: "系统管理",
    keepDirectWhenAllCovered: false,
    ...overrides
  };
}

function task(cmds: VariantCommand[], taskId = "t1"): V2GrantTaskSnapshot {
  return {
    taskId,
    domainCode: "",
    roleExternalId: "R1",
    roleTypeCode: "BASIC_ROLE",
    resourceTypeCode: "MENU",
    scopeMode: "INSTANCE",
    intent: "grant",
    operationCodes: ["VIEW"],
    resources: [
      { resourceCode: "sys", codeType: "MENU", resourceName: "系统管理" }
    ],
    commands: cmds,
    createdAt: 1
  };
}

function baselineFrom(items: RolePermissionItem[]) {
  return buildBaseline(items);
}

describe("replay", () => {
  it("同输入连续 replay 两次结果完全一致（纯函数确定性）", () => {
    // 空 baseline + grant add，确保 v-new 存在便于确定性断言
    const base = createV2DraftState();
    const tasks = [task([grantCmd("v-new")])];
    const r1 = replayGrantTasks(base, tasks);
    const r2 = replayGrantTasks(base, tasks);
    expect([...r1.mainDraft.keys()]).toEqual([...r2.mainDraft.keys()]);
    expect(r1.mainDraft.get("v-new")!.variantId).toBe(
      r2.mainDraft.get("v-new")!.variantId
    );
    expect(r1.taskEffects.get("t1")!.commands).toEqual(
      r2.taskEffects.get("t1")!.commands
    );
  });

  it("grant: 不存在 -> add（proposedVariantId）", () => {
    const base = createV2DraftState();
    const r = replayGrantTasks(base, [task([grantCmd("v1")])]);
    expect(r.mainDraft.get("v1")).toBeDefined();
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("add");
  });

  it("grant: 同 cell+condition 命中且属性相同 -> noChange", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      }
    ]);
    const r = replayGrantTasks(base, [task([grantCmd("v1")])]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("noChange");
    expect(r.taskEffects.get("t1")!.commands[0].variantId).toBe(10);
  });

  it("grant: 同 cell+condition canGrant 不同 -> update（保持 variantId）", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      }
    ]);
    const r = replayGrantTasks(base, [
      task([grantCmd("v1", { canGrant: true })])
    ]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("update");
    expect(r.mainDraft.get(10)!.canGrant).toBe(true);
  });

  it("grant: 同 cell+不同 condition -> add 新变体（多分支并存）", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      }
    ]);
    const r = replayGrantTasks(base, [
      task([grantCmd("v-c1", { conditionCode: "C1" })])
    ]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("add");
    // 同 cell 两分支并存
    const cellKey = permCellKeyStr(makeMain(10));
    expect(r.mainIndex.get(cellKey)?.length).toBe(2);
  });

  it("update: 保持 variantId，编辑 conditionCode（非 remove+add）", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      }
    ]);
    const r = replayGrantTasks(base, [
      task([
        {
          kind: "update",
          targetVariantId: 10,
          conditionCode: "C1",
          canGrant: false
        }
      ])
    ]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("update");
    expect(r.mainDraft.get(10)!.conditionCode).toBe("C1");
    // variantId 不变，未产生新变体
    expect(r.mainDraft.size).toBe(1);
  });

  it("update: 编辑 conditionCode 与同 cell 另一分支重复 -> 阻断 noChange", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      },
      {
        id: 11,
        domainCode: "",
        resourceTypeCode: "MENU",
        resourceCode: "sys",
        codeType: "MENU",
        resourceName: "系统管理",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: "C1",
        canGrant: false,
        dependOn: null,
        grantSource: "MANUAL"
      }
    ]);
    // 把 v10 改成 C1 -> 与 v11 重复 -> 阻断
    const r = replayGrantTasks(base, [
      task([
        {
          kind: "update",
          targetVariantId: 10,
          conditionCode: "C1",
          canGrant: false
        }
      ])
    ]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("noChange");
    expect(r.taskEffects.get("t1")!.commands[0].reason).toMatch(/已存在/);
    expect(r.mainDraft.get(10)!.conditionCode).toBeNull();
  });

  it("remove: 删变体 + 级联该父分支子权限", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      },
      {
        id: 20,
        domainCode: "",
        resourceTypeCode: "MENU",
        resourceCode: "sys",
        codeType: "MENU",
        resourceName: null,
        operationCode: "CREATE",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false,
        dependOn: 10,
        grantSource: "MANUAL"
      }
    ]);
    const r = replayGrantTasks(base, [
      task([{ kind: "remove", targetVariantId: 10 }])
    ]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe("remove");
    expect(r.mainDraft.has(10)).toBe(false);
    // 级联删除子权限 20
    expect(r.childDraft.has(20)).toBe(false);
  });

  it("redundantSkipped: INSTANCE + ALL 覆盖 + 无直接记录 + !keepDirect", () => {
    const base = baselineFrom([
      {
        id: 100,
        domainCode: "",
        resourceTypeCode: "MENU",
        resourceCode: null,
        codeType: null,
        resourceName: null,
        operationCode: "VIEW",
        scopeMode: "ALL",
        conditionCode: null,
        canGrant: false,
        dependOn: null,
        grantSource: "MANUAL"
      }
    ]);
    // INSTANCE sys VIEW 被 ALL 覆盖，无直接记录 -> redundantSkipped
    const r = replayGrantTasks(base, [task([grantCmd("v1")])]);
    expect(r.taskEffects.get("t1")!.commands[0].effect).toBe(
      "redundantSkipped"
    );
    expect(r.mainDraft.has("v1")).toBe(false);
  });

  it("输入 baseline maps 未被原地修改", () => {
    const base = baselineFrom([
      {
        id: 10,
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
        grantSource: "MANUAL"
      }
    ]);
    const sizeBefore = base.mainMap.size;
    replayGrantTasks(base, [task([grantCmd("v1")])]);
    expect(base.mainMap.size).toBe(sizeBefore);
    expect(base.mainMap.has("v1")).toBe(false);
  });

  it("applyGrant: proposedVariantId 已占用（不同 cell）-> 抛错", () => {
    const base = createV2DraftState();
    const createCell: PermCellKey = {
      domainCode: "",
      resourceTypeCode: "MENU",
      scopeMode: "INSTANCE",
      resourceCode: "sys",
      codeType: "MENU",
      operationCode: "CREATE"
    };
    // t1 add v1（VIEW cell）；t2 用同 proposedVariantId v1 在 CREATE cell，
    // 不命中 v1 -> add 分支 -> mainMap.has("v1") 占用 -> 抛错
    const tasks = [
      task([grantCmd("v1")], "t1"),
      task([grantCmd("v1", { cell: createCell })], "t2")
    ];
    expect(() => replayGrantTasks(base, tasks)).toThrow(/已被占用/);
  });
});
