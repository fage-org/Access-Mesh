// 权限排查 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回 PermResult 信封：{ code, message, data }
// 模拟未来 admin-service 聚合路径 /permission-query/*（T-PERM-033 实现后对接真聚合层，前端无需改路径）
// 零 src 依赖：类型本地声明，避免 fake-server 经 bundle-import 打包 src/api 链
//
// 契约依据：docs/design/permission-center/api-contract.md §6.6-6.8
// 主体模型：effective-permissions/explain 支持 USER/ROLE；query-scopes 仅 USER
//
// 单一事实源（评审 P1 修复）：PermissionGrant 统一授权事实表派生 effective-permissions + explain，
// 消除列表(explain 判定)与事实表的双源漂移。
// - USER explain 复用 auth/check 语义：ALL 授权覆盖 INSTANCE 查询（§6.8 L1515）
// - ROLE explain 精确匹配 scopeMode：仅判定角色直接拥有（§6.8 L1787-1789，不做 ALL 覆盖）
// - 主体按复合键解析：USER=subjectTypeCode+subjectExternalId，ROLE=domainCode+roleTypeCode+roleExternalId
//
// Mock 场景覆盖：
// - Tab1 effective-permissions：从 userGrants/roleGrants 派生，按 domainCode/resourceTypeCodes/
//   operationCodes/resourceKeyword 过滤 + operationCodes 裁剪为交集 + includeSourceRoles/sourceRoleLimit
//   投影 + 分组去重合并（§6.6 L1247）+ USER/ROLE 成功/未知空结果/分页翻页/sourceRolesTruncated
// - Tab2 query-scopes：主权限从 userGrants 派生（auth/check 语义：INSTANCE 精确 OR ALL 覆盖）
//   + 按请求 scopeResourceTypeCodes × scopeOperationCodes 笛卡尔积返回 + 四态(ALL/INSTANCE/DENIED/EMPTY)
//   + 主权限全不通过 -> NO_PERMISSION + 全 DENIED 笛卡尔积（§6.7 L1334）
//   + USER_NOT_FOUND/OBJECT_KEY_NOT_FOUND
// - Tab3 explain：USER 按复合键+auth/check 语义判定（ALL 覆盖 INSTANCE）；
//   ROLE 按复合键+精确 scopeMode 判定；includeSourceRoles=false -> sourceRoles=[]
//   + allowed=true/false/USER_NOT_FOUND/ROLE_NOT_FOUND/NO_PERMISSION
import { defineFakeRoute } from "vite-plugin-fake-server/client";

type TargetType = "USER" | "ROLE";
type ScopeMode = "DENIED" | "INSTANCE" | "ALL" | "EMPTY";
/** 授权事实只使用 INSTANCE/ALL（§6.6 L15） */
type GrantScopeMode = "INSTANCE" | "ALL";

interface SourceRole {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  via: string[];
}
interface EffectivePermissionItem {
  domainCode: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  resourceName: string | null;
  codeType: string | null;
  operationCodes: string[];
  scopeMode: ScopeMode;
  sourceRoles: SourceRole[];
  sourceRoleCount: number;
  sourceRolesTruncated: boolean;
  matchedPermissionIds: number[];
}
interface ScopeItem {
  resourceCode: string;
  codeType: string;
  resourceName: string | null;
}
interface ScopeGroup {
  resourceTypeCode: string;
  operationCode: string;
  scopeMode: ScopeMode;
  items: ScopeItem[];
  matchedRoleIds: number[];
  matchedPermissionIds: number[];
  dependOnPermissionIds: number[];
}
interface ExplainPermission {
  domainCode: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
  scopeMode: ScopeMode;
}
interface RecentChange {
  changeLogId: number;
  eventType: string;
  changeType: string;
  impactLevel: string;
  message: string;
  permission?: ExplainPermission;
  sourceRole?: SourceRole;
  operatorId: number | null;
  operatorName: string | null;
  changeReason: string | null;
  createdAt: string;
}

