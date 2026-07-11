// 权限条件 Mock（T-FE-009）。
// 对齐 permission-center 的 permission-condition 管理接口；
// 扁平条件模板 CRUD，conditionRules 为 {logic, items[]} JSON 字符串。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
import { defineFakeRoute } from "vite-plugin-fake-server/client";

// ========== 本地类型（对齐后端 DTO，api-contract.md §5.6） ==========

type ConditionResp = {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  conditionRules: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
  description: string | null;
  createdAt: string;
};

type InternalCondition = ConditionResp & { deleted: boolean };

// ========== 常量与种子 ==========

const now = () => new Date().toISOString().slice(0, 19).replace("T", " ");
const BASE_TIME = "2026-06-18 09:30:00";
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

/** 对齐 ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES */
const GATEWAY_PUSHABLE_TYPES = new Set([
  "IP_WHITELIST",
  "IP_BLACKLIST",
  "DATE_RANGE",
  "TIME_RANGE"
]);
/** 对齐 ConditionEvalUtils.VALID_LOGIC */
const VALID_LOGIC = new Set(["AND", "OR"]);

let nextId = 701;

const conditions: InternalCondition[] = [
  {
    id: 601,
    tenantId: 1,
    code: "office-hours",
    name: "工作时间",
    conditionRules:
      '{"logic":"AND","items":[{"type":"TIME_RANGE","params":{"start":"09:00:00","end":"18:00:00"}}]}',
    enabled: true,
    gatewayEvaluable: true,
    description: "仅工作时间段可访问",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 602,
    tenantId: 1,
    code: "corp-ip-only",
    name: "公司网络",
    conditionRules:
      '{"logic":"AND","items":[{"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24","10.0.0.0/8"]}}]}',
    enabled: true,
    gatewayEvaluable: true,
    description: "仅公司内网 IP 可访问",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 603,
    tenantId: 1,
    code: "temp-access",
    name: "临时开放窗口",
    conditionRules:
      '{"logic":"AND","items":[{"type":"DATE_RANGE","params":{"start":"2026-07-01","end":"2026-07-31"}},{"type":"TIME_RANGE","params":{"start":"09:00:00","end":"18:00:00"}}]}',
    enabled: false,
    gatewayEvaluable: true,
    description: "2026 年 7 月工作日时间窗（当前停用）",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 604,
    tenantId: 1,
    code: "blacklist-vpn",
    name: "VPN 黑名单",
    conditionRules:
      '{"logic":"AND","items":[{"type":"IP_BLACKLIST","params":{"cidrs":["203.0.113.0/24"]}}]}',
    enabled: true,
    gatewayEvaluable: false,
    description: "封禁已知 VPN 出口（走实时鉴权）",
    createdAt: BASE_TIME,
    deleted: false
  }
];

// ========== 工具函数 ==========

function clone(c: InternalCondition): ConditionResp {
  const { deleted: _d, ...resp } = c;
  return { ...resp };
}

function isDuplicateCode(code: string, excludeId?: number): boolean {
  return conditions.some(
    c => !c.deleted && c.id !== excludeId && c.code === code
  );
}

/** 校验 conditionRules（对齐后端 JsonValidationUtils + ConditionEvalUtils.isGatewayPushable）。
 *  - 始终校验 JSON 语法 + 对象结构。
 *  - gatewayEvaluable=true 时追加 isGatewayPushable 全部规则
 *    （logic ∈ VALID_LOGIC / items 非空 / items[].type 全在白名单）。 */
