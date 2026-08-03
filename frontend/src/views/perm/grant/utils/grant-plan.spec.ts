/**
 * 草稿 → plan 构建 / 草稿生效视图 / 弹窗确定语义（方案二全量比对）测试。
 * 对齐设计 §4（三键模型/确定语义）/ §6.4（单入口提交）/ §6.2（diff 标记）。
 */
import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import {
  applyDialogResultToDraft,
  applyDraftToRecords,
  buildAddChange,
  buildGrantPlan,
  buildRemoveChange,
  buildReplaceChange,
  buildSummary,
  buildUpdateChange,
  cellDraftMark,
  draftParentKey,
  findBranchConflict,
  groupKeyOf,
  persistedParentKey,
  resolveGrantedBits,
  computePreset,
  type DialogResult,
  type EffectiveRecord
} from "./grant-plan";
import type { AddChange, ChangeSummary, DraftChange } from "./types";

// ========== 测试数据工厂 ==========

let idSeq = 1000;
function makeRecord(
  overrides: Partial<RolePermissionItem> = {}
): RolePermissionItem {
  idSeq += 1;
  return {
    id: idSeq,
    resourceTypeCode: "DATA",
    resourceCode: "data:r1",
    codeType: "default",
    resourceName: "数据一",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2",
    createdAt: "2026-08-01T10:00:00",
    childCount: 0,
    ...overrides
  };
}

const OPS = [
  {
    code: "VIEW",
    name: "查看",
    resourceTypeCode: "DATA",
    binaryBit: "2",
    inheritMask: "0"
  },
  {
    code: "UPDATE",
    name: "更新",
    resourceTypeCode: "DATA",
    binaryBit: "4",
    inheritMask: "2"
  }
];

function summaryOf(record: RolePermissionItem): ChangeSummary {
  return buildSummary({
    recordKey: {
      resourceTypeCode: record.resourceTypeCode,
      resourceCode: record.resourceCode,
      codeType: record.codeType,
      operationCode: record.operationCode,
      scopeMode: record.scopeMode,
      conditionCode: record.conditionCode,
      canGrant: record.canGrant
    },
    resourceLabel: record.resourceName ?? record.resourceCode ?? ""
  });
}

function addChangeOf(record: RolePermissionItem): AddChange {
  return buildAddChange({
    recordKey: {
      resourceTypeCode: record.resourceTypeCode,
      resourceCode: record.resourceCode,
      codeType: record.codeType,
      operationCode: record.operationCode,
      scopeMode: record.scopeMode,
      conditionCode: record.conditionCode,
      canGrant: record.canGrant
    },
    summary: summaryOf(record)
  });
}

// ========== groupKeyOf ==========

describe("groupKeyOf（三键模型）", () => {
  it("operationKey = operationCode ?? bits:+grantedBits（组合位记录以位串为键不折叠）", () => {
    const a = makeRecord({ operationCode: "VIEW", grantedBits: "2" });
    const b = makeRecord({ operationCode: null, grantedBits: "6" });
    const c = makeRecord({ operationCode: null, grantedBits: "10" });
    expect(groupKeyOf(a)).toContain("VIEW");
    expect(groupKeyOf(b)).toContain("bits:6");
    expect(groupKeyOf(b)).not.toBe(groupKeyOf(c));
  });
});

// ========== buildGrantPlan ==========

