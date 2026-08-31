// 资源与操作定义 Mock（T-FE-008）。
// 对齐 access-service 权限域的 resource-entity / operation-permission 管理接口；
// 树形资源 CRUD + 移动，操作权限按资源类型维度 CRUD。
//
// T-PERM-028 收口：
// - detail/update/move/remove 切业务键定位（resource: resourceTypeCode+code+codeType[缺省 default]；
//   operation: resourceTypeCode[可空=全局]+code），不再接受内部 id；
// - binaryBit/inheritMask 线格式为十进制字符串（63 位 bigint）；
// - update 支持 extraClear 显式清空 extra。
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
  /** 十进制字符串（63 位 bigint 线格式，T-PERM-028） */
  binaryBit: string;
  inheritMask: string;
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
  DATA: "数据",
  // REPORT（T-FE-036 权限授予页 S7 场景：>500 节点大树；additive 新增类型，不影响既有数据）
  REPORT: "报表"
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
  },
  // REPORT 大树（T-FE-036 权限授予页 S7 验收：>500 节点矩阵虚拟滚动；
  // 单根折叠展示，3.1 资源操作页同样可见——additive，不影响既有节点）
  ...generateReportResources()
];

/**
 * 生成 REPORT 类型大树：1 根 + 24 分组 × 25 子节点 = 625 节点（id 10001+，避免与既有 id 冲突）。
 * 资源继承演示：对根/分组授权可覆盖全部子孙。
 */
function generateReportResources(): InternalResource[] {
  const result: InternalResource[] = [];
  result.push({
    id: 10000,
    tenantId: 1,
    parentId: null,
    resourceTypeCode: "REPORT",
    resourceTypeName: "报表",
    code: "report-root",
    codeType: "default",
    name: "报表中心",
    path: null,
    status: 1,
    sortOrder: 10,
    extra: null,
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  });
  let id = 10001;
  for (let g = 1; g <= 24; g++) {
    const groupId = id++;
    result.push({
      id: groupId,
      tenantId: 1,
      parentId: 10000,
      resourceTypeCode: "REPORT",
      resourceTypeName: "报表",
      code: `report-g${String(g).padStart(2, "0")}`,
      codeType: "default",
      name: `报表分组 ${g}`,
      path: null,
      status: 1,
      sortOrder: g * 10,
      extra: null,
      createdAt: BASE_TIME,
      updatedAt: BASE_TIME,
      deleted: false
    });
    for (let i = 1; i <= 25; i++) {
      result.push({
        id: id++,
        tenantId: 1,
        parentId: groupId,
        resourceTypeCode: "REPORT",
        resourceTypeName: "报表",
        code: `report-g${String(g).padStart(2, "0")}-r${String(i).padStart(2, "0")}`,
        codeType: "default",
        name: `报表 ${g}-${i}`,
        path: null,
        status: 1,
        sortOrder: i * 10,
        extra: null,
        createdAt: BASE_TIME,
        updatedAt: BASE_TIME,
        deleted: false
      });
    }
  }
  return result;
}

