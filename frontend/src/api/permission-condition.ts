/**
 * 权限条件 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/permission-condition/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/permission-condition`）。
 * T-FE-041 切换真实链路后，mock/permission-condition.ts 的旧 `/api/perm/**` 路径已自然失配。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6
 * 后端实现：access-service ConditionController + ConditionAppServiceImpl
 *
 * 🔧 API 核对项（登记 T-PERM-029）：
 * - detail/update/remove 均用内部主键 id（IdReq/IdsReq），应切业务键 code。
 *   schema uk_permission_condition(tenant_id, code) 已保证唯一，Phase 2 后端收敛。
 * - list 用 EmptyReq 无分页无筛选，应补 keyword/enabled/pageNum/pageSize（ConditionListReq 缺失）。
 * - list/detail 未见 CONDITION:VIEW 校验，种子可能缺失，联调真后端时可能全账号 403--
 *   前端仍按 VIEW 门控路由可达性。
 * - ConditionResp 缺 updatedAt（entity 有但 Resp 不返回，表格仅展示 createdAt）。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

/** 权限条件响应（对齐后端 ConditionResp） */
export type ConditionResp = {
  id: number;
  tenantId: number;
  /** 条件编码（业务键，uk tenant+code） */
  code: string;
  /** 条件名称 */
  name: string;
  /** 条件规则 JSON 字符串，结构 {logic, items[]}，详见 ConditionEvalUtils */
  conditionRules: string;
  /** 是否启用 */
  enabled: boolean;
  /** 是否可下发 Gateway 评估（T-PERM-017），true 时规则随接口快照内联到 Gateway 本地重评 */
  gatewayEvaluable: boolean;
  /** 条件描述 */
  description: string | null;
  /** 创建时间（后端 Resp 无 updatedAt） */
  createdAt: string;
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

/** 条件更新请求（对齐 ConditionUpdateReq；code 不可改--业务键） */
export type ConditionUpdateReq = {
  /** 🔧 内部主键，Phase 2 切业务键 code（T-PERM-029） */
  conditionId: number;
  name?: string;
  conditionRules?: string;
  enabled?: boolean;
  gatewayEvaluable?: boolean;
  description?: string;
};

/** 查询条件列表（POST /perm/api/perm/permission-condition/list，EmptyReq）。
 *  后端返回 ItemsResp<ConditionResp>（无分页无筛选），前端本地过滤。
 *  🔧 Phase 2 补 ConditionListReq（keyword/enabled/pageNum/pageSize），登记 T-PERM-029。 */
export const getConditionList = async (): Promise<ItemsResp<ConditionResp>> => {
  const res = await http.request<PermResult<ItemsResp<ConditionResp>>>(
    "post",
    "/perm/api/perm/permission-condition/list",
    { data: {} }
  );
  return unwrap(res);
};

/** 查询条件详情（POST /perm/api/perm/permission-condition/detail，IdReq{id}）。
 *  🔧 用内部主键 id，Phase 2 切业务键 code（T-PERM-029）。 */
export const getConditionDetail = async (
  id: number
): Promise<ConditionResp> => {
  const res = await http.request<PermResult<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/detail",
    { data: { id } }
  );
  return unwrap(res);
};

/** 创建条件（POST /perm/api/perm/permission-condition/create） */
export const createCondition = async (
  data: ConditionCreateReq
): Promise<ConditionResp> => {
  const res = await http.request<PermResult<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/create",
    { data }
  );
  return unwrap(res);
};

/** 更新条件（POST /perm/api/perm/permission-condition/update）。
 *  🔧 conditionId 为内部 id，Phase 2 切业务键 code（T-PERM-029）。 */
export const updateCondition = async (
  data: ConditionUpdateReq
): Promise<ConditionResp> => {
  const res = await http.request<PermResult<ConditionResp>>(
    "post",
    "/perm/api/perm/permission-condition/update",
    { data }
  );
  return unwrap(res);
};

/** 删除条件，支持批量（POST /perm/api/perm/permission-condition/remove，IdsReq{ids}）。
 *  🔧 用内部 id，Phase 2 切业务键 code（T-PERM-029）。 */
export const removeConditions = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/permission-condition/remove",
      { data: { ids } }
    )
  );
};

export { type ItemsResp };
