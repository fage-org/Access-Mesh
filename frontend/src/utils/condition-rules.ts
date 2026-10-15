/**
 * 权限条件规则模型（页面无关共享模块）。
 *
 * 字段对齐后端 DTO（api-contract.md §5.6）与 ConditionEvalUtils（perm-common）：
 * - conditionRules 结构：{logic: "AND"|"OR", items: [{type, params}]}
 * - 预置类型：DATE_RANGE / TIME_RANGE / IP_WHITELIST / IP_BLACKLIST
 *   （对齐 PermConstants.ConditionType + ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES）
 *
 * 从原 `views/system/permission-condition/utils/types.ts` 抽取，供：
 * - ReConditionEditor（共享组件）
 * - permission-condition 页 ConditionForm
 * 统一引用，消除 API 层反向依赖页面工具的问题。
 */

/** 条件逻辑（对齐 PermConstants.ConditionLogic + ConditionEvalUtils.VALID_LOGIC） */
export const CONDITION_LOGIC_OPTIONS = [
  { label: "全部满足 (AND)", value: "AND" },
  { label: "任一满足 (OR)", value: "OR" }
] as const;

/** 条件类型（对齐 PermConstants.ConditionType + ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES） */
export const CONDITION_TYPE_OPTIONS = [
  { label: "日期范围", value: "DATE_RANGE" },
  { label: "时间范围", value: "TIME_RANGE" },
  { label: "IP 白名单", value: "IP_WHITELIST" },
  { label: "IP 黑名单", value: "IP_BLACKLIST" }
] as const;

/** 条件类型 -> 中文名映射（规则摘要用） */
export const CONDITION_TYPE_LABEL: Record<string, string> = {
  DATE_RANGE: "日期范围",
  TIME_RANGE: "时间范围",
  IP_WHITELIST: "IP 白名单",
  IP_BLACKLIST: "IP 黑名单"
};

/** 条件项（conditionRules.items[] 的结构化表示）。
 *  `_id` 为前端运行时 id（v-for key 用），不进提交 JSON（serializeRules 已剔除）。 */
export type ConditionItem = {
  _id: number;
  type: string;
  params: {
    start?: string;
    end?: string;
    cidrs?: string[];
  };
};

/** 条件规则结构化表示（conditionRules JSON 的结构化形式） */
export type ConditionRules = {
  logic: "AND" | "OR";
  items: ConditionItem[];
};

// ========== 运行时 id 生成器（v-for key 稳定用） ==========

let _itemId = 1;

// ========== CIDR 输入基础校验（T-PERM-109） ==========

/**
 * CIDR 输入合法性（基础形态校验，语义对齐 perm-common ConditionEvalUtils.isValidCidr）：
 * 合法 = IPv4/IPv6 字面量（无 /），或 字面量/前缀（1-3 位纯数字且 ≤ 地址族位数，IPv4=32、IPv6=128）。
 * 负数/带符号/超界/全角数字前缀与主机名样串、结构非法 IPv6（如 :::）均非法
 * （外评 P2-2：字符集校验升级为结构校验）。后端写入口为权威校验，前端仅提前拦截明显非法输入。
 */
export function isValidCidrInput(value: string): boolean {
  const v = value.trim();
  if (!v) return false;
  const slash = v.indexOf("/");
  const literal = slash < 0 ? v : v.slice(0, slash);
  const bits = literalAddressBits(literal);
  if (bits < 0) return false;
  if (slash < 0) return true;
  const prefix = v.slice(slash + 1);
  return /^\d{1,3}$/.test(prefix) && Number(prefix) <= bits;
}

/** 数值地址字面量形态判定：IPv4=严格点分四段（每段 0-255）；IPv6=结构合法字面量。返回位数，非法 -1。 */
function literalAddressBits(literal: string): number {
  if (literal.includes(":")) {
    return isValidIpv6Literal(literal) ? 128 : -1;
  }
  const octets = literal.split(".");
  if (octets.length !== 4) return -1;
  return octets.every(o => /^\d{1,3}$/.test(o) && Number(o) <= 255) ? 32 : -1;
}

/**
 * IPv6 结构校验（对齐 Java 单源算法，codex sol r2 P2-2 同步）：
 * 8 组 1-4 位 hex（或 6 组+IPv4 尾）或恰一个 :: 且两侧组数 ≤7；IPv4 点分尾仅允许
 * 整地址末组（压缩左侧出现 v4 尾即 1.2.3.4:: 形态拒绝）；zone（%scope）一律拒绝
 * ——CIDR 条目无 zone 匹配语义且其解析平台相关。
 */