/**
 * 统一授权事实（单一事实源，派生 effective-permissions + explain）。
 * 每条 grant 等价一条 role_resource_permission 记录（permissionId 标识）。
 * - ownerType=USER：u-10001 的有效授权（sourceRole 为来源角色）
 * - ownerType=ROLE：role_report_viewer 的直接授权（sourceRole=null）
 */
interface PermissionGrant {
  permissionId: number;
  ownerType: TargetType;
  domainCode: string;
  resourceTypeCode: string;
  /** null = ALL（类型级全量授权） */
  resourceCode: string | null;
  /** null = ALL */
  codeType: string | null;
  operationCode: string;
  scopeMode: GrantScopeMode;
  resourceName: string | null;
  /** USER 视角来源角色；ROLE 视角 null */
  sourceRole: SourceRole | null;
}

const ok = (data: unknown) => ({ code: 200, message: "success", data });

// 已知主体（复合键）：
// - 用户：(ADMIN_USER, u-10001)
// - 角色：(example, BASIC_ROLE, role_report_viewer)
// 域：example（report:sales / data:*）/ finance（report:finance）/ hr（report:hr）
// 资源类型：REPORT / DATA
// 资源：report:sales / report:finance / report:hr / data:dept:A / data:dept:B / data:dept:C
// 操作：DATA_READ / DATA_EDIT / DATA_EXPORT / DATA_DELETE

// ========== 来源角色常量 ==========

const SOURCE_ROLE_VIEWER: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_report_viewer",
  roleName: "报表查看员",
  via: []
};
const SOURCE_ROLE_ADMIN: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_admin",
  roleName: "管理员",
  via: []
};
const SOURCE_ROLE_FINANCE: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_finance",
  roleName: "财务员",
  via: []
};
const SOURCE_ROLE_HR: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_hr",
  roleName: "人事员",
  via: []
};
const SOURCE_ROLE_ANALYST: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_data_analyst",
  roleName: "数据分析员",
  via: []
};
const SOURCE_ROLE_AUDITOR: SourceRole = {
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_auditor",
  roleName: "审计员",
  via: []
};

// ========== 统一授权事实表 ==========

/** USER (ADMIN_USER, u-10001) 有效授权事实（13 条 grant，派生 8 条 EffectivePermissionItem） */
const userGrants: PermissionGrant[] = [
  {
    permissionId: 200,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "销售报表",
    sourceRole: SOURCE_ROLE_VIEWER
  },
  {
    permissionId: 201,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL",
    resourceName: null,
    sourceRole: SOURCE_ROLE_ADMIN
  },
  {
    permissionId: 302,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "A部门数据",
    sourceRole: SOURCE_ROLE_VIEWER
  },
  {
    permissionId: 302,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE",
    resourceName: "A部门数据",
    sourceRole: SOURCE_ROLE_VIEWER
  },
  {
    permissionId: 303,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "B部门数据",
    sourceRole: SOURCE_ROLE_VIEWER
  },
  {
    permissionId: 304,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:C",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "C部门数据",
    sourceRole: SOURCE_ROLE_VIEWER
  },
  {
    permissionId: 305,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:C",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "C部门数据",
    sourceRole: SOURCE_ROLE_ADMIN
  },
  {
    permissionId: 306,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:C",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "C部门数据",
    sourceRole: SOURCE_ROLE_ANALYST
  },
  {
    permissionId: 307,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:C",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "C部门数据",
    sourceRole: SOURCE_ROLE_AUDITOR
  },
  {
    permissionId: 401,
    ownerType: "USER",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_EXPORT",
    scopeMode: "ALL",
    resourceName: null,
    sourceRole: SOURCE_ROLE_ADMIN
  },
  {
    permissionId: 208,
    ownerType: "USER",
    domainCode: "finance",
    resourceTypeCode: "REPORT",
    resourceCode: "report:finance",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "财务报表",
    sourceRole: SOURCE_ROLE_FINANCE
  },
  {
    permissionId: 209,
    ownerType: "USER",
    domainCode: "hr",
    resourceTypeCode: "REPORT",
    resourceCode: "report:hr",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "人事报表",
    sourceRole: SOURCE_ROLE_HR
  },
  {
    permissionId: 209,
    ownerType: "USER",
    domainCode: "hr",
    resourceTypeCode: "REPORT",
    resourceCode: "report:hr",
    codeType: "default",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE",
    resourceName: "人事报表",
    sourceRole: SOURCE_ROLE_HR
  }
];

