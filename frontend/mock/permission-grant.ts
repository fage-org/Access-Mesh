// 权限授予 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回 PermResult 信封：{ code, message, data }
// 零 src 依赖：类型本地声明，避免 fake-server 经 bundle-import 打包 src/api 链
//
// 契约依据：docs/design/permission-center/api-contract.md §5.5 / §6.4 / §6.5
// 单一事实源：PermissionFact 统一权限事实表，派生 list/save/children/add-child/remove-child
//
// 能力驱动（决策点 6 按归属拆分）：
// - 角色节点：directGrantable / canView / canManage（roleFacts）
// - 资源类型：supportsInstance/All/Condition/Delegation（resourceTypeFacts）
// - 业务域：supportsChildren / childResourceTypeCodes（domainConfig）
// - 候选权限单元：grantableByOperator / denyReason（由 operatorCapability 组合判定，前端计算）
//
// 场景覆盖：
// - 五类型角色：BASIC_ROLE(role_admin/role_security/role_report_viewer/role_disabled)
//   GROUP_ROLE(role_audit_group directGrantable=false 只读)、ORG/POSITION/PERSONAL
// - 资源类型：MENU/DATA/REPORT/API/BUTTON（BUTTON/API supportsAll=false supportsDelegation=false）
// - ALL+INSTANCE 并存：role_report_viewer REPORT/DATA_READ 同时有 ALL(id=260)+INSTANCE(id=200)
// - 子权限：role_report_viewer 主权限 200 下挂 201/202/203（§6.5 示例）
// - 条件绑定：role_admin sys:role/UPDATE 绑 office-hours
// - 不可授予：operatorCapability.grantableOperationCodes 不含 DELETE/DATA_DELETE（disabled 演示）
// - 禁用角色：role_disabled enabled=false
//
// 可控失败（决策点 5）：localStorage "__permGrant_simulateChildFailure"="true" 时
// add-child 返回 500，hook 捕获后重载事实 + 保留失败草稿 + 重试状态
import { defineFakeRoute } from "vite-plugin-fake-server/client";

type GrantScopeMode = "INSTANCE" | "ALL";

// ========== 类型（本地声明，零 src 依赖） ==========

interface RoleFact {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  domainCode: string;
  parentExternalId: string | null;
  enabled: boolean;
  directGrantable: boolean;
}
interface ResourceTypeFact {
  resourceTypeCode: string;
  resourceTypeName: string;
  supportsInstance: boolean;
  supportsAll: boolean;
  supportsCondition: boolean;
  supportsDelegation: boolean;
}
interface ResourceFact {
  resourceTypeCode: string;
  resourceCode: string;
  codeType: string;
  resourceName: string;
  parentCode: string | null;
}
interface OperationFact {
  operationCode: string;
  operationName: string;
  resourceTypeCode: string;
  inheritMask: string | null;
}
interface ConditionFact {
  conditionId: number;
  code: string;
  name: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
  summary: string;
}
interface PermissionFact {
  id: number;
  domainCode: string;
  roleTypeCode: string;
  roleExternalId: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  resourceName: string | null;
  operationCode: string;
  scopeMode: GrantScopeMode;
  conditionCode: string | null;
  canGrant: boolean;
  dependOn: number | null;
  grantSource: string;
}

interface RoleTreeNode {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  domainCode: string;
  parentId: string | null;
  enabled: boolean;
  directGrantable: boolean;
  canView: boolean;
  canManage: boolean;
  children: RoleTreeNode[];
}
interface ResourceTreeNode {
  resourceCode: string;
  codeType: string;
  resourceName: string;
  parentCode: string | null;
  children: ResourceTreeNode[];
}

const ok = (data: unknown) => ({ code: 200, message: "success", data });
const fail = (code: number, message: string, data: unknown = null) => ({
  code,
  message,
  data
});

// ========== 已知数据常量 ==========
// 域：example（REPORT/DATA/MENU）/ finance（DATA）
// 资源类型：MENU / DATA / REPORT / API / BUTTON
// 角色：5 类型覆盖（BASIC_ROLE/GROUP_ROLE/ORG/POSITION/PERSONAL）

