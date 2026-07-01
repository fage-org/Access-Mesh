/**
 * 业务域 API
 * 经 @/utils/http 调用 permission-center 端点（`/api/perm/biz-domain/*`）；
 * Phase 1 由 mock/biz-domain.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.1（类型与域）
 * 后端实现：permission-center BizDomainController + BizDomainAppServiceImpl
 *
 * 后端端点 5 个：list / detail / create / update / remove（批量软删）。
 * - biz-domain 有独立 create/update/remove（非 save upsert，与 system-config 不同）。
 * - list 接 EmptyReq 返回全量 ItemsResp（无分页，🔧 登记 T-PERM-026）；前端本地过滤+分页。
 * - detail 接 IdReq 内部主键 id（🔧 应切业务键 code，登记 T-PERM-026）。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 业务域定义 ==========

/** 业务域响应（对齐后端 BizDomainResp）。
 *  🔧 后端 Resp 不返回 global 字段（entity/schema 有 global，每租户仅一个全局域），
 *  前端无法区分全局域，登记 T-PERM-026。 */
export type BizDomainResp = {
  id: number;
  tenantId?: number;
  /** 业务域编码（租户内唯一，uk_biz_domain，如 HR / ORDER / GLOBAL） */
  code: string;
  /** 业务域名称 */
  name: string;
  /** 描述（可空） */
  description?: string | null;
  createdAt?: string;
};

/** 业务域创建请求（POST /create，BizDomainCreateReq）。
 *  code 必填（@NotBlank），租户内唯一；name 必填；description 可空。 */
export type BizDomainCreateReq = {
  code: string;
  name: string;
  description?: string | null;
};

/** 业务域更新请求（POST /update，BizDomainUpdateReq）。
 *  domainId 必填（@NotNull）；name/description 可空（仅更新非空字段）。
 *  🔧 后端用内部主键 domainId，应切业务键 code（登记 T-PERM-026）。 */
export type BizDomainUpdateReq = {
  domainId: number;
  name?: string | null;
  description?: string | null;
};

// ========== API 函数 ==========

/** 查询业务域列表（POST /api/perm/biz-domain/list）。
 *  后端 EmptyReq 无参，返回 ItemsResp<BizDomainResp>（租户全量，无分页/无过滤）。
 *  前端 hook 拿全量 items 后本地做 keyword 过滤 + 切片分页。
 *  🔧 后端补 keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 登记于 T-PERM-026。 */
export const getBizDomainList = async (): Promise<ItemsResp<BizDomainResp>> => {
  const res = await http.request<PermResult<ItemsResp<BizDomainResp>>>(
    "post",
    "/api/perm/biz-domain/list",
    { data: {} }
  );
  return unwrap(res);
};

/** 查询业务域详情（POST /api/perm/biz-domain/detail，IdReq{id}）。
 *  🔧 后端用内部主键 id，应切业务键 code（schema uk_biz_domain 已保证 tenant+code 唯一，登记 T-PERM-026）。 */
export const getBizDomainDetail = async (
  id: number
): Promise<BizDomainResp> => {
  const res = await http.request<PermResult<BizDomainResp>>(
    "post",
    "/api/perm/biz-domain/detail",
    { data: { id } }
  );
  return unwrap(res);
};

/** 创建业务域（POST /api/perm/biz-domain/create，BizDomainCreateReq）。 */
export const createBizDomain = async (
  data: BizDomainCreateReq
): Promise<BizDomainResp> => {
  const res = await http.request<PermResult<BizDomainResp>>(
    "post",
    "/api/perm/biz-domain/create",
    { data }
  );
  return unwrap(res);
};

/** 更新业务域（POST /api/perm/biz-domain/update，BizDomainUpdateReq）。 */
export const updateBizDomain = async (
  data: BizDomainUpdateReq
): Promise<BizDomainResp> => {
  const res = await http.request<PermResult<BizDomainResp>>(
    "post",
    "/api/perm/biz-domain/update",
    { data }
  );
  return unwrap(res);
};

/** 删除业务域（POST /api/perm/biz-domain/remove，IdsReq 批量软删）。
 *  全局域不可删（后端需校验 global=true 拒绝；🔧 Resp 不返回 global 前端无法预判，登记 T-PERM-026）。 */
export const removeBizDomain = async (ids: number[]): Promise<void> => {
  const res = await http.request<PermResult<void>>(
    "post",
    "/api/perm/biz-domain/remove",
    { data: { ids } }
  );
  unwrap(res);
};

export { type ItemsResp };