/** ROLE (example, BASIC_ROLE, role_report_viewer) 直接授权事实（5 条 grant，派生 4 条） */
const roleGrants: PermissionGrant[] = [
  {
    permissionId: 200,
    ownerType: "ROLE",
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "销售报表",
    sourceRole: null
  },
  {
    permissionId: 302,
    ownerType: "ROLE",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "A部门数据",
    sourceRole: null
  },
  {
    permissionId: 302,
    ownerType: "ROLE",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE",
    resourceName: "A部门数据",
    sourceRole: null
  },
  {
    permissionId: 303,
    ownerType: "ROLE",
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    resourceName: "B部门数据",
    sourceRole: null
  },
  {
    permissionId: 210,
    ownerType: "ROLE",
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL",
    resourceName: null,
    sourceRole: null
  }
];

// ========== 主体键解析（P1 修复：复合键） ==========

const KNOWN_USER = {
  subjectTypeCode: "ADMIN_USER",
  subjectExternalId: "u-10001"
};
const KNOWN_ROLE = {
  domainCode: "example",
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "role_report_viewer"
};
/** 已知主资源复合键（query-scopes 按 domainCode+type+code+codeType 解析，§6.7 L1335） */
const KNOWN_PARENT_RESOURCE = {
  domainCode: "example",
  resourceTypeCode: "REPORT",
  resourceCode: "report:sales",
  codeType: "default"
};

/** 匹配用户主体键（subjectTypeCode + subjectExternalId） */
function matchUser(body: any): boolean {
  return (
    body.subjectTypeCode === KNOWN_USER.subjectTypeCode &&
    body.subjectExternalId === KNOWN_USER.subjectExternalId
  );
}

/** 匹配角色主体键（domainCode + roleTypeCode + roleExternalId） */
function matchRole(body: any): boolean {
  return (
    body.domainCode === KNOWN_ROLE.domainCode &&
    body.roleTypeCode === KNOWN_ROLE.roleTypeCode &&
    body.roleExternalId === KNOWN_ROLE.roleExternalId
  );
}

/** 匹配主资源完整复合键（domainCode + parentResourceTypeCode + parentResourceCode + parentCodeType） */
function matchParentResource(body: any): boolean {
  const domainCode = body.domainCode || "example";
  return (
    domainCode === KNOWN_PARENT_RESOURCE.domainCode &&
    body.parentResourceTypeCode === KNOWN_PARENT_RESOURCE.resourceTypeCode &&
    body.parentResourceCode === KNOWN_PARENT_RESOURCE.resourceCode &&
    body.parentCodeType === KNOWN_PARENT_RESOURCE.codeType
  );
}

// ========== Tab1 effective-permissions 派生 ==========

/**
 * 从 grants 派生 EffectivePermissionItem 列表。
 * 按 (domainCode, resourceTypeCode, resourceCode, codeType, scopeMode) 分组，
 * 合并 operationCodes / sourceRoles / matchedPermissionIds（§6.6 L1247 去重合并）。
 */