function isValidIpv6Literal(literal: string): boolean {
  if (literal.includes("%")) return false;
  if (!literal) return false;
  const compressIndex = literal.indexOf("::");
  if (compressIndex >= 0) {
    if (literal.indexOf("::", compressIndex + 1) >= 0) return false;
    const left = ipv6Groups(literal.slice(0, compressIndex), false);
    const right = ipv6Groups(literal.slice(compressIndex + 2), true);
    if (left < 0 || right < 0) return false;
    return left + right <= 7;
  }
  return ipv6Groups(literal, true) === 8;
}

/** 冒号侧组数：空串=0；每组 1-4 位 hex；v4 点分尾（计 2 组）仅当 allowV4Tail（=该侧为
 *  整地址末侧）且为本侧末组时接受；违规 -1。 */
function ipv6Groups(side: string, allowV4Tail: boolean): number {
  if (!side) return 0;
  const parts = side.split(":");
  let count = 0;
  for (let i = 0; i < parts.length; i++) {
    const group = parts[i];
    if (!group) return -1;
    if (group.includes(".")) {
      if (!allowV4Tail || i !== parts.length - 1) return -1;
      const octets = group.split(".");
      if (octets.length !== 4) return -1;
      if (!octets.every(o => /^\d{1,3}$/.test(o) && Number(o) <= 255))
        return -1;
      count += 2;
    } else {
      if (!/^[0-9a-fA-F]{1,4}$/.test(group)) return -1;
      count++;
    }
  }
  return count;
}

/** 创建空规则（新建用） */
export function createEmptyRules(): ConditionRules {
  return { logic: "AND", items: [] };
}

/** 创建空条件项（按类型初始化 params 结构） */
export function createEmptyItem(type = "DATE_RANGE"): ConditionItem {
  const base = { _id: _itemId++ };
  if (type === "IP_WHITELIST" || type === "IP_BLACKLIST") {
    return { ...base, type, params: { cidrs: [] } };
  }
  return { ...base, type, params: { start: "", end: "" } };
}

/** 将结构化规则序列化为 JSON 字符串（对齐后端 conditionRules 字段）。
 *  手动映射剔除 `_id` 运行时字段；空 items 时仍输出合法结构 {logic, items: []}。 */
export function serializeRules(rules: ConditionRules): string {
  return JSON.stringify({
    logic: rules.logic,
    items: rules.items.map(i => ({
      type: i.type,
      params: i.params
    }))
  });
}

/** 将后端 conditionRules JSON 字符串解析为结构化形式。
 *  解析失败或结构不完整时回退到空规则（fail-safe，不阻塞编辑）。 */
export function parseRules(json: string | null | undefined): ConditionRules {
  if (!json) return createEmptyRules();
  try {
    const obj = JSON.parse(json);
    const logic: "AND" | "OR" = obj?.logic === "OR" ? "OR" : "AND";
    const items = Array.isArray(obj?.items) ? obj.items : [];
    return { logic, items: items.map(normalizeItem) };
  } catch {
    return createEmptyRules();
  }
}

function normalizeItem(raw: any): ConditionItem {
  const type = typeof raw?.type === "string" ? raw.type : "DATE_RANGE";
  const params = raw?.params ?? {};
  const base = { _id: _itemId++ };
  if (type === "IP_WHITELIST" || type === "IP_BLACKLIST") {
    return {
      ...base,
      type,
      params: {
        cidrs: Array.isArray(params.cidrs)
          ? params.cidrs.filter((c: any) => typeof c === "string")
          : []
      }
    };
  }
  return {
    ...base,
    type,
    params: {
      start: typeof params.start === "string" ? params.start : "",
      end: typeof params.end === "string" ? params.end : ""
    }
  };
}

/** 生成规则可读摘要（表格列展示用）。
 *  如 "AND · 2 项（日期范围、IP 白名单）" */
/**
 * 规则完整性校验（T-PERM-048 codex 外评 P2-2 收敛共享）：
 * 至少 1 项 + 每项参数完整（DATE_RANGE/TIME_RANGE 需 start/end；IP 类需 cidrs 非空；
 * 未知类型 fail-close 不完整）——与 ReConditionEditor.itemsValid 同口径（原为组件私有，
 * 半成品 item 经非聚焦草稿提交绕过编辑器校验致引擎恒拒绝静默失效）。
 */
export function validateRulesComplete(rules: ConditionRules): boolean {
  if (rules.items.length === 0) return false;
  return rules.items.every(item => {
    if (item.type === "DATE_RANGE" || item.type === "TIME_RANGE") {
      return !!item.params.start && !!item.params.end;
    }
    if (item.type === "IP_WHITELIST" || item.type === "IP_BLACKLIST") {
      return (item.params.cidrs?.length ?? 0) > 0;
    }
    return false;
  });
}

export function summarizeRules(json: string | null | undefined): string {
  const rules = parseRules(json);
  if (rules.items.length === 0) return "无规则";
  const labels = rules.items.map(i => CONDITION_TYPE_LABEL[i.type] ?? i.type);
  return `${rules.logic} · ${rules.items.length} 项（${labels.join("、")}）`;
}
