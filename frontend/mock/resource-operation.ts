// 资源与操作定义 Mock —— 共享数据模块（T-FE-017 联调收口）。
// 本文件原为 T-FE-008 资源/操作页 mock（11 个 fake-server 路由），T-FE-041 api 层切
// 真实路径（/perm/api/perm/**）后路由段自然失配；T-FE-017 资源/操作页联调收口时删除
// 失配路由段，仅保留仍被 resource-dependency / type-def / permission-grant 三个 mock
// 引用的数据导出（resources / operations / InternalResource / presetOperationsForType）。
// 三个依赖页面的联调任务（T-FE-018/020 等）届时随各自 mock 退役一并处置。
//
// T-PERM-028 口径保留：
// - binaryBit/inheritMask 线格式为十进制字符串（63 位 bigint）；
// - resource_type 创建联动预置 CRUD 四操作位（对齐后端 TypeDefinitionAppServiceImpl 同事务预置语义）。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。

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

export type OperationPermissionResp = {
  id: number;
  tenantId: number;
  /** 恒非空（全局操作概念已退役，对齐 api/ 契约类型 string） */
  resourceTypeCode: string;
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

const RESOURCE_TYPE_LABEL: Record<string, string> = {
  MENU: "菜单",
  BUTTON: "按钮",
  API: "接口",
  DATA: "数据",
  // REPORT（T-FE-036 权限授予页 S7 场景：>500 节点大树；additive 新增类型，不影响既有数据）
  REPORT: "报表"
};

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
