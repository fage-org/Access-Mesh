/**
 * 草稿 → plan 构建 / 草稿生效视图 / 弹窗确定语义（方案二全量比对）测试。
 * 对齐设计 §4（单记录模型/确定语义）/ §6.4（单入口提交）/ §6.2（diff 标记）。
 */
import { describe, it, expect } from "vitest";
import type { RolePermissionItem } from "@/api/permission-grant";
import {
  applyDialogResultToDraft,
  applyDialogResultsToDraft,
  applyDraftToRecords,
  applyFocusAttributes,
  buildAddChange,
  buildGrantPlan,
  buildRemoveChange,
  buildReplaceChange,
  buildSummary,
  buildUpdateChange,
  cellDraftMark,
  copySlotAttributes,
  createSlotDraftState,
  draftParentKey,
  expandSuspended,
  findSlotRecord,
  groupKeyOf,
  normalizeChildGrantKey,
  parentLocateOf,
  persistedParentKey,
  resourceGroupKeyOf,
  resolveGrantedBits,
  computePreset,
  resumeSlot,
  slotKeyOf,
  uncheckSlot,
  type DialogResult,
  type EffectiveRecord,
  type FocusSlot,
  type SlotDraftState
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

describe("groupKeyOf（单记录键模型）", () => {
  it("operationKey = operationCode ?? bits:+grantedBits（组合位记录以位串为键不折叠）", () => {
    const a = makeRecord({ operationCode: "VIEW", grantedBits: "2" });
    const b = makeRecord({ operationCode: null, grantedBits: "6" });
    const c = makeRecord({ operationCode: null, grantedBits: "10" });
    expect(groupKeyOf(a)).toContain("VIEW");
    expect(groupKeyOf(b)).toContain("bits:6");
    expect(groupKeyOf(b)).not.toBe(groupKeyOf(c));
  });
});

describe("normalizeChildGrantKey（子权限属性收敛）", () => {
  it("无论输入为何，子权限都不携带条件或再授予属性", () => {
    expect(
      normalizeChildGrantKey({
        resourceTypeCode: "MENU",
        resourceCode: "menu:role",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: "office-hours",
        canGrant: true
      })
    ).toEqual({
      resourceTypeCode: "MENU",
      resourceCode: "menu:role",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE",
      conditionCode: null,
      canGrant: false
    });
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

/** 记录 → 焦点槽位（v3.1 记录级编辑） */
function slotOf(record: RolePermissionItem) {
  return {
    resourceTypeCode: record.resourceTypeCode,
    resourceCode: record.resourceCode,
    codeType: record.codeType,
    operationCode: record.operationCode ?? "",
    scopeMode: record.scopeMode
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

  it("勾选不重写已有记录属性；focusAttributes 驱动 update（按 id）；相同 → 无变更", () => {
    const existing = makeRecord({ canGrant: false });
    // v3.1：勾选仅表达授权存在性，不重写已有记录属性
    const checked = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ canGrant: true }),
      operations: OPS
    });
    expect(checked.changes).toHaveLength(0);
    // 属性修改只作用于聚焦记录（记录级）
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: null, canGrant: true }
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    expect(result.changes[0].kind).toBe("update");
    const noop = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: null, canGrant: false }
      }),
      operations: OPS
    });
    expect(noop.changes).toHaveLength(0);
  });

  it("focusAttributes 修改已有授权条件 → update（按 id），不新增记录", () => {
    const existing = makeRecord({ conditionCode: null });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: "office-hours", canGrant: false }
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    expect(result.changes[0].kind).toBe("update");
    const update = result.changes[0] as Extract<
      DraftChange,
      { kind: "update" }
    >;
    expect(update.recordId).toBe(existing.id);
    expect(update.after.conditionCode).toBe("office-hours");
  });

  it("已有授权保持勾选且设置未修改 → 保留原属性", () => {
    const existing = makeRecord({
      conditionCode: "office-hours",
      canGrant: false
    });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        conditionCode: null,
        canGrant: true,
        untouchedResourceKeys: [resourceGroupKeyOf(existing)]
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(0);
  });

  it("focusAttributes 带条件 + canGrant=true → canGrant 强制 false（条件不可转授兜底，🔧 T-FE-039）", () => {
    const existing = makeRecord({ conditionCode: null, canGrant: false });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: "office-hours", canGrant: true }
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(1);
    const update = result.changes[0] as Extract<
      DraftChange,
      { kind: "update" }
    >;
    expect(update.after.conditionCode).toBe("office-hours");
    expect(update.after.canGrant).toBe(false);
  });

  it("聚焦未授权资源（无 MANUAL 记录）→ focusAttributes 不生成变更（设置区只读默认值）", () => {
    const result = applyDialogResultToDraft({
      baseline: [],
      changes: [],
      dialog: dialogOf({
        resources: [],
        focusSlot: slotOf(makeRecord({ id: 9999 })),
        focusAttributes: { conditionCode: "office-hours", canGrant: false }
      }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(0);
  });

  it("带条件的已有授权尝试置 canGrant=true → 兜底为 false 后无变更", () => {
    const existing = makeRecord({ conditionCode: "office-hours" });
    const result = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({ conditionCode: "office-hours", canGrant: true }),
      operations: OPS
    });
    expect(result.changes).toHaveLength(0);
  });

  it("取消勾选 → remove（MANUAL 直接授权；AUTO_DEP 不进比对保留）", () => {
    const b1 = makeRecord({ id: 7000, conditionCode: null, childCount: 1 });
    const autoDep = makeRecord({ id: 7002, grantSource: "AUTO_DEP" });
    const result = applyDialogResultToDraft({
      baseline: [b1, autoDep],
      changes: [],
      dialog: dialogOf({ resources: [] }), // 全部取消勾选
      operations: OPS
    });
    const removes = result.changes.filter(c => c.kind === "remove");
    expect(removes).toHaveLength(1);
    const removedIds = (removes[0] as Extract<DraftChange, { kind: "remove" }>)
      .records;
    expect(removedIds.map(r => r.id)).toEqual([7000]);
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

  it("草稿新增后再以 focusAttributes 聚焦同键 → 就地改授权属性（不重复建变更）", () => {
    const first = applyDialogResultToDraft({
      baseline: [],
      changes: [],
      dialog: dialogOf(), // add 默认属性：无条件、不可再授予
      operations: OPS
    });
    const added = first.changes[0] as AddChange;
    expect(added.recordKey.canGrant).toBe(false);
    const second = applyDialogResultToDraft({
      baseline: [],
      changes: first.changes,
      dialog: dialogOf({
        focusSlot: {
          resourceTypeCode: added.recordKey.resourceTypeCode,
          resourceCode: added.recordKey.resourceCode,
          codeType: added.recordKey.codeType,
          operationCode: added.recordKey.operationCode ?? "",
          scopeMode: added.recordKey.scopeMode
        },
        focusAttributes: { conditionCode: null, canGrant: true }
      }),
      operations: OPS
    });
    expect(second.changes).toHaveLength(1);
    expect(second.changes[0].kind).toBe("add");
    expect((second.changes[0] as AddChange).recordKey.canGrant).toBe(true);
  });

  it("已有 update 变更再次 focusAttributes → 就地改 after；回退为无差异时剔除幽灵变更", () => {
    const existing = makeRecord({ canGrant: false });
    const first = applyDialogResultToDraft({
      baseline: [existing],
      changes: [],
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: null, canGrant: true }
      }),
      operations: OPS
    });
    expect(first.changes).toHaveLength(1);
    // 改回 false → 与 baseline 无差异 → 幽灵剔除
    const second = applyDialogResultToDraft({
      baseline: [existing],
      changes: first.changes,
      dialog: dialogOf({
        focusSlot: slotOf(existing),
        focusAttributes: { conditionCode: null, canGrant: false }
      }),
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

describe("applyDialogResultToDraft 类型隔离边界（问题 2；全局操作概念已退役，仅类型内边界）", () => {
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

  it("批量确认按顺序组合 ALL 撤销与 INSTANCE 最终集合", () => {
    const dataAll = makeRecord({
      id: 7450,
      resourceTypeCode: "DATA",
      resourceCode: null,
      codeType: null,
      operationCode: "VIEW",
      scopeMode: "ALL",
      grantedBits: "2"
    });
    const existingInstance = makeRecord({
      id: 7451,
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE",
      grantedBits: "2"
    });
    const existingKey = resourceGroupKeyOf(existingInstance);

    const result = applyDialogResultsToDraft({
      baseline: [dataAll, existingInstance],
      changes: [],
      dialogs: [
        {
          operation: { code: "VIEW", resourceTypeCode: "DATA" },
          scopeMode: "ALL",
          resources: [],
          conditionCode: null,
          canGrant: false
        },
        {
          operation: { code: "VIEW", resourceTypeCode: "DATA" },
          scopeMode: "INSTANCE",
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
            }
          ],
          untouchedResourceKeys: [existingKey],
          conditionCode: null,
          canGrant: false
        }
      ],
      operations: TYPED_OPS
    });

    const removes = result.changes.filter(
      (change): change is Extract<DraftChange, { kind: "remove" }> =>
        change.kind === "remove"
    );
    const adds = result.changes.filter(
      (change): change is AddChange => change.kind === "add"
    );
    expect(removes).toHaveLength(1);
    expect(removes[0].records.map(record => record.id)).toEqual([7450]);
    expect(adds).toHaveLength(1);
    expect(adds[0].recordKey.resourceCode).toBe("data:r2");
    expect(
      removes.flatMap(change => change.records.map(record => record.id))
    ).not.toContain(7451);
  });

  it("同一次授权弹窗可为新主权限挂载跨类型子权限", () => {
    const mainResult = applyDialogResultsToDraft({
      baseline: [],
      changes: [],
      dialogs: [
        {
          operation: { code: "VIEW", resourceTypeCode: "DATA" },
          scopeMode: "INSTANCE",
          resources: [
            {
              resourceTypeCode: "DATA",
              resourceCode: "data:new",
              codeType: "default",
              name: "新数据"
            }
          ],
          conditionCode: null,
          canGrant: false
        }
      ],
      operations: TYPED_OPS
    });
    const parent = mainResult.changes.find(
      (change): change is AddChange =>
        change.kind === "add" && change.parentChangeId == null
    );
    expect(parent).toBeDefined();

    const childKey = {
      resourceTypeCode: "MENU",
      resourceCode: "menu:child",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    const child = buildAddChange({
      recordKey: childKey,
      parentChangeId: parent!.changeId,
      summary: buildSummary({
        recordKey: childKey,
        resourceLabel: "子菜单"
      })
    });
    const changes = [...mainResult.changes, child];
    const view = applyDraftToRecords({
      baseline: [],
      changes,
      operations: TYPED_OPS
    });
    expect(
      view.childrenByParent.get(draftParentKey(parent!.changeId))
    ).toHaveLength(1);

    const plan = buildGrantPlan(changes);
    expect(plan?.creates).toHaveLength(1);
    expect(plan?.creates?.[0].key.resourceCode).toBe("data:new");
    expect(plan?.creates?.[0].children?.[0]).toMatchObject({
      resourceTypeCode: "MENU",
      resourceCode: "menu:child",
      operationCode: "VIEW"
    });
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

// ========== resolveGrantedBits（旧响应缺 grantedBits 的回退解析） ==========

describe("resolveGrantedBits（旧响应缺 grantedBits 的回退解析）", () => {
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
});

// ========== parentLocateOf（T-FE-038 review P2-4 定位投影） ==========

describe("parentLocateOf（子权限变更投影到父主权限单元格）", () => {
  const parent = addChangeOf(makeRecord({ id: 9001 })); // DATA/data:r1/VIEW 主权限 add
  const mains = [makeRecord({ id: 9001 })]; // 持久化父（effective.mains 视角）

  it("主权限变更（无父）→ null（用变更自身定位）", () => {
    expect(parentLocateOf(parent, [parent], mains)).toBeNull();
    const remove = buildRemoveChange({
      records: [makeRecord({ id: 9002 })],
      cascadeChildCount: 0,
      reason: "detail-delete",
      summary: summaryOf(makeRecord({ id: 9002 }))
    });
    expect(parentLocateOf(remove, [], mains)).toBeNull();
  });

  it("add + parentChangeId（草稿父）→ 父 add 的 recordKey", () => {
    const child: AddChange = {
      ...addChangeOf(
        makeRecord({
          resourceTypeCode: "REPORT",
          resourceCode: "report:r1"
        })
      ),
      parentChangeId: parent.changeId
    };
    const locate = parentLocateOf(child, [parent], mains);
    expect(locate).toEqual({
      resourceTypeCode: "DATA",
      resourceCode: "data:r1",
      codeType: "default",
      operationCode: "VIEW"
    });
  });

  it("add + parentPermissionId（持久化父）→ effective.mains 匹配的主权限", () => {
    const child: AddChange = {
      ...addChangeOf(
        makeRecord({
          resourceTypeCode: "REPORT",
          resourceCode: "report:r1"
        })
      ),
      parentPermissionId: 9001
    };
    const locate = parentLocateOf(child, [], mains);
    expect(locate?.resourceTypeCode).toBe("DATA");
    expect(locate?.operationCode).toBe("VIEW");
  });

  it("update（子权限编辑，before.dependOn）→ 父主权限", () => {
    const childUpdate = buildUpdateChange({
      before: makeRecord({
        id: 9003,
        dependOn: 9001,
        resourceTypeCode: "REPORT"
      }),
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(makeRecord({ id: 9003 }))
    });
    const locate = parentLocateOf(childUpdate, [], mains);
    expect(locate?.resourceTypeCode).toBe("DATA");
    expect(locate?.resourceCode).toBe("data:r1");
  });

  it("remove（子权限删除，records[0].dependOn）→ 父主权限", () => {
    const childRemove = buildRemoveChange({
      records: [
        makeRecord({
          id: 9004,
          dependOn: 9001,
          resourceTypeCode: "REPORT"
        })
      ],
      cascadeChildCount: 0,
      reason: "detail-delete",
      summary: summaryOf(makeRecord({ id: 9004 }))
    });
    const locate = parentLocateOf(childRemove, [], mains);
    expect(locate?.resourceTypeCode).toBe("DATA");
    expect(locate?.operationCode).toBe("VIEW");
  });

  it("replace（子权限跨键替换，removedRecords[0].dependOn）→ 父主权限", () => {
    const oldChild = makeRecord({
      id: 9005,
      dependOn: 9001,
      resourceTypeCode: "REPORT",
      childCount: 0
    });
    const replace = buildReplaceChange({
      removedRecords: [oldChild],
      newKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      cascadeChildCount: 0,
      summary: summaryOf(oldChild)
    });
    const locate = parentLocateOf(replace, [], mains);
    expect(locate?.resourceTypeCode).toBe("DATA");
    expect(locate?.operationCode).toBe("VIEW");
  });
});

// ========== v3.1 记录槽位 reducer（T-FE-040：焦点生命周期/显式复制/suspended 双路径） ==========

/** 测试焦点槽位（DATA · data:r1 · default · VIEW · INSTANCE） */
const SLOT: FocusSlot = {
  resourceTypeCode: "DATA",
  resourceCode: "data:r1",
  codeType: "default",
  operationCode: "VIEW",
  scopeMode: "INSTANCE"
};

describe("slotKeyOf / findSlotRecord（焦点槽位模型）", () => {
  it("slotKeyOf 与 groupKeyOf 同构（完整授权键）", () => {
    expect(slotKeyOf(SLOT)).toBe(groupKeyOf({ ...SLOT, grantedBits: "2" }));
  });

  it("findSlotRecord 仅匹配 MANUAL 主记录（排除 AUTO_DEP / remove 标记）", () => {
    const record = makeRecord({ id: 9001 });
    const autoDep = makeRecord({
      id: 9003,
      resourceCode: "data:r3",
      grantSource: "AUTO_DEP"
    });
    const removed = makeRecord({ id: 9004, resourceCode: "data:r4" });
    const view = applyDraftToRecords({
      baseline: [record, autoDep],
      changes: [
        buildRemoveChange({
          records: [removed],
          cascadeChildCount: 0,
          reason: "detail-delete",
          summary: summaryOf(removed)
        })
      ],
      operations: OPS
    });
    expect(findSlotRecord(view, slotOf(record))?.id).toBe(9001);
    expect(findSlotRecord(view, slotOf(autoDep))).toBeNull(); // AUTO_DEP 不进比对
    expect(findSlotRecord(view, slotOf(removed))).toBeNull(); // remove 标记视同不存在
  });
});

describe("uncheckSlot（取消勾选 → suspended 暂存，§6.1）", () => {
  it("baseline 路径：持久化记录入 suspended（不生成 remove），update 草稿随记录移出", () => {
    const existing = makeRecord({ id: 9001, canGrant: false });
    const update = buildUpdateChange({
      before: existing,
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(existing)
    });
    const state = createSlotDraftState([update]);
    const view = applyDraftToRecords({
      baseline: [existing],
      changes: state.changes,
      operations: OPS
    });
    const next = uncheckSlot(state, { slot: slotOf(existing), view });
    expect(next.changes).toHaveLength(0); // update 草稿移出，不生成 remove
    const stashed = next.suspended.get(slotKeyOf(slotOf(existing)));
    expect(stashed?.persistedRecord?.id).toBe(9001);
    // 快照属性取生效视图（含未提交的 update）
    expect(stashed?.attributes).toEqual({
      conditionCode: null,
      canGrant: true
    });
  });

  it("add 路径：add 草稿及其虚拟挂载子权限移出（确认时取消整个变更组）", () => {
    const parentAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: true
      },
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const childAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "REPORT",
        resourceCode: "report:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      parentChangeId: parentAdd.changeId,
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const state = createSlotDraftState([parentAdd, childAdd]);
    const view = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    const slot: FocusSlot = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r9",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE"
    };
    const next = uncheckSlot(state, { slot, view });
    expect(next.changes).toHaveLength(0); // add 主变更 + 子权限变更全部移出
    const stashed = next.suspended.get(slotKeyOf(slot));
    expect(stashed?.addChangeId).toBe(parentAdd.changeId);
    expect(stashed?.childChanges?.map(c => c.changeId)).toEqual([
      childAdd.changeId
    ]);
  });

  it("取消勾选焦点记录 → 焦点清空；取消勾选非焦点记录 → 焦点保持", () => {
    const existing = makeRecord({ id: 9001 });
    const other = makeRecord({ id: 9002, resourceCode: "data:r2" });
    const key = slotKeyOf(slotOf(existing));
    const state: SlotDraftState = {
      changes: [],
      suspended: new Map(),
      focusSlotKey: key
    };
    const view = applyDraftToRecords({
      baseline: [existing, other],
      changes: [],
      operations: OPS
    });
    const next = uncheckSlot(state, { slot: slotOf(existing), view });
    expect(next.focusSlotKey).toBeNull(); // 焦点记录被取消 → 焦点清空
    const next2 = uncheckSlot(state, { slot: slotOf(other), view });
    expect(next2.focusSlotKey).toBe(key); // 非焦点记录取消 → 焦点保持
  });
});

describe("resumeSlot（重新勾选恢复）", () => {
  it("suspended add 路径 → 按快照属性重建 add（不生成 remove）", () => {
    const key = slotKeyOf(SLOT);
    const state: SlotDraftState = {
      changes: [],
      suspended: new Map([
        [
          key,
          {
            slotKey: key,
            addChangeId: "add-1",
            attributes: { conditionCode: "office-hours", canGrant: false }
          }
        ]
      ]),
      focusSlotKey: null
    };
    const view = applyDraftToRecords({
      baseline: [],
      changes: [],
      operations: OPS
    });
    const next = resumeSlot(state, {
      slot: SLOT,
      view,
      baseline: [],
      operations: OPS
    });
    expect(next.changes).toHaveLength(1);
    expect(next.changes[0].kind).toBe("add");
    const add = next.changes[0] as AddChange;
    expect(add.recordKey.conditionCode).toBe("office-hours");
    expect(add.recordKey.canGrant).toBe(false);
    expect(next.suspended.has(key)).toBe(false);
  });

  it("suspended addChangeId 兜底路径 → 子权限 parentChangeId 重映射到重建父（三审 P3）", () => {
    const key = slotKeyOf(SLOT);
    const childAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "REPORT",
        resourceCode: "r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      parentChangeId: "add-1",
      summary: buildSummary({
        recordKey: {
          resourceTypeCode: "REPORT",
          resourceCode: "r1",
          codeType: "default",
          operationCode: "VIEW",
          scopeMode: "INSTANCE",
          conditionCode: null,
          canGrant: false
        },
        resourceLabel: "r1"
      })
    });
    const state: SlotDraftState = {
      changes: [],
      suspended: new Map([
        [
          key,
          {
            slotKey: key,
            addChangeId: "add-1",
            attributes: { conditionCode: "office-hours", canGrant: false },
            childChanges: [childAdd]
          }
        ]
      ]),
      focusSlotKey: null
    };
    const view = applyDraftToRecords({
      baseline: [],
      changes: [],
      operations: OPS
    });
    const next = resumeSlot(state, {
      slot: SLOT,
      view,
      baseline: [],
      operations: OPS
    });
    expect(next.changes).toHaveLength(2);
    const add = next.changes[0] as AddChange;
    expect(add.kind).toBe("add");
    const child = next.changes[1] as AddChange;
    expect(child.kind).toBe("add");
    expect(child.parentChangeId).toBe(add.changeId);
    expect(child.recordKey.resourceCode).toBe("r1");
    expect(next.suspended.has(key)).toBe(false);
  });

  it("suspended baseline 路径 → 属性差异生成 update；子权限草稿随主记录恢复", () => {
    const existing = makeRecord({ id: 9001, canGrant: false });
    const childAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "REPORT",
        resourceCode: "report:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      parentPermissionId: 9001,
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const key = slotKeyOf(slotOf(existing));
    const state: SlotDraftState = {
      changes: [],
      suspended: new Map([
        [
          key,
          {
            slotKey: key,
            persistedRecord: existing,
            childChanges: [childAdd],
            attributes: { conditionCode: null, canGrant: true }
          }
        ]
      ]),
      focusSlotKey: null
    };
    const view = applyDraftToRecords({
      baseline: [existing],
      changes: [],
      operations: OPS
    });
    const next = resumeSlot(state, {
      slot: slotOf(existing),
      view,
      baseline: [existing],
      operations: OPS
    });
    // 子权限草稿恢复 + 属性差异 update
    expect(next.changes.map(c => c.changeId)).toContain(childAdd.changeId);
    const update = next.changes.find(c => c.kind === "update") as Extract<
      DraftChange,
      { kind: "update" }
    >;
    expect(update.recordId).toBe(9001);
    expect(update.after.canGrant).toBe(true);
    expect(next.suspended.has(key)).toBe(false);
  });

  it("普通勾选：撤销该槽位 remove 标记变更（恢复原记录保留子权限）", () => {
    const existing = makeRecord({ id: 9001 });
    const remove = buildRemoveChange({
      records: [existing],
      cascadeChildCount: 0,
      reason: "dialog-uncheck",
      summary: summaryOf(existing)
    });
    const state = createSlotDraftState([remove]);
    const view = applyDraftToRecords({
      baseline: [existing],
      changes: state.changes,
      operations: OPS
    });
    const next = resumeSlot(state, {
      slot: slotOf(existing),
      view,
      baseline: [existing],
      operations: OPS
    });
    expect(next.changes).toHaveLength(0); // remove 撤销，无新变更
  });

  it("普通勾选：无 MANUAL 记录 → add 默认属性（无条件、不可再授予）", () => {
    const state = createSlotDraftState([]);
    const view = applyDraftToRecords({
      baseline: [],
      changes: [],
      operations: OPS
    });
    const next = resumeSlot(state, {
      slot: SLOT,
      view,
      baseline: [],
      operations: OPS
    });
    expect(next.changes).toHaveLength(1);
    const add = next.changes[0] as AddChange;
    expect(add.recordKey.conditionCode).toBeNull();
    expect(add.recordKey.canGrant).toBe(false);
  });
});

describe("applyFocusAttributes（设置区修改只作用于聚焦记录）", () => {
  it("持久化记录 → update（按 id）；相同属性 → 无变更", () => {
    const existing = makeRecord({ id: 9001, canGrant: false });
    const view = applyDraftToRecords({
      baseline: [existing],
      changes: [],
      operations: OPS
    });
    const next = applyFocusAttributes(createSlotDraftState([]), {
      slot: slotOf(existing),
      attributes: { conditionCode: null, canGrant: true },
      view
    });
    expect(next.changes).toHaveLength(1);
    expect(next.changes[0].kind).toBe("update");
    const noop = applyFocusAttributes(createSlotDraftState([]), {
      slot: slotOf(existing),
      attributes: { conditionCode: null, canGrant: false },
      view
    });
    expect(noop.changes).toHaveLength(0);
  });

  it("add 草稿 → 就地改（不重复建变更）", () => {
    const add = buildAddChange({
      recordKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const state = createSlotDraftState([add]);
    const view = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    const slot: FocusSlot = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r9",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE"
    };
    const next = applyFocusAttributes(state, {
      slot,
      attributes: { conditionCode: "office-hours", canGrant: true },
      view
    });
    expect(next.changes).toHaveLength(1);
    expect(next.changes[0].kind).toBe("add");
    const recordKey = (next.changes[0] as AddChange).recordKey;
    expect(recordKey.conditionCode).toBe("office-hours");
    expect(recordKey.canGrant).toBe(false); // 条件非空 → canGrant 强制 false
  });

  it("无 MANUAL 记录（未授权资源）→ 不生成变更（设置区只读默认值）", () => {
    const view = applyDraftToRecords({
      baseline: [],
      changes: [],
      operations: OPS
    });
    const next = applyFocusAttributes(createSlotDraftState([]), {
      slot: SLOT,
      attributes: { conditionCode: "office-hours", canGrant: false },
      view
    });
    expect(next.changes).toHaveLength(0);
  });
});

describe("copySlotAttributes（显式复制，设计 §4 v3.1）", () => {
  const source = makeRecord({ id: 9001, conditionCode: "office-hours" });
  const target = makeRecord({
    id: 9002,
    resourceCode: "data:r2",
    canGrant: false
  });
  const slotOfTarget: FocusSlot = {
    resourceTypeCode: "DATA",
    resourceCode: "data:r2",
    codeType: "default",
    operationCode: "VIEW",
    scopeMode: "INSTANCE"
  };

  it("目标持久化记录 → update 合并；禁止同 id 多条（先撤销既有 update）", () => {
    const staleUpdate = buildUpdateChange({
      before: target,
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(target)
    });
    const state = createSlotDraftState([staleUpdate]);
    const view = applyDraftToRecords({
      baseline: [source, target],
      changes: state.changes,
      operations: OPS
    });
    const next = copySlotAttributes(state, {
      sourceSlot: slotOf(source),
      targetSlots: [slotOfTarget],
      view
    });
    // 既有 update 撤销 + 新 update 合并 → 恰好 1 条 update（同 id 不交叉）
    const updates = next.changes.filter(c => c.kind === "update") as Array<
      Extract<DraftChange, { kind: "update" }>
    >;
    expect(updates).toHaveLength(1);
    expect(updates[0].recordId).toBe(9002);
    expect(updates[0].after.conditionCode).toBe("office-hours");
  });

  it("目标 add 草稿 → 就地改（不产生新变更）", () => {
    const targetAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r2",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      summary: summaryOf(target)
    });
    const state = createSlotDraftState([targetAdd]);
    const view = applyDraftToRecords({
      baseline: [source],
      changes: state.changes,
      operations: OPS
    });
    const next = copySlotAttributes(state, {
      sourceSlot: slotOf(source),
      targetSlots: [slotOfTarget],
      view
    });
    expect(next.changes).toHaveLength(1);
    expect(next.changes[0].kind).toBe("add");
    expect((next.changes[0] as AddChange).recordKey.conditionCode).toBe(
      "office-hours"
    );
    // 源带条件 → 复制 canGrant 强制 false
    expect((next.changes[0] as AddChange).recordKey.canGrant).toBe(false);
  });

  it("跳过源自身与属性相同目标；复制后 after==before → 归一化剔除", () => {
    const sameAsSource = makeRecord({
      id: 9003,
      resourceCode: "data:r3",
      conditionCode: "office-hours",
      canGrant: false
    });
    const view = applyDraftToRecords({
      baseline: [source, sameAsSource],
      changes: [],
      operations: OPS
    });
    const next = copySlotAttributes(createSlotDraftState([]), {
      sourceSlot: slotOf(source),
      targetSlots: [slotOf(source), slotOf(sameAsSource)],
      view
    });
    expect(next.changes).toHaveLength(0); // 源自身跳过 + 属性相同无变更
  });
});

describe("expandSuspended（确认时双路径展开，§6.1）", () => {
  it("persistedRecord 保持未勾选 → remove（dialog-uncheck；级联数量取自 childCount）", () => {
    const existing = makeRecord({ id: 9001, childCount: 2 });
    const key = slotKeyOf(slotOf(existing));
    const state: SlotDraftState = {
      changes: [],
      suspended: new Map([
        [
          key,
          {
            slotKey: key,
            persistedRecord: existing,
            attributes: { conditionCode: null, canGrant: false }
          }
        ]
      ]),
      focusSlotKey: null
    };
    const expanded = expandSuspended(state);
    expect(expanded).toHaveLength(1);
    expect(expanded[0].kind).toBe("remove");
    const remove = expanded[0] as Extract<DraftChange, { kind: "remove" }>;
    expect(remove.reason).toBe("dialog-uncheck");
    expect(remove.cascadeChildCount).toBe(2);
  });

  it("addChangeId 保持未勾选 → 取消整个变更组，不生成 remove（无持久化 ID）", () => {
    const parentAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const key = slotKeyOf({
      resourceTypeCode: "DATA",
      resourceCode: "data:r9",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE"
    });
    const state: SlotDraftState = {
      changes: [], // uncheckSlot 已把 add 变更组移出草稿
      suspended: new Map([
        [
          key,
          {
            slotKey: key,
            addChangeId: parentAdd.changeId,
            attributes: { conditionCode: null, canGrant: false }
          }
        ]
      ]),
      focusSlotKey: null
    };
    const expanded = expandSuspended(state);
    // 变更组已在 uncheckSlot 时移出；展开不产生 remove
    expect(expanded).toHaveLength(0);
  });
});

// ========== 评审修复回归（2026-08-09，P1-1/P1-2/P1-3/P2-1） ==========

describe("评审修复回归（ALL 勾选 / add 恢复 / update 恢复去重 / 复制回基线）", () => {
  it("P1-1 ALL 槽位：无记录勾选 ALL → add 默认属性；取消后再勾选 → 原 changeId 恢复", () => {
    const allSlot: FocusSlot = {
      resourceTypeCode: "DATA",
      resourceCode: null,
      codeType: null,
      operationCode: "VIEW",
      scopeMode: "ALL"
    };
    let state = createSlotDraftState([]);
    const view0 = applyDraftToRecords({
      baseline: [],
      changes: [],
      operations: OPS
    });
    state = resumeSlot(state, {
      slot: allSlot,
      view: view0,
      baseline: [],
      operations: OPS
    });
    expect(state.changes).toHaveLength(1);
    const add = state.changes[0] as AddChange;
    expect(add.recordKey.scopeMode).toBe("ALL");
    expect(add.recordKey.conditionCode).toBeNull();
    expect(add.recordKey.canGrant).toBe(false);
    // 取消 ALL → suspended（add 路径）
    const view1 = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    state = uncheckSlot(state, { slot: allSlot, view: view1 });
    expect(state.changes).toHaveLength(0);
    // 重新勾选 → 原样恢复（保留 changeId）
    const view2 = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    state = resumeSlot(state, {
      slot: allSlot,
      view: view2,
      baseline: [],
      operations: OPS
    });
    expect(state.changes).toHaveLength(1);
    expect(state.changes[0].kind).toBe("add");
    expect((state.changes[0] as AddChange).changeId).toBe(add.changeId);
  });

  it("P1-2 新增父+子权限取消后再勾选 → 原 changeId + 子权限恢复，creates[].children 完整", () => {
    const parentAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "DATA",
        resourceCode: "data:r9",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: "office-hours",
        canGrant: false
      },
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    const childAdd = buildAddChange({
      recordKey: {
        resourceTypeCode: "REPORT",
        resourceCode: "report:r1",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE",
        conditionCode: null,
        canGrant: false
      },
      parentChangeId: parentAdd.changeId,
      summary: summaryOf(makeRecord({ id: 9999 }))
    });
    let state = createSlotDraftState([parentAdd, childAdd]);
    const slot: FocusSlot = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r9",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE"
    };
    // 取消 → addChange 完整保存 + 子权限移入 suspended
    const view1 = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    state = uncheckSlot(state, { slot, view: view1 });
    expect(state.changes).toHaveLength(0);
    expect(state.suspended.get(slotKeyOf(slot))?.addChange?.changeId).toBe(
      parentAdd.changeId
    );
    // 重新勾选 → add 原样放回（原 changeId）+ 子权限恢复（parentChangeId 无需重映射）
    const view2 = applyDraftToRecords({
      baseline: [],
      changes: state.changes,
      operations: OPS
    });
    state = resumeSlot(state, {
      slot,
      view: view2,
      baseline: [],
      operations: OPS
    });
    expect(state.changes).toHaveLength(2);
    const restoredParent = state.changes.find(
      c =>
        c.kind === "add" &&
        (c as AddChange).recordKey.resourceCode === "data:r9"
    ) as AddChange;
    expect(restoredParent.changeId).toBe(parentAdd.changeId);
    expect(restoredParent.recordKey.conditionCode).toBe("office-hours"); // 摘要/属性完整
    expect(
      state.changes.some(
        c =>
          c.kind === "add" &&
          (c as AddChange).parentChangeId === parentAdd.changeId
      )
    ).toBe(true);
    // buildGrantPlan：creates 主权限带 children 一次性建树
    const plan = buildGrantPlan(state.changes)!;
    expect(plan.creates).toHaveLength(1);
    expect(plan.creates[0].children).toHaveLength(1);
  });

  it("P1-3 已有 update 取消/恢复后同 ID 恰好一条 update（不重复）", () => {
    const existing = makeRecord({ id: 9001, canGrant: false });
    const update = buildUpdateChange({
      before: existing,
      after: { canGrant: true, conditionCode: null },
      summary: summaryOf(existing)
    });
    let state = createSlotDraftState([update]);
    // 取消：主记录自身 update 丢弃，不入 childChanges（后者仅子权限变更）
    const view1 = applyDraftToRecords({
      baseline: [existing],
      changes: state.changes,
      operations: OPS
    });
    state = uncheckSlot(state, { slot: slotOf(existing), view: view1 });
    expect(state.changes).toHaveLength(0);
    expect(
      state.suspended.get(slotKeyOf(slotOf(existing)))?.childChanges
    ).toBeUndefined();
    // 重新勾选：相对 baseline 重建恰好一条 update
    const view2 = applyDraftToRecords({
      baseline: [existing],
      changes: state.changes,
      operations: OPS
    });
    state = resumeSlot(state, {
      slot: slotOf(existing),
      view: view2,
      baseline: [existing],
      operations: OPS
    });
    const updates = state.changes.filter(c => c.kind === "update") as Array<
      Extract<DraftChange, { kind: "update" }>
    >;
    expect(updates).toHaveLength(1);
    expect(updates[0].recordId).toBe(9001);
    expect(updates[0].after.canGrant).toBe(true);
    expect(updates[0].before.canGrant).toBe(false); // before 恒为 baseline
    // 未修改（attributes==baseline）→ 恢复后无变更
    const noMod = createSlotDraftState([]);
    const view3 = applyDraftToRecords({
      baseline: [existing],
      changes: [],
      operations: OPS
    });
    const unmod = uncheckSlot(noMod, { slot: slotOf(existing), view: view3 });
    const view4 = applyDraftToRecords({
      baseline: [existing],
      changes: unmod.changes,
      operations: OPS
    });
    const resumed = resumeSlot(unmod, {
      slot: slotOf(existing),
      view: view4,
      baseline: [existing],
      operations: OPS
    });
    expect(resumed.changes).toHaveLength(0);
  });

  it("P2-1 复制值回到 baseline → 变更数归零（无幽灵 update）", () => {
    // 源属性 = canGrant true / 无条件；目标 baseline 相同（true/null）
    const source = makeRecord({ id: 9001, canGrant: true });
    const target = makeRecord({
      id: 9002,
      resourceCode: "data:r2",
      canGrant: true
    });
    const targetSlot: FocusSlot = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r2",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE"
    };
    // 目标先被改为 false（既有 update）
    const firstUpdate = buildUpdateChange({
      before: target,
      after: { canGrant: false, conditionCode: null },
      summary: summaryOf(target)
    });
    const state = createSlotDraftState([firstUpdate]);
    const view = applyDraftToRecords({
      baseline: [source, target],
      changes: state.changes,
      operations: OPS
    });
    const next = copySlotAttributes(state, {
      sourceSlot: slotOf(source),
      targetSlots: [targetSlot],
      view
    });
    // 合并既有 update（before=baseline true/null）+ after=复制值 true/null → noop 归一化剔除
    expect(next.changes).toHaveLength(0);
  });
});