function deriveEffectiveItems(
  grants: PermissionGrant[],
  body: any
): EffectivePermissionItem[] {
  let filtered = grants;
  // 1. domainCode 过滤
  if (body.domainCode) {
    filtered = filtered.filter(g => g.domainCode === body.domainCode);
  }
  // 2. resourceTypeCodes 过滤
  if (Array.isArray(body.resourceTypeCodes) && body.resourceTypeCodes.length) {
    filtered = filtered.filter(g =>
      body.resourceTypeCodes.includes(g.resourceTypeCode)
    );
  }
  // 3. operationCodes 过滤（裁剪为交集：只保留请求操作对应的 grant）
  if (Array.isArray(body.operationCodes) && body.operationCodes.length) {
    filtered = filtered.filter(g =>
      body.operationCodes.includes(g.operationCode)
    );
  }
  // 4. resourceKeyword 过滤（resourceName 模糊匹配）
  if (body.resourceKeyword) {
    filtered = filtered.filter(
      g => g.resourceName && g.resourceName.includes(body.resourceKeyword)
    );
  }

  // 5. 分组合并
  const groupMap = new Map<string, PermissionGrant[]>();
  for (const g of filtered) {
    // 分组键用 JSON.stringify 生成（无控制字节、无歧义，避免 rg 误判 binary）
    const key = JSON.stringify([
      g.domainCode,
      g.resourceTypeCode,
      g.resourceCode,
      g.codeType,
      g.scopeMode
    ]);
    if (!groupMap.has(key)) groupMap.set(key, []);
    groupMap.get(key)!.push(g);
  }

  const items: EffectivePermissionItem[] = [];
  for (const gs of groupMap.values()) {
    const first = gs[0];
    const operationCodes = [...new Set(gs.map(g => g.operationCode))];
    // 来源角色去重（按 roleExternalId）
    const sourceRoleMap = new Map<string, SourceRole>();
    for (const g of gs) {
      if (g.sourceRole && !sourceRoleMap.has(g.sourceRole.roleExternalId)) {
        sourceRoleMap.set(g.sourceRole.roleExternalId, g.sourceRole);
      }
    }
    const allSourceRoles = [...sourceRoleMap.values()];
    const sourceRoleCount = allSourceRoles.length;
    const matchedPermissionIds = [...new Set(gs.map(g => g.permissionId))];

    // 6. includeSourceRoles / sourceRoleLimit 投影
    const includeSourceRoles = body.includeSourceRoles !== false;
    let sourceRoles: SourceRole[] = includeSourceRoles ? allSourceRoles : [];
    let truncated = false;
    const limit = body.sourceRoleLimit;
    if (
      includeSourceRoles &&
      typeof limit === "number" &&
      limit > 0 &&
      sourceRoles.length > limit
    ) {
      sourceRoles = sourceRoles.slice(0, limit);
      truncated = true;
    }

    items.push({
      domainCode: first.domainCode,
      resourceTypeCode: first.resourceTypeCode,
      resourceCode: first.resourceCode,
      resourceName: first.resourceName,
      codeType: first.codeType,
      operationCodes,
      scopeMode: first.scopeMode,
      sourceRoles,
      sourceRoleCount,
      sourceRolesTruncated: truncated,
      matchedPermissionIds
    });
  }
  return items;
}

// ========== auth/check 语义匹配（ALL 优先短路，共用） ==========

/**
 * auth/check 语义匹配（对齐 PermQueryEngine L149-160 earlyReturnOnScopeAll=true）。
 * 命中 ALL 授权时只返回 ALL grant，不收集 INSTANCE grant（避免污染来源角色/matchedPermissionIds
 * 及错误激活 dependOn 实例权限）。调用方保证 grants 已按 ownerType 过滤。
 */
function authCheckMatch(
  grants: PermissionGrant[],
  domainCode: string,
  resourceTypeCode: string,
  operationCode: string,
  resourceCode: string | null,
  codeType: string | null
): PermissionGrant[] {
  const candidates = grants.filter(
    g =>
      g.domainCode === domainCode &&
      g.resourceTypeCode === resourceTypeCode &&
      g.operationCode === operationCode
  );
  const allGrants = candidates.filter(g => g.scopeMode === "ALL");
  if (allGrants.length > 0) {
    return allGrants;
  }
  return candidates.filter(
    g =>
      g.scopeMode === "INSTANCE" &&
      g.resourceCode === resourceCode &&
      g.codeType === codeType
  );
}

// ========== Tab2 query-scopes 派生 ==========