function validateRules(
  rulesJson: string,
  gatewayEvaluable: boolean
): string | null {
  if (!rulesJson || !rulesJson.trim()) return "conditionRules 不能为空";
  let tree: any;
  try {
    tree = JSON.parse(rulesJson);
  } catch (e: any) {
    return "conditionRules 不是合法 JSON: " + (e?.message ?? "");
  }
  if (typeof tree !== "object" || tree === null || Array.isArray(tree)) {
    return "conditionRules 必须是对象";
  }
  if (gatewayEvaluable) {
    const logicNode = tree.logic;
    if (logicNode != null && !VALID_LOGIC.has(logicNode)) {
      return "logic 仅允许 AND 或 OR";
    }
    const items = tree.items;
    if (!Array.isArray(items) || items.length === 0) {
      return "gatewayEvaluable=true 时 items 不能为空";
    }
    for (const item of items) {
      if (!item || typeof item.type !== "string") {
        return "每个 item 需含 type";
      }
      if (!GATEWAY_PUSHABLE_TYPES.has(item.type)) {
        return "gatewayEvaluable=true 仅允许 IP_WHITELIST/IP_BLACKLIST/DATE_RANGE/TIME_RANGE";
      }
    }
  }
  return null;
}

export default defineFakeRoute([
  {
    url: "/api/perm/permission-condition/list",
    method: "post",
    response: () => {
      const items = conditions
        .filter(c => !c.deleted)
        .sort((a, b) => a.id - b.id)
        .map(clone);
      return ok({ items });
    }
  },

  {
    url: "/api/perm/permission-condition/detail",
    method: "post",
    response: ({ body }) => {
      const c = conditions.find(item => item.id === body?.id && !item.deleted);
      return c ? ok(clone(c)) : error(404, "条件不存在");
    }
  },

  {
    url: "/api/perm/permission-condition/create",
    method: "post",
    response: ({ body }) => {
      const {
        code,
        name,
        conditionRules,
        enabled,
        gatewayEvaluable,
        description
      } = body || {};
      if (!code || !name || !conditionRules) {
        return error(400, "编码、名称、条件规则不能为空");
      }
      if (isDuplicateCode(code)) {
        return error(409, "条件编码已存在（uk tenant+code）");
      }
      const ge = gatewayEvaluable ?? false;
      const err = validateRules(conditionRules, ge);
      if (err) return error(400, err);
      const created: InternalCondition = {
        id: nextId++,
        tenantId: 1,
        code,
        name,
        conditionRules,
        enabled: enabled ?? true,
        gatewayEvaluable: ge,
        description: description ?? null,
        createdAt: now(),
        deleted: false
      };
      conditions.push(created);
      return ok(clone(created));
    }
  },

  {
    url: "/api/perm/permission-condition/update",
    method: "post",
    response: ({ body }) => {
      const {
        conditionId,
        name,
        conditionRules,
        enabled,
        gatewayEvaluable,
        description
      } = body || {};
      const c = conditions.find(
        item => item.id === conditionId && !item.deleted
      );
      if (!c) return error(404, "条件不存在");
      // 取最终状态做联合校验（对齐后端 ConditionAppServiceImpl.validateGatewayPushable）：
      // 只切 flag 不改 rules 时需重读 DB 老 rules 校验；同时改 rules+flag 用新 rules。
      const finalRules = conditionRules ?? c.conditionRules;
      const finalGe = gatewayEvaluable ?? c.gatewayEvaluable;
      if (conditionRules != null) {
        const err = validateRules(finalRules, finalGe);
        if (err) return error(400, err);
      } else if (gatewayEvaluable != null && finalGe) {
        const err = validateRules(finalRules, finalGe);
        if (err) return error(400, err);
      }
      if (name != null) c.name = name;
      if (conditionRules != null) c.conditionRules = conditionRules;
      if (enabled != null) c.enabled = enabled;
      if (gatewayEvaluable != null) c.gatewayEvaluable = gatewayEvaluable;
      if (description != null) c.description = description;
      return ok(clone(c));
    }
  },

  {
    url: "/api/perm/permission-condition/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      let count = 0;
      for (const c of conditions) {
        if (ids.includes(c.id) && !c.deleted) {
          c.deleted = true;
          count += 1;
        }
      }
      return ok({ removed: count });
    }
  }
]);
