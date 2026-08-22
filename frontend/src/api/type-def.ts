/**
 * 类型定义 API
 * 经 @/utils/http 调用 access-service 端点（`/api/perm/type-definition/*`）；
 * Phase 1 由 mock/type-def.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页/列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.1
 * 后端实现：access-service TypeDefinitionController + TypeDefinitionAppService
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { PaginatedResp, ItemsResp } from "./role-manage";

// ========== 类型种类常量（type_key 分组） ==========

/**
 * type_definition.type_key 取值（对齐 schema access-service.sql 注释）。
 * - user_type：用户/主体类型
 * - role_type：角色类型（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）
 * - resource_type：资源类型（MENU/BUTTON/API/DATA 等）
 * - group_type：分组类型
 */
export const TYPE_KEY = {
  USER_TYPE: "user_type",
  ROLE_TYPE: "role_type",
  RESOURCE_TYPE: "resource_type",
  GROUP_TYPE: "group_type"
} as const;

export type TypeKey = (typeof TYPE_KEY)[keyof typeof TYPE_KEY];

/** type_key 中文标签（前端展示用） */
export const TYPE_KEY_LABEL: Record<string, string> = {
  user_type: "用户类型",
  role_type: "角色类型",
  resource_type: "资源类型",
  group_type: "分组类型"
};

/** 全部 type_key（下拉选项 + mock 种子遍历用） */
export const ALL_TYPE_KEYS: TypeKey[] = [
  TYPE_KEY.USER_TYPE,
  TYPE_KEY.ROLE_TYPE,
  TYPE_KEY.RESOURCE_TYPE,
  TYPE_KEY.GROUP_TYPE
];

// ========== 类型定义 ==========

/** 类型定义响应（对齐后端 TypeDefinitionResp） */
export type TypeDefResp = {
  id: number;
  tenantId?: number;
  /** 类型分组键（user_type/role_type/resource_type/group_type） */
  typeKey: string;
  /** 对外稳定编码（租户+typeKey 内唯一） */
  typeCode: string;
  /** 内部存储/计算值（服务端自动分配，前端只读展示） */
  typeValue: number;
  /** 显示名称 */
  name: string;
  /** 描述（可空） */
  description: string | null;
  /** 系统预置不可删改（true）；租户自定义（false） */
  isSystem: boolean;
  /** 排序顺序 */
  sortOrder: number;
  /** 扩展属性 JSON（可空，如 {"max_depth":5}） */
  extra: string | null;
  createdAt?: string;
  updatedAt?: string;
};

/** 类型定义列表查询参数。
 *  🔧 API 核对项（登记 T-PERM-023）：后端 TypeListReq 仅 domainCode（未用），返回租户全量
 *  ItemsResp（无分页、无 typeKey/keyword 过滤）。前端按 typeKey/keyword 本地过滤 + 切片分页。
 *  Phase 2 后端补 typeKey/keyword/pageNum/pageSize 参数并返回 PaginatedResp 后，前端可切回服务端分页。 */
export type TypeDefListQuery = {
  domainCode?: string | null;
};

/** 类型定义创建请求。
 *  🔧 API 核对项（登记 T-PERM-023 / T-PERM-019 D1）：
 *  - 不含 typeValue（设计意图：服务端在 tenant+typeKey 内自动分配、软删不复用）。
 *    后端 TypeCreateReq.typeValue 当前 @NotNull（DESIGN_DRIFT），Phase 2 收敛。
 *  - 含 typeCode（对外稳定编码）。后端 TypeCreateReq 当前无 typeCode 字段（createType 未 setTypeCode，
 *    潜在 bug），Phase 2 后端补字段或服务端生成。
 *  - 不含 isSystem（schema 语义：系统预置走初始化种子，不由前端创建；后端 TypeCreateReq 现有
 *    isSystem 字段为 DESIGN_DRIFT，Phase 2 收敛——前端固定创建租户自定义项 isSystem=false）。 */
export type TypeDefCreateReq = {
  typeKey: string;
  typeCode?: string | null;
  name: string;
  description?: string | null;
  sortOrder?: number;
  extra?: string | null;
};

/** 类型定义更新请求（对齐 TypeUpdateReq，仅可改 name/description/sortOrder/extra；
 *  typeKey/typeCode/typeValue 不可改——稳定编码设计） */
export type TypeDefUpdateReq = {
  typeId: number;
  name?: string;
  description?: string | null;
  sortOrder?: number;
  extra?: string | null;
};

// ========== API 函数 ==========

/** 查询类型定义列表（POST /api/perm/type-definition/list）。
 *  后端 TypeListReq 现仅 domainCode（未生效），返回 ItemsResp<TypeDefinitionResp>（租户全量，
 *  无分页/无 typeKey 过滤）。前端 hook 拿全量 items 后本地做 typeKey/keyword 过滤 + 切片分页。
 *  🔧 后端补 typeKey/keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 登记于 T-PERM-023。 */
export const getTypeDefList = async (
  params: TypeDefListQuery
): Promise<ItemsResp<TypeDefResp>> => {
  const res = await http.request<PermResult<ItemsResp<TypeDefResp>>>(
    "post",
    "/api/perm/type-definition/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询类型定义详情（POST /api/perm/type-definition/detail，IdReq{id}） */
export const getTypeDefDetail = async (id: number): Promise<TypeDefResp> => {
  const res = await http.request<PermResult<TypeDefResp>>(
    "post",
    "/api/perm/type-definition/detail",
    { data: { id } }
  );
  return unwrap(res);
};

/** 创建类型定义（POST /api/perm/type-definition/create） */
export const createTypeDef = async (
  data: TypeDefCreateReq
): Promise<TypeDefResp> => {
  const res = await http.request<PermResult<TypeDefResp>>(
    "post",
    "/api/perm/type-definition/create",
    { data }
  );
  return unwrap(res);
};

/** 更新类型定义（POST /api/perm/type-definition/update） */
export const updateTypeDef = async (
  data: TypeDefUpdateReq
): Promise<TypeDefResp> => {
  const res = await http.request<PermResult<TypeDefResp>>(
    "post",
    "/api/perm/type-definition/update",
    { data }
  );
  return unwrap(res);
};

/** 删除类型定义，支持批量（POST /api/perm/type-definition/remove，IdsReq{ids}）。
 *  isSystem=true 的系统预置项后端跳过删除。 */
export const removeTypeDefs = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/type-definition/remove",
      { data: { ids } }
    )
  );
};

export { type PaginatedResp, type ItemsResp };
