import { describe, it, expect } from "vitest";
import { ref, computed } from "vue";
import type { RolePermissionItem } from "@/api/permission-grant";
import type { PermCellKey } from "@/utils/permission-grant-types";
import { useV2DraftModel } from "./useV2DraftModel";
import { permCellKeyStr } from "./grant-variant";
import type { VariantCommand } from "./v2-types";

/** 构造 baseline item（ALL 行 codeType/resourceName 自动 null） */
function item(
  id: number,
  resourceCode: string | null,
  operationCode: string,
  overrides: Partial<RolePermissionItem> = {}
): RolePermissionItem {
  const scopeMode = overrides.scopeMode ?? "INSTANCE";
  return {
    id,
    domainCode: "HR",
    resourceTypeCode: "MENU",
    scopeMode,
    resourceCode,
    codeType: scopeMode === "ALL" ? null : "MENU",
    resourceName: resourceCode,
    operationCode,
    conditionCode: overrides.conditionCode ?? null,
    canGrant: overrides.canGrant ?? false,
    dependOn: overrides.dependOn ?? null,
    grantSource: "MANUAL",
    ...overrides
  };
}

function cell(
  resourceCode: string | null,
  operationCode: string,
  scopeMode: "INSTANCE" | "ALL" = "INSTANCE"
): PermCellKey {
  return {
    domainCode: "HR",
    resourceTypeCode: "MENU",
    scopeMode,
    resourceCode,
    codeType: scopeMode === "ALL" ? null : "MENU",
    operationCode
  };
}

function makeDraft(
  items: RolePermissionItem[],
  opts: {
    readonly?: boolean;
    grantable?: boolean;
    grantableTypes?: string[];
    grantableOps?: string[];
  } = {}
) {
  const baselineItems = ref(items);
  const roleContext = computed(() => ({
    domainCode: "HR",
    roleExternalId: "R1",
    roleTypeCode: "BASIC_ROLE"
  }));
  const capability = computed(() => ({
    readonly: opts.readonly ?? false,
    operatorCanManage: opts.grantable ?? true,
    grantableResourceTypeCodes: opts.grantableTypes ?? ["MENU"],
    grantableOperationCodes: opts.grantableOps ?? ["VIEW", "CREATE", "MANAGE"]
  }));
  return useV2DraftModel(baselineItems, roleContext, capability);
}

