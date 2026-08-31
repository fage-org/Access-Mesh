/**
 * 来源链计算（前端纯函数，对齐引擎语义，设计文档 permission-grant.md §3.5）。
 *
 * 对齐目标：
 * - 资源继承：PermQueryEngine.collectDescendants（父节点授权作用于全部子孙；
 *   后端 collectDescendants 不含源节点本身，本模块显式并入 {r}，与 §3.5 节点集一致）；
 * - 操作继承：OperationPermissionUtils.covers（effectiveBits = binaryBit | inheritMask，
 *   覆盖判定 (effectiveBits(A) & B.binaryBit) != 0）；
 * - 组合闭包一次完成：节点集 descendants(r) ∪ {r} × 操作集 coveredOperations(A) 笛卡尔积展开；
 * - 两段式来源标注：节点段（r'≠r 时"资源继承自 <r>"）+ 操作段（B≠A 时"操作继承自 <A>"）；
 * - ALL 虚拟行：scopeMode=ALL 记录聚合到对应资源类型的 ALL 虚拟行（节点段为空，操作段照常）；
 * - 位运算全部走 BigInt（P1-3）；组合位按位拆解（P1-4）：operationCode=null 记录按
 *   grantedBits 与各操作列 binaryBit 逐位比对，命中多列则多列同时点亮（标注"组合位"），
 *   无法匹配任何定义位的余位归入 undefinedBitsByRecord（详情层"未定义位"展示）。
 *
 * 本计算为展示口径，不代表运行时判定（运行时以 PermQueryEngine 为准）。
 * 引擎双写消除（方案三）：本模块与后端 GoldenFixturePgIT 同用例集比对，
 * fixtures 见 source-chain.fixtures.json（5 用例：组合位/ALL/资源继承/操作继承/两段组合来源）。
 */
import type { GrantScopeMode, GrantSource } from "@/api/permission-grant";
import { effectiveBits, toBigIntBits } from "./bits";

// ========== 输入模型 ==========

/** 操作定义输入（线格式宽容：binaryBit/inheritMask 接受十进制字符串或 number，内部统一 BigInt） */
export type OperationDefInput = {
  code: string;
  name: string;
  /** 操作定义必属某类型（全局操作概念已退役，2026-08-30 设计定案） */
  resourceTypeCode: string;
  binaryBit: string | number;
  inheritMask: string | number;
};

/** 操作列排序：默认全部显示、按 binaryBit 升序（permission-grant.md §3.2 实现确认）——
 *  排序在前端归一，与后端 ORDER BY id 返回顺序无关（BigInt 比较，十进制字符串线格式；
 *  不变异入参数组） */
export function sortOperationDefs(
  defs: OperationDefInput[]
): OperationDefInput[] {
  return [...defs].sort((a, b) => {
    const x = BigInt(a.binaryBit);
    const y = BigInt(b.binaryBit);
    return x < y ? -1 : x > y ? 1 : 0;
  });
}

/** 资源树节点输入（仅消费必要字段；children 可嵌套或经由 parentId 关联） */
export type ResourceNodeInput = {
  id: number;
  parentId: number | null;
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
  children?: ResourceNodeInput[] | null;
};

/** 来源链记录输入（主权限，dependOn==null；展示字段可省略） */
export type SourceRecordInput = {
  id: number;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  resourceName?: string | null;
  operationCode: string | null;
  canGrant: boolean;
  conditionCode: string | null;
  scopeMode: GrantScopeMode;
  grantSource: GrantSource;
  /** 后端标准字段；兼容旧接口缺失时由 operationCode 回退单操作位。 */
  grantedBits?: string;
  createdAt?: string;
  childCount?: number;
};

// ========== 输出模型 ==========

/** 操作列（按类型隔离，§3.2；全局操作概念已退役——每类型列集 = 该类型专属定义） */
export type MergedOperation = {
  code: string;
  name: string;
  resourceTypeCode: string;
  binaryBit: bigint;
  inheritMask: bigint;
};

/** 兼容旧列表响应：缺少 grantedBits 时，单操作记录按 operationCode 定位定义位。 */
function resolvedGrantedBits(
  record: SourceRecordInput,
  merged: MergedOperation[]
): string {
  if (
    typeof record.grantedBits === "string" &&
    record.grantedBits.trim() !== ""
  ) {
    return record.grantedBits;
  }
  return (
    merged
      .find(operation => operation.code === record.operationCode)
      ?.binaryBit.toString(10) ?? "0"
  );
}

