// 冲突规则 Mock（T-FE-010）。
// 对齐 permission-center 的 conflict-rule 管理接口；
// 冲突规则 CRUD + 冲突检测，ROLE_MUTEX（角色互斥）/ PERM_MUTEX（权限互斥）两类。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
//
// 种子 ID 对齐（名称映射由前端 hook 加载 role-manage/resource-operation/type-def mock 建立）：
// - 角色：101 基础用户 / 102 高级用户 / 201 核心开发组 / 202 运维保障组（BASIC_ROLE + GROUP_ROLE）
// - 操作权限：501 MENU:CREATE / 502 MENU:VIEW / 504 MENU:DELETE / 509 API:CREATE / 512 API:DELETE
// - 资源类型 typeValue：1 MENU / 3 API
import { defineFakeRoute } from "vite-plugin-fake-server/client";

// ========== 本地类型（对齐后端 DTO，api-contract.md §5.6） ==========

type ConflictRuleResp = {
  id: number;
  tenantId: number;
  conflictType: string;
  firstOperationPermissionId: number | null;
  secondOperationPermissionId: number | null;
  resourceTypeValue: number | null;
  firstAbstractRoleId: number | null;
  secondAbstractRoleId: number | null;
  description: string | null;
  createdAt: string;
};

type InternalRule = ConflictRuleResp & { deleted: boolean };

// ========== 常量与种子 ==========

const now = () => new Date().toISOString().slice(0, 19).replace("T", " ");
const BASE_TIME = "2026-06-20 10:00:00";
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

const VALID_TYPES = new Set(["ROLE_MUTEX", "PERM_MUTEX"]);

let nextId = 801;

const rules: InternalRule[] = [
  {
    id: 701,
    tenantId: 1,
    conflictType: "ROLE_MUTEX",
    firstOperationPermissionId: null,
    secondOperationPermissionId: null,
    resourceTypeValue: null,
    firstAbstractRoleId: 101,
    secondAbstractRoleId: 102,
    description: "基础用户与高级用户互斥（同一用户不可兼具）",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 702,
    tenantId: 1,
    conflictType: "ROLE_MUTEX",
    firstOperationPermissionId: null,
    secondOperationPermissionId: null,
    resourceTypeValue: null,
    firstAbstractRoleId: 201,
    secondAbstractRoleId: 202,
    description: "核心开发组与运维保障组互斥（职责分离）",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 703,
    tenantId: 1,
    conflictType: "PERM_MUTEX",
    firstOperationPermissionId: 501,
    secondOperationPermissionId: 504,
    resourceTypeValue: 1,
    firstAbstractRoleId: null,
    secondAbstractRoleId: null,
    description: "MENU 资源：创建与删除操作互斥",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 704,
    tenantId: 1,
    conflictType: "PERM_MUTEX",
    firstOperationPermissionId: 509,
    secondOperationPermissionId: 512,
    resourceTypeValue: 3,
    firstAbstractRoleId: null,
    secondAbstractRoleId: null,
    description: "API 资源：创建与删除操作互斥",
    createdAt: BASE_TIME,
    deleted: false
  }
];

// ========== 工具函数 ==========

function clone(r: InternalRule): ConflictRuleResp {
  const { deleted: _d, ...resp } = r;
  return { ...resp };
}

/** 校验规则字段与冲突类型一致性（对齐后端 ConflictRuleAppServiceImpl 隐式约束）。
 *  - ROLE_MUTEX：firstAbstractRoleId + secondAbstractRoleId 必填，操作权限字段须为 null
 *  - PERM_MUTEX：firstOperationPermissionId + secondOperationPermissionId 必填，
 *    resourceTypeValue 可空（null=全部资源类型），角色字段须为 null
 *  - 两个对象不能相同 */
function validateRule(body: any): string | null {
  const type = body?.conflictType;
  if (!VALID_TYPES.has(type))
    return "conflictType 仅允许 ROLE_MUTEX 或 PERM_MUTEX";
  if (type === "ROLE_MUTEX") {
    if (body.firstAbstractRoleId == null || body.secondAbstractRoleId == null) {
      return "角色互斥需指定两个角色";
    }
    if (body.firstAbstractRoleId === body.secondAbstractRoleId) {
      return "两个角色不能相同";
    }
  } else {
    if (
      body.firstOperationPermissionId == null ||
      body.secondOperationPermissionId == null
    ) {
      return "权限互斥需指定两个操作权限";
    }
    if (body.firstOperationPermissionId === body.secondOperationPermissionId) {
      return "两个操作权限不能相同";
    }
  }
  return null;
}

/** 判定两条规则是否语义等价（同类型 + 同对象对，双向匹配）。
 *  用于 create/update 去重，避免重复定义同一冲突关系。 */
function isDuplicate(body: any, excludeId?: number): boolean {
  const type = body.conflictType;
  const a =
    type === "ROLE_MUTEX"
      ? body.firstAbstractRoleId
      : body.firstOperationPermissionId;
  const b =
    type === "ROLE_MUTEX"
      ? body.secondAbstractRoleId
      : body.secondOperationPermissionId;
  const rtv = body.resourceTypeValue ?? null;
  return rules.some(r => {
    if (r.deleted || r.id === excludeId || r.conflictType !== type)
      return false;
    if (r.resourceTypeValue !== rtv) return false;
    const ra =
      type === "ROLE_MUTEX"
        ? r.firstAbstractRoleId
        : r.firstOperationPermissionId;
    const rb =
      type === "ROLE_MUTEX"
        ? r.secondAbstractRoleId
        : r.secondOperationPermissionId;
    return (ra === a && rb === b) || (ra === b && rb === a);
  });
}

