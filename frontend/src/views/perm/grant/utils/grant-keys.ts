/**
 * 授权决策与展示键的统一无歧义编码（T-FE-060，Q-044 前端半边）。
 *
 * 设计依据：docs/design/frontend/permission-grant.md §键约定（编码方式+适用面）。
 *
 * 背景：resourceCode/codeType 为自由文本可含分隔符（`:`、`|`），直接拼串做键时不同
 * 元组会同键——(TD,"a:b","c") 与 (TD,"a","b:c") 碰撞，可错预填树勾选、使 uncheckSlot
 * 的 suspended 去重吞撤销、混并矩阵来源行/主体树高亮。本模块是这些键的唯一构造点，
 * 禁止在消费方各自拼串。
 *
 * 编码：JSON 数组序列化后再 percent-encode——JSON 转义保证段间无歧义（单射），
 * percent-encode 保证产物不含 `"`、`[`、`]`、`\` 等字符（el-table-v2 固定列把行键
 * 未经转义插入 CSS 属性选择器 `[rowkey="…"]` 做悬停/展开同步，含双引号的键会产生
 * 非法选择器使 querySelectorAll 抛 SyntaxError——外评 P2，2026-10-01）；undefined/null
 * 段统一序列化为 null（与 findSlotRecord 等字段直比面的 `?? null` 归一语义对齐）。
 * 键存在于内存 Map/Set、el-tree node-key 与 el-table-v2 row-key，不进请求载荷
 * （后端 DTO 字段不变）。
 */

/** 元组段（undefined 统一序列化为 null） */
export type KeySegment = string | number | boolean | null | undefined;

/** 元组 → 无歧义且 DOM 选择器安全的键串（本模块编码基元，消费方不得绕过它拼段） */
export function encodeKeyTuple(...parts: KeySegment[]): string {
  return encodeURIComponent(
    JSON.stringify(parts.map(p => (p === undefined ? null : p)))
  );
}

/**
 * 授权直接键（五段）：resourceTypeCode + resourceCode + codeType + operationKey + scopeMode。
 * operationKey = operationCode ?? `bits:${grantedBits}`（组合位记录以位串为键）。
 */
export function grantTupleKey(input: {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationKey: string;
  scopeMode: string;
}): string {
  return encodeKeyTuple(
    input.resourceTypeCode,
    input.resourceCode,
    input.codeType,
    input.operationKey,
    input.scopeMode
  );
}

/** 资源三段键：resourceTypeCode + resourceCode + codeType（弹窗全量比对按资源聚合） */
export function resourceTupleKey(input: {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
}): string {
  return encodeKeyTuple(
    input.resourceTypeCode,
    input.resourceCode,
    input.codeType
  );
}

/**
 * 资源树节点键（三段）：resourceTypeCode + code + codeType。
 * computePreset 的 checkedTripleKeys 与 GrantDialog 的节点映射必须同构（同一构造器）。
 */
export function resourceNodeKey(input: {
  resourceTypeCode: string;
  code: string;
  codeType: string | null;
}): string {
  return encodeKeyTuple(input.resourceTypeCode, input.code, input.codeType);
}

/**
 * 主体树角色键：externalId 与数字 id 分命名空间。
 * externalId 是租户可手填的业务键（可为 "5" 这类数字串），与角色的数据库 id 分属两个
 * 空间——旧 `role:${externalId ?? id}` 下 externalId="5" 的角色与 externalId 缺省、
 * id=5 的角色同键，el-tree 按 key 定位时高亮/选中错乱。组织键（`org:{id}`）为系统
 * 数字 id 单一空间，不经本函数。
 */
export function roleSubjectKey(externalId: string | null, id: number): string {
  return externalId != null ? `role:ext:${externalId}` : `role:id:${id}`;
}
