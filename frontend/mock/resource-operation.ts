// 资源与操作定义 Mock（T-FE-008）。
// 对齐 permission-center 的 resource-entity / operation-permission 管理接口；
// 树形资源 CRUD + 移动，操作权限按资源类型维度 CRUD。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
import { defineFakeRoute } from "vite-plugin-fake-server/client";

// ========== 本地类型（对齐后端 DTO，api-contract.md §5.3） ==========

type ResourceResp = {
  id: number;
  tenantId: number;
  parentId: number | null;
  resourceTypeCode: string;
  resourceTypeName: string | null;
  code: string;
  codeType: string;
  name: string;
  path: string | null;
  status: number;
  sortOrder: number;
  extra: string | null;
  createdAt: string;
  updatedAt: string;
};

type ResourceTreeNode = {
  id: number;
  parentId: number | null;
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
  path: string | null;
  status: number;
  sortOrder: number;
  children: ResourceTreeNode[];
};

export type OperationPermissionResp = {
  id: number;
  tenantId: number;
  resourceTypeCode: string | null;
  resourceTypeName: string | null;
  code: string;
  name: string;
  binaryBit: number;
  inheritMask: number;
  createdAt: string;
  updatedAt: string;
};

export type InternalResource = ResourceResp & { deleted: boolean };

// ========== 常量与种子 ==========

const now = () => new Date().toISOString().slice(0, 19).replace("T", " ");
const BASE_TIME = "2026-06-18 09:30:00";
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

const RESOURCE_TYPE_LABEL: Record<string, string> = {
  MENU: "菜单",
  BUTTON: "按钮",
  API: "接口",
  DATA: "数据"
};

let nextResourceId = 301;
let nextOperationId = 501;

