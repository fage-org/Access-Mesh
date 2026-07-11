// 权限排查 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回 PermResult 信封：{ code, message, data }
// 模拟未来 admin-service 聚合路径 /permission-query/*（T-PERM-033 实现后对接真聚合层，前端无需改路径）
// 零 src 依赖：类型本地声明，避免 fake-server 经 bundle-import 打包 src/api 链
//
// 契约依据：docs/design/permission-center/api-contract.md §6.6-6.8
// 主体模型：effective-permissions/explain 支持 USER/ROLE；query-scopes 仅 USER
//
// Mock 场景覆盖（评审 P1 修复：按完整主体键+资源键+操作键判定）：
// - Tab1 effective-permissions：按 domainCode/resourceTypeCodes/operationCodes/resourceKeyword 过滤
//   + operationCodes 裁剪为交集（非仅过滤行）+ includeSourceRoles/sourceRoleLimit 控制 sourceRoles
//   + USER/ROLE 成功/未知空结果/分页翻页/sourceRolesTruncated
// - Tab2 query-scopes：主权限判定（只返回通过的主操作，全不通过 -> NO_PERMISSION）
//   + 按请求 scopeResourceTypeCodes × scopeOperationCodes 笛卡尔积返回 + 四态(ALL/INSTANCE/DENIED/EMPTY)
//   + USER_NOT_FOUND/OBJECT_KEY_NOT_FOUND
// - Tab3 explain：按完整权限键判定 allowed（domainCode+resourceTypeCode+resourceCode+codeType+operationCode+scopeMode）
//   + ROLE 校验 roleTypeCode(BASIC_ROLE)+roleExternalId+domainCode
//   + includeSourceRoles=false -> sourceRoles=[] + allowed=true/false/USER_NOT_FOUND/ROLE_NOT_FOUND/NO_PERMISSION
import { defineFakeRoute } from "vite-plugin-fake-server/client";

type TargetType = "USER" | "ROLE";
type ScopeMode = "DENIED" | "INSTANCE" | "ALL" | "EMPTY";

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
/** 完整权限键事实（explain 判定依据） */
interface PermissionFact {
  domainCode: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
  scopeMode: ScopeMode;
}

const ok = (data: unknown) => ({ code: 200, message: "success", data });

// 已知主体：
// - 用户 u-10001（ADMIN_USER）
// - 角色 role_report_viewer（BASIC_ROLE，域 example）
// 域：example（report:sales/data:*）/ finance（report:finance）/ hr（report:hr）
// 资源类型：REPORT / DATA
// 资源：report:sales / report:finance / report:hr / data:dept:A / data:dept:B / data:dept:C
// 操作：DATA_READ / DATA_EDIT / DATA_EXPORT / DATA_DELETE

// ========== 权限事实表（按完整权限键，explain 判定依据） ==========

/** USER u-10001 权限事实（domainCode+resourceTypeCode+resourceCode+codeType+operationCode+scopeMode） */
const userPermissionFacts: PermissionFact[] = [
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_EXPORT",
    scopeMode: "ALL"
  }
];

/** ROLE role_report_viewer (BASIC_ROLE) 权限事实 */
const rolePermissionFacts: PermissionFact[] = [
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    operationCode: "DATA_EDIT",
    scopeMode: "INSTANCE"
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    codeType: "default",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE"
  }
];

/** 主资源 report:sales 通过的主操作（query-scopes 主权限判定依据） */
const matchedParentOps: string[] = ["DATA_READ", "DATA_EDIT"];

/** 按完整权限键匹配事实（scopeMode=ALL 时 resourceCode/codeType 置 null） */
function matchFact(facts: PermissionFact[], body: any): boolean {
  const all = body.scopeMode === "ALL";
  const resourceCode = all ? null : body.resourceCode || null;
  const codeType = all ? null : body.codeType || null;
  const domainCode = body.domainCode || "example";
  return facts.some(
    p =>
      p.domainCode === domainCode &&
      p.resourceTypeCode === body.resourceTypeCode &&
      p.resourceCode === resourceCode &&
      p.codeType === codeType &&
      p.operationCode === body.operationCode &&
      p.scopeMode === body.scopeMode
  );
}

// ========== Tab1 effective-permissions mock 数据 ==========