// ========== 角色事实表（5 类型） ==========
const roleFacts: RoleFact[] = [
  // BASIC_ROLE
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_admin",
    roleName: "系统管理员",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_security",
    roleName: "安全管理员",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    roleName: "报表查看员",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_disabled",
    roleName: "已禁用角色",
    domainCode: "example",
    parentExternalId: null,
    enabled: false,
    directGrantable: true
  },
  // GROUP_ROLE（组合角色，directGrantable=false 只读，§3.4）
  {
    roleTypeCode: "GROUP_ROLE",
    roleExternalId: "role_audit_group",
    roleName: "审计组合角色",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: false
  },
  // ORG
  {
    roleTypeCode: "ORG",
    roleExternalId: "org_dev",
    roleName: "研发部",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "ORG",
    roleExternalId: "org_finance",
    roleName: "财务部",
    domainCode: "finance",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  // POSITION
  {
    roleTypeCode: "POSITION",
    roleExternalId: "pos_engineer",
    roleName: "工程师岗位",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  },
  // PERSONAL
  {
    roleTypeCode: "PERSONAL",
    roleExternalId: "personal_u10001",
    roleName: "用户10001个人角色",
    domainCode: "example",
    parentExternalId: null,
    enabled: true,
    directGrantable: true
  }
];

// ========== 资源类型事实表 ==========
const resourceTypeFacts: ResourceTypeFact[] = [
  {
    resourceTypeCode: "MENU",
    resourceTypeName: "菜单",
    supportsInstance: true,
    supportsAll: true,
    supportsCondition: true,
    supportsDelegation: true
  },
  {
    resourceTypeCode: "DATA",
    resourceTypeName: "数据",
    supportsInstance: true,
    supportsAll: true,
    supportsCondition: true,
    supportsDelegation: true
  },
  {
    resourceTypeCode: "REPORT",
    resourceTypeName: "报表",
    supportsInstance: true,
    supportsAll: true,
    supportsCondition: true,
    supportsDelegation: true
  },
  {
    resourceTypeCode: "API",
    resourceTypeName: "接口",
    supportsInstance: true,
    supportsAll: true,
    supportsCondition: true,
    supportsDelegation: false
  },
  {
    resourceTypeCode: "BUTTON",
    resourceTypeName: "按钮",
    supportsInstance: true,
    supportsAll: false,
    supportsCondition: true,
    supportsDelegation: false
  }
];

