/**
 * 矩阵单元格图标正交模型纯函数测试（T-FE-039，§3.3/§6.2）。
 * 覆盖：多来源聚合归并（颜色/箭头/条纹/粗黑边框/AUTO_DEP 参与聚合）、
 * 撤销图标对称归并、子权限分叉精确投影、按草稿标记拆分有效/撤销来源。
 */
import { describe, it, expect } from "vitest";
import type { CellSource } from "./source-chain";
import {
  aggregateValidIcon,
  aggregateRemoveIcon,
  cellBackgroundMark,
  forkAttachmentOf,
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

describe("aggregateValidIcon（§3.3 多来源聚合归并）", () => {
  it("空来源 → null", () => {
    expect(aggregateValidIcon([])).toBeNull();
  });

  it("单条直接授权：实色 + 无箭头 + 实底 + 无粗黑边框", () => {
    expect(aggregateValidIcon([source({ recordId: 1 })])).toEqual({
      solid: true,
      arrow: "none",
      striped: false,
      boldBorder: false
    });
  });

  it("单条资源继承 → 淡色 + 上箭头", () => {
    expect(
      aggregateValidIcon([
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
      aggregateValidIcon([source({ recordId: 1, opInheritFromCode: "MANAGE" })])
    ).toMatchObject({ solid: false, arrow: "right" });
  });

  it("单条两段继承（同记录资源+操作段）→ 淡色 + 组合箭头", () => {
    expect(
      aggregateValidIcon([
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
      aggregateValidIcon([
        source({ recordId: 1 }),
        source({ recordId: 2, nodeInheritFromCode: "parent" })
      ])
    ).toMatchObject({ solid: true, arrow: "none" });
  });

  it("资源继承 + 操作继承（不同记录并存）→ 组合箭头（两段来自不同记录亦可）", () => {
    expect(
      aggregateValidIcon([
        source({ recordId: 1, nodeInheritFromCode: "parent" }),
        source({ recordId: 2, opInheritFromCode: "MANAGE" })
      ])
    ).toMatchObject({ solid: false, arrow: "combined" });
  });

  it("全部来源带条件 → 条纹", () => {
    expect(
      aggregateValidIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2, conditionCode: "corp-ip-only" })
      ])
    ).toMatchObject({ striped: true });
  });

  it("任一来源无条件 → 实色展示（不条纹）", () => {
    expect(
      aggregateValidIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2 })
      ])
    ).toMatchObject({ striped: false });
  });

  it("任一来源 canGrant=true → 粗黑边框", () => {
    expect(
      aggregateValidIcon([
        source({ recordId: 1 }),
        source({ recordId: 2, canGrant: true })
      ])
    ).toMatchObject({ boldBorder: true });
  });

  it("全部 canGrant=false → 无粗黑边框", () => {
    expect(
      aggregateValidIcon([source({ recordId: 1 }), source({ recordId: 2 })])
    ).toMatchObject({ boldBorder: false });
  });

  it("仅 AUTO_DEP → 普通有效图标（实色，与无权限空白可区分）", () => {
    expect(
      aggregateValidIcon([source({ recordId: 1, grantSource: "AUTO_DEP" })])
    ).toEqual({
      solid: true,
      arrow: "none",
      striped: false,
      boldBorder: false
    });
  });

  it("MANUAL 带条件 + AUTO_DEP 无条件 → 实色不条纹（AUTO_DEP 参与聚合）", () => {
    expect(
      aggregateValidIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2, grantSource: "AUTO_DEP" })
      ])
    ).toMatchObject({ solid: true, striped: false });
  });

  it("MANUAL + AUTO_DEP 全部带条件 → 条纹（AUTO_DEP 参与聚合）", () => {
    expect(
      aggregateValidIcon([
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
      aggregateValidIcon([
        source({ recordId: 1, grantSource: "AUTO_DEP", canGrant: true })
      ])
    ).toMatchObject({ boldBorder: true });
  });
});

