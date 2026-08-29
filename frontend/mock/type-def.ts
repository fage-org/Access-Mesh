// 类型定义 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 TypeDefinitionResp
// 契约依据：docs/design/permission-center/api-contract.md §5.1
// 表结构：docs/design/schema/access-service.sql
import { defineFakeRoute } from "vite-plugin-fake-server/client";
// T-PERM-028：resource_type 创建联动预置 CRUD 操作位（对齐后端 TypeDefinitionAppServiceImpl
// 同事务预置语义）；mock→mock 导入无 src/api 链风险
import { presetOperationsForType } from "./resource-operation";

/**
 * 本地声明 type_key 常量与类型（不 import src/api/type-def，避免 fake-server 经
 * bundle-import 打包 src/api 链——该链 import 了 @/utils/http 等浏览器侧依赖，
 * 在 node platform 下打包会失败，导致整个 mock 文件加载被静默吞掉 → 路由不注册 → 404）。
 * 与 role-manage.ts mock 零 src 依赖范式一致；字段定义同步注释于下方，保持与 api 层对齐。
 */
const TYPE_KEY = {
  USER_TYPE: "user_type",
  ROLE_TYPE: "role_type",
  RESOURCE_TYPE: "resource_type",
  GROUP_TYPE: "group_type"
} as const;
type TypeKey = (typeof TYPE_KEY)[keyof typeof TYPE_KEY];

/** 类型定义响应（对齐 src/api/type-def.ts 的 TypeDefResp / 后端 TypeDefinitionResp）。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中——
 *  clone 后列表/detail 路由已过滤 deleted 行。 */