describe("useV2DraftModel 批量操作 (T-FE-032)", () => {
  it("batchGrantAll: UNAUTHORIZED+grantable -> 新增无条件分支", () => {
    const draft = makeDraft([]);
    const r = draft.batchGrantAll([cell("sys", "VIEW")]);
    expect(r.granted).toBe(1);
    expect(r.redundantCandidates).toBe(0);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe("DIRECT");
  });

  it("batchGrantAll: 已有授权的不变（仅对 UNAUTHORIZED 操作）", () => {
    const draft = makeDraft([item(10, "sys", "VIEW")]);
    const r = draft.batchGrantAll([cell("sys", "VIEW"), cell("sys", "CREATE")]);
    expect(r.granted).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe("DIRECT");
    expect(draft.cellSummary(cell("sys", "CREATE")).effective).toBe("DIRECT");
  });

  it("batchGrantAll: redundant 候选计入 redundantCandidates 且单元格不变", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    const r = draft.batchGrantAll([cell("sys", "VIEW")]);
    expect(r.granted).toBe(0);
    expect(r.redundantCandidates).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe(
      "UNAUTHORIZED"
    );
  });

  it("batchRemove: 有直接记录 -> 移除", () => {
    const draft = makeDraft([item(10, "sys", "VIEW")]);
    const r = draft.batchRemove([cell("sys", "VIEW")]);
    expect(r.removed).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe(
      "UNAUTHORIZED"
    );
    expect(draft.cellSummary(cell("sys", "VIEW")).draftChange).toBe("REMOVE");
  });

  it("batchRemove: 幂等（已有 remove 任务不重复提交）", () => {
    const draft = makeDraft([item(10, "sys", "VIEW")]);
    draft.batchRemove([cell("sys", "VIEW")]);
    const tasksBefore = draft.grantTasks.value.length;
    draft.batchRemove([cell("sys", "VIEW")]);
    expect(draft.grantTasks.value.length).toBe(tasksBefore);
  });

  it("batchAddBranch: 重复条件 -> failures", () => {
    const draft = makeDraft([item(10, "sys", "VIEW", { conditionCode: "C1" })]);
    const r = draft.batchAddBranch([cell("sys", "VIEW")], "C1", false, false);
    expect(r.added).toBe(0);
    expect(r.failures.length).toBe(1);
    expect(r.failures[0].reason).toMatch(/已存在/);
  });

  it("batchAddBranch: keepDirect=true -> redundant 候选创建直接记录", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    const r = draft.batchAddBranch([cell("sys", "VIEW")], null, false, true);
    expect(r.added).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe("DIRECT");
  });

  it("batchAddBranch: keepDirect=false -> redundant 候选 redundantSkipped", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    const r = draft.batchAddBranch([cell("sys", "VIEW")], null, false, false);
    expect(r.added).toBe(0);
    expect(r.redundantSkipped).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe(
      "UNAUTHORIZED"
    );
    // 全 redundantSkipped 不提交任务（避免污染 grantTasks）
    expect(draft.grantTasks.value.length).toBe(0);
  });

  it("redundantCandidate: INSTANCE+ALL覆盖+无变体 -> true；非同操作/ALL 本身 -> false", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    expect(draft.redundantCandidate(cell("sys", "VIEW"))).toBe(true);
    // CREATE 未被 ALL VIEW 覆盖
    expect(draft.redundantCandidate(cell("sys", "CREATE"))).toBe(false);
    // ALL cell 本身不是 redundant 候选
    expect(draft.redundantCandidate(cell(null, "VIEW", "ALL"))).toBe(false);
  });

  it("redundantCandidate: 有直接记录 -> false", () => {
    const draft = makeDraft([
      item(100, null, "VIEW", { scopeMode: "ALL" }),
      item(10, "sys", "VIEW")
    ]);
    expect(draft.redundantCandidate(cell("sys", "VIEW"))).toBe(false);
  });

  it("handleMainClick Q4: ALL覆盖+grantable+无直接记录 -> 展开分支列表，不 grantUnconditional", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    draft.handleMainClick(cell("sys", "VIEW"), "sys");
    expect(draft.expandedCell.value).toBe(permCellKeyStr(cell("sys", "VIEW")));
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe(
      "UNAUTHORIZED"
    );
    expect(draft.grantTasks.value.length).toBe(0);
  });

  it("handleMainClick: UNAUTHORIZED+grantable+无ALL覆盖 -> grantUnconditional", () => {
    const draft = makeDraft([]);
    draft.handleMainClick(cell("sys", "VIEW"), "sys");
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe("DIRECT");
  });

  it("批量操作受 readonly 总门拦截", () => {
    const draft = makeDraft([item(10, "sys", "VIEW")], { readonly: true });
    expect(draft.batchGrantAll([cell("sys", "VIEW")]).granted).toBe(0);
    expect(draft.batchRemove([cell("sys", "VIEW")]).removed).toBe(0);
    expect(
      draft.batchAddBranch([cell("sys", "VIEW")], null, false, false).added
    ).toBe(0);
    // readonly 下 handleMainClick 也不操作
    draft.handleMainClick(cell("sys", "CREATE"), "sys");
    expect(draft.grantTasks.value.length).toBe(0);
  });

  it("P1-1: 批量授予后逐分支撤销只删该 command，不影响同批其他分支", () => {
    const draft = makeDraft([]);
    draft.batchGrantAll([cell("sys", "VIEW"), cell("sys", "CREATE")]);
    expect(draft.grantTasks.value.length).toBe(1);
    expect(draft.grantTasks.value[0].commands.length).toBe(2);
    const task = draft.grantTasks.value[0];
    const viewCmd = task.commands.find(
      c => c.kind === "grant" && c.cell.operationCode === "VIEW"
    ) as Extract<VariantCommand, { kind: "grant" }>;
    draft.removeVariant(viewCmd.proposedVariantId, cell("sys", "VIEW"));
    // 任务仍在，commands 只剩 CREATE
    expect(draft.grantTasks.value.length).toBe(1);
    expect(draft.grantTasks.value[0].commands.length).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe(
      "UNAUTHORIZED"
    );
    expect(draft.cellSummary(cell("sys", "CREATE")).effective).toBe("DIRECT");
  });

  it("P1-1: 批量撤销后逐分支恢复只删该 remove command", () => {
    const draft = makeDraft([
      item(10, "sys", "VIEW"),
      item(11, "sys", "CREATE")
    ]);
    draft.batchRemove([cell("sys", "VIEW"), cell("sys", "CREATE")]);
    expect(draft.grantTasks.value[0].commands.length).toBe(2);
    draft.restoreVariant(10);
    expect(draft.grantTasks.value[0].commands.length).toBe(1);
    expect(draft.cellSummary(cell("sys", "VIEW")).effective).toBe("DIRECT");
    expect(draft.cellSummary(cell("sys", "CREATE")).draftChange).toBe("REMOVE");
  });

  it("P1-2: 编辑 conditionCode 与同 cell 其他分支重复 -> 阻断返回失败", () => {
    const draft = makeDraft([
      item(10, "sys", "VIEW", { conditionCode: null }),
      item(11, "sys", "VIEW", { conditionCode: "C1" })
    ]);
    // 把 v10（无条件）改为 C1 -> 与 v11 重复 -> 阻断
    const r = draft.updateVariant(10, "C1", false, cell("sys", "VIEW"));
    expect(r.ok).toBe(false);
    expect(r.reason).toMatch(/已存在/);
    // 未提交，v10 仍无条件，两分支并存
    expect(draft.cellSummary(cell("sys", "VIEW")).variants.length).toBe(2);
    expect(draft.grantTasks.value.length).toBe(0);
  });

  it("P2-2: batchGrantAll granted 排除 redundantSkipped", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    // sys VIEW redundant 候选（redundantSkipped）；sys CREATE 正常 add
    const r = draft.batchGrantAll([cell("sys", "VIEW"), cell("sys", "CREATE")]);
    expect(r.granted).toBe(1);
    expect(r.redundantCandidates).toBe(1);
  });

  it("P1-1: 混合批次撤销最后一个真实 add 后清理纯 redundant 任务", () => {
    const draft = makeDraft([item(100, null, "VIEW", { scopeMode: "ALL" })]);
    // sys CREATE 正常 add；sys VIEW redundant 候选（redundantSkipped）-> 1 任务 2 commands
    draft.batchGrantAll([cell("sys", "CREATE"), cell("sys", "VIEW")]);
    expect(draft.grantTasks.value.length).toBe(1);
    const task = draft.grantTasks.value[0];
    const createCmd = task.commands.find(
      c => c.kind === "grant" && c.cell.operationCode === "CREATE"
    ) as Extract<VariantCommand, { kind: "grant" }>;
    // 撤销 CREATE（真实 add）-> 剩余全 redundantSkipped -> 任务清理
    draft.removeVariant(createCmd.proposedVariantId, cell("sys", "CREATE"));
    expect(draft.grantTasks.value.length).toBe(0);
    expect(draft.hasDraft.value).toBe(false);
  });

  it("P1-2: 恢复修改时若与新增分支冲突 -> 阻断", () => {
    // baseline v10 conditionCode C1
    const draft = makeDraft([item(10, "sys", "VIEW", { conditionCode: "C1" })]);
    // 改 v10 为 C2
    draft.updateVariant(10, "C2", false, cell("sys", "VIEW"));
    // 新增 C1 分支
    draft.addBranch(cell("sys", "VIEW"), "C1", false, "sys");
    // 恢复修改（v10 回 C1）-> 与新增 C1 冲突 -> 阻断
    const r = draft.restoreModify(10);
    expect(r.ok).toBe(false);
    expect(r.reason).toMatch(/重复/);
  });

  it("P1-2: 恢复待移除分支时若与 update 后分支冲突 -> 阻断", () => {
    // baseline v10 C1, v11 C2
    const draft = makeDraft([
      item(10, "sys", "VIEW", { conditionCode: "C1" }),
      item(11, "sys", "VIEW", { conditionCode: "C2" })
    ]);
    // 移除 v10
    draft.removeVariant(10, cell("sys", "VIEW"));
    // 改 v11 为 C1（与被移除 v10 同条件，updateVariant 不查 baseline 待移除）
    draft.updateVariant(11, "C1", false, cell("sys", "VIEW"));
    // 恢复 v10 -> 与 v11(C1) 冲突 -> 阻断
    const r = draft.restoreVariant(10);
    expect(r.ok).toBe(false);
    expect(r.reason).toMatch(/重复/);
  });

  it("P1: baseline 待移除同条件分支时 addBranch 阻断（避免 remove+add 替换）", () => {
    const draft = makeDraft([item(10, "sys", "VIEW", { conditionCode: "C1" })]);
    draft.removeVariant(10, cell("sys", "VIEW"));
    const r = draft.addBranch(cell("sys", "VIEW"), "C1", false, "sys");
    expect(r.ok).toBe(false);
    expect(r.reason).toMatch(/待移除/);
    // 不产生 add 任务（仅 remove 任务）
    expect(draft.grantTasks.value.length).toBe(1);
  });

  it("P1: batchAddBranch baseline 待移除同条件分支 -> failures", () => {
    const draft = makeDraft([item(10, "sys", "VIEW", { conditionCode: "C1" })]);
    draft.removeVariant(10, cell("sys", "VIEW"));
    const r = draft.batchAddBranch([cell("sys", "VIEW")], "C1", false, false);
    expect(r.added).toBe(0);
    expect(r.failures.length).toBe(1);
    expect(r.failures[0].reason).toMatch(/待移除/);
  });
});

