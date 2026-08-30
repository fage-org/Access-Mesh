/**
 * 权限授予页（4.1 v3）页面级类型模型。
 * 设计依据：docs/design/frontend/permission-grant.md §2.1（GrantContext）/ §4（单直接授权模型）/ §6（草稿模型）。
 */
import type {
  GrantRecordKey,
  GrantScopeMode,
  RolePermissionItem
} from "@/api/permission-grant";

// ========== 主体上下文（设计 §2.1） ==========

/** 主体入口类型（PERSONAL 预留，首期不挂路由） */
export type SubjectType = "ROLE" | "ORG";

/**
 * 主体上下文：页面所有 list / apply-grant-plan 调用统一携带，禁止散落拼装。
 * 由左栏选中节点派生（P1-1，对齐 api-contract §5.5 角色定位三元组）。
 */
export type GrantContext = {
  /** 恒 null（P1-1：abstract_role 无域列，domainCode 仅校验域存在性、不按域过滤） */
  domainCode: null;
  /** BASIC_ROLE（角色入口/分组展开） / ORG / POSITION（组织入口二期） */
  roleTypeCode: string;
  /** 角色 externalId（组织入口为 String(orgId)，二期） */
  roleExternalId: string;
  /** 页头展示名 */
  displayName: string;
  /** GROUP_ROLE 展开来源标注（"来自分组角色 <名>"）；非展开选中为 null */
  fromGroupRoleName?: string | null;
};

/**
 * 左栏主体树节点（角色入口：T-PERM-043 后主体树仅 BASIC_ROLE；GROUP_ROLE 展开为基础角色
 * 虚拟子节点的分支保留为不可达，待 role_inclusion 立项恢复）
 */
export type SubjectTreeNode = {
  /** el-tree node-key（BASIC_ROLE 用 `role:{externalId}`；分组展开子节点 `role:{externalId}@{groupExternalId}`） */
  key: string;
  /**
   * 节点种类（不依赖 roleTypeCode 猜测，评审问题 5）：
   * - ROLE：真实角色节点（T-PERM-043 后主体树仅 BASIC_ROLE，来自 getRoleTree，含嵌套真实 children）
   * - EXTRA_CONTAINER：GROUP_ROLE 展开时"关联基础角色"虚拟容器（不可选，装 EXTRA_ROLE；
   *   T-PERM-043 后不可达，extra-roles/list 已退役，结构保留待恢复）
   * - EXTRA_ROLE：虚拟容器内的基础角色子节点（选中主体=该基础角色；同上不可达）
   */
  kind: "ROLE" | "EXTRA_CONTAINER" | "EXTRA_ROLE";
  roleTypeCode: string;
  externalId: string | null;
  name: string;
  status: number;
  /** GROUP_ROLE 展开的虚拟子节点标记（选中后主体 = 该基础角色本身） */
  expandedFromGroup?: boolean;
  /** 展开来源分组角色名（展示标注） */
  groupRoleName?: string | null;
  /** 分组节点：基础角色子节点已加载标记（避免重复请求 extra-roles/list） */
  expandedLoaded?: boolean;
  children?: SubjectTreeNode[];
};

// ========== 操作定义（页面模型） ==========

/** 操作定义（binaryBit/inheritMask 已解析为 BigInt；列合并后模型见 source-chain.ts MergedOperation） */
export type OperationDef = {
  code: string;
  name: string;
  /** 操作定义必属某类型（全局操作概念已退役） */
  resourceTypeCode: string;
  binaryBit: bigint;
  inheritMask: bigint;
};

// ========== 草稿变更模型（设计 §6.1） ==========

/** 变更清单条目摘要（分组聚合展示用：操作+条件+canGrant+scopeMode，§4 多选确定第十四轮） */
export type ChangeSummary = {
  operationCode: string | null;
  conditionCode: string | null;
  canGrant: boolean;
  scopeMode: GrantScopeMode;
  /** 展示文本（如 "销售报表 · VIEW · 无条件"） */
  label: string;
  /** 资源维度展示（资源名或 "全部资源"；定位用） */
  resourceLabel: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
};