/** 单元格来源项（一条直接记录的闭包结果命中本格） */
export type CellSource = {
  recordId: number;
  grantSource: GrantSource;
  /** 节点段：资源继承自（r'≠r 时非空） */
  nodeInheritFromCode: string | null;
  nodeInheritFromName: string | null;
  /** 操作段：操作继承自（B≠A 时非空） */
  opInheritFromCode: string | null;
  /** 组合位记录（operationCode=null 按位拆解命中，来源标注"组合位"） */
  combinationBit: boolean;
  conditionCode: string | null;
  canGrant: boolean;
  scopeMode: GrantScopeMode;
  createdAt: string;
  childCount: number;
};

/** 单元格状态（sources 为空 = 无权限） */
export type CellState = {
  sources: CellSource[];
};

export type SourceChainResult = {
  /** 资源类型 → 合并操作列（按 binaryBit 升序） */
  columnsByType: Map<string, MergedOperation[]>;
  /** 行键 → (列操作 code → CellState) */
  cells: Map<string, Map<string, CellState>>;
  /** 记录 id → 未定义位（十进制字符串；组合位按位拆解后无法匹配任何定义位的余位） */
  undefinedBitsByRecord: Map<number, string>;
};

// ========== 行键 ==========

/** ALL 虚拟行键（承载 scopeMode=ALL 记录，与实例行互斥展示） */
export function allRowKey(resourceTypeCode: string): string {
  return `ALL:${resourceTypeCode}`;
}

/** 资源实例行键（resourceTypeCode + resourceCode + codeType，§2.1 资源键） */
export function instanceRowKey(
  resourceTypeCode: string,
  resourceCode: string,
  codeType: string
): string {
  return `RES:${resourceTypeCode}:${resourceCode}:${codeType}`;
}

// ========== 操作列（按类型隔离，§3.2） ==========

/**
 * 单类型操作列（全局操作概念已退役：列集 = 该类型专属定义）。
 * 列按 binaryBit 升序稳定排序。
 */
export function mergeOperationsForType(
  operations: OperationDefInput[],
  resourceTypeCode: string
): MergedOperation[] {
  const merged: MergedOperation[] = operations
    .filter(op => op.resourceTypeCode === resourceTypeCode)
    .map(op => ({
      code: op.code,
      name: op.name,
      resourceTypeCode: op.resourceTypeCode,
      binaryBit: toBigIntBits(op.binaryBit),
      inheritMask: toBigIntBits(op.inheritMask)
    }));
  merged.sort((a, b) =>
    a.binaryBit < b.binaryBit ? -1 : a.binaryBit > b.binaryBit ? 1 : 0
  );
  return merged;
}

/**
 * 多类型操作列 Map（按类型隔离，§3.2）。
 * typeCodes 由外部驱动（资源树类型 ∪ 当前有效记录类型，§3.1）。
 * 不传 typeCodes 时回退操作定义自身类型。
 */
export function mergeOperationsByType(
  operations: OperationDefInput[],
  typeCodes?: Iterable<string>
): Map<string, MergedOperation[]> {
  const codes =
    typeCodes != null
      ? new Set(typeCodes)
      : new Set(operations.map(op => op.resourceTypeCode));
  const result = new Map<string, MergedOperation[]>();
  for (const code of codes) {
    result.set(code, mergeOperationsForType(operations, code));
  }
  return result;
}

/** 取某资源类型的操作列 */
export function getMergedColumns(
  columnsByType: Map<string, MergedOperation[]>,
  resourceTypeCode: string
): MergedOperation[] {
  return columnsByType.get(resourceTypeCode) ?? [];
}

// ========== 内部：资源索引 ==========

type FlatResource = {
  id: number;
  parentId: number | null;
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
};

function flattenResources(resources: ResourceNodeInput[]): FlatResource[] {
  const flat: FlatResource[] = [];
  const walk = (nodes: ResourceNodeInput[]) => {
    for (const node of nodes) {
      flat.push({
        id: node.id,
        parentId: node.parentId,
        resourceTypeCode: node.resourceTypeCode,
        code: node.code,
        codeType: node.codeType,
        name: node.name
      });
      if (node.children?.length) walk(node.children);
    }
  };
  walk(resources);
  return flat;
}

