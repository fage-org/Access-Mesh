/**
 * 权限授予草稿与单元格契约类型（页面无关共享模块）。
 *
 * 从 views/system/permission-grant/utils/types.ts 抽取，供：
 * - RePermissionCell / ChildPermissionInline（共享组件，消除反向依赖页面工具）
 * - permission-grant 页面（hook / types.ts re-export）
 * 统一引用。
 *
 * 设计依据：docs/design/frontend/permission-grant.md
 * - §9.1 草稿模型（baseline + draft + 稳定键）
 * - §4.4 单元格 6 态
 * - §7 子权限 / 范围权限
 */
import type { GrantScopeMode } from "@/api/permission-grant";

// ========== 稳定键 ==========

/** 权限单元稳定键（§9.1） */
export interface PermCellKey {
  domainCode: string;
  resourceTypeCode: string;
  scopeMode: GrantScopeMode;
  /** null = ALL（类型级全量授权，不含 resourceCode/codeType） */
  resourceCode: string | null;
  /** null = ALL */
  codeType: string | null;
  operationCode: string;
}

/** 计算权限单元稳定键字符串（用 JSON.stringify 避免控制字符，对齐 T-FE-013 mock 分组键修复） */
export function permCellKey(k: PermCellKey): string {
  return JSON.stringify([
    k.domainCode,
    k.resourceTypeCode,
    k.scopeMode,
    k.resourceCode ?? "",
    k.codeType ?? "",
    k.operationCode
  ]);
}

// ========== 草稿 ==========

/** 草稿权限项（baseline 拷贝 + 本地修改） */
export interface DraftPermission extends PermCellKey {
  /** 服务端 id，新增时 null */
  id: number | null;
  /** 权限条件码，null = 无条件 */
  conditionCode: string | null;
  /** 是否允许继续授权（canGrant） */
  canGrant: boolean;
  /** 父权限服务端 id（子权限），null = 主权限 */
  dependOn: number | null;
  /** 父权限稳定键（新增子权限挂未保存主权限时），null = 主权限或已保存子权限 */
  dependOnTempKey: string | null;
  /** 来源：MANUAL=直接配置 / AUTO_DEP=资源依赖自动补全 / COMPOSED=角色组合 */
  grantSource: string;
  /** 资源名称（展示用，ALL 时 null） */
  resourceName: string | null;
}

// ========== 单元格状态（§4.4） ==========

/**
 * P0 实现 6 态（决策点修正 A：已修改属性态必须实现，否则中栏右栏不一致）：
 * - UNAUTHORIZED 未授权
 * - GRANTED 已直接授权
 * - PENDING_ADD 待新增
 * - PENDING_REMOVE 待移除
 * - MODIFIED 已修改属性（条件/canGrant 变化但授权未变）
 * - ALL_COVERED 被 ALL 覆盖（实例单元对应操作有 ALL 授权）
 * 暂缓：派生/自动补全态（AUTO_DEP/COMPOSED）、操作者无法授予 disabled 另由 grantableByOperator 控制
 */
export type CellState =
  | "UNAUTHORIZED"
  | "GRANTED"
  | "PENDING_ADD"
  | "PENDING_REMOVE"
  | "MODIFIED"
  | "ALL_COVERED";

/** 单元格上下文（RePermissionCell 渲染入参） */
export interface PermissionCellContext {
  state: CellState;
  /** 当前草稿权限（GRANTED/PENDING_ADD/PENDING_REMOVE/MODIFIED 时非 null） */
  draft: DraftPermission | null;
  /**
   * 是否被 ALL 覆盖（正交字段，R6 修正）。
   * INSTANCE 单元格 + 同操作在 ALL draft 存在授权即为 true（计划态，不读 baseline：
   * ALL 被草稿移除后实例不再显示 ALL 覆盖，三栏一致），
   * 不因当前存在直接记录而被压平（支持「★ ALL 覆盖 + ● 有直接记录」共存）。
   */
  allCovered: boolean;
  /** baseline 是否存在该 INSTANCE 直接记录（R6 副标记「● 有直接记录」依据） */
  hasBaselineDirectRecord: boolean;
  /** 操作者是否可授予（决策点 6：候选权限单元能力） */
  grantableByOperator: boolean;
  /** 不可授予原因（grantableByOperator=false 时） */
  denyReason: string | null;
  /** 是否只读（角色 directGrantable=false 或 操作者无 MANAGE） */
  readonly: boolean;
  /** 子权限数量（附加设置入口标记） */
  childCount: number;
  /** 条件摘要（已绑定条件时展示） */
  conditionSummary: string | null;
}

// ========== 子权限内联组件 ==========

/** 子权限内联组件上下文（ChildPermissionInline 入参） */
export interface ChildPermissionContext {
  /** 父权限草稿（主权限） */
  parent: DraftPermission;
  /** 允许的子资源类型码列表（domainCapability.childResourceTypeCodes） */
  childResourceTypeCodes: string[];
  /** 是否只读 */
  readonly: boolean;
}

// ========== 授权弹窗触发契约（T-FE-025 中栏 emit / T-FE-026 弹窗接收） ==========

/** 授权弹窗公共上下文（角色 + 资源类型 + 范围模式） */
export interface GrantTriggerBase {
  domainCode: string;
  roleExternalId: string;
  roleTypeCode: string;
  roleName: string;
  resourceTypeCode: string;
  /** INSTANCE=资源类型标题区授权按钮 / ALL=ALL 特殊节点授权按钮 */
  scopeMode: GrantScopeMode;
}