describe("buildGrantPlan（§6.4 单入口）", () => {
  it("空草稿 → null", () => {
    expect(buildGrantPlan([])).toBeNull();
  });

  it("add（主权限）→ creates；新增主权限带 children 一次性建树", () => {
    const main = addChangeOf(makeRecord());
    const child: AddChange = {
      ...addChangeOf(makeRecord({ resourceCode: "data:r2" })),
      parentChangeId: main.changeId
    };
    const plan = buildGrantPlan([main, child])!;
    expect(plan.creates).toHaveLength(1);
    expect(plan.creates![0].children).toHaveLength(1);
    expect(plan.creates![0].children![0].resourceCode).toBe("data:r2");
    expect(plan.removes).toBeUndefined();
  });

  it("add（子权限 parentPermissionId 挂已存在父）→ creates 带 parentPermissionId", () => {
    const child: AddChange = {
      ...addChangeOf(makeRecord({ resourceCode: "data:r2" })),
      parentPermissionId: 555
    };
    const plan = buildGrantPlan([child])!;
    expect(plan.creates![0].parentPermissionId).toBe(555);
    expect(plan.creates![0].children).toBeUndefined();
  });

  it("updates 三态：canGrant 变更 / conditionCode 覆盖 / 清除映射为空串；无差异不入 updates", () => {
    const record = makeRecord({ canGrant: false, conditionCode: "a" });
    const summary = summaryOf(record);
    const changes: DraftChange[] = [
      buildUpdateChange({
        before: record,
        after: { canGrant: true, conditionCode: null },
        summary
      }),
      // 无差异 → 不入 plan
      buildUpdateChange({
        before: makeRecord({ id: 2000, canGrant: true, conditionCode: "x" }),
        after: { canGrant: true, conditionCode: "x" },
        summary
      })
    ];
    const plan = buildGrantPlan(changes)!;
    expect(plan.updates).toHaveLength(1);
    expect(plan.updates![0]).toEqual({
      id: record.id,
      canGrant: true,
      conditionCode: ""
    });
  });

  it("removes（主权限）→ 级联删子由后端负责，其下子权限草稿静默丢弃", () => {
    const main = makeRecord({ id: 3000 });
    const childUpdate = buildUpdateChange({
      before: makeRecord({ id: 3001, dependOn: 3000 }),
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(main)
    });
    const childAdd: AddChange = {
      ...addChangeOf(makeRecord({ resourceCode: "data:r3" })),
      parentPermissionId: 3000
    };
    const remove = buildRemoveChange({
      records: [main],
      cascadeChildCount: 1,
      reason: "detail-delete",
      summary: summaryOf(main)
    });
    const plan = buildGrantPlan([childUpdate, childAdd, remove])!;
    expect(plan.removes).toEqual([3000]);
    expect(plan.updates).toBeUndefined(); // 子权限 update 静默丢弃
    expect(plan.creates).toBeUndefined(); // 挂父子权限 add 静默丢弃
  });

  it("replace（跨键替换）= removes 旧 + creates 新（同 plan 原子，子权限不迁移）", () => {
    const old = makeRecord({ id: 4000, childCount: 2 });
    const replace = buildReplaceChange({
      removedRecords: [old],
      newKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      cascadeChildCount: 2,
      summary: summaryOf(old)
    });
    const plan = buildGrantPlan([replace])!;
    expect(plan.removes).toEqual([4000]);
    expect(plan.creates).toHaveLength(1);
    expect(plan.creates![0].key.resourceCode).toBe("data:r9");
    expect(plan.creates![0].children).toBeUndefined();
  });
});

// ========== applyDraftToRecords ==========