/** 收集子孙（对齐 PermQueryEngine.collectDescendants：不含源节点本身；Set 防环） */
function collectDescendants(
  sourceId: number,
  childrenByParent: Map<number, FlatResource[]>
): FlatResource[] {
  const result: FlatResource[] = [];
  const visited = new Set<number>();
  const walk = (parentId: number) => {
    for (const child of childrenByParent.get(parentId) ?? []) {
      if (visited.has(child.id)) continue;
      visited.add(child.id);
      result.push(child);
      walk(child.id);
    }
  };
  walk(sourceId);
  return result;
}

/** 记录已匹配操作（定义位）→ 覆盖位集（⋃(binaryBit | inheritMask)，§4 P1-2 覆盖位集展开） */
export function coveredSetOf(
  grantedBits: string,
  merged: MergedOperation[]
): { coveredSet: bigint; matched: MergedOperation[]; undefinedBits: bigint } {
  const G = toBigIntBits(grantedBits);
  const matched = merged.filter(
    op => op.binaryBit !== 0n && (G & op.binaryBit) === op.binaryBit
  );
  let coveredSet = 0n;
  for (const op of matched) coveredSet |= effectiveBits(op);
  let allDefined = 0n;
  for (const op of merged) allDefined |= op.binaryBit;
  const undefinedBits = G & ~allDefined;
  return { coveredSet, matched, undefinedBits };
}

// ========== 主入口：来源链计算 ==========

export function computeSourceChain(input: {
  records: SourceRecordInput[];
  resources: ResourceNodeInput[];
  operations: OperationDefInput[];
  /** 树级继承开关（默认 true）；false = 只看直接授权（子孙行不再显示祖先来源） */
  includeResourceInherit?: boolean;
  /** 操作继承开关（默认 true）；false = 只显示显式授予的操作列 */
  includeOpInherit?: boolean;
}): SourceChainResult {
  const includeResourceInherit = input.includeResourceInherit !== false;
  const includeOpInherit = input.includeOpInherit !== false;

  const flat = flattenResources(input.resources);
  // 类型全集 = 资源树类型 ∪ 当前有效记录类型（§3.1）
  const allTypeCodes = new Set<string>();
  for (const node of flat) allTypeCodes.add(node.resourceTypeCode);
  for (const record of input.records) allTypeCodes.add(record.resourceTypeCode);
  const columnsByType = mergeOperationsByType(input.operations, allTypeCodes);
  const nodesByType = new Map<string, FlatResource[]>();
  const childrenByParent = new Map<number, FlatResource[]>();
  for (const node of flat) {
    const list = nodesByType.get(node.resourceTypeCode) ?? [];
    list.push(node);
    nodesByType.set(node.resourceTypeCode, list);
    if (node.parentId != null) {
      const children = childrenByParent.get(node.parentId) ?? [];
      children.push(node);
      childrenByParent.set(node.parentId, children);
    }
  }

  const cells = new Map<string, Map<string, CellState>>();
  const undefinedBitsByRecord = new Map<number, string>();

  const pushSource = (rowKey: string, opCode: string, source: CellSource) => {
    let row = cells.get(rowKey);
    if (!row) {
      row = new Map<string, CellState>();
      cells.set(rowKey, row);
    }
    let cell = row.get(opCode);
    if (!cell) {
      cell = { sources: [] };
      row.set(opCode, cell);
    }
    cell.sources.push(source);
  };

  for (const record of input.records) {
    const merged = getMergedColumns(columnsByType, record.resourceTypeCode);
    if (merged.length === 0) continue;
    const grantedBits = resolvedGrantedBits(record, merged);
    const { coveredSet, matched, undefinedBits } = coveredSetOf(
      grantedBits,
      merged
    );
    if (undefinedBits !== 0n) {
      undefinedBitsByRecord.set(record.id, undefinedBits.toString(10));
    }
    // 无任何定义位命中（纯未定义位组合记录）→ 不点亮任何列，余位仅在详情层展示
    if (coveredSet === 0n) continue;

    // ---- 节点集（§3.5 步骤 2：descendants(r) ∪ {r}；ALL = 单元素虚拟节点） ----
    type NodeTarget = {
      rowKey: string;
      inheritFromCode: string | null;
      inheritFromName: string | null;
    };
    const nodeTargets: NodeTarget[] = [];
    if (record.scopeMode === "ALL") {
      nodeTargets.push({
        rowKey: allRowKey(record.resourceTypeCode),
        inheritFromCode: null,
        inheritFromName: null
      });
    } else if (record.resourceCode != null && record.codeType != null) {
      const typeNodes = nodesByType.get(record.resourceTypeCode) ?? [];
      const node =
        typeNodes.find(
          n => n.code === record.resourceCode && n.codeType === record.codeType
        ) ?? null;
      const nodeName = node?.name ?? record.resourceName ?? record.resourceCode;
      nodeTargets.push({
        rowKey: instanceRowKey(
          record.resourceTypeCode,
          record.resourceCode,
          record.codeType
        ),
        inheritFromCode: null,
        inheritFromName: null
      });
      if (includeResourceInherit && node) {
        for (const desc of collectDescendants(node.id, childrenByParent)) {
          nodeTargets.push({
            rowKey: instanceRowKey(
              record.resourceTypeCode,
              desc.code,
              desc.codeType
            ),
            inheritFromCode: node.code,
            inheritFromName: nodeName
          });
        }
      }
    }

    // ---- 操作集 × 节点集笛卡尔积（§3.5 步骤 2/3 两段式标注） ----
    const G = toBigIntBits(grantedBits);
    for (const target of nodeTargets) {
      for (const col of merged) {
        if ((coveredSet & col.binaryBit) === 0n) continue;
        const direct =
          col.binaryBit !== 0n && (G & col.binaryBit) === col.binaryBit;
        // 关闭操作继承：只显示显式授予的列（被 inheritMask 覆盖的列不显示，§3.4）
        if (!direct && !includeOpInherit) continue;
        // 操作段：B≠A 时标注"操作继承自 <A>"（A = 覆盖本列的已授权操作，确定性取位序首个）
        let opInheritFromCode: string | null = null;
        if (!direct) {
          const covering = matched.find(
            m => (effectiveBits(m) & col.binaryBit) !== 0n
          );
          opInheritFromCode = covering?.code ?? null;
        }
        pushSource(target.rowKey, col.code, {
          recordId: record.id,
          grantSource: record.grantSource,
          nodeInheritFromCode: target.inheritFromCode,
          nodeInheritFromName: target.inheritFromName,
          opInheritFromCode,
          combinationBit: record.operationCode === null,
          conditionCode: record.conditionCode,
          canGrant: record.canGrant,
          scopeMode: record.scopeMode,
          createdAt: record.createdAt ?? "",
          childCount: record.childCount ?? 0
        });
      }
    }
  }

  return { columnsByType, cells, undefinedBitsByRecord };
}