type TypeDefResp = {
  id: number;
  tenantId?: number;
  typeKey: string;
  typeCode: string;
  typeValue: number;
  name: string;
  description: string | null;
  isSystem: boolean;
  sortOrder: number;
  extra: string | null;
  createdAt?: string;
  deleted?: boolean;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

// ========== Mock 数据：4 个 type_key 的类型定义字典 ==========

/**
 * type_definition 内存数据（扁平数组，按 typeKey 分组）。
 *
 * 系统预置项（isSystem=true）：租户初始化自动写入，不可删改（schema:47 注释）。
 * - user_type：USER(1)/SERVICE(2)
 * - role_type：ORG(1)/POSITION(2)/PERSONAL(3)/GROUP_ROLE(5)/BASIC_ROLE(6)
 * - resource_type：MENU/BUTTON/API/DATA（基础资源类型）
 * - group_type：DEPT(1)（示例）
 *
 * 租户自定义项（isSystem=false）：可 CRUD，用于验证页面编辑/删除交互。
 */
const mockTypeDefs: TypeDefResp[] = [
  // user_type
  {
    id: 1,
    tenantId: 1,
    typeKey: TYPE_KEY.USER_TYPE,
    typeCode: "USER",
    typeValue: 1,
    name: "人员",
    description: "自然人用户",
    isSystem: true,
    sortOrder: 1,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 2,
    tenantId: 1,
    typeKey: TYPE_KEY.USER_TYPE,
    typeCode: "SERVICE",
    typeValue: 2,
    name: "服务账号",
    description: "系统间调用账号",
    isSystem: true,
    sortOrder: 2,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  // role_type
  {
    id: 11,
    tenantId: 1,
    typeKey: TYPE_KEY.ROLE_TYPE,
    typeCode: "ORG",
    typeValue: 1,
    name: "组织角色",
    description: "组织同步生成",
    isSystem: true,
    sortOrder: 1,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 12,
    tenantId: 1,
    typeKey: TYPE_KEY.ROLE_TYPE,
    typeCode: "POSITION",
    typeValue: 2,
    name: "岗位角色",
    description: "岗位同步生成",
    isSystem: true,
    sortOrder: 2,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 13,
    tenantId: 1,
    typeKey: TYPE_KEY.ROLE_TYPE,
    typeCode: "PERSONAL",
    typeValue: 3,
    name: "个人角色",
    description: "用户同步连带创建",
    isSystem: true,
    sortOrder: 3,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 14,
    tenantId: 1,
    typeKey: TYPE_KEY.ROLE_TYPE,
    typeCode: "GROUP_ROLE",
    typeValue: 5,
    name: "分组角色",
    description: "可聚合基本角色",
    isSystem: true,
    sortOrder: 5,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 15,
    tenantId: 1,
    typeKey: TYPE_KEY.ROLE_TYPE,
    typeCode: "BASIC_ROLE",
    typeValue: 6,
    name: "基础角色",
    description: "可手工 CRUD 的功能角色",
    isSystem: true,
    sortOrder: 6,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  // resource_type
  {
    id: 21,
    tenantId: 1,
    typeKey: TYPE_KEY.RESOURCE_TYPE,
    typeCode: "MENU",
    typeValue: 1,
    name: "菜单",
    description: "导航菜单资源",
    isSystem: true,
    sortOrder: 1,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 22,
    tenantId: 1,
    typeKey: TYPE_KEY.RESOURCE_TYPE,
    typeCode: "BUTTON",
    typeValue: 2,
    name: "按钮",
    description: "页面按钮资源",
    isSystem: true,
    sortOrder: 2,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 23,
    tenantId: 1,
    typeKey: TYPE_KEY.RESOURCE_TYPE,
    typeCode: "API",
    typeValue: 3,
    name: "接口",
    description: "后端 API 资源",
    isSystem: true,
    sortOrder: 3,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 24,
    tenantId: 1,
    typeKey: TYPE_KEY.RESOURCE_TYPE,
    typeCode: "DATA",
    typeValue: 4,
    name: "数据",
    description: "数据范围资源",
    isSystem: true,
    sortOrder: 4,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 25,
    tenantId: 1,
    typeKey: TYPE_KEY.RESOURCE_TYPE,
    typeCode: "CUSTOM_DATA",
    typeValue: 10,
    name: "自定义数据资源",
    description: "租户扩展资源类型",
    isSystem: false,
    sortOrder: 10,
    extra: '{"max_depth":3}',
    createdAt: "2026-02-01 00:00:00"
  },
  // group_type
  {
    id: 31,
    tenantId: 1,
    typeKey: TYPE_KEY.GROUP_TYPE,
    typeCode: "DEPT",
    typeValue: 1,
    name: "部门组",
    description: "按部门分组",
    isSystem: true,
    sortOrder: 1,
    extra: null,
    createdAt: "2026-01-01 00:00:00"
  },
  {
    id: 32,
    tenantId: 1,
    typeKey: TYPE_KEY.GROUP_TYPE,
    typeCode: "PROJECT",
    typeValue: 2,
    name: "项目组",
    description: "按项目分组",
    isSystem: false,
    sortOrder: 2,
    extra: null,
    createdAt: "2026-02-01 00:00:00"
  }
];

/** 自增 id（已用最大 id + 1） */
let _nextId = mockTypeDefs.reduce((max, t) => Math.max(max, t.id), 0) + 1;

/**
 * 自动分配 typeValue（mock 层模拟 T-PERM-019 D1：服务端在 tenant+typeKey 内 max+1 分配）。
 * 软删不复用——已软删行的 typeValue 仍占位，新分配取全部行（含 deleted）的 max+1，
 * 保证不会复用已删除的最高 typeValue。
 */
function nextTypeValue(typeKey: string): number {
  const used = mockTypeDefs
    .filter(t => t.typeKey === typeKey)
    .map(t => t.typeValue);
  return used.length === 0 ? 1 : Math.max(...used) + 1;
}

/** typeCode 自动生成（mock 层兜底，对齐 D3：typeCode 应为对外稳定编码）。
 *  唯一性按 schema uk_type_definition_code WHERE delete_flag=0：已软删行的 code 可复用。 */
function genTypeCode(typeKey: string, name: string): string {
  // 按名称生成大写下划线编码，同名追加数字后缀避免冲突
  const base = `${typeKey}_${name.toUpperCase().replace(/[\s-]+/g, "_")}`;
  let code = base;
  let suffix = 1;
  while (
    mockTypeDefs.some(
      t => !t.deleted && t.typeKey === typeKey && t.typeCode === code
    )
  ) {
    code = `${base}_${suffix++}`;
  }
  return code;
}

/** 深拷贝（返回给前端，避免外部修改内存） */
function clone(t: TypeDefResp): TypeDefResp {
  return { ...t };
}

export default defineFakeRoute([
  // 列表：对齐后端 ItemsResp（全量，无分页/无 typeKey/keyword 过滤，登记 T-PERM-023 🔧）。
  // 过滤/分页由前端 hook 本地完成；此处仅排除已软删行。
  {
    url: "/api/perm/type-definition/list",
    method: "post",
    response: () => {
      const items = mockTypeDefs.filter(t => !t.deleted).map(clone);
      return ok({ items });
    }
  },
  // 详情
  {
    url: "/api/perm/type-definition/detail",
    method: "post",
    response: ({ body }) => {
      const { id } = body || {};
      const found = mockTypeDefs.find(t => t.id === id && !t.deleted);
      if (!found) return err(404, "类型定义不存在");
      return ok(clone(found));
    }
  },
  // 创建：自动分配 typeValue/typeCode，校验 typeKey+typeCode 唯一（排除已软删行）。
  // isSystem 固定 false（schema 语义：系统预置走初始化种子，不由前端创建）。
  {
    url: "/api/perm/type-definition/create",
    method: "post",
    response: ({ body }) => {
      const { typeKey, typeCode, name, description, sortOrder, extra } =
        body || {};
      if (!typeKey) return err(400, "typeKey 不能为空");
      if (!name) return err(400, "name 不能为空");
      const resolvedCode = typeCode || genTypeCode(typeKey, name);
      if (
        mockTypeDefs.some(
          t =>
            !t.deleted && t.typeKey === typeKey && t.typeCode === resolvedCode
        )
      ) {
        return err(409, `类型编码「${resolvedCode}」在 ${typeKey} 下已存在`);
      }
      const newDef: TypeDefResp = {
        id: _nextId++,
        tenantId: 1,
        typeKey: typeKey as TypeKey,
        typeCode: resolvedCode,
        typeValue: nextTypeValue(typeKey),
        name,
        description: description ?? null,
        isSystem: false,
        sortOrder: sortOrder ?? 0,
        extra: extra ?? null,
        createdAt: "2026-06-30 00:00:00"
      };
      mockTypeDefs.push(newDef);
      // T-PERM-028：resource_type 新类型联动预置 CRUD 四操作位（DDL 预置组模板同款）
      if (typeKey === TYPE_KEY.RESOURCE_TYPE) {
        presetOperationsForType(resolvedCode);
      }
      return ok(clone(newDef));
    }
  },
  // 更新：isSystem=true 拒绝改名，仅可改 description/sortOrder/extra（+name 非系统项）
  {
    url: "/api/perm/type-definition/update",
    method: "post",
    response: ({ body }) => {
      const { typeId, name, description, sortOrder, extra } = body || {};
      const found = mockTypeDefs.find(t => t.id === typeId && !t.deleted);
      if (!found) return err(404, "类型定义不存在");
      if (found.isSystem && name && name !== found.name) {
        return err(403, "系统预置类型不可改名");
      }
      if (name !== undefined) found.name = name;
      if (description !== undefined) found.description = description;
      if (sortOrder !== undefined) found.sortOrder = sortOrder;
      if (extra !== undefined) found.extra = extra;
      return ok(clone(found));
    }
  },
  // 删除：批量软删（置 deleted=true，对齐 schema delete_flag），isSystem=true 跳过。
  // 软删不复用 typeValue——已删行保留在 mockTypeDefs，nextTypeValue 仍计入其 typeValue。
  {
    url: "/api/perm/type-definition/remove",
    method: "post",
    response: ({ body }) => {
      const { ids = [] } = body || {};
      const skipped: number[] = [];
      for (const id of ids as number[]) {
        const found = mockTypeDefs.find(t => t.id === id);
        if (!found || found.deleted) continue;
        if (found.isSystem) {
          skipped.push(id);
          continue;
        }
        found.deleted = true;
      }
      if (skipped.length > 0) {
        return err(403, `系统预置类型不可删除（跳过 ${skipped.length} 项）`);
      }
      return ok(null);
    }
  }
]);
