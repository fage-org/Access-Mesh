/**
 * 矩阵单元格图标正交模型纯函数测试（T-FE-039，§3.3/§6.2）。
 * 覆盖：多来源聚合归并（颜色/箭头/条纹/粗黑边框/AUTO_DEP 参与聚合）、
 * 三态图标同形聚合、子权限数量精确投影、按草稿标记拆分三态来源。
 */
import { describe, it, expect } from "vitest";
import type { CellSource } from "./source-chain";
import {
  aggregatePermissionIcon,
  cellBackgroundMark,
  childPermissionCountOf,
  splitSourcesByDraft,
  isDirectSource
} from "./cell-visual";

function source(
  partial: Partial<CellSource> & { recordId: number }
): CellSource {
  return {
    grantSource: "MANUAL",
    nodeInheritFromCode: null,
    nodeInheritFromName: null,
    opInheritFromCode: null,
    combinationBit: false,
    globalOperation: false,
    conditionCode: null,
    canGrant: false,
    scopeMode: "INSTANCE",
    createdAt: "",
    childCount: 0,
    ...partial
  };
}

describe("isDirectSource（直接 = 无资源段且无操作段，含 AUTO_DEP 直接记录）", () => {
  it("无段 → 直接", () => {
    expect(isDirectSource(source({ recordId: 1 }))).toBe(true);
  });
  it("有资源段 → 非直接", () => {
    expect(
      isDirectSource(source({ recordId: 1, nodeInheritFromCode: "parent" }))
    ).toBe(false);
  });
  it("有操作段 → 非直接", () => {
    expect(
      isDirectSource(source({ recordId: 1, opInheritFromCode: "MANAGE" }))
    ).toBe(false);
  });
  it("AUTO_DEP 直接记录 → 直接（参与聚合为实色）", () => {
    expect(
      isDirectSource(source({ recordId: 1, grantSource: "AUTO_DEP" }))
    ).toBe(true);
  });
});

describe("aggregatePermissionIcon（§3.3 颜色无关的多来源聚合）", () => {
  it("空来源 → null", () => {
    expect(aggregatePermissionIcon([])).toBeNull();
  });

  it("单条直接授权：实色 + 无箭头 + 实底 + 无粗黑边框", () => {
    expect(aggregatePermissionIcon([source({ recordId: 1 })])).toEqual({
      solid: true,
      arrow: "none",
      striped: false,
      boldBorder: false
    });
  });

  it("单条资源继承 → 淡色 + 上箭头", () => {
    expect(
      aggregatePermissionIcon([
        source({
          recordId: 1,
          nodeInheritFromCode: "parent",
          nodeInheritFromName: "父资源"
        })
      ])
    ).toMatchObject({ solid: false, arrow: "up" });
  });

  it("单条操作继承 → 淡色 + 右箭头", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, opInheritFromCode: "MANAGE" })
      ])
    ).toMatchObject({ solid: false, arrow: "right" });
  });

  it("单条两段继承（同记录资源+操作段）→ 淡色 + 组合箭头", () => {
    expect(
      aggregatePermissionIcon([
        source({
          recordId: 1,
          nodeInheritFromCode: "parent",
          opInheritFromCode: "MANAGE"
        })
      ])
    ).toMatchObject({ solid: false, arrow: "combined" });
  });

  it("直接 + 资源继承并存 → 实色（直接优先）+ 无箭头", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1 }),
        source({ recordId: 2, nodeInheritFromCode: "parent" })
      ])
    ).toMatchObject({ solid: true, arrow: "none" });
  });

  it("资源继承 + 操作继承（不同记录并存）→ 组合箭头（两段来自不同记录亦可）", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, nodeInheritFromCode: "parent" }),
        source({ recordId: 2, opInheritFromCode: "MANAGE" })
      ])
    ).toMatchObject({ solid: false, arrow: "combined" });
  });

  it("全部来源带条件 → 条纹", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2, conditionCode: "corp-ip-only" })
      ])
    ).toMatchObject({ striped: true });
  });

  it("无条件可转授与条件来源并存 → 实色粗框，不额外表现条件来源", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, canGrant: true }),
        source({ recordId: 2, conditionCode: "office-hours" })
      ])
    ).toMatchObject({
      solid: true,
      striped: false,
      boldBorder: true
    });
  });

  it("任一来源 canGrant=true → 粗黑边框", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1 }),
        source({ recordId: 2, canGrant: true })
      ])
    ).toMatchObject({ boldBorder: true });
  });

  it("全部 canGrant=false → 无粗黑边框", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1 }),
        source({ recordId: 2 })
      ])
    ).toMatchObject({ boldBorder: false });
  });

  it("仅 AUTO_DEP → 普通有效图标（实色，与无权限空白可区分）", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, grantSource: "AUTO_DEP" })
      ])
    ).toEqual({
      solid: true,
      arrow: "none",
      striped: false,
      boldBorder: false
    });
  });

  it("MANUAL 带条件 + AUTO_DEP 无条件 → 实色，不额外表现条件来源", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2, grantSource: "AUTO_DEP" })
      ])
    ).toMatchObject({
      solid: true,
      striped: false
    });
  });

  it("MANUAL + AUTO_DEP 全部带条件 → 条纹（AUTO_DEP 参与聚合）", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({
          recordId: 2,
          grantSource: "AUTO_DEP",
          conditionCode: "corp-ip-only"
        })
      ])
    ).toMatchObject({ striped: true });
  });

  it("AUTO_DEP canGrant=true 参与粗黑边框判定", () => {
    expect(
      aggregatePermissionIcon([
        source({ recordId: 1, grantSource: "AUTO_DEP", canGrant: true })
      ])
    ).toMatchObject({ boldBorder: true });
  });
});

