/**
 * 冲突规则 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/conflict-rule/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/conflict-rule`）。
 * T-FE-020 修正：本文件原用裸 `/api/perm/conflict-rule/*`（T-FE-041 全局切 Gateway 路径时漏改
 * 本页），Gateway 仅路由 /admin/**、/perm/**，裸路径必 404；mock/conflict-rule.ts 已随真实链路退役删除。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6（T-PERM-030 收口契约要点）
 * 后端实现：access-service ConflictRuleController + ConflictRuleAppService
 *
 * T-PERM-030 收口（原 🔧 登记全部处置）：
 * - detail/update/remove 维持内部主键 id（评估定案：冲突规则无业务键，type+对象对+rtv 为
 *   复合语义身份，无单列 code 可切，与 resource/operation/condition 不同）。
 * - list 全量不分页（量小非流水表，对齐 condition/domain-config 定案）；后端读端点
 *   （list/detail/detect）已补类型级 CONFLICT_RULE:VIEW 门禁。
 * - Resp 已补 updatedAt；detail 查不到抛 20020（CONFLICT_RULE_NOT_FOUND）。
 * - ConflictRuleDetailReq 死代码已删除（Controller 用 IdReq{id}）。
 * - conflictType 实体注释已对齐枚举（ROLE_MUTEX/PERM_MUTEX）。
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
  /** 最后更新时间（T-PERM-030 后端补齐） */
  updatedAt: string;
};

/** 冲突规则字段集（按 conflictType 区分，create/update 共用）。
 *  对齐后端全量替换契约（PUT）：conflictType 必填，按类型字段必填。
 *  - ROLE_MUTEX：firstAbstractRoleId + secondAbstractRoleId 必填，操作权限字段 null
 *  - PERM_MUTEX：firstOperationPermissionId + secondOperationPermissionId 必填，
 *    resourceTypeValue 显式传（null=清空"全部"），角色字段 null */
export type ConflictRulePayload =
  | {
      conflictType: "ROLE_MUTEX";
      firstAbstractRoleId: number;
      secondAbstractRoleId: number;
      firstOperationPermissionId?: null;
      secondOperationPermissionId?: null;
      resourceTypeValue?: null;
      description?: string;
    }
  | {
      conflictType: "PERM_MUTEX";
      firstOperationPermissionId: number;
      secondOperationPermissionId: number;
      resourceTypeValue: number | null;
      firstAbstractRoleId?: null;
      secondAbstractRoleId?: null;
      description?: string;
    };

/** 冲突规则创建请求（对齐 ConflictRuleReq） */
export type ConflictRuleCreateReq = ConflictRulePayload;

/** 冲突规则更新请求（对齐 ConflictRuleUpdateReq，id 必填，全量替换语义） */
export type ConflictRuleUpdateReq = { id: number } & ConflictRulePayload;

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
    "/perm/api/perm/conflict-rule/list",
    { data: {} }
  );
  return unwrap(res);
};

/** 查询冲突规则详情（POST /api/perm/conflict-rule/detail，IdReq{id}）。
 *  内部主键 id 定位（T-PERM-030 评估定案：冲突规则无业务键）；查不到抛 20020。 */
export const getConflictRuleDetail = async (
  id: number
): Promise<ConflictRuleResp> => {
  const res = await http.request<PermResult<ConflictRuleResp>>(
    "post",
    "/perm/api/perm/conflict-rule/detail",
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
    "/perm/api/perm/conflict-rule/create",
    { data }
  );
  return unwrap(res);
};

/** 更新冲突规则（POST /api/perm/conflict-rule/update）。
 *  全量替换语义（PUT）：须传完整字段集（按 conflictType），rtv 显式传（null=清空"全部"）。
 *  内部主键 id 定位（T-PERM-030 评估定案）；查不到抛 20020。 */
export const updateConflictRule = async (
  data: ConflictRuleUpdateReq
): Promise<ConflictRuleResp> => {
  const res = await http.request<PermResult<ConflictRuleResp>>(
    "post",
    "/perm/api/perm/conflict-rule/update",
    { data }
  );
  return unwrap(res);
};

/** 删除冲突规则，支持批量（POST /api/perm/conflict-rule/remove，IdsReq{ids}）。
 *  幂等：幽灵 id 静默跳过；无类型级 DELETE 权限整批拒绝。 */
export const removeConflictRules = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/conflict-rule/remove",
      { data: { ids } }
    )
  );
};

/** 冲突检测（POST /api/perm/conflict-rule/detect）。
 *  仅检测操作权限对（PERM_MUTEX 场景），双向匹配（A-B 和 B-A 都算）。
 *  T-PERM-030：后端已补类型级 CONFLICT_RULE:VIEW 门禁（与 list/detail 同款）。 */
export const detectConflictRule = async (
  data: ConflictDetectReq
): Promise<ConflictDetectResp> => {
  const res = await http.request<PermResult<ConflictDetectResp>>(
    "post",
    "/perm/api/perm/conflict-rule/detect",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