/** query-scopes 主权限判定（auth/check 语义：ALL 优先短路） */
function checkParentPermissions(
  grants: PermissionGrant[],
  body: any
): { matchedOps: string[]; parentPermissionIds: number[] } {
  const domainCode = body.domainCode || "example";
  const requestedParentOps: string[] = Array.isArray(body.parentOperationCodes)
    ? body.parentOperationCodes
    : [];
  const matchedOps: string[] = [];
  const permIdSet = new Set<number>();
  for (const op of requestedParentOps) {
    const matched = authCheckMatch(
      grants,
      domainCode,
      body.parentResourceTypeCode,
      op,
      body.parentResourceCode,
      body.parentCodeType
    );
    if (matched.length > 0) {
      matchedOps.push(op);
      matched.forEach(g => permIdSet.add(g.permissionId));
    }
  }
  return { matchedOps, parentPermissionIds: [...permIdSet] };
}

/** 四态固定格（DATA 类型 4 操作）；请求的其他类型/操作默认 DENIED */
const fixedScopeGroups: ScopeGroup[] = [
  {
    resourceTypeCode: "DATA",
    operationCode: "DATA_READ",
    scopeMode: "ALL",
    items: [],
    matchedRoleIds: [10, 12],
    matchedPermissionIds: [301, 302],
    dependOnPermissionIds: [200]
  },
  {
    resourceTypeCode: "DATA",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE",
    items: [
      {
        resourceCode: "data:dept:A",
        codeType: "default",
        resourceName: "A部门数据"
      },
      {
        resourceCode: "data:dept:B",
        codeType: "default",
        resourceName: "B部门数据"
      }
    ],
    matchedRoleIds: [10, 15],
    matchedPermissionIds: [302, 501],
    dependOnPermissionIds: []
  },
  {
    resourceTypeCode: "DATA",
    operationCode: "DATA_EXPORT",
    scopeMode: "DENIED",
    items: [],
    matchedRoleIds: [],
    matchedPermissionIds: [],
    dependOnPermissionIds: []
  },
  {
    resourceTypeCode: "DATA",
    operationCode: "DATA_DELETE",
    scopeMode: "EMPTY",
    items: [],
    matchedRoleIds: [10],
    matchedPermissionIds: [303],
    dependOnPermissionIds: []
  }
];

/** 生成全 DENIED 笛卡尔积（§6.7 L1334：主权限全不通过时每格 DENIED） */
function buildDeniedScopeGroups(
  scopeTypes: string[],
  scopeOps: string[]
): ScopeGroup[] {
  const groups: ScopeGroup[] = [];
  for (const rt of scopeTypes) {
    for (const op of scopeOps) {
      groups.push({
        resourceTypeCode: rt,
        operationCode: op,
        scopeMode: "DENIED" as ScopeMode,
        items: [],
        matchedRoleIds: [],
        matchedPermissionIds: [],
        dependOnPermissionIds: []
      });
    }
  }
  return groups;
}

/** 按请求笛卡尔积生成 scopeGroups（四态固定格优先，其余 DENIED） */
function buildScopeGroups(
  scopeTypes: string[],
  scopeOps: string[]
): ScopeGroup[] {
  const groups: ScopeGroup[] = [];
  for (const rt of scopeTypes) {
    for (const op of scopeOps) {
      const fixed = fixedScopeGroups.find(
        g => g.resourceTypeCode === rt && g.operationCode === op
      );
      groups.push(
        fixed ?? {
          resourceTypeCode: rt,
          operationCode: op,
          scopeMode: "DENIED" as ScopeMode,
          items: [],
          matchedRoleIds: [],
          matchedPermissionIds: [],
          dependOnPermissionIds: []
        }
      );
    }
  }
  return groups;
}

// ========== Tab3 explain 派生 ==========

const mockRecentChanges: RecentChange[] = [
  {
    changeLogId: 9001,
    eventType: "ROLE_PERMISSION_CHANGE",
    changeType: "REMOVE",
    impactLevel: "POSSIBLE",
    message: "角色 报表编辑员 删除了销售报表 DATA_EDIT 权限，可能影响该用户",
    permission: {
      domainCode: "example",
      resourceTypeCode: "REPORT",
      resourceCode: "report:sales",
      codeType: "default",
      operationCode: "DATA_EDIT",
      scopeMode: "INSTANCE"
    },
    sourceRole: {
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "role_report_editor",
      roleName: "报表编辑员",
      via: []
    },
    operatorId: 100,
    operatorName: "admin",
    changeReason: "权限清理",
    createdAt: "2026-07-06 10:30:00"
  }
];

