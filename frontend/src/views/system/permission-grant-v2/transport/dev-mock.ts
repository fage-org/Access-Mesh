/**
 * V2 transport dev 内存实现（T-FE-029 骨架 + T-FE-033 内存持久化）。
 *
 * 隔离机制：不经全局 http client，绕开 vite-plugin-fake-server 对
 * /api/perm/role-resource-permission/* 的路由接管；不注册任何 fake route
 * 到全局 mock 路由表。
 *
 * T-FE-033 范围：save/add-child/remove-child/getChildPermissions 最小可用内存持久化，
 * 支持 happy-path 多条件分支 + 子权限两步保存。条件 wire 对齐后端 update 语义
 * （null=不更新 / ""=清空 / 非空=绑定）。T-FE-035 保留失败注入、复杂边界 fixture、降级回归。
 *
 * P2 修复：store 按 domainCode+roleTypeCode+roleExternalId 分区，避免跨角色串数据；
 * add-child/remove-child/getChildPermissions 按 permissionId 反查所属 store。
 *
 * 类型契约复用 @/api/permission-grant（import type），不引入运行时 src/api 依赖。
 */
import type {
  RolePermissionListReq,
  RolePermissionListResp,
  RolePermissionItem,
  RolePermissionSaveReq,
  RolePermissionSaveResp,
  ChildPermissionQueryReq,
  ChildPermissionQueryResp,
  AddChildReq,
  AddChildResp,
  RemoveChildReq,
  RemoveChildResp,
  OperatorCapability,
  DomainCapability
} from "@/api/permission-grant";
import type { V2GrantTransport } from "./types";

// 基础 fixture：主权限 MENU VIEW + 子权限 BUTTON VIEW（验证父子挂载 + 子矩阵展开）
const INITIAL_ITEMS: RolePermissionItem[] = [
  {
    id: 9001,
    domainCode: "",
    resourceTypeCode: "MENU",
    resourceCode: "system",
    codeType: "MENU",
    resourceName: "系统管理",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  },
  {
    id: 9101,
    domainCode: "",
    resourceTypeCode: "BUTTON",
    resourceCode: "btn-save",
    codeType: "BUTTON",
    resourceName: "保存按钮",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: 9001,
    grantSource: "MANUAL"
  }
];

const DEFAULT_DOMAIN_CAPABILITY: DomainCapability = {
  supportsChildren: true,
  childResourceTypeCodes: ["BUTTON"]
};

const DEFAULT_OPERATOR_CAPABILITY: OperatorCapability = {
  canManage: true,
  grantableResourceTypeCodes: ["MENU", "BUTTON", "API", "DATA"],
  grantableOperationCodes: ["VIEW", "CREATE", "UPDATE", "DELETE", "MANAGE"]
};

// 内存持久化 store（按角色分区，P2 修复；T-FE-035 可重置/注入失败）
const stores = new Map<string, RolePermissionItem[]>();
let nextId = 10000;

function storeKey(dc: string, rtc: string, rei: string): string {
  return `${dc}|${rtc}|${rei}`;
}
/** 为新角色 store 初始化 fixture（全局唯一 ID + dependOn 重写，P2 修复：避免跨角色 permissionId 冲突） */
function createInitialItemsForContext(dc: string): RolePermissionItem[] {
  const idMap = new Map<number, number>();
  const items: RolePermissionItem[] = [];
  for (const it of INITIAL_ITEMS) {
    if (it.dependOn === null) {
      const newId = genId();
      idMap.set(it.id, newId);
      items.push({ ...it, id: newId, domainCode: dc });
    }
  }
  for (const it of INITIAL_ITEMS) {
    if (it.dependOn !== null) {
      const newDependOn = idMap.get(it.dependOn);
      if (newDependOn === undefined) continue;
      items.push({
        ...it,
        id: genId(),
        domainCode: dc,
        dependOn: newDependOn
      });
    }
  }
  return items;
}
function getStoreByContext(
  dc: string,
  rtc: string,
  rei: string
): RolePermissionItem[] {
  const key = storeKey(dc, rtc, rei);
  if (!stores.has(key)) stores.set(key, createInitialItemsForContext(dc));
  return stores.get(key)!;
}
/** 按 permissionId 反查所属角色 store（add-child/remove-child/getChildPermissions 用） */
function findStoreByPermissionId(
  permissionId: number
): RolePermissionItem[] | null {
  for (const items of stores.values()) {
    if (items.some(it => it.id === permissionId)) return items;
  }
  return null;
}