// 操作权限种子：每个资源类型预置 CRUD 四操作（schema 注释 CREATE(1,0) VIEW(2,0) UPDATE(4,2) DELETE(8,2)）
const CRUD_OPS = [
  { code: "CREATE", name: "创建", binaryBit: "1", inheritMask: "0" },
  { code: "VIEW", name: "查看", binaryBit: "2", inheritMask: "0" },
  { code: "UPDATE", name: "更新", binaryBit: "4", inheritMask: "2" },
  { code: "DELETE", name: "删除", binaryBit: "8", inheritMask: "2" }
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
/** T-PERM-028：为新建 resource_type 联动预置 CRUD 四操作位（type-definition/create 调用，
 * 对齐后端 TypeDefinitionAppServiceImpl 同事务预置语义；DDL CROSS JOIN 预置组模板同款） */
export function presetOperationsForType(typeCode: string): void {
  for (const op of CRUD_OPS) {
    operations.push({
      id: nextOperationId++,
      tenantId: 1,
      resourceTypeCode: typeCode,
      resourceTypeName: null,
      code: op.code,
      name: op.name,
      binaryBit: op.binaryBit,
      inheritMask: op.inheritMask,
      createdAt: now(),
      updatedAt: now()
    });
  }
}

// ========== 工具函数 ==========

function cloneResource(r: InternalResource): ResourceResp {
  const { deleted: _deleted, ...resp } = r;
  return { ...resp };
}

/** 业务键定位资源（codeType 缺省归一 default，对齐后端 ResourceKeyReq.normalizedCodeType） */
function findResourceByKey(key: {
  resourceTypeCode?: string | null;
  code?: string | null;
  codeType?: string | null;
}): InternalResource | undefined {
  const codeType = (key.codeType ?? "").trim() || "default";
  return resources.find(
    r =>
      !r.deleted &&
      r.resourceTypeCode === key.resourceTypeCode &&
      r.code === key.code &&
      r.codeType === codeType
  );
}

/** 业务键定位操作（resourceTypeCode null/缺省 = 全局操作） */
function findOperationByKey(key: {
  resourceTypeCode?: string | null;
  code?: string | null;
}): OperationPermissionResp | undefined {
  // 全局操作概念已退役：resourceTypeCode 必填，仅按类型+码定位
  if (key.resourceTypeCode == null || key.resourceTypeCode.trim() === "") {
    return undefined;
  }
  return operations.find(
    op => op.code === key.code && op.resourceTypeCode === key.resourceTypeCode
  );
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
function isPowerOfTwo(n: string | number): boolean {
  try {
    const bn = BigInt(n);
    return bn > 0n && (bn & (bn - 1n)) === 0n;
  } catch {
    return false;
  }
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

/** 校验同资源类型下 binaryBit 唯一（uk_operation_permission_typed_bit；十进制字符串 BigInt 比较） */
function isDuplicateBit(
  typeCode: string | null,
  bit: string,
  excludeId?: number
): boolean {
  const target = BigInt(bit);
  return operations.some(
    op =>
      op.id !== excludeId &&
      op.resourceTypeCode === typeCode &&
      BigInt(op.binaryBit) === target
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
      const r = findResourceByKey(body ?? {});
      return r ? ok(cloneResource(r)) : error(20004, "资源不存在");
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
      const { name, path, status, sortOrder, extra, extraClear } = body || {};
      const r = findResourceByKey(body ?? {});
      if (!r) return error(20004, "资源不存在");
      if (name != null) r.name = name;
      if (path != null) r.path = path;
      if (status != null) r.status = status;
      if (sortOrder != null) r.sortOrder = sortOrder;
      // extraClear 显式清空（优先于 extra；JSON null 无法区分「未传」与「清空」）
      if (extraClear === true) r.extra = null;
      else if (extra != null) r.extra = extra;
      r.updatedAt = now();
      return ok(cloneResource(r));
    }
  },

  {
    url: "/api/perm/resource-entity/move",
    method: "post",
    response: ({ body }) => {
      const { resource, parent } = body || {};
      const r = findResourceByKey(resource ?? {});
      if (!r) return error(20004, "资源不存在");
      // 不允许移动到自身或自身子孙下（防环）
      const descendantIds = new Set(collectDescendantIds(r.id));
      if (parent != null) {
        const target = findResourceByKey(parent);
        if (!target) return error(20004, "目标父资源不存在");
        if (descendantIds.has(target.id) || target.id === r.id) {
          return error(20053, "目标父资源不能是自身或其子孙节点");
        }
        if (target.resourceTypeCode !== r.resourceTypeCode) {
          return error(20053, "不可跨资源类型移动");
        }
        r.parentId = target.id;
      } else {
        r.parentId = null;
      }
      r.updatedAt = now();
      return ok(null);
    }
  },

  {
    url: "/api/perm/resource-entity/remove",
    method: "post",
    response: ({ body }) => {
      const items: Array<{
        resourceTypeCode: string;
        code: string;
        codeType?: string | null;
      }> = Array.isArray(body?.items) ? body.items : [];
      if (items.length === 0) return error(400, "items 不能为空");
      // 业务键解析（未命中的键静默跳过，对齐后端语义）
      const targets = items
        .map(item => findResourceByKey(item))
        .filter((r): r is InternalResource => r != null);
      // 级联软删子孙
      const toDelete = new Set<number>();
      for (const r of targets) {
        for (const did of collectDescendantIds(r.id)) toDelete.add(did);
      }
      for (const r of resources) {
        if (toDelete.has(r.id) && !r.deleted) {
          r.deleted = true;
          r.updatedAt = now();
        }
      }
      return ok(null);
    }
  },

  // ========== 操作权限 ==========

  {
    url: "/api/perm/operation-permission/list",
    method: "post",
    response: ({ body }) => {
      const { resourceTypeCode } = body || {};
      // 全局操作概念已退役：有类型过滤返回该类型定义；无类型返回全量定义。
      // 口径差异登记（T-PERM-040）：空白串（如 " "）后端 isBlank 跳过过滤返回全量、
      // mock 按 truthy 当过滤值返回空列表；排序后端 ORDER BY id（插入序）、mock 按
      // binaryBit 升序——契约无排序承诺，类型下拉来自类型定义页，联调以真实后端为准
      const items = operations
        .filter(op =>
          resourceTypeCode ? op.resourceTypeCode === resourceTypeCode : true
        )
        .slice()
        .sort((a, b) => {
          const x = BigInt(a.binaryBit);
          const y = BigInt(b.binaryBit);
          return x < y ? -1 : x > y ? 1 : 0;
        });
      return ok({ items });
    }
  },

  {
    url: "/api/perm/operation-permission/detail",
    method: "post",
    response: ({ body }) => {
      const op = findOperationByKey(body ?? {});
      return op ? ok({ ...op }) : error(20005, "操作权限不存在");
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
      // 位字段为十进制字符串线格式（63 位 bigint），BigInt 解析校验
      const bit = String(binaryBit);
      if (!/^-?\d+$/.test(bit) || BigInt(bit) <= 0n) {
        return error(400, "二进制位必须为正整数（2 的幂次）");
      }
      if (!isPowerOfTwo(bit)) {
        return error(400, "二进制位必须为 2 的幂次（1/2/4/8/16…）");
      }
      if (isDuplicateOperation(resourceTypeCode, code)) {
        return error(409, "同资源类型下该操作编码已存在");
      }
      if (isDuplicateBit(resourceTypeCode, bit)) {
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
        binaryBit: bit,
        inheritMask: String(inheritMask ?? 0),
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
      const { name, binaryBit, inheritMask } = body || {};
      const op = findOperationByKey(body ?? {});
      if (!op) return error(20005, "操作权限不存在");
      if (binaryBit != null) {
        const bit = String(binaryBit);
        if (!/^-?\d+$/.test(bit) || BigInt(bit) <= 0n) {
          return error(400, "二进制位必须为正整数（2 的幂次）");
        }
        if (!isPowerOfTwo(bit)) {
          return error(400, "二进制位必须为 2 的幂次（1/2/4/8/16…）");
        }
        if (isDuplicateBit(op.resourceTypeCode, bit, op.id)) {
          return error(409, "同资源类型下该二进制位已被占用");
        }
        op.binaryBit = bit;
      }
      if (name != null) op.name = name;
      if (inheritMask != null) op.inheritMask = String(inheritMask);
      op.updatedAt = now();
      return ok({ ...op });
    }
  },

  {
    url: "/api/perm/operation-permission/remove",
    method: "post",
    response: ({ body }) => {
      const items: Array<{
        resourceTypeCode?: string | null;
        code: string;
      }> = Array.isArray(body?.items) ? body.items : [];
      if (items.length === 0) return error(400, "items 不能为空");
      for (let i = operations.length - 1; i >= 0; i -= 1) {
        const hit = items.some(
          item =>
            item.code === operations[i].code &&
            item.resourceTypeCode === operations[i].resourceTypeCode
        );
        if (hit) {
          operations.splice(i, 1);
        }
      }
      return ok(null);
    }
  }
]);
