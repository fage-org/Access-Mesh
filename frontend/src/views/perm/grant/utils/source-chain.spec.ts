/**
 * 来源链纯函数测试（DoD-2，引擎双写消除方案三）。
 *
 * Golden Fixture 5 用例（source-chain.fixtures.json）与后端 GoldenFixturePgIT（T-PERM-034）
 * 同用例集：组合位/ALL/资源继承/操作继承/两段组合来源（全局操作概念已退役）。
 * 后端落地时移植同一 fixtures 比对，前端本测试逐例全字段断言（CI 失败即阻断）。
 *
 * 其余用例（AUTO_DEP 并列/未定义位/继承开关/操作授权覆盖）为前端补充覆盖，非 golden 集合。
 */
import { describe, it, expect } from "vitest";
import fixtures from "./source-chain.fixtures.json";
import {
  computeSourceChain,
  collectOperationGrants,
  getCellState,
  allRowKey,
  instanceRowKey,
  type CellSource,
  type SourceRecordInput
} from "./source-chain";

/** 期望来源项（fixture 字段子集；createdAt/childCount 为展示字段不参与比对） */
type ExpectedSource = {
  recordId: number;
  grantSource: string;
  nodeInheritFromCode: string | null;
  opInheritFromCode: string | null;
  combinationBit: boolean;
  conditionCode: string | null;
  canGrant: boolean;
  scopeMode: string;
};

type ExpectedCell = { row: string; column: string; sources: ExpectedSource[] };

type FixtureCase = {
  name: string;
  title: string;
  operations: Array<{
    code: string;
    name: string;
    resourceTypeCode: string;
    binaryBit: string;
    inheritMask: string;
  }>;
  resources: Array<{
    id: number;
    parentId: number | null;
    resourceTypeCode: string;
    code: string;
    codeType: string;
    name: string;
  }>;
  records: SourceRecordInput[];
  expected: {
    columns: Record<string, string[]>;
    cells: ExpectedCell[];
    undefinedBits: Record<string, string>;
  };
};

/** 实际输出 → fixture 同形（仅保留比对字段，排序保证确定性） */
function normalize(result: ReturnType<typeof computeSourceChain>): {
  columns: Record<string, string[]>;
  cells: ExpectedCell[];
  undefinedBits: Record<string, string>;
} {
  const columns: Record<string, string[]> = {};
  for (const [typeCode, ops] of result.columnsByType) {
    columns[typeCode] = ops.map(op => op.code);
  }
  const cells: ExpectedCell[] = [];
  for (const [rowKey, row] of result.cells) {
    for (const [opCode, cell] of row) {
      cells.push({
        row: rowKey,
        column: opCode,
        sources: cell.sources.map((s: CellSource) => ({
          recordId: s.recordId,
          grantSource: s.grantSource,
          nodeInheritFromCode: s.nodeInheritFromCode,
          opInheritFromCode: s.opInheritFromCode,
          combinationBit: s.combinationBit,
          conditionCode: s.conditionCode,
          canGrant: s.canGrant,
          scopeMode: s.scopeMode
        }))
      });
    }
  }
  const rowOrder = (row: string) => row;
  cells.sort(
    (a, b) =>
      rowOrder(a.row).localeCompare(b.row) || a.column.localeCompare(b.column)
  );
  for (const cell of cells) {
    cell.sources.sort((a, b) => a.recordId - b.recordId);
  }
  const expectedCells = (raw: ExpectedCell[]) => {
    const cloned = raw.map(c => ({
      ...c,
      sources: [...c.sources].sort((a, b) => a.recordId - b.recordId)
    }));
    cloned.sort(
      (a, b) => a.row.localeCompare(b.row) || a.column.localeCompare(b.column)
    );
    return cloned;
  };
  const undefinedBits: Record<string, string> = {};
  for (const [id, bits] of result.undefinedBitsByRecord) {
    undefinedBits[String(id)] = bits;
  }
  return { columns, cells: expectedCells(cells), undefinedBits };
}

