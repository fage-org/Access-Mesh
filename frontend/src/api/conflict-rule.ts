/**
 * 冲突规则 API
 * 经 @/utils/http 调用 permission-center 端点（`/api/perm/conflict-rule/*`）；
 * Phase 1 由 mock/conflict-rule.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6
 * 后端实现：permission-center ConflictRuleController + ConflictRuleAppService
 *
 * 🔧 API 核对项（登记 T-PERM-030）：
 * - detail/update/remove 用内部主键 id。冲突规则无业务键（无 code 字段），id 即唯一标识，
 *   与 resource/operation/condition（有 code）不同，切业务键诉求弱，可接受--仍登记后端评估。
 * - list 无分页无筛选（全量），后端 listConflictRules 无 VIEW 校验，种子可能缺失--
 *   前端按 CONFLICT_RULE:VIEW 门控路由可达性。
 * - Resp 缺 updatedAt（只有 createdAt），🔧 登记后端补齐。
 * - ConflictRuleDetailReq（conflictRuleId）为死代码，Controller 实际用 IdReq{id}，❌ 登记后端清理。
 * - conflictType 实体注释（MUTEX_OP/MUTEX_ROLE）与 enum（ROLE_MUTEX/PERM_MUTEX）不一致，🔧 登记后端修正注释。
 * - detect 无权限校验（public 方法），🔧 登记后端评估是否补 VIEW 校验。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 常量 ==========

/**
 * 冲突类型编码（对齐后端 ConflictType 枚举）。
 * - ROLE_MUTEX：角色互斥（两个角色不能同时授予同一用户），用 firstAbstractRoleId + secondAbstractRoleId
 * - PERM_MUTEX：权限互斥（两个操作权限不能同时授予），用 firstOperationPermissionId + secondOperationPermissionId + resourceTypeValue
 */
export const CONFLICT_TYPE = {
  ROLE_MUTEX: "ROLE_MUTEX",
  PERM_MUTEX: "PERM_MUTEX"
} as const;

export type ConflictTypeCode =
  (typeof CONFLICT_TYPE)[keyof typeof CONFLICT_TYPE];

/** 冲突类型中文标签 */
export const CONFLICT_TYPE_LABEL: Record<string, string> = {
  ROLE_MUTEX: "角色互斥",
  PERM_MUTEX: "权限互斥"
};

// ========== 类型定义 ==========

/** 冲突规则响应（对齐后端 ConflictRuleResp） */
export type ConflictRuleResp = {
  id: number;
  tenantId: number;
  /** 冲突类型（ROLE_MUTEX/PERM_MUTEX） */
  conflictType: string;
  /** 第一个操作权限 ID（PERM_MUTEX 用） */
  firstOperationPermissionId: number | null;
  /** 第二个操作权限 ID（PERM_MUTEX 用） */
  secondOperationPermissionId: number | null;
  /** 资源类型值（type_definition.type_value，PERM_MUTEX 用；null 表示适用所有资源类型） */
  resourceTypeValue: number | null;
  /** 第一个角色 ID（ROLE_MUTEX 用） */
  firstAbstractRoleId: number | null;
  /** 第二个角色 ID（ROLE_MUTEX 用） */
  secondAbstractRoleId: number | null;
  description: string | null;
  createdAt: string;
};

/** 冲突规则创建请求（对齐 ConflictRuleReq） */
export type ConflictRuleCreateReq = {
  conflictType: string;
  firstOperationPermissionId?: number | null;
  secondOperationPermissionId?: number | null;
  resourceTypeValue?: number | null;
  firstAbstractRoleId?: number | null;
  secondAbstractRoleId?: number | null;
  description?: string;
};

/** 冲突规则更新请求（对齐 ConflictRuleUpdateReq，id 必填） */
export type ConflictRuleUpdateReq = {
  id: number;
  conflictType?: string;
  firstOperationPermissionId?: number | null;
  secondOperationPermissionId?: number | null;
  resourceTypeValue?: number | null;
  firstAbstractRoleId?: number | null;
  secondAbstractRoleId?: number | null;
  description?: string;
};

/** 冲突检测结果响应（对齐 ConflictDetectResp） */
export type ConflictDetectResp = {
  conflictDetected: boolean;
  matchedRules: ConflictRuleResp[];
};

/** 冲突检测请求（对齐 ConflictRuleDetectReq） */
export type ConflictDetectReq = {
  firstOperationPermissionId: number;
  secondOperationPermissionId: number;
  resourceTypeValue?: number | null;
};

// ========== API 函数 ==========

/** 查询冲突规则列表（POST /api/perm/conflict-rule/list，EmptyReq）。
 *  后端返回 ItemsResp（无分页），前端本地处理。 */
export const getConflictRuleList = async (): Promise<
  ItemsResp<ConflictRuleResp>
> => {
  const res = await http.request<PermResult<ItemsResp<ConflictRuleResp>>>(
    "post",
    "/api/perm/conflict-rule/list",
    { data: {} }
  );
  return unwrap(res);
};

/** 查询冲突规则详情（POST /api/perm/conflict-rule/detail，IdReq{id}）。
 *  🔧 用内部主键 id（冲突规则无业务键，登记 T-PERM-030）。 */
export const getConflictRuleDetail = async (
  id: number
): Promise<ConflictRuleResp> => {
  const res = await http.request<PermResult<ConflictRuleResp>>(
    "post",
    "/api/perm/conflict-rule/detail",
    { data: { id } }
  );
  return unwrap(res);
};

/** 创建冲突规则（POST /api/perm/conflict-rule/create） */
export const createConflictRule = async (
  data: ConflictRuleCreateReq
): Promise<ConflictRuleResp> => {
  const res = await http.request<PermResult<ConflictRuleResp>>(
    "post",
    "/api/perm/conflict-rule/create",
    { data }
  );
  return unwrap(res);
};

/** 更新冲突规则（POST /api/perm/conflict-rule/update）。
 *  🔧 用内部 id（登记 T-PERM-030）。 */
export const updateConflictRule = async (
  data: ConflictRuleUpdateReq
): Promise<ConflictRuleResp> => {
  const res = await http.request<PermResult<ConflictRuleResp>>(
    "post",
    "/api/perm/conflict-rule/update",
    { data }
  );
  return unwrap(res);
};

/** 删除冲突规则，支持批量（POST /api/perm/conflict-rule/remove，IdsReq{ids}）。
 *  🔧 用内部 id（登记 T-PERM-030）。 */
export const removeConflictRules = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/conflict-rule/remove",
      { data: { ids } }
    )
  );
};

/** 冲突检测（POST /api/perm/conflict-rule/detect）。
 *  仅检测操作权限对（PERM_MUTEX 场景），双向匹配（A-B 和 B-A 都算）。
 *  🔧 后端 detect 无权限校验（登记 T-PERM-030）。 */
export const detectConflictRule = async (
  data: ConflictDetectReq
): Promise<ConflictDetectResp> => {
  const res = await http.request<PermResult<ConflictDetectResp>>(
    "post",
    "/api/perm/conflict-rule/detect",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
