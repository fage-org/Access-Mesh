// 权限排查 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回 PermResult 信封：{ code, message, data }
// 模拟未来 admin-service 聚合路径 /permission-query/*（T-PERM-033 实现后对接真聚合层，前端无需改路径）
// 零 src 依赖：类型本地声明，避免 fake-server 经 bundle-import 打包 src/api 链
// （该链 import @/utils/http 等浏览器侧依赖，node platform 下打包失败 -> mock 加载被静默吞掉 -> 404）
//
// 契约依据：docs/design/permission-center/api-contract.md §6.6-6.8
// 主体模型：effective-permissions/explain 支持 USER/ROLE；query-scopes 仅 USER
//
// Mock 场景覆盖：
// - Tab1 effective-permissions：USER 成功(INSTANCE+ALL+sourceRoles)/ROLE 成功/未知主体空结果/分页翻页/sourceRolesTruncated
// - Tab2 query-scopes：四态 scopeGroups(ALL/INSTANCE/DENIED/EMPTY)/USER_NOT_FOUND/OBJECT_KEY_NOT_FOUND
// - Tab3 explain：allowed=true(INSTANCE)/allowed=true(ALL,不发resourceCode)/allowed=false(NO_PERMISSION+recentChanges)/USER_NOT_FOUND/ROLE_NOT_FOUND
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

const ok = (data: unknown) => ({ code: 200, message: "success", data });

// 已知主体：
// - 用户 u-10001（ADMIN_USER）
// - 角色 role_report_viewer（BASIC_ROLE）
// 域：example
// 资源类型：REPORT / DATA
// 资源：report:sales / data:dept:A / data:dept:B / data:dept:C
// 操作：DATA_READ / DATA_EDIT / DATA_EXPORT / DATA_DELETE

// ========== Tab1 effective-permissions mock 数据 ==========

/** USER 视角权限条目（8 条，用于分页翻页测试 pageSize=5） */
const userPermItems: EffectivePermissionItem[] = [
  {
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

// ========== Tab2 query-scopes mock 数据（四态） ==========

const mockScopeGroups: ScopeGroup[] = [
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
  // ========== Tab1: effective-permissions ==========
  {
    url: "/permission-query/effective-permissions",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const pageNum = body.pageNum || 1;
      const pageSize = body.pageSize || 10;
      const start = (pageNum - 1) * pageSize;

      let items: EffectivePermissionItem[] = [];
      const targetType: TargetType = body.targetType || "USER";

      if (body.targetType === "USER") {
        // 未知用户 -> 空结果（非 reason，effective-permissions 无 reason 字段）
        items = body.subjectExternalId === "u-10001" ? userPermItems : [];
      } else if (body.targetType === "ROLE") {
        // 未知角色 -> 空结果
        items =
          body.roleExternalId === "role_report_viewer" ? rolePermItems : [];
      }

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
  // ========== Tab2: query-scopes ==========
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
      return ok({
        reason: null,
        matchedParentOperations: body.parentOperationCodes || [],
        parentPermissionIds: [200, 260],
        scopeGroups: mockScopeGroups,
        cacheTtlSeconds: 60
      });
    }
  },
  // ========== Tab3: explain ==========
  {
    url: "/permission-query/explain",
    method: "POST",
    response: (req: { body: any }) => {
      const body = req.body || {};
      const permission = buildPermission(body);

      // USER 分支
      if (body.targetType === "ROLE") {
        if (body.roleExternalId !== "role_report_viewer") {
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
        // ROLE 视角：DATA_READ 允许
        return ok({
          targetType: "ROLE",
          allowed: true,
          reason: null,
          permission,
          sourceRoles: [
            {
              roleTypeCode: "BASIC_ROLE",
              roleExternalId: "role_report_viewer",
              roleName: "报表查看员",
              via: []
            }
          ],
          matchedPermissionIds: [200],
          recentChanges: body.includeRecentChanges ? mockRecentChanges : []
        });
      }

      // USER 分支
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
      // DATA_READ 允许（INSTANCE + ALL 均允许，验证 ALL 不发 resourceCode/codeType）
      if (body.operationCode === "DATA_READ") {
        return ok({
          targetType: "USER",
          allowed: true,
          reason: null,
          permission,
          sourceRoles: [
            {
              roleTypeCode: "BASIC_ROLE",
              roleExternalId: "role_report_viewer",
              roleName: "报表查看员",
              via: []
            }
          ],
          matchedPermissionIds: [200],
          recentChanges: []
        });
      }
      // 其余操作拒绝 + recentChanges（验证 NO_PERMISSION）
      return ok({
        targetType: "USER",
        allowed: false,
        reason: "NO_PERMISSION",
        permission,
        sourceRoles: [],
        matchedPermissionIds: [],
        recentChanges: body.includeRecentChanges ? mockRecentChanges : []
      });
    }
  }
]);