describe("source-chain Golden Fixtures（6 用例精简，DoD-2）", () => {
  for (const fixtureCase of (fixtures as { cases: FixtureCase[] }).cases) {
    it(`${fixtureCase.name}：${fixtureCase.title}`, () => {
      const result = computeSourceChain({
        records: fixtureCase.records,
        resources: fixtureCase.resources,
        operations: fixtureCase.operations
      });
      const actual = normalize(result);
      const expectedCells = [...fixtureCase.expected.cells]
        .map(c => ({
          ...c,
          sources: [...c.sources].sort((a, b) => a.recordId - b.recordId)
        }))
        .sort(
          (a, b) =>
            a.row.localeCompare(b.row) || a.column.localeCompare(b.column)
        );
      expect(actual.columns).toEqual(fixtureCase.expected.columns);
      expect(actual.cells).toEqual(expectedCells);
      expect(actual.undefinedBits).toEqual(fixtureCase.expected.undefinedBits);
    });
  }
});

describe("source-chain 前端补充覆盖（非 golden 集）", () => {
  const OPS = [
    {
      code: "VIEW",
      name: "查看",
      resourceTypeCode: "REPORT",
      binaryBit: "2",
      inheritMask: "0"
    },
    {
      code: "MANAGE",
      name: "管理",
      resourceTypeCode: "REPORT",
      binaryBit: "8",
      inheritMask: "2"
    }
  ];
  const TREE = [
    {
      id: 1,
      parentId: null,
      resourceTypeCode: "REPORT",
      code: "rpt:p",
      codeType: "default",
      name: "父报表",
      children: [
        {
          id: 2,
          parentId: 1,
          resourceTypeCode: "REPORT",
          code: "rpt:p:c",
          codeType: "default",
          name: "子报表",
          children: []
        }
      ]
    }
  ];

  it("AUTO_DEP 与 MANUAL 同键并存并列展示（各自真实生效，§3.3）", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "AUTO_DEP",
        grantedBits: "2"
      },
      {
        id: 101,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: true,
        conditionCode: "office-hours",
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "2"
      }
    ];
    const result = computeSourceChain({
      records,
      resources: TREE,
      operations: OPS
    });
    const cell = getCellState(
      result,
      instanceRowKey("REPORT", "rpt:p", "default"),
      "VIEW"
    );
    expect(cell?.sources).toHaveLength(2);
    expect(cell?.sources.map(s => s.grantSource).sort()).toEqual([
      "AUTO_DEP",
      "MANUAL"
    ]);
  });

  it("组合位未定义位归入 undefinedBitsByRecord（详情层展示），纯未定义位记录不点亮任何列", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: null,
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "34" // 2|32：32 无定义
      },
      {
        id: 101,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: null,
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "64" // 纯未定义位
      }
    ];
    const result = computeSourceChain({
      records,
      resources: TREE,
      operations: OPS
    });
    expect(result.undefinedBitsByRecord.get(100)).toBe("32");
    expect(result.undefinedBitsByRecord.get(101)).toBe("64");
    // 100 命中 VIEW（位 2）；101 不点亮任何列
    const cell = getCellState(
      result,
      instanceRowKey("REPORT", "rpt:p", "default"),
      "VIEW"
    );
    expect(cell?.sources.map(s => s.recordId)).toEqual([100]);
  });

  it("关闭树级继承：子孙行不再显示祖先来源（§3.4 开关为查看态过滤）", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "2"
      }
    ];
    const result = computeSourceChain({
      records,
      resources: TREE,
      operations: OPS,
      includeResourceInherit: false
    });
    expect(
      getCellState(result, instanceRowKey("REPORT", "rpt:p", "default"), "VIEW")
        ?.sources
    ).toHaveLength(1);
    expect(
      getCellState(
        result,
        instanceRowKey("REPORT", "rpt:p:c", "default"),
        "VIEW"
      )
    ).toBeUndefined();
  });

  it("关闭操作继承：被 inheritMask 覆盖的列不显示", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "MANAGE",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "8"
      }
    ];
    const result = computeSourceChain({
      records,
      resources: TREE,
      operations: OPS,
      includeOpInherit: false
    });
    const rowKey = instanceRowKey("REPORT", "rpt:p", "default");
    expect(getCellState(result, rowKey, "MANAGE")?.sources).toHaveLength(1);
    expect(getCellState(result, rowKey, "VIEW")).toBeUndefined();
  });

  it("记录引用的资源不在树中：按记录 resourceName/编码兜底建行，不崩溃（S7 边界）", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:ghost",
        codeType: "default",
        resourceName: "幽灵报表",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "2"
      }
    ];
    const result = computeSourceChain({
      records,
      resources: TREE,
      operations: OPS
    });
    const cell = getCellState(
      result,
      instanceRowKey("REPORT", "rpt:ghost", "default"),
      "VIEW"
    );
    expect(cell?.sources).toHaveLength(1);
  });

  it("collectOperationGrants：覆盖位集展开命中（非裸 grantedBits & binaryBit），组合位标注", () => {
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "MANAGE",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "8" // MANAGE 覆盖 VIEW
      },
      {
        id: 101,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p:c",
        codeType: "default",
        operationCode: null,
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "10" // VIEW|MANAGE 组合位
      },
      {
        id: 102,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "AUTO_DEP",
        grantedBits: "2"
      }
    ];
    const hits = collectOperationGrants({
      records,
      operations: OPS,
      target: { resourceTypeCode: "REPORT", code: "VIEW" }
    });
    expect(hits).toHaveLength(3);
    const byId = new Map(hits.map(h => [h.record.id, h]));
    expect(byId.get(100)?.hit).toBe("COVERED"); // MANAGE 继承覆盖，非 DIRECT
    expect(byId.get(101)?.hit).toBe("DIRECT");
    expect(byId.get(101)?.combinationBit).toBe(true);
    expect(byId.get(102)?.hit).toBe("DIRECT");
    // MANAGE 列现状：100 DIRECT（定义位含 MANAGE），101 DIRECT（组合位含 MANAGE），102 未命中
    const manageHits = collectOperationGrants({
      records,
      operations: OPS,
      target: { resourceTypeCode: "REPORT", code: "MANAGE" }
    });
    expect(manageHits.map(h => h.record.id).sort()).toEqual([100, 101]);
  });

  it("collectOperationGrants：操作按类型隔离，只统计同类型记录", () => {
    const ops = [
      ...OPS,
      {
        code: "VIEW",
        name: "查看",
        resourceTypeCode: "DATA",
        binaryBit: "2",
        inheritMask: "0"
      }
    ];
    const records: SourceRecordInput[] = [
      {
        id: 100,
        resourceTypeCode: "DATA",
        resourceCode: "data:x",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "2"
      },
      {
        id: 101,
        resourceTypeCode: "REPORT",
        resourceCode: "rpt:p",
        codeType: "default",
        operationCode: "VIEW",
        canGrant: false,
        conditionCode: null,
        scopeMode: "INSTANCE",
        grantSource: "MANUAL",
        grantedBits: "2"
      }
    ];
    const dataHits = collectOperationGrants({
      records,
      operations: ops,
      target: { resourceTypeCode: "DATA", code: "VIEW" }
    });
    expect(dataHits.map(h => h.record.id)).toEqual([100]);
    const reportHits = collectOperationGrants({
      records,
      operations: ops,
      target: { resourceTypeCode: "REPORT", code: "VIEW" }
    });
    expect(reportHits.map(h => h.record.id)).toEqual([101]);
  });

  it("ALL 虚拟行键与实例行键互斥", () => {
    expect(allRowKey("REPORT")).not.toBe(
      instanceRowKey("REPORT", "rpt:p", "default")
    );
  });

  it("computeSourceChain：兼容旧响应缺少 grantedBits 时按 operationCode 回退", () => {
    const record: SourceRecordInput = {
      id: 200,
      resourceTypeCode: "REPORT",
      resourceCode: "rpt:p",
      codeType: "default",
      operationCode: "VIEW",
      canGrant: false,
      conditionCode: null,
      scopeMode: "INSTANCE",
      grantSource: "MANUAL"
    };
    const result = computeSourceChain({
      records: [record],
      resources: [
        {
          id: 1,
          parentId: null,
          resourceTypeCode: "REPORT",
          code: "rpt:p",
          codeType: "default",
          name: "父报表",
          children: []
        }
      ],
      operations: OPS
    });

    expect(
      getCellState(
        result,
        instanceRowKey("REPORT", "rpt:p", "default"),
        "VIEW"
      )?.sources.map(source => source.recordId)
    ).toEqual([200]);
    expect(
      collectOperationGrants({
        records: [record],
        operations: OPS,
        target: { resourceTypeCode: "REPORT", code: "VIEW" }
      })[0]?.hit
    ).toBe("DIRECT");
  });
});