describe("applyDraftToRecords（草稿生效视图）", () => {
  it("baseline 叠加 update/remove 标记；新增主权限进 mains（负数临时 id）", () => {
    const r1 = makeRecord({ id: 5000 });
    const r2 = makeRecord({ id: 5001, resourceCode: "data:r2" });
    const add = addChangeOf(makeRecord({ resourceCode: "data:r3" }));
    const changes: DraftChange[] = [
      buildUpdateChange({
        before: r1,
        after: { canGrant: true, conditionCode: "c1" },
        summary: summaryOf(r1)
      }),
      buildRemoveChange({
        records: [r2],
        cascadeChildCount: 0,
        reason: "detail-delete",
        summary: summaryOf(r2)
      }),
      add
    ];
    const view = applyDraftToRecords({
      baseline: [r1, r2],
      changes,
      operations: OPS
    });
    expect(view.mains).toHaveLength(3);
    const m1 = view.mains.find(m => m.id === 5000)!;
    expect(m1.draftMark).toBe("update");
    expect(m1.canGrant).toBe(true);
    expect(m1.conditionCode).toBe("c1");
    const m2 = view.mains.find(m => m.id === 5001)!;
    expect(m2.draftMark).toBe("remove");
    const added = view.mains.find(m => m.draftMark === "add")!;
    expect(added.id).toBeLessThan(0);
    expect(added.grantedBits).toBe("2"); // VIEW binaryBit 解析
    expect(view.removedIds.has(5001)).toBe(true);
  });

  it("子权限分组：持久化父按 id 分组；草稿新父按 chg:changeId 虚拟挂载", () => {
    const parent = makeRecord({ id: 6000 });
    const baselineChild = makeRecord({ id: 6001, dependOn: 6000 });
    const draftParent = addChangeOf(makeRecord({ resourceCode: "data:new" }));
    const draftChild: AddChange = {
      ...addChangeOf(makeRecord({ resourceCode: "data:new:c" })),
      parentChangeId: draftParent.changeId
    };
    const view = applyDraftToRecords({
      baseline: [parent, baselineChild],
      changes: [draftParent, draftChild],
      operations: OPS
    });
    expect(
      view.childrenByParent.get(persistedParentKey(6000))!.map(c => c.id)
    ).toEqual([6001]);
    const virtualChildren = view.childrenByParent.get(
      draftParentKey(draftParent.changeId)
    )!;
    expect(virtualChildren).toHaveLength(1);
    expect(virtualChildren[0].resourceCode).toBe("data:new:c");
    // 草稿父 childCount 统计
    const addedParent = view.mains.find(
      m => m.changeId === draftParent.changeId
    )!;
    expect(addedParent.childCount).toBe(1);
  });
});

// ========== applyDialogResultToDraft（方案二全量语义） ==========

function dialogOf(overrides: Partial<DialogResult> = {}): DialogResult {
  return {
    operation: { code: "VIEW", resourceTypeCode: "DATA" },
    scopeMode: "INSTANCE",
    resources: [
      {
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        name: "数据一"
      }
    ],
    conditionCode: null,
    canGrant: false,
    ...overrides
  };
}

