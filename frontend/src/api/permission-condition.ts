/**
 * 权限条件 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/permission-condition/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/permission-condition`）。
 * T-FE-041 切换真实链路后，mock/permission-condition.ts 路由失配，已随 T-FE-020 退役删除
 *（_shared/permission-condition-store 亦随 T-FE-018 授权页 mock 退役一并删除，条件链路全真实）。
 * 响应统一为后端 R<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6（T-PERM-029 收口：detail/update/remove
 * 均以业务键 code 定位，uk tenant+code；ConditionResp 含 updatedAt；detail 查不到抛 20006）。
 * 后端实现：access-service ConditionController + ConditionAppServiceImpl。
 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

/** 权限条件响应（对齐后端 ConditionResp） */
export type ConditionResp = {
  /** 内部主键（授权链路 conditionId 引用；管理端点定位一律用 code） */
  id: number;
  tenantId: number;
  /** 条件编码（业务键，uk tenant+code，创建后不可改；INLINE 条件为 inline- 前缀自动生成） */
  code: string;
  /** 条件名称 */
  name: string;
  /** 条件规则 JSON 字符串，结构 {logic, items[]}，详见 ConditionEvalUtils */
  conditionRules: string;
  /** 是否启用（INLINE 条件恒 true） */
  enabled: boolean;
  /** 是否可下发 Gateway 评估（T-PERM-017），true 时规则随接口快照内联到 Gateway 本地重评 */
  gatewayEvaluable: boolean;
  /** 条件来源（T-PERM-048 双轨制）：MANAGED=管理页条件（可被 conditionCode 引用/可实例级授权）；
   *  INLINE=授权页内联（1:1 属于创建它的授权记录，不可被显式 code 引用，只能在授权页随记录更改） */
  source: "MANAGED" | "INLINE";
  /** 条件描述 */
  description: string | null;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间（T-PERM-029 补齐） */
  updatedAt: string;
};

/** 条件创建请求（对齐 ConditionCreateReq） */
export type ConditionCreateReq = {
  code: string;
  name: string;
  /** JSON 字符串 */
  conditionRules: string;
  enabled?: boolean;
  gatewayEvaluable?: boolean;
  description?: string;
};

/** 条件更新请求（对齐 ConditionUpdateReq；code 为定位键，业务键本身不可改） */
export type ConditionUpdateReq = {
  code: string;
  name?: string;
  conditionRules?: string;
  enabled?: boolean;
  gatewayEvaluable?: boolean;
  description?: string;
};

/** 查询条件列表（POST /perm/api/perm/permission-condition/list）。
 *  后端返回 ItemsResp<ConditionResp> 全量不分页（T-PERM-029 设计定案：条件模板数量有界，
 *  与 domain-config/service-config 同款；keyword/enabled 过滤由前端本地完成）。
 *  双轨制（T-PERM-048）：缺省只返回 MANAGED 管理页条件（权限条件页口径——内联条件在管理页
 *  查不到也不能管理）；includeInline=true 时含 INLINE（授权页回显内联条件名称/规则摘要用）。 */
export const getConditionList = async (
  includeInline?: boolean
): Promise<ItemsResp<ConditionResp>> => {
  const res = await http.request<R<ItemsResp<ConditionResp>>>(
    "post",
    "/perm/api/perm/permission-condition/list",
    { data: includeInline === true ? { includeInline: true } : {} }
  );
  return unwrap(res);
};

/** 查询条件详情（POST /perm/api/perm/permission-condition/detail，ConditionDetailReq{conditionCode}）。
 *  读取无门禁（2026-08-08 产品确认：条件规则全租户开放）；查不到抛 20006 CONDITION_NOT_FOUND。
 *  ⚠️ 该端点未注册 bootstrap Gateway 清单（本页不消费，T-FE-020 口径）——
 *  后续页面接入前须先在 BootstrapGraphDefinition.apiRoutes() 补注册，否则 fail-closed 403。 */
export const getConditionDetail = async (
  conditionCode: string
): Promise<ConditionResp> => {
  const res = await http.request<R<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/detail",
    { data: { conditionCode } }
  );
  return unwrap(res);
};

/** 创建条件（POST /perm/api/perm/permission-condition/create） */
export const createCondition = async (
  data: ConditionCreateReq
): Promise<ConditionResp> => {
  const res = await http.request<R<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/create",
    { data }
  );
  return unwrap(res);
};

/** 更新条件（POST /perm/api/perm/permission-condition/update）。
 *  以业务键 code 定位（创建后不可改）。 */
export const updateCondition = async (
  data: ConditionUpdateReq
): Promise<ConditionResp> => {
  const res = await http.request<R<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/update",
    { data }
  );
  return unwrap(res);
};

/** 按业务键删除条件，支持批量（POST /perm/api/perm/permission-condition/remove，ConditionRemoveReq{codes}）。
 *  请求中不存在的 code 静默跳过（幂等语义）。 */
export const removeConditions = async (codes: string[]): Promise<void> => {
  unwrap(
    await http.request<R<void>>(
      "post",
      "/perm/api/perm/permission-condition/remove",
      { data: { codes } }
    )
  );
};

export { type ItemsResp };