// ========== 资源事实表（扁平，按类型组织成树） ==========
const resourceFacts: ResourceFact[] = [
  // MENU
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys",
    codeType: "default",
    resourceName: "系统管理",
    parentCode: null
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys:user",
    codeType: "default",
    resourceName: "组织与用户",
    parentCode: "sys"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys:role",
    codeType: "default",
    resourceName: "角色管理",
    parentCode: "sys"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys:resource",
    codeType: "default",
    resourceName: "资源与操作",
    parentCode: "sys"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys:config",
    codeType: "default",
    resourceName: "系统配置",
    parentCode: "sys"
  },
  // DATA
  {
    resourceTypeCode: "DATA",
    resourceCode: "data",
    codeType: "default",
    resourceName: "数据根",
    parentCode: null
  },
  {
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:A",
    codeType: "default",
    resourceName: "A 部门数据",
    parentCode: "data"
  },
  {
    resourceTypeCode: "DATA",
    resourceCode: "data:dept:B",
    codeType: "default",
    resourceName: "B 部门数据",
    parentCode: "data"
  },
  {
    resourceTypeCode: "DATA",
    resourceCode: "data:city:shanghai",
    codeType: "default",
    resourceName: "上海数据",
    parentCode: "data"
  },
  {
    resourceTypeCode: "DATA",
    resourceCode: "data:city:hangzhou",
    codeType: "default",
    resourceName: "杭州数据",
    parentCode: "data"
  },
  // REPORT
  {
    resourceTypeCode: "REPORT",
    resourceCode: "report",
    codeType: "default",
    resourceName: "报表根",
    parentCode: null
  },
  {
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    resourceName: "销售报表",
    parentCode: "report"
  },
  {
    resourceTypeCode: "REPORT",
    resourceCode: "report:finance",
    codeType: "default",
    resourceName: "财务报表",
    parentCode: "report"
  },
  {
    resourceTypeCode: "REPORT",
    resourceCode: "report:hr",
    codeType: "default",
    resourceName: "人力报表",
    parentCode: "report"
  },
  // API
  {
    resourceTypeCode: "API",
    resourceCode: "api",
    codeType: "default",
    resourceName: "接口根",
    parentCode: null
  },
  {
    resourceTypeCode: "API",
    resourceCode: "api:auth-check",
    codeType: "default",
    resourceName: "鉴权校验",
    parentCode: "api"
  },
  {
    resourceTypeCode: "API",
    resourceCode: "api:user-list",
    codeType: "default",
    resourceName: "用户列表",
    parentCode: "api"
  },
  // BUTTON
  {
    resourceTypeCode: "BUTTON",
    resourceCode: "btn",
    codeType: "default",
    resourceName: "按钮根",
    parentCode: null
  },
  {
    resourceTypeCode: "BUTTON",
    resourceCode: "btn:save",
    codeType: "default",
    resourceName: "保存按钮",
    parentCode: "btn"
  },
  {
    resourceTypeCode: "BUTTON",
    resourceCode: "btn:delete",
    codeType: "default",
    resourceName: "删除按钮",
    parentCode: "btn"
  }
];

// ========== 操作事实表（按资源类型） ==========
const operationFacts: OperationFact[] = [
  // MENU
  {
    operationCode: "VIEW",
    operationName: "查看",
    resourceTypeCode: "MENU",
    inheritMask: null
  },
  {
    operationCode: "CREATE",
    operationName: "新增",
    resourceTypeCode: "MENU",
    inheritMask: null
  },
  {
    operationCode: "UPDATE",
    operationName: "编辑",
    resourceTypeCode: "MENU",
    inheritMask: null
  },
  {
    operationCode: "DELETE",
    operationName: "删除",
    resourceTypeCode: "MENU",
    inheritMask: null
  },
  // DATA
  {
    operationCode: "DATA_READ",
    operationName: "读取",
    resourceTypeCode: "DATA",
    inheritMask: null
  },
  {
    operationCode: "DATA_EDIT",
    operationName: "编辑",
    resourceTypeCode: "DATA",
    inheritMask: null
  },
  {
    operationCode: "DATA_DELETE",
    operationName: "删除",
    resourceTypeCode: "DATA",
    inheritMask: null
  },
  {
    operationCode: "DATA_EXPORT",
    operationName: "导出",
    resourceTypeCode: "DATA",
    inheritMask: null
  },
  // REPORT
  {
    operationCode: "DATA_READ",
    operationName: "读取",
    resourceTypeCode: "REPORT",
    inheritMask: null
  },
  {
    operationCode: "DATA_EDIT",
    operationName: "编辑",
    resourceTypeCode: "REPORT",
    inheritMask: null
  },
  // API
  {
    operationCode: "VIEW",
    operationName: "查看",
    resourceTypeCode: "API",
    inheritMask: null
  },
  // BUTTON
  {
    operationCode: "CLICK",
    operationName: "点击",
    resourceTypeCode: "BUTTON",
    inheritMask: null
  }
];

// ========== 条件候选 ==========
const conditionFacts: ConditionFact[] = [
  {
    conditionId: 1,
    code: "office-hours",
    name: "办公时间",
    enabled: true,
    gatewayEvaluable: true,
    summary: "AND · 1 项（日期范围）"
  },
  {
    conditionId: 2,
    code: "ip-whitelist-hq",
    name: "总部 IP 白名单",
    enabled: true,
    gatewayEvaluable: true,
    summary: "AND · 1 项（IP 白名单）"
  },
  {
    conditionId: 3,
    code: "time-restriction",
    name: "时间限制（已停用）",
    enabled: false,
    gatewayEvaluable: false,
    summary: "AND · 1 项（时间范围）"
  }
];