describe("applyDialogResultToDraft（弹窗确定语义，匹配仅限 MANUAL）", () => {
  it("分组键不存在 → add（含 ALL 虚拟行 diff）", () => {
    const result = applyDialogResultToDraft({
      baseline: [],
      changes: [],
      dialog: dialogOf(),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    expect(result.changes[0].kind).toBe("add");
  });

  it("同键同条件且 canGrant 不同 → update（按 id）；相同 → 无变更", () => {
    const existing = makeRecord({ canGrant: false });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ canGrant: true }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    expect(result.changes[0].kind).toBe("update");
    const noop = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ canGrant: false }),
      operations: OPS
    });
    expect(noop.changes).toHaveLength(0);
  });

  it("同键不同条件 → add 新分支（同键多条件并存）", () => {
    const existing = makeRecord({ conditionCode: null });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ conditionCode: "office-hours" }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    expect(result.changes[0].kind).toBe("add");
    expect((result.changes[0] as AddChange).recordKey.conditionCode).toBe(
      "office-hours"
    );
  });

  it("取消勾选 → remove（同分组键全部 MANUAL 分支；AUTO_DEP 不进比对保留）", () => {
    const b1 = makeRecord({ id: 7000, conditionCode: null, childCount: 1 });
    const b2 = makeRecord({ id: 7001, conditionCode: "c1" });
    const autoDep = makeRecord({ id: 7002, grantSource: "AUTO_DEP" });
    const result = applyDialogResultToDraft({
      baseline: [b1, b2, autoDep],
      changes: [],
      dialog: dialogOf({ resources: [] }), // 全部取消勾选
      operations: OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(1);
    const removedIds = (removes[0] as Extract<DraftChange, { kind: "remove" }>)
      .records;
    expect(removedIds.map(r => r.id).sort()).toEqual([7000, 7001]);
    expect(removedIds.some(r => r.id === 7002)).toBe(false); // AUTO_DEP 不进比对
    expect(
      (removes[0] as Extract<DraftChange, { kind: "remove" }>).cascadeChildCount
    ).toBe(1);
  });

  it("取消勾选草稿新增 → 撤销 AddChange（不产生 remove）", () => {
    const add = addChangeOf(makeRecord({ id: 0 }));
    const result = applyDialogResultToDraft({
      baseline: [],
      changes: [add],
      dialog: dialogOf({ resources: [] }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(0);
    expect(result.revertedChangeIds).toContain(add.changeId);
  });

  it("重新勾选已标记 remove 的记录 → 撤销 RemoveChange（恢复原记录，保留子权限）", () => {
    const existing = makeRecord({ id: 8000, childCount: 2 });
    const remove = buildRemoveChange({
      records: [existing],
      cascadeChildCount: 2,
      reason: "dialog-uncheck",
      summary: summaryOf(existing)
    });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [remove],
      dialog: dialogOf(),
      operations: OPS
    });
    expect(result.revertedChangeIds).toContain(remove.changeId);
    expect(result.changes.filter(c => c.kind === "remove")).toHaveLength(0);
    // 恢复后 canGrant 与原记录一致 → 无新增 update
    expect(result.changes).toHaveLength(0);
  });

  it("多选 N 资源 → N 条 add（第十四轮多选确定）", () => {
    const result = applyDialogResultToDraft({
      baseline: [],
      changes: [],
      dialog: dialogOf({
        resources: [
          {
            resourceTypeCode: "DATA",
            resourceCode: "data:r1",
            codeType: "default",
            name: "数据一"
          },
          {
            resourceTypeCode: "DATA",
            resourceCode: "data:r2",
            codeType: "default",
            name: "数据二"
          },
          {
            resourceTypeCode: "DATA",
            resourceCode: "data:r3",
            codeType: "default",
            name: "数据三"
          }
        ]
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(3);
    expect(result.changes.every(c => c.kind === "add")).toBe(true);
  });

  it("草稿新增再次确定同键同条件 → 就地改 canGrant（不重复建变更）", () => {
    const first = applyDialogResultToDraft({
      baseline: [],
      changes: [],
      dialog: dialogOf({ canGrant: false }),
      operations: OPS
    });
    const second = applyDialogResultToDraft({
      baseline: [],
      changes: first.changes,
      dialog: dialogOf({ canGrant: true }),
      operations: OPS
    });
    expect(second.changes).toHaveLength(1);
    expect(second.changes[0].kind).toBe("add");
    expect((second.changes[0] as AddChange).recordKey.canGrant).toBe(true);
  });

  it("已有 update 变更再次确定 → 就地改 after；回退为无差异时剔除幽灵变更", () => {
    const existing = makeRecord({ canGrant: false });
    const first = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ canGrant: true }),
      operations: OPS
    });
    expect(first.changes).toHaveLength(1);
    // 改回 false → 与 baseline 无差异 → 幽灵剔除
    const second = applyDialogResultToDraft({
      baseline: [existing],
      changes: first.changes,
      dialog: dialogOf({ canGrant: false }),
      operations: OPS
    });
    expect(second.changes).toHaveLength(0);
  });

  it("取消勾选同时撤销该记录上的 update 变更（updates/removes 不交叉同 id）", () => {
    const existing = makeRecord({ id: 9000, canGrant: false });
    const update = buildUpdateChange({
      before: existing,
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(existing)
    });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [update],
      dialog: dialogOf({ resources: [] }),
      operations: OPS
    });
    expect(result.revertedChangeIds).toContain(update.changeId);
    const plan = buildGrantPlan(result.changes);
    expect(plan?.updates).toBeUndefined();
    expect(plan?.removes).toEqual([9000]);
  });
});

// ========== 问题 2：全局 ALL/INSTANCE 跨类型撤权边界 ==========