describe("aggregateRemoveIcon（§6.2 撤销图标；对称归并）", () => {
  it("空来源 → null", () => {
    expect(aggregateRemoveIcon([])).toBeNull();
  });

  it("撤销直接权限（无条件）→ 红 + 实色", () => {
    expect(aggregateRemoveIcon([source({ recordId: 1 })])).toEqual({
      red: true,
      striped: false
    });
  });

  it("撤销继承权限 → 淡红", () => {
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1, nodeInheritFromCode: "parent" })
      ])
    ).toEqual({ red: false, striped: false });
  });

  it("撤销来源带条件（直接）→ 红白条纹", () => {
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1, conditionCode: "office-hours" })
      ])
    ).toEqual({ red: true, striped: true });
  });

  it("撤销来源带条件（继承）→ 淡红白条纹", () => {
    expect(
      aggregateRemoveIcon([
        source({
          recordId: 1,
          opInheritFromCode: "MANAGE",
          conditionCode: "office-hours"
        })
      ])
    ).toEqual({ red: false, striped: true });
  });

  it("多条撤销：任一直接 → 红；全部继承 → 淡红（对称归并）", () => {
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1 }),
        source({ recordId: 2, nodeInheritFromCode: "parent" })
      ])
    ).toMatchObject({ red: true });
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1, nodeInheritFromCode: "parent" }),
        source({ recordId: 2, opInheritFromCode: "MANAGE" })
      ])
    ).toMatchObject({ red: false });
  });

  it("多条撤销：全部带条件 → 条纹；任一无条件 → 实色", () => {
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2, conditionCode: "corp-ip-only" })
      ])
    ).toMatchObject({ striped: true });
    expect(
      aggregateRemoveIcon([
        source({ recordId: 1, conditionCode: "office-hours" }),
        source({ recordId: 2 })
      ])
    ).toMatchObject({ striped: false });
  });
});

describe("forkAttachmentOf（§3.3 子权限分叉精确投影）", () => {
  it("无直接主权限记录（含继承投影携带 childCount 不参与）→ 双侧 0", () => {
    expect(
      forkAttachmentOf(
        [
          source({
            recordId: 1,
            nodeInheritFromCode: "parent",
            childCount: 3
          })
        ],
        []
      )
    ).toEqual({ validCount: 0, removedCount: 0 });
  });

  it("存续直接主权限 childCount=3 → 分叉附有效图标", () => {
    expect(
      forkAttachmentOf([source({ recordId: 1, childCount: 3 })], [])
    ).toEqual({ validCount: 3, removedCount: 0 });
  });

  it("被撤销直接主权限（级联删子）childCount=2 → 分叉附撤销图标", () => {
    expect(
      forkAttachmentOf([], [source({ recordId: 1, childCount: 2 })])
    ).toEqual({ validCount: 0, removedCount: 2 });
  });

  it("存续 + 撤销双侧并存 → 分叉各自附着（有效图标 + 带分叉撤销图标并列）", () => {
    expect(
      forkAttachmentOf(
        [source({ recordId: 1, childCount: 3 })],
        [source({ recordId: 2, childCount: 2 })]
      )
    ).toEqual({ validCount: 3, removedCount: 2 });
  });

  it("多条直接记录 childCount 累加", () => {
    expect(
      forkAttachmentOf(
        [
          source({ recordId: 1, childCount: 1 }),
          source({ recordId: 2, childCount: 2 })
        ],
        []
      )
    ).toEqual({ validCount: 3, removedCount: 0 });
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

  it("add + remove 混合（撤销旧分支、同格新增新分支）→ partial-add（P2-1 核心用例）", () => {
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

  it("update → update（黄底+黄描边）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 })],
        markOf([{ id: 1, mark: "update" }])
      )
    ).toBe("update");
  });

  it("update + remove 混合 → update（有效侧修改背景保留）", () => {
    expect(
      cellBackgroundMark(
        [source({ recordId: 1 }), source({ recordId: 2 })],
        markOf([
          { id: 1, mark: "remove" },
          { id: 2, mark: "update" }
        ])
      )
    ).toBe("update");
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

describe("splitSourcesByDraft（撤销并列规则：有效 + 撤销最多并列两个）", () => {
  const markInfo = new Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >([
    [1, { mark: "remove", changeId: "c1" }],
    [2, { mark: "add", changeId: "c2" }],
    [3, { mark: "update", changeId: "c3" }]
  ]);

  it("remove 标记 → removed；add/update/未标记 → valid", () => {
    const sources = [
      source({ recordId: 1 }),
      source({ recordId: 2 }),
      source({ recordId: 3 }),
      source({ recordId: 4 })
    ];
    const { valid, removed } = splitSourcesByDraft(sources, markInfo);
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
});