// ========== 业务域能力配置 ==========
const domainConfig: Record<
  string,
  { supportsChildren: boolean; childResourceTypeCodes: string[] }
> = {
  example: { supportsChildren: true, childResourceTypeCodes: ["DATA"] },
  finance: { supportsChildren: false, childResourceTypeCodes: [] }
};

// ========== 操作者能力（模拟当前操作者） ==========
// canManage=true；DELETE/DATA_DELETE 不可授予（演示 grantableByOperator=false disabled）
const operatorCapability = {
  canManage: true,
  grantableResourceTypeCodes: ["MENU", "DATA", "REPORT", "API", "BUTTON"],
  grantableOperationCodes: [
    "VIEW",
    "CREATE",
    "UPDATE",
    "DATA_READ",
    "DATA_EDIT",
    "DATA_EXPORT",
    "CLICK"
  ]
};

// ========== 权限事实表（可变，save/add-child/remove-child 写入） ==========
let permissionFacts: PermissionFact[] = [
  // role_admin: MENU/sys:role/UPDATE 绑 office-hours 条件
  {
    id: 100,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_admin",
    resourceTypeCode: "MENU",
    resourceCode: "sys:role",
    codeType: "default",
    resourceName: "角色管理",
    operationCode: "UPDATE",
    scopeMode: "INSTANCE",
    conditionCode: "office-hours",
    canGrant: true,
    dependOn: null,
    grantSource: "MANUAL"
  },
  {
    id: 101,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_admin",
    resourceTypeCode: "MENU",
    resourceCode: "sys:user",
    codeType: "default",
    resourceName: "组织与用户",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  // role_report_viewer: §6.5 示例（主权限 200 + 子权限 201/202/203 + ALL 260）
  {
    id: 200,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    resourceTypeCode: "REPORT",
    resourceCode: "report:sales",
    codeType: "default",
    resourceName: "销售报表",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  {
    id: 260,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    resourceName: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  // 子权限（dependOn=200）
  {
    id: 201,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    resourceTypeCode: "DATA",
    resourceCode: "data:city:shanghai",
    codeType: "default",
    resourceName: "上海数据",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  },
  {
    id: 202,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    resourceTypeCode: "DATA",
    resourceCode: "data:city:hangzhou",
    codeType: "default",
    resourceName: "杭州数据",
    operationCode: "DATA_READ",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  },
  {
    id: 203,
    domainCode: "example",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "role_report_viewer",
    resourceTypeCode: "DATA",
    resourceCode: null,
    codeType: null,
    resourceName: null,
    operationCode: "DATA_READ",
    scopeMode: "ALL",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  }
];

// ========== id 生成器 ==========
let nextPermissionId = 1000;

// ========== 工具函数 ==========

/** 查找角色事实 */
function findRole(
  roleTypeCode: string,
  roleExternalId: string
): RoleFact | undefined {
  return roleFacts.find(
    r => r.roleTypeCode === roleTypeCode && r.roleExternalId === roleExternalId
  );
}

/** 查询角色直接权限事实（主权限 + 子权限，按 domainCode+role 三键） */
function findRolePermissions(
  domainCode: string,
  roleTypeCode: string,
  roleExternalId: string
): PermissionFact[] {
  return permissionFacts.filter(
    p =>
      p.domainCode === domainCode &&
      p.roleTypeCode === roleTypeCode &&
      p.roleExternalId === roleExternalId
  );
}

/** PermissionFact -> RolePermissionItem（剥离角色键） */
function toRolePermissionItem(p: PermissionFact) {
  return {
    id: p.id,
    domainCode: p.domainCode,
    resourceTypeCode: p.resourceTypeCode,
    resourceCode: p.resourceCode,
    codeType: p.codeType,
    resourceName: p.resourceName,
    operationCode: p.operationCode,
    scopeMode: p.scopeMode,
    conditionCode: p.conditionCode,
    canGrant: p.canGrant,
    dependOn: p.dependOn,
    grantSource: p.grantSource
  };
}

/** 扁平资源 -> 树（按 resourceTypeCode 过滤） */
function buildResourceTree(
  resourceTypeCode: string,
  keyword?: string
): ResourceTreeNode[] {
  const facts = resourceFacts.filter(
    r => r.resourceTypeCode === resourceTypeCode
  );
  const kw = keyword?.trim().toLowerCase();
  const matchedCodes = new Set<string>();
  if (kw) {
    for (const f of facts) {
      if (
        f.resourceName.toLowerCase().includes(kw) ||
        f.resourceCode.toLowerCase().includes(kw)
      ) {
        matchedCodes.add(f.resourceCode);
      }
    }
    // 保留祖先路径
    for (const f of facts) {
      if (matchedCodes.has(f.resourceCode)) {
        let cur = f.parentCode;
        while (cur) {
          matchedCodes.add(cur);
          const parent = facts.find(x => x.resourceCode === cur);
          cur = parent?.parentCode ?? null;
        }
      }
    }
  }
  const buildNode = (parentCode: string | null): ResourceTreeNode[] => {
    return facts
      .filter(
        f =>
          f.parentCode === parentCode &&
          (!kw || matchedCodes.has(f.resourceCode))
      )
      .map(f => ({
        resourceCode: f.resourceCode,
        codeType: f.codeType,
        resourceName: f.resourceName,
        parentCode: f.parentCode,
        children: buildNode(f.resourceCode)
      }));
  };
  return buildNode(null);
}

/** 角色事实 -> 树节点（带能力，模拟操作者对每个角色的 canView/canManage） */
function toRoleTreeNode(r: RoleFact): RoleTreeNode {
  return {
    roleTypeCode: r.roleTypeCode,
    roleExternalId: r.roleExternalId,
    roleName: r.roleName,
    domainCode: r.domainCode,
    parentId: r.parentExternalId,
    enabled: r.enabled,
    directGrantable: r.directGrantable,
    // 模拟操作者能力：全部可查看；可管理 = directGrantable && enabled
    canView: true,
    canManage: r.directGrantable && r.enabled,
    children: []
  };
}

/** 构建角色树（五类型虚拟根 + 搜索） */
function buildRoleTree(keyword?: string): RoleTreeNode[] {
  const kw = keyword?.trim().toLowerCase();
  const matched = kw
    ? roleFacts.filter(
        r =>
          r.roleName.toLowerCase().includes(kw) ||
          r.roleExternalId.toLowerCase().includes(kw)
      )
    : roleFacts;

  const typeOrder = ["BASIC_ROLE", "GROUP_ROLE", "ORG", "POSITION", "PERSONAL"];
  const typeLabels: Record<string, string> = {
    BASIC_ROLE: "基础角色",
    GROUP_ROLE: "组合角色",
    ORG: "组织角色",
    POSITION: "岗位角色",
    PERSONAL: "个人角色"
  };

  return typeOrder
    .map(typeCode => {
      const roles = matched.filter(r => r.roleTypeCode === typeCode);
      if (roles.length === 0) return null;
      // 虚拟根（不是真实角色，不能被选择/授权）
      const root: RoleTreeNode = {
        roleTypeCode: typeCode,
        roleExternalId: `__virtual_root_${typeCode}`,
        roleName: typeLabels[typeCode],
        domainCode: "",
        parentId: null,
        enabled: true,
        directGrantable: false,
        canView: false,
        canManage: false,
        children: roles.map(toRoleTreeNode)
      };
      return root;
    })
    .filter((n): n is RoleTreeNode => n !== null);
}

/** 校验 add-child 的 parentPermissionId 存在且 depend_on IS NULL */
function findMainPermission(id: number): PermissionFact | undefined {
  return permissionFacts.find(p => p.id === id && p.dependOn === null);
}

/** 判断是否模拟 add-child 失败（决策点 5：可控 rejection） */
function shouldSimulateChildFailure(): boolean {
  try {
    return (
      typeof localStorage !== "undefined" &&
      localStorage.getItem("__permGrant_simulateChildFailure") === "true"
    );
  } catch {
    return false;
  }
}

// ========== Mock 路由 ==========

export default defineFakeRoute([
  // 1. 角色树
  {
    method: "post",
    url: "/api/perm/abstract-role/tree",
    response: ({ body }) => {
      const keyword = typeof body?.keyword === "string" ? body.keyword : "";
      return ok({ items: buildRoleTree(keyword) });
    }
  },

  // 2. 资源类型列表
  {
    method: "post",
    url: "/api/perm/type-definition/list",
    response: () => {
      return ok({
        items: resourceTypeFacts.map(t => ({ ...t }))
      });
    }
  },

  // 3. 资源树（按资源类型）
  {
    method: "post",
    url: "/api/perm/resource-entity/tree",
    response: ({ body }) => {
      const resourceTypeCode = body?.resourceTypeCode;
      const keyword = typeof body?.keyword === "string" ? body.keyword : "";
      if (!resourceTypeCode) return fail(400, "resourceTypeCode 必填");
      return ok({ items: buildResourceTree(resourceTypeCode, keyword) });
    }
  },

  // 4. 操作列表（按资源类型）
  {
    method: "post",
    url: "/api/perm/operation-permission/list",
    response: ({ body }) => {
      const resourceTypeCode = body?.resourceTypeCode;
      if (!resourceTypeCode) return fail(400, "resourceTypeCode 必填");
      const ops = operationFacts.filter(
        o => o.resourceTypeCode === resourceTypeCode
      );
      return ok({ items: ops.map(o => ({ ...o })) });
    }
  },

  // 5. 条件候选列表
  {
    method: "post",
    url: "/api/perm/permission-condition/list",
    response: ({ body }) => {
      const keyword = typeof body?.keyword === "string" ? body.keyword : "";
      const enabledOnly = body?.enabledOnly === true;
      let list = conditionFacts;
      if (enabledOnly) list = list.filter(c => c.enabled);
      if (keyword) {
        const kw = keyword.trim().toLowerCase();
        list = list.filter(
          c =>
            c.name.toLowerCase().includes(kw) ||
            c.code.toLowerCase().includes(kw)
        );
      }
      return ok({ items: list.map(c => ({ ...c })) });
    }
  },

  // 6. 已有权限 list（+ 业务域能力 + 操作者能力）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/list",
    response: ({ body }) => {
      const { domainCode, roleTypeCode, roleExternalId } = body ?? {};
      if (!domainCode || !roleTypeCode || !roleExternalId) {
        return fail(400, "domainCode/roleTypeCode/roleExternalId 必填");
      }
      const role = findRole(roleTypeCode, roleExternalId);
      if (!role) {
        return fail(404, "角色不存在");
      }
      const facts = findRolePermissions(
        domainCode,
        roleTypeCode,
        roleExternalId
      );
      const cap = domainConfig[domainCode] ?? {
        supportsChildren: false,
        childResourceTypeCodes: []
      };
      return ok({
        items: facts.map(toRolePermissionItem),
        domainCapability: cap,
        operatorCapability
      });
    }
  },

  // 7. 三段式 save（§6.4 add/update/remove 同事务）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/save",
    response: ({ body }) => {
      const { domainCode, roleTypeCode, roleExternalId, add, update, remove } =
        body ?? {};
      if (!domainCode || !roleTypeCode || !roleExternalId) {
        return fail(400, "domainCode/roleTypeCode/roleExternalId 必填");
      }
      const role = findRole(roleTypeCode, roleExternalId);
      if (!role) return fail(404, "角色不存在");
      if (!role.directGrantable || !role.enabled) {
        return fail(403, "角色不可直接配置权限或已禁用");
      }

      const affected: PermissionFact[] = [];

      // remove（级联删除子权限）
      if (Array.isArray(remove)) {
        for (const id of remove) {
          // 先删子权限
          permissionFacts = permissionFacts.filter(
            p => p.dependOn !== id && p.id !== id
          );
        }
      }

      // update
      if (Array.isArray(update)) {
        for (const u of update) {
          const idx = permissionFacts.findIndex(p => p.id === u.id);
          if (idx >= 0) {
            if (u.conditionCode !== undefined)
              permissionFacts[idx].conditionCode = u.conditionCode;
            if (u.canGrant !== undefined)
              permissionFacts[idx].canGrant = u.canGrant;
            affected.push(permissionFacts[idx]);
          }
        }
      }

      // add
      if (Array.isArray(add)) {
        for (const a of add) {
          const fact: PermissionFact = {
            id: nextPermissionId++,
            domainCode,
            roleTypeCode,
            roleExternalId,
            resourceTypeCode: a.resourceTypeCode,
            resourceCode: a.resourceCode ?? null,
            codeType: a.codeType ?? null,
            resourceName: resolveResourceName(
              a.resourceTypeCode,
              a.resourceCode
            ),
            operationCode: a.operationCode,
            scopeMode: a.scopeMode,
            conditionCode: a.conditionCode ?? null,
            canGrant: a.canGrant ?? false,
            dependOn: null,
            grantSource: "MANUAL"
          };
          permissionFacts.push(fact);
          affected.push(fact);
        }
      }

      return ok({ items: affected.map(toRolePermissionItem) });
    }
  },

  // 8. 子权限查询（§6.5 children）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/children",
    response: ({ body }) => {
      const { permissionId } = body ?? {};
      if (!permissionId) return fail(400, "permissionId 必填");
      const children = permissionFacts.filter(p => p.dependOn === permissionId);
      return ok({ items: children.map(toRolePermissionItem) });
    }
  },

  // 9. 添加子权限（§6.5 add-child，含可控失败模拟）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/add-child",
    response: ({ body }) => {
      const { parentPermissionId, children } = body ?? {};
      if (!parentPermissionId || !Array.isArray(children)) {
        return fail(400, "parentPermissionId/children 必填");
      }
      // parentPermissionId 必须存在且 depend_on IS NULL
      const parent = findMainPermission(parentPermissionId);
      if (!parent) {
        return fail(400, "主权限不存在或不是主权限（depend_on 非空）");
      }

      // 决策点 5：可控失败模拟
      if (shouldSimulateChildFailure()) {
        return fail(
          500,
          "模拟子权限保存失败（测试部分失败分支）：localStorage __permGrant_simulateChildFailure=true"
        );
      }

      const added: PermissionFact[] = [];
      for (const c of children) {
        const fact: PermissionFact = {
          id: nextPermissionId++,
          domainCode: parent.domainCode,
          roleTypeCode: parent.roleTypeCode,
          roleExternalId: parent.roleExternalId,
          resourceTypeCode: c.resourceTypeCode,
          resourceCode: c.resourceCode ?? null,
          codeType: c.codeType ?? null,
          resourceName: resolveResourceName(c.resourceTypeCode, c.resourceCode),
          operationCode: c.operationCode,
          scopeMode: c.scopeMode,
          conditionCode: c.conditionCode ?? null,
          canGrant: c.canGrant ?? false,
          dependOn: parentPermissionId,
          grantSource: "MANUAL"
        };
        permissionFacts.push(fact);
        added.push(fact);
      }
      return ok({ items: added.map(toRolePermissionItem) });
    }
  },

  // 10. 删除子权限（§6.5 remove-child）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/remove-child",
    response: ({ body }) => {
      const { permissionId } = body ?? {};
      if (!permissionId) return fail(400, "permissionId 必填");
      permissionFacts = permissionFacts.filter(p => p.id !== permissionId);
      return ok({ success: true });
    }
  }
]);

/** 根据资源类型 + code 解析资源名称（add-child/add 时补全 resourceName） */
function resolveResourceName(
  resourceTypeCode: string,
  resourceCode: string | null | undefined
): string | null {
  if (!resourceCode) return null;
  const r = resourceFacts.find(
    f =>
      f.resourceTypeCode === resourceTypeCode && f.resourceCode === resourceCode
  );
  return r?.resourceName ?? null;
}
