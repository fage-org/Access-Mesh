/**
 * 业务域 API
 * 经 @/utils/http 调用 access-service 端点（`/perm/api/perm/biz-domain/*`，Gateway /perm 前缀——T-FE-021 切换）；
 * mock/biz-domain.ts 已于 T-FE-021 联调退役删除。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.1（类型与域，T-PERM-026 收口契约要点）
 * 后端实现：access-service BizDomainController + BizDomainAppServiceImpl
 *
 * 后端端点 5 个：list / detail / create / update / remove（批量软删）。
 * - biz-domain 有独立 create/update/remove（非 save upsert，与 system-config 不同）。
 * - T-PERM-026 收口：list 服务端 keyword（code/name/description LIKE）+ 分页返回 PaginatedResp
 *   （ORDER BY code,id；不传分页参数=字典全量上限 200）；detail/update 切业务键 code；
 *   Resp 返回 global（全局域标识，前端禁删）；remove 删除保护（全局域/域下有配置 20051）；
 *   create 编码重复 20052。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { PaginatedResp } from "./role-manage";

// ========== 业务域定义 ==========

/** 业务域响应（对齐后端 BizDomainResp，T-PERM-026 起 Resp 含 global）。 */
export type BizDomainResp = {
  id: number;
  tenantId?: number;
  /** 业务域编码（租户内唯一，uk_biz_domain，如 HR / ORDER / GLOBAL） */
  code: string;
  /** 业务域名称 */
  name: string;
  /** 描述（可空） */
  description?: string | null;
  /** 是否全局域（每租户仅一个，范围隐式包含未被其他域认领的资源类型；全局域不可删；后端保证非空） */
  global: boolean;
  createdAt?: string;
};

/** 业务域列表查询参数（POST /list，BizDomainListReq，T-PERM-026 服务端过滤+分页）。
 *  keyword 匹配 code/name/description（LIKE 大小写敏感）；pageNum/pageSize 均不传 = 字典全量（上限 200）。 */
export type BizDomainListQuery = {
  keyword?: string | null;
  pageNum?: number;
  pageSize?: number;
};

/** 业务域创建请求（POST /create，BizDomainCreateReq）。
 *  code 必填（@NotBlank），租户内唯一；name 必填；description 可空。 */
export type BizDomainCreateReq = {
  code: string;
  name: string;
  description?: string | null;
};

/** 业务域更新请求（POST /update，BizDomainUpdateReq，T-PERM-026 切业务键 code 定位）。
 *  domainCode 必填（uk_biz_domain 租户内唯一）；code 不可改（改 code 等于新建新域）；
 *  name/description 可空（null=不更新，description 空串=显式清空）。 */
export type BizDomainUpdateReq = {
  domainCode: string;
  name?: string | null;
  description?: string | null;
};

// ========== API 函数 ==========

/** 查询业务域列表（POST /api/perm/biz-domain/list，BizDomainListReq）。
 *  T-PERM-026 收口：服务端 keyword 过滤（code/name/description，LIKE）+ 分页，
 *  返回 PaginatedResp（ORDER BY code,id）；不传分页参数 = 字典全量（上限 200）。 */
export const getBizDomainList = async (
  params: BizDomainListQuery
): Promise<PaginatedResp<BizDomainResp>> => {
  const res = await http.request<PermResult<PaginatedResp<BizDomainResp>>>(
    "post",
    "/perm/api/perm/biz-domain/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询业务域详情（POST /api/perm/biz-domain/detail，BizDomainDetailReq{domainCode}）。
 *  T-PERM-026 切业务键 code；未知编码返回成功信封 data=null 不抛错（role detail 先例），
 *  故返回类型含 null。 */
export const getBizDomainDetail = async (
  domainCode: string
): Promise<BizDomainResp | null> => {
  const res = await http.request<PermResult<BizDomainResp | null>>(
    "post",
    "/perm/api/perm/biz-domain/detail",
    { data: { domainCode } }
  );
  return unwrap(res);
};

/** 创建业务域（POST /api/perm/biz-domain/create，BizDomainCreateReq）。 */
export const createBizDomain = async (
  data: BizDomainCreateReq
): Promise<BizDomainResp> => {
  const res = await http.request<PermResult<BizDomainResp>>(
    "post",
    "/perm/api/perm/biz-domain/create",
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
    "/perm/api/perm/biz-domain/update",
    { data }
  );
  return unwrap(res);
};

/** 删除业务域（POST /api/perm/biz-domain/remove，IdsReq 批量软删）。
 *  删除保护（T-PERM-026，20051）：全局域不可删（Resp.global=true 前端预判禁用按钮）；
 *  域下仍存在有效域配置时拒删（需先删除该域下全部配置）。 */
export const removeBizDomain = async (ids: number[]): Promise<void> => {
  const res = await http.request<PermResult<void>>(
    "post",
    "/perm/api/perm/biz-domain/remove",
    { data: { ids } }
  );
  unwrap(res);
};