export const resources: InternalResource[] = [
  // MENU
  {
    id: 201,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "MENU",
    resourceTypeName: "菜单",
    code: "sys-mgmt",
    codeType: "default",
    name: "系统管理",
    path: "/system",
    status: 1,
    sortOrder: 10,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 202,
    tenantId: 1,
    parentId: 201,
    resourceTypeCode: "MENU",
    resourceTypeName: "菜单",
    code: "user",
    codeType: "default",
    name: "组织与用户",
    path: "/system/user",
    status: 1,
    sortOrder: 10,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 203,
    tenantId: 1,
    parentId: 201,
    resourceTypeCode: "MENU",
    resourceTypeName: "菜单",
    code: "role",
    codeType: "default",
    name: "角色管理",
    path: "/system/role",
    status: 1,
    sortOrder: 20,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 204,
    tenantId: 1,
    parentId: 201,
    resourceTypeCode: "MENU",
    resourceTypeName: "菜单",
    code: "res-op",
    codeType: "default",
    name: "资源与操作",
    path: "/system/resource-operation",
    status: 1,
    sortOrder: 60,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  // BUTTON
  {
    id: 211,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "BUTTON",
    resourceTypeName: "按钮",
    code: "btn-add",
    codeType: "default",
    name: "新增按钮",
    path: null,
    status: 1,
    sortOrder: 10,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 212,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "BUTTON",
    resourceTypeName: "按钮",
    code: "btn-edit",
    codeType: "default",
    name: "编辑按钮",
    path: null,
    status: 0,
    sortOrder: 20,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: "2026-07-01 10:00:00",
    deleted: false
  },
  // API
  {
    id: 221,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "API",
    resourceTypeName: "接口",
    code: "auth-check",
    codeType: "default",
    name: "鉴权校验",
    path: "/api/perm/auth/check",
    status: 1,
    sortOrder: 10,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 222,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "API",
    resourceTypeName: "接口",
    code: "res-tree",
    codeType: "default",
    name: "资源树查询",
    path: "/api/perm/resource-entity/tree",
    status: 1,
    sortOrder: 20,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  // DATA
  {
    id: 231,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "DATA",
    resourceTypeName: "数据",
    code: "dept-data",
    codeType: "default",
    name: "部门数据",
    path: null,
    status: 1,
    sortOrder: 10,
    extra: '{"scope":"DEPT"}',
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 232,
    tenantId: 1,
    parentId: 231,
    resourceTypeCode: "DATA",
    resourceTypeName: "数据",
    code: "role-data",
    codeType: "default",
    name: "角色数据",
    path: null,
    status: 1,
    sortOrder: 10,
    extra: '{"scope":"ROLE"}',
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  }
];

// 操作权限种子：每个资源类型预置 CRUD 四操作（schema 注释 CREATE(1,0) VIEW(2,0) UPDATE(4,2) DELETE(8,2)）
const CRUD_OPS = [
  { code: "CREATE", name: "创建", binaryBit: 1, inheritMask: 0 },
  { code: "VIEW", name: "查看", binaryBit: 2, inheritMask: 0 },
  { code: "UPDATE", name: "更新", binaryBit: 4, inheritMask: 2 },
  { code: "DELETE", name: "删除", binaryBit: 8, inheritMask: 2 }
] as const;

export const operations: OperationPermissionResp[] = [];
for (const [typeCode, typeName] of Object.entries(RESOURCE_TYPE_LABEL)) {
  for (const op of CRUD_OPS) {
    operations.push({
      id: nextOperationId++,
      tenantId: 1,
      resourceTypeCode: typeCode,
      resourceTypeName: typeName,
      code: op.code,
      name: op.name,
      binaryBit: op.binaryBit,
      inheritMask: op.inheritMask,
      createdAt: BASE_TIME,
      updatedAt: BASE_TIME
    });
  }
}
// 全局操作（resourceTypeCode=null，适用所有资源类型）
operations.push({
  id: nextOperationId++,
  tenantId: 1,
  resourceTypeCode: null,
  resourceTypeName: "全局",
  code: "MANAGE",
  name: "管理",
  binaryBit: 16,
  inheritMask: 0,
  createdAt: BASE_TIME,
  updatedAt: BASE_TIME
});

// ========== 工具函数 ==========

function cloneResource(r: InternalResource): ResourceResp {
  const { deleted: _deleted, ...resp } = r;
  return { ...resp };
}

function toTreeNode(r: InternalResource): ResourceTreeNode {
  return {
    id: r.id,
    parentId: r.parentId,
    resourceTypeCode: r.resourceTypeCode,
    code: r.code,
    codeType: r.codeType,
    name: r.name,
    path: r.path,
    status: r.status,
    sortOrder: r.sortOrder,
    children: []
  };
}

/** 从扁平资源数组构建森林（按 resourceTypeCode 过滤，排序） */
function buildForest(typeCode?: string | null): ResourceTreeNode[] {
  const filtered = resources.filter(
    r => !r.deleted && (typeCode ? r.resourceTypeCode === typeCode : true)
  );
  const byId = new Map<number, ResourceTreeNode>();
  for (const r of filtered) byId.set(r.id, toTreeNode(r));
  const roots: ResourceTreeNode[] = [];
  for (const node of byId.values()) {
    if (node.parentId != null && byId.has(node.parentId)) {
      byId.get(node.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortRecursive = (nodes: ResourceTreeNode[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
    nodes.forEach(n => sortRecursive(n.children));
  };
  sortRecursive(roots);
  return roots;
}

/** 递归收集某节点及其所有子孙 id（用于级联软删） */
function collectDescendantIds(id: number): number[] {
  const ids = [id];
  const children = resources.filter(r => !r.deleted && r.parentId === id);
  for (const child of children) ids.push(...collectDescendantIds(child.id));
  return ids;
}

/** 判断 n 是否为 2 的幂次（BigInt 实现，兼容 63 位 bigint 列）。
 *  JS `&` 强转 32 位，>2^31 误判（如 4294967297 截断为 1）。
 *  mock 禁 import src，故本地实现（与 utils/types.ts 同源）。 */
function isPowerOfTwo(n: number): boolean {
  if (!Number.isInteger(n) || n <= 0) return false;
  const bn = BigInt(n);
  return (bn & (bn - 1n)) === 0n;
}

/** 校验资源业务键唯一（tenant+resourceType+code+codeType） */
function isDuplicateResource(
  typeCode: string,
  code: string,
  codeType: string,
  excludeId?: number
): boolean {
  return resources.some(
    r =>
      !r.deleted &&
      r.id !== excludeId &&
      r.resourceTypeCode === typeCode &&
      r.code === code &&
      r.codeType === codeType
  );
}

/** 校验操作业务键唯一（tenant+resourceType+code） */
function isDuplicateOperation(
  typeCode: string | null,
  code: string,
  excludeId?: number
): boolean {
  return operations.some(
    op =>
      op.id !== excludeId &&
      op.resourceTypeCode === typeCode &&
      op.code === code
  );
}

/** 校验同资源类型下 binaryBit 唯一（uk_operation_permission_typed_bit） */
function isDuplicateBit(
  typeCode: string | null,
  bit: number,
  excludeId?: number
): boolean {
  return operations.some(
    op =>
      op.id !== excludeId &&
      op.resourceTypeCode === typeCode &&
      op.binaryBit === bit
  );
}

export default defineFakeRoute([
  // ========== 资源实体 ==========

  {
    url: "/api/perm/resource-entity/tree",
    method: "post",
    response: ({ body }) => {
      const { resourceTypeCode } = body || {};
      const forest = buildForest(resourceTypeCode ?? null);
      // 后端 ItemsResp<ResourceTreeResp>，每个顶层资源作为一个 root
      return ok({ items: forest.map(root => ({ root })) });
    }
  },

  {
    url: "/api/perm/resource-entity/list",
    method: "post",
    response: ({ body }) => {
      const { resourceTypeCode, pageNum, pageSize } = body || {};
      const all = resources
        .filter(r => !r.deleted)
        .filter(
          r => !resourceTypeCode || r.resourceTypeCode === resourceTypeCode
        )
        .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
      const total = all.length;
      const page = Math.max(1, pageNum ?? 1);
      const size = Math.max(1, pageSize ?? 15);
      const start = (page - 1) * size;
      const items = all.slice(start, start + size).map(cloneResource);
      return ok({
        items,
        total,
        pageNum: page,
        pageSize: size,
        hasNext: start + items.length < total
      });
    }
  },

  {
    url: "/api/perm/resource-entity/detail",
    method: "post",
    response: ({ body }) => {
      const r = resources.find(item => item.id === body?.id && !item.deleted);
      return r ? ok(cloneResource(r)) : error(404, "资源不存在");
    }
  },

  {
    url: "/api/perm/resource-entity/create",
    method: "post",
    response: ({ body }) => {
      const {
        parentId,
        resourceTypeCode,
        code,
        codeType,
        name,
        path,
        status,
        sortOrder,
        extra
      } = body || {};
      if (!resourceTypeCode || !code || !name) {
        return error(400, "资源类型、编码、名称不能为空");
      }
      const finalCodeType = codeType || "default";
      if (isDuplicateResource(resourceTypeCode, code, finalCodeType)) {
        return error(409, "同类型下该编码已存在");
      }
      if (parentId != null) {
        const parent = resources.find(r => r.id === parentId && !r.deleted);
        if (!parent) return error(404, "父资源不存在");
        if (parent.resourceTypeCode !== resourceTypeCode) {
          return error(400, "父资源类型不一致，不可跨类型建子");
        }
      }
      const ts = now();
      const created: InternalResource = {
        id: nextResourceId++,
        tenantId: 1,
        parentId: parentId ?? null,
        resourceTypeCode,
        resourceTypeName: RESOURCE_TYPE_LABEL[resourceTypeCode] ?? null,
        code,
        codeType: finalCodeType,
        name,
        path: path ?? null,
        status: status ?? 1,
        sortOrder: sortOrder ?? 0,
        extra: extra ?? null,
        createdAt: ts,
        updatedAt: ts,
        deleted: false
      };
      resources.push(created);
      return ok(cloneResource(created));
    }
  },

  {
    url: "/api/perm/resource-entity/update",
    method: "post",
    response: ({ body }) => {
      const { id, code, name, path, status, sortOrder, extra } = body || {};
      const r = resources.find(item => item.id === id && !item.deleted);
      if (!r) return error(404, "资源不存在");
      if (code != null && code !== r.code) {
        return error(
          400,
          "资源编码为业务键，不可修改（Phase 2 切业务键后另议）"
        );
      }
      if (name != null) r.name = name;
      if (path != null) r.path = path;
      if (status != null) r.status = status;
      if (sortOrder != null) r.sortOrder = sortOrder;
      if (extra != null) r.extra = extra;
      r.updatedAt = now();
      return ok(cloneResource(r));
    }
  },

  {
    url: "/api/perm/resource-entity/move",
    method: "post",
    response: ({ body }) => {
      const { resourceId, parentId } = body || {};
      const r = resources.find(item => item.id === resourceId && !item.deleted);
      if (!r) return error(404, "资源不存在");
      // 不允许移动到自身或自身子孙下（防环）
      const descendantIds = new Set(collectDescendantIds(resourceId));
      if (parentId != null) {
        if (descendantIds.has(parentId)) {
          return error(400, "不能移动到自身或其子孙节点下");
        }
        const parent = resources.find(
          item => item.id === parentId && !item.deleted
        );
        if (!parent) return error(404, "目标父资源不存在");
        if (parent.resourceTypeCode !== r.resourceTypeCode) {
          return error(400, "不可跨资源类型移动");
        }
      }
      r.parentId = parentId ?? null;
      r.updatedAt = now();
      return ok(null);
    }
  },

  {
    url: "/api/perm/resource-entity/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      // 级联软删子孙
      const toDelete = new Set<number>();
      for (const id of ids) {
        for (const did of collectDescendantIds(id)) toDelete.add(did);
      }
      let count = 0;
      for (const r of resources) {
        if (toDelete.has(r.id) && !r.deleted) {
          r.deleted = true;
          r.updatedAt = now();
          count += 1;
        }
      }
      return ok({ removed: count });
    }
  },

  // ========== 操作权限 ==========

  {
    url: "/api/perm/operation-permission/list",
    method: "post",
    response: ({ body }) => {
      const { resourceTypeCode } = body || {};
      const items = operations
        .filter(op =>
          resourceTypeCode ? op.resourceTypeCode === resourceTypeCode : true
        )
        .slice()
        .sort((a, b) => a.binaryBit - b.binaryBit);
      return ok({ items });
    }
  },

  {
    url: "/api/perm/operation-permission/detail",
    method: "post",
    response: ({ body }) => {
      const op = operations.find(item => item.id === body?.id);
      return op ? ok({ ...op }) : error(404, "操作权限不存在");
    }
  },

  {
    url: "/api/perm/operation-permission/create",
    method: "post",
    response: ({ body }) => {
      const { resourceTypeCode, code, name, binaryBit, inheritMask } =
        body || {};
      if (!resourceTypeCode || !code || !name || binaryBit == null) {
        return error(400, "资源类型、操作编码、名称、二进制位不能为空");
      }
      if (!Number.isInteger(binaryBit) || binaryBit <= 0) {
        return error(400, "二进制位必须为正整数（2 的幂次）");
      }
      if (!isPowerOfTwo(binaryBit)) {
        return error(400, "二进制位必须为 2 的幂次（1/2/4/8/16…）");
      }
      if (isDuplicateOperation(resourceTypeCode, code)) {
        return error(409, "同资源类型下该操作编码已存在");
      }
      if (isDuplicateBit(resourceTypeCode, binaryBit)) {
        return error(
          409,
          "同资源类型下该二进制位已被占用（uk_operation_permission_typed_bit）"
        );
      }
      const ts = now();
      const created: OperationPermissionResp = {
        id: nextOperationId++,
        tenantId: 1,
        resourceTypeCode,
        resourceTypeName: RESOURCE_TYPE_LABEL[resourceTypeCode] ?? null,
        code,
        name,
        binaryBit,
        inheritMask: inheritMask ?? 0,
        createdAt: ts,
        updatedAt: ts
      };
      operations.push(created);
      return ok({ ...created });
    }
  },

  {
    url: "/api/perm/operation-permission/update",
    method: "post",
    response: ({ body }) => {
      const { operationId, name, binaryBit, inheritMask } = body || {};
      const op = operations.find(item => item.id === operationId);
      if (!op) return error(404, "操作权限不存在");
      if (binaryBit != null) {
        if (!Number.isInteger(binaryBit) || binaryBit <= 0) {
          return error(400, "二进制位必须为正整数（2 的幂次）");
        }
        if (!isPowerOfTwo(binaryBit)) {
          return error(400, "二进制位必须为 2 的幂次（1/2/4/8/16…）");
        }
        if (isDuplicateBit(op.resourceTypeCode, binaryBit, op.id)) {
          return error(409, "同资源类型下该二进制位已被占用");
        }
        op.binaryBit = binaryBit;
      }
      if (name != null) op.name = name;
      if (inheritMask != null) op.inheritMask = inheritMask;
      op.updatedAt = now();
      return ok({ ...op });
    }
  },

  {
    url: "/api/perm/operation-permission/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      let count = 0;
      for (let i = operations.length - 1; i >= 0; i -= 1) {
        if (ids.includes(operations[i].id)) {
          operations.splice(i, 1);
          count += 1;
        }
      }
      return ok({ removed: count });
    }
  }
]);