describe("applyDialogResultToDraft 跨类型撤权边界（问题 2）", () => {
  const GLOBAL_OPS = [
    {
      code: "MANAGE",
      name: "管理",
      resourceTypeCode: null,
      binaryBit: "16",
      inheritMask: "0"
    }
  ];
  const TYPED_OPS = [
    {
      code: "VIEW",
      name: "查看",
      resourceTypeCode: "DATA",
      binaryBit: "2",
      inheritMask: "0"
    },
    {
      code: "VIEW",
      name: "查看",
      resourceTypeCode: "MENU",
      binaryBit: "2",
      inheritMask: "0"
    }
  ];

  it("全局 ALL 选类型 A：不修改类型 B 的 ALL 授权（问题 2）", () => {
    const dataAll = makeRecord({
      id: 7100,
      resourceTypeCode: "DATA",
      resourceCode: null,
      codeType: null,
      operationCode: "MANAGE",
      scopeMode: "ALL",
      grantedBits: "16"
    });
    const menuAll = makeRecord({
      id: 7101,
      resourceTypeCode: "MENU",
      resourceCode: null,
      codeType: null,
      operationCode: "MANAGE",
      scopeMode: "ALL",
      grantedBits: "16"
    });
    const result = applyDialogResultToDraft({
      baseline: [dataAll, menuAll],
      changes: [],
      dialog: {
        operation: { code: "MANAGE", resourceTypeCode: null },
        scopeMode: "ALL",
        resources: [
          {
            resourceTypeCode: "DATA",
            resourceCode: null,
            codeType: null,
            name: "全部资源（DATA）"
          }
        ],
        conditionCode: null,
        canGrant: false
      },
      operations: GLOBAL_OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(0); // DATA 保留（同键同条件），MENU 不在比对集
  });

  it("全局 INSTANCE 只保留类型 A：类型 B 未勾选产生 remove（问题 2）", () => {
    const dataInst = makeRecord({
      id: 7200,
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "MANAGE",
      scopeMode: "INSTANCE",
      grantedBits: "16"
    });
    const menuInst = makeRecord({
      id: 7201,
      resourceTypeCode: "MENU",
      resourceCode: "menu:m1",
      codeType: "default",
      operationCode: "MANAGE",
      scopeMode: "INSTANCE",
      grantedBits: "16"
    });
    const result = applyDialogResultToDraft({
      baseline: [dataInst, menuInst],
      changes: [],
      dialog: {
        operation: { code: "MANAGE", resourceTypeCode: null },
        scopeMode: "INSTANCE",
        resources: [
          {
            resourceTypeCode: "DATA",
            resourceCode: "data:r1",
            codeType: "default",
            name: "数据一"
          }
        ],
        conditionCode: null,
        canGrant: false
      },
      operations: GLOBAL_OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(1);
    const removedIds = (
      removes[0] as Extract<DraftChange, { kind: "remove" }>
    ).records.map(r => r.id);
    expect(removedIds).toEqual([7201]); // MENU 未勾选 -> 撤权
  });

  it("全局 INSTANCE 空集合：撤销所有类型的该操作实例授权（问题 2）", () => {
    const dataInst = makeRecord({
      id: 7300,
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "MANAGE",
      scopeMode: "INSTANCE",
      grantedBits: "16"
    });
    const menuInst = makeRecord({
      id: 7301,
      resourceTypeCode: "MENU",
      resourceCode: "menu:m1",
      codeType: "default",
      operationCode: "MANAGE",
      scopeMode: "INSTANCE",
      grantedBits: "16"
    });
    const result = applyDialogResultToDraft({
      baseline: [dataInst, menuInst],
      changes: [],
      dialog: {
        operation: { code: "MANAGE", resourceTypeCode: null },
        scopeMode: "INSTANCE",
        resources: [],
        conditionCode: null,
        canGrant: false
      },
      operations: GLOBAL_OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(2); // 两个不同资源 resKey -> 两个 remove
    const allRemovedIds = removes.flatMap(r =>
      (r as Extract<DraftChange, { kind: "remove" }>).records.map(rec => rec.id)
    );
    expect(allRemovedIds.sort()).toEqual([7300, 7301]); // 全部撤权
  });

  it("专属操作始终只影响其所属资源类型（问题 2）", () => {
    const dataView = makeRecord({
      id: 7400,
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE",
      grantedBits: "2"
    });
    const menuView = makeRecord({
      id: 7401,
      resourceTypeCode: "MENU",
      resourceCode: "menu:m1",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE",
      grantedBits: "2"
    });
    const result = applyDialogResultToDraft({
      baseline: [dataView, menuView],
      changes: [],
      dialog: {
        operation: { code: "VIEW", resourceTypeCode: "DATA" },
        scopeMode: "INSTANCE",
        resources: [
          {
            resourceTypeCode: "DATA",
            resourceCode: "data:r1",
            codeType: "default",
            name: "数据一"
          }
        ],
        conditionCode: null,
        canGrant: false
      },
      operations: TYPED_OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(0); // DATA 保留，MENU 不在比对集（专属限本类型）
  });
});

// ========== findBranchConflict / cellDraftMark ==========

describe("findBranchConflict（S4 改条件撞已占用条件 → 前端禁用）", () => {
  it("完整键同键同 conditionCode 已有 MANUAL 分支 → 命中；remove 标记不命中", () => {
    const existing = makeRecord({ conditionCode: "c1" });
    const key = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    expect(findBranchConflict([existing], key, "c1")?.id).toBe(existing.id);
    expect(findBranchConflict([existing], key, "c2")).toBeUndefined();
    expect(
      findBranchConflict(
        [{ ...existing, draftMark: "remove" as const }],
        key,
        "c1"
      )
    ).toBeUndefined();
  });
});

describe("cellDraftMark（§6.2 部分移除与整格移除标记区分）", () => {
  const markInfo = new Map([
    [1, { mark: "remove" as const, changeId: "c1" }],
    [2, { mark: "add" as const, changeId: "c2" }],
    [3, { mark: "update" as const, changeId: "c3" }]
  ]);
  it("全部来源被移除 → remove（整格删除线）；非最后一条 → partial-remove（⧄）", () => {
    expect(cellDraftMark({ sources: [{ recordId: 1 }], markInfo }).mark).toBe(
      "remove"
    );
    expect(
      cellDraftMark({ sources: [{ recordId: 1 }, { recordId: 9 }], markInfo })
        .mark
    ).toBe("partial-remove");
  });
  it("整格新增 → add；已有格新增分支 → partial-add；内容变化 → update", () => {
    expect(cellDraftMark({ sources: [{ recordId: 2 }], markInfo }).mark).toBe(
      "add"
    );
    expect(
      cellDraftMark({ sources: [{ recordId: 2 }, { recordId: 9 }], markInfo })
        .mark
    ).toBe("partial-add");
    expect(cellDraftMark({ sources: [{ recordId: 3 }], markInfo }).mark).toBe(
      "update"
    );
    expect(cellDraftMark({ sources: [{ recordId: 9 }], markInfo }).mark).toBe(
      null
    );
  });
});

// ========== resolveGrantedBits（问题 7：纯全局操作类型解析） ==========

describe("resolveGrantedBits（问题 7：纯全局操作类型 grantedBits 解析）", () => {
  it("纯全局操作类型能解析 grantedBits（无专属定义回退全局）", () => {
    const ops = [
      {
        code: "MANAGE",
        name: "管理",
        resourceTypeCode: null,
        binaryBit: "16",
        inheritMask: "0"
      }
    ];
    const bits = resolveGrantedBits(
      {
        resourceTypeCode: "WIDGET",
        resourceCode: "w1",
        codeType: "default",
        operationCode: "MANAGE",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      ops
    );
    expect(bits).toBe("16");
  });

  it("operationCode 为 null -> 0（组合位不解析）", () => {
    expect(
      resolveGrantedBits(
        {
          resourceTypeCode: "WIDGET",
          resourceCode: "w1",
          codeType: "default",
          operationCode: null,
          scopeMode: "INSTANCE",
          conditionCode: null,
          canGrant: false
        },
        []
      )
    ).toBe("0");
  });

  it("专属优先：同 code 专属定义覆盖全局定义的 binaryBit", () => {
    const ops = [
      {
        code: "VIEW",
        name: "全局查看",
        resourceTypeCode: null,
        binaryBit: "32",
        inheritMask: "0"
      },
      {
        code: "VIEW",
        name: "专属查看",
        resourceTypeCode: "DATA",
        binaryBit: "2",
        inheritMask: "0"
      }
    ];
    const bits = resolveGrantedBits(
      {
        resourceTypeCode: "DATA",
        resourceCode: "d1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      ops
    );
    expect(bits).toBe("2"); // 专属 binaryBit=2，非全局 32
  });
});

describe("computePreset（评审问题 1）", () => {
  function makeEff(
    overrides: Partial<RolePermissionItem> = {},
    draftMark: "add" | "update" | "remove" | null = null
  ): EffectiveRecord {
    return { ...makeRecord(overrides), draftMark };
  }

  it("op null -> INSTANCE + 空勾选", () => {
    const preset = computePreset({ op: null, records: [] });
    expect(preset.scopeMode).toBe("INSTANCE");
    expect(preset.checkedTripleKeys.size).toBe(0);
  });

  it("INSTANCE 记录 -> INSTANCE + 勾选 tripleKeys", () => {
    const records = [
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      }),
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: "data:r2",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      })
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect(preset.scopeMode).toBe("INSTANCE");
    expect([...preset.checkedTripleKeys]).toEqual([
      "DATA:data:r1:default",
      "DATA:data:r2:default"
    ]);
  });

  it("ALL 记录存在 -> 默认 ALL（hasAll 优先）", () => {
    const records = [
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: null,
        codeType: null,
        operationCode: "VIEW",
        scopeMode: "ALL"
      })
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect(preset.scopeMode).toBe("ALL");
    expect(preset.checkedTripleKeys.size).toBe(0);
  });

  it("ALL + INSTANCE 并存 -> ALL + INSTANCE tripleKeys", () => {
    const records = [
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: null,
        codeType: null,
        operationCode: "VIEW",
        scopeMode: "ALL"
      }),
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      })
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect(preset.scopeMode).toBe("ALL");
    expect([...preset.checkedTripleKeys]).toEqual(["DATA:data:r1:default"]);
  });

  it("AUTO_DEP 记录忽略", () => {
    const records = [
      makeEff({
        grantSource: "AUTO_DEP",
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      })
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect(preset.checkedTripleKeys.size).toBe(0);
    expect(preset.scopeMode).toBe("INSTANCE");
  });

  it("draftMark=remove 记录忽略", () => {
    const records = [
      makeEff(
        {
          resourceTypeCode: "DATA",
          resourceCode: "data:r1",
          codeType: "default",
          operationCode: "VIEW",
          scopeMode: "INSTANCE"
        },
        "remove"
      )
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect(preset.checkedTripleKeys.size).toBe(0);
  });

  it("专属操作限本类型：其他类型记录忽略", () => {
    const records = [
      makeEff({
        resourceTypeCode: "OTHER",
        resourceCode: "o:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      }),
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE"
      })
    ];
    const preset = computePreset({
      op: { code: "VIEW", resourceTypeCode: "DATA" },
      records
    });
    expect([...preset.checkedTripleKeys]).toEqual(["DATA:data:r1:default"]);
  });

  it("全局操作不限类型：所有类型 INSTANCE 记录勾选", () => {
    const records = [
      makeEff({
        resourceTypeCode: "DATA",
        resourceCode: "data:r1",
        codeType: "default",
        operationCode: "GLOBAL_OP",
        scopeMode: "INSTANCE"
      }),
      makeEff({
        resourceTypeCode: "OTHER",
        resourceCode: "o:r1",
        codeType: "default",
        operationCode: "GLOBAL_OP",
        scopeMode: "INSTANCE"
      })
    ];
    const preset = computePreset({
      op: { code: "GLOBAL_OP", resourceTypeCode: null },
      records
    });
    expect(preset.checkedTripleKeys.size).toBe(2);
  });
});
