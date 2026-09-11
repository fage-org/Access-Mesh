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