export default defineFakeRoute([
  {
    url: "/api/perm/conflict-rule/list",
    method: "post",
    response: () => {
      const items = rules
        .filter(r => !r.deleted)
        .sort((a, b) => a.id - b.id)
        .map(clone);
      return ok({ items });
    }
  },

  {
    url: "/api/perm/conflict-rule/detail",
    method: "post",
    response: ({ body }) => {
      const r = rules.find(item => item.id === body?.id && !item.deleted);
      return r ? ok(clone(r)) : error(404, "冲突规则不存在");
    }
  },

  {
    url: "/api/perm/conflict-rule/create",
    method: "post",
    response: ({ body }) => {
      const err = validateRule(body);
      if (err) return error(400, err);
      if (isDuplicate(body)) {
        return error(409, "等价冲突规则已存在（双向匹配）");
      }
      const created: InternalRule = {
        id: nextId++,
        tenantId: 1,
        conflictType: body.conflictType,
        firstOperationPermissionId:
          body.conflictType === "PERM_MUTEX"
            ? body.firstOperationPermissionId
            : null,
        secondOperationPermissionId:
          body.conflictType === "PERM_MUTEX"
            ? body.secondOperationPermissionId
            : null,
        resourceTypeValue:
          body.conflictType === "PERM_MUTEX"
            ? (body.resourceTypeValue ?? null)
            : null,
        firstAbstractRoleId:
          body.conflictType === "ROLE_MUTEX" ? body.firstAbstractRoleId : null,
        secondAbstractRoleId:
          body.conflictType === "ROLE_MUTEX" ? body.secondAbstractRoleId : null,
        description: body.description ?? null,
        createdAt: now(),
        deleted: false
      };
      rules.push(created);
      return ok(clone(created));
    }
  },

  {
    url: "/api/perm/conflict-rule/update",
    method: "post",
    response: ({ body }) => {
      const r = rules.find(item => item.id === body?.id && !item.deleted);
      if (!r) return error(404, "冲突规则不存在");
      // 取最终状态校验（对齐后端 if(field!=null) 语义）
      const merged = {
        conflictType: body.conflictType ?? r.conflictType,
        firstAbstractRoleId:
          body.firstAbstractRoleId != null
            ? body.firstAbstractRoleId
            : r.firstAbstractRoleId,
        secondAbstractRoleId:
          body.secondAbstractRoleId != null
            ? body.secondAbstractRoleId
            : r.secondAbstractRoleId,
        firstOperationPermissionId:
          body.firstOperationPermissionId != null
            ? body.firstOperationPermissionId
            : r.firstOperationPermissionId,
        secondOperationPermissionId:
          body.secondOperationPermissionId != null
            ? body.secondOperationPermissionId
            : r.secondOperationPermissionId,
        resourceTypeValue:
          body.resourceTypeValue != null
            ? body.resourceTypeValue
            : r.resourceTypeValue
      };
      const err = validateRule(merged);
      if (err) return error(400, err);
      if (isDuplicate(merged, r.id)) {
        return error(409, "等价冲突规则已存在（双向匹配）");
      }
      if (body.conflictType != null) r.conflictType = body.conflictType;
      // 类型切换时清空对侧字段（对齐后端 if(field!=null) 语义，前端表单切换已清空）
      if (r.conflictType === "ROLE_MUTEX") {
        if (body.firstAbstractRoleId != null)
          r.firstAbstractRoleId = body.firstAbstractRoleId;
        if (body.secondAbstractRoleId != null)
          r.secondAbstractRoleId = body.secondAbstractRoleId;
        r.firstOperationPermissionId = null;
        r.secondOperationPermissionId = null;
        r.resourceTypeValue = null;
      } else {
        if (body.firstOperationPermissionId != null)
          r.firstOperationPermissionId = body.firstOperationPermissionId;
        if (body.secondOperationPermissionId != null)
          r.secondOperationPermissionId = body.secondOperationPermissionId;
        if (body.resourceTypeValue != null)
          r.resourceTypeValue = body.resourceTypeValue;
        r.firstAbstractRoleId = null;
        r.secondAbstractRoleId = null;
      }
      if (body.description != null) r.description = body.description;
      return ok(clone(r));
    }
  },

  {
    url: "/api/perm/conflict-rule/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      let count = 0;
      for (const r of rules) {
        if (ids.includes(r.id) && !r.deleted) {
          r.deleted = true;
          count += 1;
        }
      }
      return ok({ removed: count });
    }
  },

  {
    url: "/api/perm/conflict-rule/detect",
    method: "post",
    response: ({ body }) => {
      const {
        firstOperationPermissionId: fop,
        secondOperationPermissionId: sop,
        resourceTypeValue: rtv
      } = body || {};
      if (fop == null || sop == null) {
        return error(
          400,
          "firstOperationPermissionId/secondOperationPermissionId 不能为空"
        );
      }
      // 双向匹配（对齐后端 detectConflictRule）：
      // 规则 (A,B) 视为冲突当请求 (A,B) 或 (B,A)；resourceTypeValue null=全部
      const matched = rules
        .filter(r => !r.deleted && r.conflictType === "PERM_MUTEX")
        .filter(r => {
          const typeOk =
            rtv == null ||
            r.resourceTypeValue == null ||
            r.resourceTypeValue === rtv;
          if (!typeOk) return false;
          return (
            (r.firstOperationPermissionId === fop &&
              r.secondOperationPermissionId === sop) ||
            (r.firstOperationPermissionId === sop &&
              r.secondOperationPermissionId === fop)
          );
        })
        .map(clone);
      return ok({
        conflictDetected: matched.length > 0,
        matchedRules: matched
      });
    }
  }
]);