function genId(): number {
  return nextId++;
}

/** 规范化 conditionCode（null/undefined/"" 视为无条件） */
function normCond(code: string | null | undefined): string | null {
  if (code === null || code === undefined || code === "") return null;
  return code;
}

/** 从 add item 构造 RolePermissionItem（补 id/dependOn/grantSource） */
function itemFromAdd(
  a: {
    resourceTypeCode: string;
    resourceCode?: string;
    codeType?: string;
    operationCode: string;
    scopeMode: RolePermissionItem["scopeMode"];
    conditionCode?: string | null;
    canGrant?: boolean;
  },
  id: number,
  domainCode: string,
  dependOn: number | null
): RolePermissionItem {
  return {
    id,
    domainCode,
    resourceTypeCode: a.resourceTypeCode,
    resourceCode: a.resourceCode ?? null,
    codeType: a.codeType ?? null,
    resourceName: null,
    operationCode: a.operationCode,
    scopeMode: a.scopeMode,
    conditionCode: normCond(a.conditionCode),
    canGrant: a.canGrant ?? false,
    dependOn,
    grantSource: "MANUAL"
  };
}

export function createDevMockTransport(): V2GrantTransport {
  return {
    async getRolePermissionList(
      data: RolePermissionListReq
    ): Promise<RolePermissionListResp> {
      const items = getStoreByContext(
        data.domainCode,
        data.roleTypeCode,
        data.roleExternalId
      ).map(it => ({ ...it, domainCode: data.domainCode }));
      return {
        items,
        domainCapability: DEFAULT_DOMAIN_CAPABILITY,
        operatorCapability: DEFAULT_OPERATOR_CAPABILITY
      };
    },
    async saveRolePermission(
      data: RolePermissionSaveReq
    ): Promise<RolePermissionSaveResp> {
      const store = getStoreByContext(
        data.domainCode,
        data.roleTypeCode,
        data.roleExternalId
      );
      const added: RolePermissionItem[] = [];
      // add
      for (const a of data.add) {
        const id = genId();
        const item = itemFromAdd(a, id, data.domainCode, null);
        store.push(item);
        added.push({ ...item });
      }
      // update（wire：null=不更新 / ""=清空 / 非空=绑定）
      for (const u of data.update) {
        const cur = store.find(it => it.id === u.id);
        if (!cur) continue;
        if (u.conditionCode !== undefined && u.conditionCode !== null) {
          cur.conditionCode = u.conditionCode === "" ? null : u.conditionCode;
        }
        if (u.canGrant !== undefined) cur.canGrant = u.canGrant;
      }
      // remove（级联删子权限，对齐后端）
      for (const id of data.remove) {
        const idx = store.findIndex(it => it.id === id);
        if (idx >= 0) store.splice(idx, 1);
        for (let i = store.length - 1; i >= 0; i--) {
          if (store[i].dependOn === id) store.splice(i, 1);
        }
      }
      return { items: added };
    },
    async getChildPermissions(
      data: ChildPermissionQueryReq
    ): Promise<ChildPermissionQueryResp> {
      const store = findStoreByPermissionId(data.permissionId);
      if (!store) return { items: [] };
      const items = store
        .filter(it => it.dependOn === data.permissionId)
        .map(it => ({ ...it, domainCode: data.domainCode ?? it.domainCode }));
      return { items };
    },
    async addChildPermission(data: AddChildReq): Promise<AddChildResp> {
      const store = findStoreByPermissionId(data.parentPermissionId);
      if (!store) {
        throw new Error(
          `dev-mock addChildPermission: parentPermissionId ${data.parentPermissionId} 不存在`
        );
      }
      const parent = store.find(it => it.id === data.parentPermissionId);
      const added: RolePermissionItem[] = [];
      for (const c of data.children) {
        const id = genId();
        const item = itemFromAdd(
          c,
          id,
          parent?.domainCode ?? "",
          data.parentPermissionId
        );
        store.push(item);
        added.push({ ...item });
      }
      return { items: added };
    },
    async removeChildPermission(
      data: RemoveChildReq
    ): Promise<RemoveChildResp> {
      const store = findStoreByPermissionId(data.permissionId);
      if (!store) return { success: false };
      const idx = store.findIndex(it => it.id === data.permissionId);
      if (idx >= 0) store.splice(idx, 1);
      return { success: true };
    }
  };
}