/** 新增变更（弹窗授权 / 弹窗添加子权限） */
export type AddChange = {
  kind: "add";
  changeId: string;
  /** 新记录键（主权限或子权限；operationCode 必传——MANUAL 只写单操作记录） */
  recordKey: GrantRecordKey;
  /** 子权限挂已存在父（详情层；仅引用提交前已存在的父记录） */
  parentPermissionId?: number;
  /** 子权限挂草稿中的新父（虚拟挂载；提交时并入父 change 的 children 一次性建树，设计 §5 P1-4a） */
  parentChangeId?: string;
  summary: ChangeSummary;
};

/** 修改变更（详情层改条件 / canGrant 微调；不涉及资源/操作/范围） */
export type UpdateChange = {
  kind: "update";
  changeId: string;
  /** 持久化记录 id */
  recordId: number;
  /** 变更前快照（清单展示 + 撤销回滚） */
  before: RolePermissionItem;
  /** 变更后值（仅微变更字段；conditionCode=null 表示清除条件，提交时映射为 ""） */
  after: { canGrant: boolean; conditionCode: string | null };
  summary: ChangeSummary;
};

/** 移除变更（弹窗取消勾选 / 弹窗撤销子权限） */
export type RemoveChange = {
  kind: "remove";
  changeId: string;
  /** 被移除记录快照 */
  records: RolePermissionItem[];
  /** 主权限级联删子数量（>0 时清单提示"该权限下 N 条子权限将随主权限一并移除"） */
  cascadeChildCount: number;
  reason: "dialog-uncheck" | "detail-delete";
  summary: ChangeSummary;
};

/**
 * 跨键替换变更（详情层跨键编辑：范围/资源/操作变化 = removes 旧 + creates 新，
 * 同一 apply-grant-plan 请求内原子执行；子权限不迁移——随旧主权限级联删除，设计 §4/§6.4）。
 */
export type ReplaceChange = {
  kind: "replace";
  changeId: string;
  /** 旧记录快照（removes；主权限级联删子） */
  removedRecords: RolePermissionItem[];
  /** 新记录键（creates） */
  newKey: GrantRecordKey;
  /** 子权限跨键替换时挂已存在父 */
  parentPermissionId?: number;
  cascadeChildCount: number;
  summary: ChangeSummary;
};

export type DraftChange =
  | AddChange
  | UpdateChange
  | RemoveChange
  | ReplaceChange;

// ========== 提交状态机（设计 §6.5，第十四轮收窄四态） ==========

/**
 * 提交状态（discriminated union，四态）：
 * idle → dirty（有变更）→ saving（保存中，按钮 disabled）→ 成功回 idle / 失败 saveFailed。
 * 砍八态的 loading/ready/outcomeUnknown/stale + 代际号 + list 对比恢复 + 重放；
 * 超时/网络未知由 saveFailed(unknownOutcome=true) 提示"网络异常，请刷新页面确认当前状态"覆盖。
 */
export type SubmitState =
  | { kind: "idle" }
  | { kind: "dirty" }
  | { kind: "saving" }
  | { kind: "saveFailed"; message: string; unknownOutcome: boolean };

/** 页面能力（与状态正交，§6.5 第十一轮 P2-7）：仅由门禁派生（ROLE:VIEW→view / ROLE:MANAGE→edit） */
export type PageCapability = "edit" | "view";

/** 记录级只读原因（与页面 capability 正交；AUTO_DEP 派生） */
export type ReadonlyReason = "AUTO_DEP" | null;

// ========== 变更清单分组（§6.3 + §4 多选聚合） ==========

/** 清单分组键（操作+条件+canGrant+scopeMode 聚合多选新增，避免 N 行刷屏） */
export function changeGroupKey(summary: ChangeSummary): string {
  return [
    summary.operationCode ?? "-",
    summary.conditionCode ?? "-",
    summary.canGrant ? "1" : "0",
    summary.scopeMode
  ].join("|");
}

/** 生成变更 id（草稿内唯一，无需持久化语义） */
let changeSeq = 0;
export function nextChangeId(): string {
  changeSeq += 1;
  return `chg-${Date.now()}-${changeSeq}`;
}