/** USER 视角权限条目（8 条，用于分页翻页测试 pageSize=5） */
const userPermItems: EffectivePermissionItem[] = [
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    resourceName: "销售报表",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_report_viewer",
        roleName: "报表查看员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [200]
  },
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    resourceName: null,
    codeType: null,
    operationCodes: ["DATA_READ"],
    scopeMode: "ALL",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_admin",
        roleName: "管理员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [201]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    resourceName: "A部门数据",
    codeType: "default",
    operationCodes: ["DATA_READ", "DATA_EDIT"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_report_viewer",
        roleName: "报表查看员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [302]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    resourceName: "B部门数据",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_report_viewer",
        roleName: "报表查看员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [303]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:C",
    resourceName: "C部门数据",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_report_viewer",
        roleName: "报表查看员",
        via: []
      },
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_admin",
        roleName: "管理员",
        via: []
      },
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_data_analyst",
        roleName: "数据分析员",
        via: []
      },
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_auditor",
        roleName: "审计员",
        via: []
      }
    ],
    sourceRoleCount: 4,
    sourceRolesTruncated: true,
    matchedPermissionIds: [304, 305, 306, 307]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: null,
    resourceName: null,
    codeType: null,
    operationCodes: ["DATA_EXPORT"],
    scopeMode: "ALL",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_admin",
        roleName: "管理员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [401]
  },
  {
    domainCode: "finance",
    resourceTypeCode: "REPORT",
    resourceCode: "report:finance",
    resourceName: "财务报表",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_finance",
        roleName: "财务员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [208]
  },
  {
    domainCode: "hr",
    resourceTypeCode: "REPORT",
    resourceCode: "report:hr",
    resourceName: "人事报表",
    codeType: "default",
    operationCodes: ["DATA_READ", "DATA_EDIT"],
    scopeMode: "INSTANCE",
    sourceRoles: [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role_hr",
        roleName: "人事员",
        via: []
      }
    ],
    sourceRoleCount: 1,
    sourceRolesTruncated: false,
    matchedPermissionIds: [209]
  }
];

/** ROLE 视角权限条目（role_report_viewer 直接权限，4 条） */
const rolePermItems: EffectivePermissionItem[] = [
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    resourceName: "销售报表",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [],
    sourceRoleCount: 0,
    sourceRolesTruncated: false,
    matchedPermissionIds: [200]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    resourceName: "A部门数据",
    codeType: "default",
    operationCodes: ["DATA_READ", "DATA_EDIT"],
    scopeMode: "INSTANCE",
    sourceRoles: [],
    sourceRoleCount: 0,
    sourceRolesTruncated: false,
    matchedPermissionIds: [302]
  },
  {
    domainCode: "example",
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    resourceName: "B部门数据",
    codeType: "default",
    operationCodes: ["DATA_READ"],
    scopeMode: "INSTANCE",
    sourceRoles: [],
    sourceRoleCount: 0,
    sourceRolesTruncated: false,
    matchedPermissionIds: [303]
  },
  {
    domainCode: "example",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    resourceName: null,
    codeType: null,
    operationCodes: ["DATA_READ"],
    scopeMode: "ALL",
    sourceRoles: [],
    sourceRoleCount: 0,
    sourceRolesTruncated: false,
    matchedPermissionIds: [210]
  }
];

// ========== Tab2 query-scopes mock 数据（四态，按请求笛卡尔积查） ==========

/** 四态固定格（DATA 类型，4 操作）；请求的其他类型/操作默认 DENIED */
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

// ========== Tab3 explain mock 数据 ==========

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

