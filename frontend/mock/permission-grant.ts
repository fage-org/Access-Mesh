// 权限授予 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回 PermResult 信封：{ code, message, data }
// 零 src 依赖：类型本地声明，避免 fake-server 经 bundle-import 打包 src/api 链
//
// 契约依据：docs/design/permission-center/api-contract.md §5.5 / §6.4 / §6.5
// 单一事实源：PermissionFact 统一权限事实表，派生 list/save/children/add-child/remove-child
//
// ⚠️ 不重复注册共享端点（P1-1 修复）：
// - abstract-role/tree -> mock/role-manage.ts（items[0].root.children，字段 name/externalId/status）
// - type-definition/list -> mock/type-def.ts（TypeDefResp，typeKey=resource_type）
// - resource-entity/tree -> mock/resource-operation.ts（items[].root，字段 code/name）
// - operation-permission/list -> mock/resource-operation.ts（字段 code/name）
// - permission-condition/list -> mock/permission-condition.ts（ConditionResp）
// API 层（api/permission-grant.ts adapt* 函数）做结构转换 + 能力推断，避免破坏现有页面
//
// 本 mock 只保留 role-resource-permission/* 5 个独有端点。
//
// 能力驱动（决策点 6 按归属拆分）：
// - 角色节点：directGrantable/canView/canManage（API adaptRoleTree 推断）
// - 资源类型：supportsInstance/All/Condition/Delegation（API adaptResourceTypes 推断）
// - 业务域：supportsChildren/childResourceTypeCodes（list 端点 domainCapability）
// - 候选权限单元：grantableByOperator/denyReason（list 端点 operatorCapability 组合判定）
//
// 全局角色 domainCode=""（P1-7 修复，契约空域=全局对象）
// 可控失败（决策点 5）：localStorage "__permGrant_simulateChildFailure"="true" 时 add-child 返回 500
import { defineFakeRoute } from "vite-plugin-fake-server/client";

type GrantScopeMode = "INSTANCE" | "ALL";

// ========== 类型（本地声明） ==========

interface RoleFact {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  /** 全局角色 domainCode="" */
  domainCode: string;
  enabled: boolean;
  directGrantable: boolean;
}
interface ResourceFact {
  resourceTypeCode: string;
  resourceCode: string;
  codeType: string;
  resourceName: string;
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

const ok = (data: unknown) => ({ code: 200, message: "success", data });
const fail = (code: number, message: string, data: unknown = null) => ({
  code,
  message,
  data
});

// ========== 角色事实（findRole 用，5 类型） ==========

const roleFacts: RoleFact[] = [
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_201",
    roleName: "基础用户",
    domainCode: "",
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    roleName: "高级用户",
    domainCode: "",
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_203",
    roleName: "访客",
    domainCode: "",
    enabled: false,
    directGrantable: true
  },
  // GROUP_ROLE：组合角色，directGrantable=false（§3.4）
  {
    roleTypeCode: "GROUP_ROLE",
    roleExternalId: "GROUP_401",
    roleName: "核心开发组",
    domainCode: "",
    enabled: true,
    directGrantable: false
  },
  {
    roleTypeCode: "GROUP_ROLE",
    roleExternalId: "GROUP_402",
    roleName: "运维保障组",
    domainCode: "",
    enabled: true,
    directGrantable: false
  },
  {
    roleTypeCode: "GROUP_ROLE",
    roleExternalId: "GROUP_403",
    roleName: "核心开发-后端",
    domainCode: "",
    enabled: true,
    directGrantable: false
  },
  {
    roleTypeCode: "ORG",
    roleExternalId: "ORG_1",
    roleName: "研发中心",
    domainCode: "",
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "POSITION",
    roleExternalId: "POSITION_30",
    roleName: "后端开发",
    domainCode: "",
    enabled: true,
    directGrantable: true
  },
  {
    roleTypeCode: "PERSONAL",
    roleExternalId: "PERSONAL_501",
    roleName: "张三-专属",
    domainCode: "",
    enabled: true,
    directGrantable: true
  }
];

// ========== 资源事实（resolveResourceName 用） ==========

const resourceFacts: ResourceFact[] = [
  // MENU（对齐共享 resource-operation.ts 资源树 code）
  {
    resourceTypeCode: "MENU",
    resourceCode: "sys-mgmt",
    codeType: "default",
    resourceName: "系统管理"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "user",
    codeType: "default",
    resourceName: "组织与用户"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "role",
    codeType: "default",
    resourceName: "角色管理"
  },
  {
    resourceTypeCode: "MENU",
    resourceCode: "res-op",
    codeType: "default",
    resourceName: "资源与操作"
  },
  // DATA（对齐共享 resource-operation.ts 资源树 code）
  {
    resourceTypeCode: "DATA",
    resourceCode: "dept-data",
    codeType: "default",
    resourceName: "部门数据"
  },
  {
    resourceTypeCode: "DATA",
    resourceCode: "role-data",
    codeType: "default",
    resourceName: "角色数据"
  }
];

// ========== 业务域能力配置（全局域 domainCode=""） ==========

const domainConfig: Record<
  string,
  { supportsChildren: boolean; childResourceTypeCodes: string[] }