describe("childPermissionCountOf（§3.3 子权限独立数量投影）", () => {
  it("无直接主权限记录（继承投影携带 childCount 不参与）→ 0", () => {
    expect(
      childPermissionCountOf([
        source({
          recordId: 1,
          nodeInheritFromCode: "parent",
          childCount: 3
        })
      ])
    ).toBe(0);
  });

  it("直接主权限 childCount=3 → 3", () => {
    expect(
      childPermissionCountOf([source({ recordId: 1, childCount: 3 })])
    ).toBe(3);
  });

  it("多条直接记录 childCount 累加", () => {
    expect(
      childPermissionCountOf([
        source({ recordId: 1, childCount: 1 }),
        source({ recordId: 2, childCount: 2 })
      ])
    ).toBe(3);
  });
});

describe("cellBackgroundMark（§6.2 diff 背景；P2-1：基于有效侧独立计算，remove 不吞新增背景）", () => {
  function markOf(
    marks: Array<{ id: number; mark: "add" | "update" | "remove" | null }>
  ) {
    return new Map<
      number,
      { mark: "add" | "update" | "remove"; changeId: string }
    >(
      marks
        .filter(m => m.mark != null)
        .map(m => [m.id, { mark: m.mark!, changeId: `c${m.id}` }])
    );
  }

  it("空来源 → null", () => {
    expect(cellBackgroundMark([], markOf([]))).toBeNull();
  });

  it("全部来源均为新增（无 remove 混合）→ add（绿底）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "add" },
          { id: 2, mark: "add" }
        ])
      )
    ).toBe("add");
  });

  it("已有格新增分支（add + baseline 未标记）→ partial-add", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "add" },
          { id: 2, mark: null }
        ])
      )
    ).toBe("partial-add");
  });

  it("add + remove 混合（撤销旧记录、同格新增其他来源）→ partial-add（P2-1 核心用例）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "remove" },
          { id: 2, mark: "add" }
        ])
      )
    ).toBe("partial-add");
  });

  it("add + remove + baseline 混合 → partial-add（新增背景保留）", () => {
    expect(
      cellBackgroundMark(
        [
          source({ recordId: 1 }),
          source({ recordId: 2 }),
          source({ recordId: 3 })
        ],
        markOf([
          { id: 1, mark: "remove" },
          { id: 2, mark: "add" },
          { id: 3, mark: null }
        ])
      )
    ).toBe("partial-add");
  });

  it("update → null（由黄色同形图标表达）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 })],
        markOf([{ id: 1, mark: "update" }])
      )
    ).toBeNull();
  });

  it("update + remove 混合 → null（分别由黄色/红色图标表达）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "remove" },
          { id: 2, mark: "update" }
        ])
      )
    ).toBeNull();
  });

  it("纯 remove → null（由撤销图标表达，不加背景）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "remove" },
          { id: 2, mark: "remove" }
        ])
      )
    ).toBeNull();
  });

  it("无任何标记 → null", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 })],
        markOf([{ id: 1, mark: null }])
      )
    ).toBeNull();
  });
});

describe("splitSourcesByDraft（三态并列：有效/新增 + 待更新 + 待撤销）", () => {
  const markInfo = new Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >([
    [1, { mark: "remove", changeId: "c1" }],
    [2, { mark: "add", changeId: "c2" }],
    [3, { mark: "update", changeId: "c3" }]
  ]);

  it("按标记拆分 current/updated/removed，并保留 valid 兼容集合", () => {
    const sources = [
      source({ recordId: 1 }),
      source({ recordId: 2 }),
      source({ recordId: 3 }),
      source({ recordId: 4 })
    ];
    const { current, updated, valid, removed } = splitSourcesByDraft(
      sources,
      markInfo
    );
    expect(current.map(s => s.recordId)).toEqual([2, 4]);
    expect(updated.map(s => s.recordId)).toEqual([3]);
    expect(valid.map(s => s.recordId)).toEqual([2, 3, 4]);
    expect(removed.map(s => s.recordId)).toEqual([1]);
  });

  it("全部被撤销 → 只显示撤销图标（valid 空）", () => {
    const { valid, removed } = splitSourcesByDraft(
      [source({ recordId: 1 })],
      markInfo
    );
    expect(valid).toEqual([]);
    expect(removed.length).toBe(1);
  });

  it("撤销后仍有其他有效来源 → 有效图标 + 撤销图标并列", () => {
    const { valid, removed } = splitSourcesByDraft(
      [source({ recordId: 1 }), source({ recordId: 4 })],
      markInfo
    );
    expect(valid.length).toBe(1);
    expect(removed.length).toBe(1);
  });

  it("无任何标记 → 全部 valid", () => {
    const { valid, removed } = splitSourcesByDraft(
      [source({ recordId: 4 })],
      markInfo
    );
    expect(valid.length).toBe(1);
    expect(removed).toEqual([]);
  });

  it("待更新与待撤销沿用完整来源形态，仅由渲染层切换颜色", () => {
    const { updated, removed } = splitSourcesByDraft(
      [
        source({
          recordId: 1,
          opInheritFromCode: "MANAGE",
          conditionCode: "office-hours"
        }),
        source({
          recordId: 3,
          nodeInheritFromCode: "parent",
          conditionCode: "corp-ip-only",
          canGrant: true
        })
      ],
      markInfo
    );

    expect(aggregatePermissionIcon(updated)).toEqual({
      solid: false,
      arrow: "up",
      striped: true,
      boldBorder: true
    });
    expect(aggregatePermissionIcon(removed)).toEqual({
      solid: false,
      arrow: "right",
      striped: true,
      boldBorder: false
    });
  });
});