export default defineFakeRoute([
  // ========== Tab1: effective-permissions（按请求条件过滤 + operationCodes 裁剪为交集） ==========
  {
    url: "/permission-query/effective-permissions",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const pageNum = body.pageNum || 1;
      const pageSize = body.pageSize || 10;

      // 1. 按 targetType + 主体获取基础 items
      let items: EffectivePermissionItem[] = [];
      const targetType: TargetType = body.targetType || "USER";
      if (body.targetType === "USER") {
        items = body.subjectExternalId === "u-10001" ? userPermItems : [];
      } else if (body.targetType === "ROLE") {
        items =
          body.roleExternalId === "role_report_viewer" ? rolePermItems : [];
      }

      // 2. 按 domainCode 过滤
      if (body.domainCode) {
        items = items.filter(i => i.domainCode === body.domainCode);
      }
      // 3. 按 resourceTypeCodes 过滤
      if (
        Array.isArray(body.resourceTypeCodes) &&
        body.resourceTypeCodes.length
      ) {
        items = items.filter(i =>
          body.resourceTypeCodes.includes(i.resourceTypeCode)
        );
      }
      // 4. 按 operationCodes 过滤行 + 裁剪 operationCodes 为交集（评审 P1 修复：非仅过滤行）
      if (Array.isArray(body.operationCodes) && body.operationCodes.length) {
        items = items
          .map(i => ({
            ...i,
            operationCodes: i.operationCodes.filter((op: string) =>
              body.operationCodes.includes(op)
            )
          }))
          .filter(i => i.operationCodes.length > 0);
      }
      // 5. 按 resourceKeyword 过滤（resourceName 模糊匹配）
      if (body.resourceKeyword) {
        items = items.filter(
          i => i.resourceName && i.resourceName.includes(body.resourceKeyword)
        );
      }

      // 6. 按请求投影 sourceRoles（复制避免污染源数据）
      items = items.map(i => {
        const copy: EffectivePermissionItem = {
          ...i,
          sourceRoles: [...i.sourceRoles]
        };
        // includeSourceRoles=false -> sourceRoles=[]（sourceRoleCount 保留）
        if (body.includeSourceRoles === false) {
          copy.sourceRoles = [];
        }
        // sourceRoleLimit 截断 sourceRoles
        const limit = body.sourceRoleLimit;
        if (
          typeof limit === "number" &&
          limit > 0 &&
          copy.sourceRoles.length > limit
        ) {
          copy.sourceRoles = copy.sourceRoles.slice(0, limit);
          copy.sourceRolesTruncated = true;
        }
        return copy;
      });

      // 7. 分页
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
  // ========== Tab2: query-scopes（主权限判定 + 按请求笛卡尔积返回） ==========
  {
    url: "/permission-query/query-scopes",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      // 未知用户
      if (body.subjectExternalId !== "u-10001") {
        return ok({
          reason: "USER_NOT_FOUND",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: [],
          cacheTtlSeconds: 60
        });
      }
      // 主资源不存在
      if (body.parentResourceCode !== "report:sales") {
        return ok({
          reason: "OBJECT_KEY_NOT_FOUND",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: [],
          cacheTtlSeconds: 60
        });
      }
      // 主权限判定：只返回通过的主操作（评审 P1 修复：模拟 NO_PERMISSION）
      const requestedParentOps: string[] = Array.isArray(
        body.parentOperationCodes
      )
        ? body.parentOperationCodes
        : [];
      const matchedOps = requestedParentOps.filter(op =>
        matchedParentOps.includes(op)
      );
      if (matchedOps.length === 0) {
        // 主权限全部不通过 -> NO_PERMISSION（§6.7 L1334）
        return ok({
          reason: "NO_PERMISSION",
          matchedParentOperations: [],
          parentPermissionIds: [],
          scopeGroups: [],
          cacheTtlSeconds: 60
        });
      }
      // 按请求 scopeResourceTypeCodes × scopeOperationCodes 笛卡尔积生成 scopeGroups
      const scopeTypes: string[] = Array.isArray(body.scopeResourceTypeCodes)
        ? body.scopeResourceTypeCodes
        : [];
      const scopeOps: string[] = Array.isArray(body.scopeOperationCodes)
        ? body.scopeOperationCodes
        : [];
      const scopeGroups: ScopeGroup[] = [];
      for (const rt of scopeTypes) {
        for (const op of scopeOps) {
          const fixed = fixedScopeGroups.find(
            g => g.resourceTypeCode === rt && g.operationCode === op
          );
          scopeGroups.push(
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
      return ok({
        reason: null,
        matchedParentOperations: matchedOps,
        parentPermissionIds: [200, 260],
        scopeGroups,
        cacheTtlSeconds: 60
      });
    }
  },
  // ========== Tab3: explain（按完整权限键判定 + roleTypeCode/domainCode/includeSourceRoles 校验） ==========
  {
    url: "/permission-query/explain",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const permission = buildPermission(body);
      const includeSourceRoles = body.includeSourceRoles !== false;

      // ROLE 分支：校验 roleTypeCode + roleExternalId（评审 P1 修复）
      if (body.targetType === "ROLE") {
        if (
          body.roleTypeCode !== "BASIC_ROLE" ||
          body.roleExternalId !== "role_report_viewer"
        ) {
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
        // 按完整权限键判定（domainCode+resourceTypeCode+resourceCode+codeType+operationCode+scopeMode）
        const allowed = matchFact(rolePermissionFacts, body);
        return ok({
          targetType: "ROLE",
          allowed,
          reason: allowed ? null : "NO_PERMISSION",
          permission,
          sourceRoles:
            allowed && includeSourceRoles
              ? [
                  {
                    roleTypeCode: "BASIC_ROLE",
                    roleExternalId: "role_report_viewer",
                    roleName: "报表查看员",
                    via: []
                  }
                ]
              : [],
          matchedPermissionIds: allowed ? [200] : [],
          recentChanges:
            body.includeRecentChanges && !allowed ? mockRecentChanges : []
        });
      }

      // USER 分支：校验 subjectExternalId + 按完整权限键判定
      if (body.subjectExternalId !== "u-10001") {
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
      const allowed = matchFact(userPermissionFacts, body);
      return ok({
        targetType: "USER",
        allowed,
        reason: allowed ? null : "NO_PERMISSION",
        permission,
        sourceRoles:
          allowed && includeSourceRoles
            ? [
                {
                  roleTypeCode: "BASIC_ROLE",
                  roleExternalId: "role_report_viewer",
                  roleName: "报表查看员",
                  via: []
                }
              ]
            : [],
        matchedPermissionIds: allowed ? [200] : [],
        recentChanges:
          body.includeRecentChanges && !allowed ? mockRecentChanges : []
      });
    }
  }
]);