/** 取单元格状态（无权限返回 undefined） */
export function getCellState(
  result: SourceChainResult,
  rowKey: string,
  opCode: string
): CellState | undefined {
  return result.cells.get(rowKey)?.get(opCode);
}

// ========== 操作授权覆盖分析（§4 P1-2 覆盖位集展开） ==========

export type OperationGrantHit = {
  record: SourceRecordInput;
  /** DIRECT = 定义位直接含当前操作；COVERED = 继承掩码覆盖命中（含组合位） */
  hit: "DIRECT" | "COVERED";
  combinationBit: boolean;
};

/**
 * 目标操作列覆盖分析：目标操作列被哪些主权限记录覆盖。
 * 对每条记录先求覆盖位集 coveredSet = ⋃(bit.binaryBit | bit.inheritMask)，
 * 判定 (coveredSet & 当前操作 binaryBit) != 0——不是裸 grantedBits & binaryBit
 * （MANAGE 覆盖 VIEW 来自 inheritMask，裸比较会误报未授权）；不做 operationCode 等值比较
 * （等值会漏掉组合位记录）。目标列与记录同资源类型（操作位空间按类型隔离）。
 */
export function collectOperationGrants(input: {
  records: SourceRecordInput[];
  operations: OperationDefInput[];
  target: { resourceTypeCode: string; code: string };
}): OperationGrantHit[] {
  const result: OperationGrantHit[] = [];
  for (const record of input.records) {
    if (record.resourceTypeCode !== input.target.resourceTypeCode) {
      continue;
    }
    const merged = mergeOperationsForType(
      input.operations,
      record.resourceTypeCode
    );
    const col = merged.find(c => c.code === input.target.code);
    if (!col || col.binaryBit === 0n) continue;
    const grantedBits = resolvedGrantedBits(record, merged);
    const G = toBigIntBits(grantedBits);
    const { coveredSet } = coveredSetOf(grantedBits, merged);
    if ((coveredSet & col.binaryBit) === 0n) continue;
    const direct = (G & col.binaryBit) === col.binaryBit;
    result.push({
      record,
      hit: direct ? "DIRECT" : "COVERED",
      combinationBit: record.operationCode === null
    });
  }
  return result;
}