/** 构造 explain 权限键（scopeMode=ALL 时 resourceCode/codeType 置 null） */
function buildPermission(body: any): ExplainPermission {
  const all = body.scopeMode === "ALL";
  return {
    domainCode: body.domainCode || "example",
    resourceTypeCode: body.resourceTypeCode,
    resourceCode: all ? null : body.resourceCode || null,
    codeType: all ? null : body.codeType || null,
    operationCode: body.operationCode,
    scopeMode: body.scopeMode
  };
}

/**
 * USER explain 判定（复用 auth/check 语义：ALL 优先短路，§6.8 L1515 + PermQueryEngine L149-160）。
 * - scopeMode=ALL：只匹配类型级 ALL 授权
 * - scopeMode=INSTANCE：authCheckMatch（命中 ALL 只返回 ALL grant，否则 INSTANCE 精确匹配）
 */
function explainUserMatch(
  grants: PermissionGrant[],
  body: any
): PermissionGrant[] {
  const domainCode = body.domainCode || "example";
  if (body.scopeMode === "ALL") {
    return grants.filter(
      g =>
        g.ownerType === "USER" &&
        g.domainCode === domainCode &&
        g.resourceTypeCode === body.resourceTypeCode &&
        g.operationCode === body.operationCode &&
        g.scopeMode === "ALL"
    );
  }
  return authCheckMatch(
    grants,
    domainCode,
    body.resourceTypeCode,
    body.operationCode,
    body.resourceCode,
    body.codeType
  );
}

/**
 * ROLE explain 判定（精确匹配 scopeMode，§6.8 L1787-1789：仅判定角色直接拥有，不做 ALL 覆盖）。
 * - scopeMode=ALL：匹配类型级 ALL 授权
 * - scopeMode=INSTANCE：按 resourceCode+codeType 精确匹配
 */
function explainRoleMatch(
  grants: PermissionGrant[],
  body: any
): PermissionGrant[] {
  const domainCode = body.domainCode || "example";
  const candidates = grants.filter(
    g =>
      g.ownerType === "ROLE" &&
      g.domainCode === domainCode &&
      g.resourceTypeCode === body.resourceTypeCode &&
      g.operationCode === body.operationCode &&
      g.scopeMode === body.scopeMode
  );
  if (body.scopeMode === "ALL") {
    return candidates;
  }
  return candidates.filter(
    g => g.resourceCode === body.resourceCode && g.codeType === body.codeType
  );
}

/** 从匹配 grant 聚合来源角色（按 roleExternalId 去重） */
function collectSourceRoles(grants: PermissionGrant[]): SourceRole[] {
  const map = new Map<string, SourceRole>();
  for (const g of grants) {
    if (g.sourceRole && !map.has(g.sourceRole.roleExternalId)) {
      map.set(g.sourceRole.roleExternalId, g.sourceRole);
    }
  }
  return [...map.values()];
}