describe("useV2DraftModel 子权限 (T-FE-033)", () => {
  it("P2-3: PENDING_ADD 父撤销清理孤儿子 command（hasDraft 回 false）", () => {
    const draft = makeDraft([], {
      grantableTypes: ["MENU", "BUTTON"],
      grantableOps: ["VIEW"]
    });
    const parentCell = cell("sys", "VIEW");
    draft.grantUnconditional(parentCell, "系统管理");
    const parentIds =
      draft.mainIndex.value.get(permCellKeyStr(parentCell)) ?? [];
    const parentVariantId = parentIds[0];
    const childCell: PermCellKey = {
      domainCode: "HR",
      resourceTypeCode: "BUTTON",
      scopeMode: "INSTANCE",
      resourceCode: "btn-save",
      codeType: "BUTTON",
      operationCode: "VIEW"
    };
    const r = draft.addChildBranch(
      parentVariantId,
      childCell,
      null,
      false,
      "保存按钮"
    );
    expect(r.ok).toBe(true);
    expect(draft.hasDraft.value).toBe(true);
    // 撤销父 PENDING_ADD -> 同步清理孤儿子 command
    draft.removeVariant(parentVariantId, parentCell);
    expect(draft.hasDraft.value).toBe(false);
    expect(draft.grantTasks.value.length).toBe(0);
  });

  it("addChildBranch: 父变体未进投影 -> 阻断", () => {
    const draft = makeDraft([], {
      grantableTypes: ["MENU", "BUTTON"],
      grantableOps: ["VIEW"]
    });
    const childCell: PermCellKey = {
      domainCode: "HR",
      resourceTypeCode: "BUTTON",
      scopeMode: "INSTANCE",
      resourceCode: "btn-save",
      codeType: "BUTTON",
      operationCode: "VIEW"
    };
    const r = draft.addChildBranch("non-existent", childCell, null, false, "x");
    expect(r.ok).toBe(false);
    expect(r.reason).toMatch(/父权限未生效/);
  });

  it("子权限重复条件阻断（同 parent+cell+condition）", () => {
    const draft = makeDraft([item(9001, "sys", "VIEW")], {
      grantableTypes: ["MENU", "BUTTON"],
      grantableOps: ["VIEW"]
    });
    const childCell: PermCellKey = {
      domainCode: "HR",
      resourceTypeCode: "BUTTON",
      scopeMode: "INSTANCE",
      resourceCode: "btn-save",
      codeType: "BUTTON",
      operationCode: "VIEW"
    };
    expect(draft.addChildBranch(9001, childCell, null, false, "x").ok).toBe(
      true
    );
    const r2 = draft.addChildBranch(9001, childCell, null, false, "x");
    expect(r2.ok).toBe(false);
    expect(r2.reason).toMatch(/已存在/);
  });
});