> = {
  "": { supportsChildren: true, childResourceTypeCodes: ["DATA"] }
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
  // BASIC_201: MENU/role/UPDATE 绑 office-hours 条件
  {
    id: 100,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_201",
    resourceTypeCode: "MENU",
    resourceCode: "role",
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
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_201",
    resourceTypeCode: "MENU",
    resourceCode: "user",
    codeType: "default",
    resourceName: "组织与用户",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  // BASIC_202: §6.5 示例（主权限 200 + 子权限 201/202/203 + ALL 260）
  // P2-1：迁移到共享资源类型 MENU/DATA（共享无 REPORT），操作用 VIEW
  {
    id: 200,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    resourceTypeCode: "MENU",
    resourceCode: "res-op",
    codeType: "default",
    resourceName: "资源与操作",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  {
    id: 260,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    resourceTypeCode: "MENU",
    resourceCode: null,
    codeType: null,
    resourceName: null,
    operationCode: "VIEW",
    scopeMode: "ALL",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  // 子权限（dependOn=200）
  {
    id: 201,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    resourceTypeCode: "DATA",
    resourceCode: "dept-data",
    codeType: "default",
    resourceName: "部门数据",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  },
  {
    id: 202,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    resourceTypeCode: "DATA",
    resourceCode: "role-data",
    codeType: "default",
    resourceName: "角色数据",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  },
  {
    id: 203,
    domainCode: "",
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_202",
    resourceTypeCode: "DATA",
    resourceCode: null,
    codeType: null,
    resourceName: null,
    operationCode: "VIEW",
    scopeMode: "ALL",
    conditionCode: null,
    canGrant: false,
    dependOn: 200,
    grantSource: "MANUAL"
  }
];

let nextPermissionId = 1000;

// ========== 工具函数 ==========

function findRole(
  roleTypeCode: string,
  roleExternalId: string
): RoleFact | undefined {
  return roleFacts.find(
    r => r.roleTypeCode === roleTypeCode && r.roleExternalId === roleExternalId
  );
}

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

function findMainPermission(id: number): PermissionFact | undefined {
  return permissionFacts.find(p => p.id === id && p.dependOn === null);
}

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

// ========== Mock 路由（仅 role-resource-permission/* 5 个独有端点） ==========

export default defineFakeRoute([
  // 1. 已有权限 list（+ 业务域能力 + 操作者能力）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/list",
    response: ({ body }) => {
      const { domainCode, roleTypeCode, roleExternalId } = body ?? {};
      if (!roleTypeCode || !roleExternalId) {
        return fail(400, "roleTypeCode/roleExternalId 必填");
      }
      const role = findRole(roleTypeCode, roleExternalId);
      if (!role) {
        return fail(404, "角色不存在");
      }
      // domainCode 为空字符串表示全局（P1-7），用 "" 查询
      const dc = typeof domainCode === "string" ? domainCode : "";
      const facts = findRolePermissions(dc, roleTypeCode, roleExternalId);
      const cap = domainConfig[dc] ?? {
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

  // 2. 三段式 save（§6.4 add/update/remove 同事务）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/save",
    response: ({ body }) => {
      const { domainCode, roleTypeCode, roleExternalId, add, update, remove } =
        body ?? {};
      if (!roleTypeCode || !roleExternalId) {
        return fail(400, "roleTypeCode/roleExternalId 必填");
      }
      const role = findRole(roleTypeCode, roleExternalId);
      if (!role) return fail(404, "角色不存在");
      if (!role.directGrantable || !role.enabled) {
        return fail(403, "角色不可直接配置权限或已禁用");
      }

      const addArr = Array.isArray(add) ? add : [];
      const updateArr = Array.isArray(update) ? update : [];
      const removeArr = Array.isArray(remove) ? remove : [];

      // P1-2 修复：GRANT_REQUEST_EMPTY 兜底校验（hook 应已跳过空 save）
      if (
        addArr.length === 0 &&
        updateArr.length === 0 &&
        removeArr.length === 0
      ) {
        return fail(400, "GRANT_REQUEST_EMPTY: add/update/remove 不能同时为空");
      }

      const dc = typeof domainCode === "string" ? domainCode : "";
      const affected: PermissionFact[] = [];

      // remove（级联删除子权限）
      for (const id of removeArr) {
        permissionFacts = permissionFacts.filter(
          p => p.dependOn !== id && p.id !== id
        );
      }

      // update
      for (const u of updateArr) {
        const idx = permissionFacts.findIndex(p => p.id === u.id);
        if (idx >= 0) {
          if (u.conditionCode !== undefined)
            permissionFacts[idx].conditionCode = u.conditionCode;
          if (u.canGrant !== undefined)
            permissionFacts[idx].canGrant = u.canGrant;
          affected.push(permissionFacts[idx]);
        }
      }

      // add
      for (const a of addArr) {
        const fact: PermissionFact = {
          id: nextPermissionId++,
          domainCode: dc,
          roleTypeCode,
          roleExternalId,
          resourceTypeCode: a.resourceTypeCode,
          resourceCode: a.resourceCode ?? null,
          codeType: a.codeType ?? null,
          resourceName: resolveResourceName(a.resourceTypeCode, a.resourceCode),
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

      return ok({ items: affected.map(toRolePermissionItem) });
    }
  },

  // 3. 子权限查询（§6.5 children）
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

  // 4. 添加子权限（§6.5 add-child，含可控失败模拟）
  {
    method: "post",
    url: "/api/perm/role-resource-permission/add-child",
    response: ({ body }) => {
      const { parentPermissionId, children } = body ?? {};
      if (!parentPermissionId || !Array.isArray(children)) {
        return fail(400, "parentPermissionId/children 必填");
      }
      const parent = findMainPermission(parentPermissionId);
      if (!parent) {
        return fail(400, "主权限不存在或不是主权限（depend_on 非空）");
      }

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

  // 5. 删除子权限（§6.5 remove-child）
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