/** open-grant 事件载荷：新建授权（操作/资源多选清空，条件默认无） */
export type GrantTriggerPayload = GrantTriggerBase;

/** open-adjust 事件载荷：点击已有权限标记，预填操作/资源/条件 */
export interface AdjustTriggerPayload extends GrantTriggerBase {
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
  /** 预填条件码（无条件时 null） */
  conditionCode: string | null;
  /** draft 快照（深拷贝，不可变引用；T-FE-026 弹窗据此恢复选择状态） */
  draft: DraftPermission | null;
}

// ========== 中栏操作权限摘要（T-FE-025 PermissionSummaryCell 渲染入参） ==========

/**
 * 摘要有效主状态（R6 优先级：ALL 覆盖 > 直接(含条件) > 派生 > 继承 > 未授权）。
 * 派生（DERIVED/⊕）渲染能力保留，normalizer 当前不产生（R1/R7 待 T-PERM-034）。
 */
export type SummaryEffective =
  | "DIRECT" // 直接权限（无条件）
  | "CONDITIONAL" // 直接权限（有条件）
  | "ALL_COVERED" // 被 ALL 覆盖
  | "DERIVED" // 派生权限（渲染能力保留，normalizer 当前不产生，R1/R7 待 T-PERM-034）
  | "NOT_GRANTABLE" // 操作者不可授予
  | "UNAUTHORIZED"; // 未授权

/** 草稿副状态（draft 相对 baseline 的变更，null=无变更） */
export type SummaryDraftChange = "ADD" | "REMOVE" | "MODIFY" | null;

/** 规范化摘要项（正交分解：有效主状态 + 草稿副状态） */
export interface SummaryItem {
  operationCode: string;
  operationName: string;
  effective: SummaryEffective;
  draftChange: SummaryDraftChange;
  /** 被 ALL 覆盖（正交，R6） */
  allCovered: boolean;
  /** baseline 有直接记录（副标记「● 有直接记录」依据） */
  hasBaselineDirectRecord: boolean;
  conditionSummary: string | null;
  canGrant: boolean;
  childCount: number;
  grantableByOperator: boolean;
  denyReason: string | null;
  readonly: boolean;
  /** open-adjust 触发上下文快照（draft 为浅拷贝，不可变引用） */
  trigger: {
    domainCode: string;
    resourceTypeCode: string;
    scopeMode: GrantScopeMode;
    resourceCode: string | null;
    codeType: string | null;
    operationCode: string;
    conditionCode: string | null;
    draft: DraftPermission | null;
  };
}

// ========== 子权限键（共享纯函数，从 hook 抽出） ==========

/** 子权限稳定键 = parentKey + "|" + permCellKey(child) */
export function childPermCellKey(
  parentKey: string,
  child: PermCellKey
): string {
  return parentKey + "|" + permCellKey(child);
}

// ========== 授权任务快照（T-FE-026 弹窗确认产物，T-FE-028 右栏分组展示源） ==========

/** 任务意图 */
export type GrantTaskIntent = "grant" | "adjust" | "remove";

/** 任务资源（ALL 时 resourceCode/codeType 均为 null，不创建虚拟编码） */
export interface TaskResource {
  /** null = ALL（类型级全量，不创建虚拟资源编码；__ALL__ 仅作弹窗内部 rowKey，不进快照） */
  resourceCode: string | null;
  /** null = ALL */
  codeType: string | null;
  resourceName: string | null;
}

/**
 * 任务子权限组（完整集合替换语义，支持删除）。
 * - group 存在：children 为该父权限最终期望的完整子权限集合。
 * - group 缺失：本任务不修改该父权限的子权限。
 * - group 存在且 children=[]：明确移除该父权限的全部子权限。
 *
 * replay 时先删除当前投影中该 parentKey 前缀下的子项，再写入完整集合。
 */
export interface TaskChildGroup {
  /** 主权限稳定键（permCellKey 生成，子键前缀） */
  parentKey: string;
  /** 主权限操作码（展示用） */
  operationCode: string;
  /** 主权限资源（展示用；ALL 时 null） */
  resourceCode: string | null;
  codeType: string | null;
  /** 最终期望的完整子权限集合（replay 先删前缀再写入） */
  children: DraftPermission[];
}

/** 授权任务快照（一条 = 一个授权意图，确认加入变更的产物） */
export interface GrantTaskSnapshot {
  taskId: string;
  /** 上下文（commit/replace 时校验与当前角色一致，防止过期弹窗提交） */
  domainCode: string;
  roleExternalId: string;
  roleTypeCode: string;
  resourceTypeCode: string;
  scopeMode: GrantScopeMode;
  /** grant=新建授权 / adjust=修改属性 / remove=移除授权 */
  intent: GrantTaskIntent;
  /** 选中的操作码集（有序） */
  operationCodes: string[];
  /** 选中的资源集（ALL 时固定 [TaskResource{resourceCode:null}]） */
  resources: TaskResource[];
  /** 一组条件（适用全部操作×资源） */
  conditionCode: string | null;
  /** 批量 canGrant */
  canGrant: boolean;
  /**
   * R11：是否为"被 ALL 覆盖且 baseline 无直接记录"的组合创建直接记录。
   * 默认 false（不创建冗余直接记录）；true 时显式保留。
   * 仅影响 INSTANCE + allCovered + 无直接记录的组合；已有直接记录的组合不跳过。
   */
  keepDirectWhenAllCovered: boolean;
  /** 步骤四子权限配置（按主权限组合分组） */
  children: TaskChildGroup[];
  /** 创建时间戳（排序；replace 保持原值，避免编辑导致任务重排） */
  createdAt: number;
}