export default defineFakeRoute([
  // ========== Tab1: effective-permissions（从 grants 派生 + 过滤 + 分组合并） ==========
  {
    url: "/permission-query/effective-permissions",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const pageNum = body.pageNum || 1;
      const pageSize = body.pageSize || 10;
      const targetType: TargetType = body.targetType || "USER";

      // 1. 按主体复合键选取 grants
      let baseGrants: PermissionGrant[] = [];
      if (targetType === "USER") {
        baseGrants = matchUser(body) ? userGrants : [];
      } else if (targetType === "ROLE") {
        baseGrants = matchRole(body) ? roleGrants : [];
      }

      // 2. 派生 items（过滤 + 分组合并 + 投影）
      const items = deriveEffectiveItems(baseGrants, body);

      // 3. 分页
      const start = (pageNum - 1) * pageSize;
      const paged = items.slice(start, start + pageSize);
      return ok({
        targetType,
        items: paged,
        total: items.length,
        pageNum,
        pageSize,
        hasNext: start + pageSize < items.length
      });
    }
  },
  // ========== Tab2: query-scopes（主权限从 grants 派生 + 笛卡尔积四态） ==========
  {
    url: "/permission-query/query-scopes",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      // 未知用户（主体复合键校验）
      if (!matchUser(body)) {
        return ok({
          reason: "USER_NOT_FOUND",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: [],
          cacheTtlSeconds: 60
        });
      }
      // 主资源不存在（按完整复合键解析：domainCode+type+code+codeType，§6.7 L1335）
      if (!matchParentResource(body)) {
        return ok({
          reason: "OBJECT_KEY_NOT_FOUND",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: [],
          cacheTtlSeconds: 60
        });
      }
      // 主权限判定（从 userGrants 派生，auth/check 语义）
      const { matchedOps, parentPermissionIds } = checkParentPermissions(
        userGrants,
        body
      );
      const scopeTypes: string[] = Array.isArray(body.scopeResourceTypeCodes)
        ? body.scopeResourceTypeCodes
        : [];
      const scopeOps: string[] = Array.isArray(body.scopeOperationCodes)
        ? body.scopeOperationCodes
        : [];
      if (matchedOps.length === 0) {
        // 主权限全部不通过 -> NO_PERMISSION + 全 DENIED 笛卡尔积（§6.7 L1334）
        return ok({
          reason: "NO_PERMISSION",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: buildDeniedScopeGroups(scopeTypes, scopeOps),
          cacheTtlSeconds: 60
        });
      }
      // 正常返回：按请求笛卡尔积 + 四态映射
      return ok({
        reason: null,
        matchedParentOperations: matchedOps,
        parentPermissionIds,
        scopeGroups: buildScopeGroups(scopeTypes, scopeOps),
        cacheTtlSeconds: 60
      });
    }
  },
  // ========== Tab3: explain（USER: auth/check 语义; ROLE: 精确 scopeMode） ==========
  {
    url: "/permission-query/explain",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const permission = buildPermission(body);
      const includeSourceRoles = body.includeSourceRoles !== false;

      // ROLE 分支：主体复合键 + 精确匹配 scopeMode
      if (body.targetType === "ROLE") {
        if (!matchRole(body)) {
          return ok({
            targetType: "ROLE",
            allowed: false,
            reason: "ROLE_NOT_FOUND",
            permission,
            sourceRoles: [],
            matchedPermissionIds: [],
            recentChanges: []
          });
        }
        const matched = explainRoleMatch(roleGrants, body);
        const allowed = matched.length > 0;
        return ok({
          targetType: "ROLE",
          allowed,
          reason: allowed ? null : "NO_PERMISSION",
          permission,
          sourceRoles:
            allowed && includeSourceRoles ? [{ ...SOURCE_ROLE_VIEWER }] : [],
          matchedPermissionIds: [...new Set(matched.map(g => g.permissionId))],
          recentChanges:
            body.includeRecentChanges && !allowed ? mockRecentChanges : []
        });
      }

      // USER 分支：主体复合键 + auth/check 语义（ALL 覆盖 INSTANCE）
      if (!matchUser(body)) {
        return ok({
          targetType: "USER",
          allowed: false,
          reason: "USER_NOT_FOUND",
          permission,
          sourceRoles: [],
          matchedPermissionIds: [],
          recentChanges: []
        });
      }
      const matched = explainUserMatch(userGrants, body);
      const allowed = matched.length > 0;
      const sourceRoles =
        allowed && includeSourceRoles ? collectSourceRoles(matched) : [];
      return ok({
        targetType: "USER",
        allowed,
        reason: allowed ? null : "NO_PERMISSION",
        permission,
        sourceRoles,
        matchedPermissionIds: [...new Set(matched.map(g => g.permissionId))],
        recentChanges:
          body.includeRecentChanges && !allowed ? mockRecentChanges : []
      });
    }
  }
]);
