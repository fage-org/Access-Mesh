/**
 * V2 权限授予方案 A 模型类型（T-FE-030）。
 *
 * 方案 A 多条件分支模型：授权事实以 GrantVariantId 为身份，同 PermCellKey（六维，
 * 不含 conditionCode）下多个条件分支并存（OR 语义）。
 *
 * 设计依据：docs/design/frontend/permission-grant-state-model.md §0/§1/§2.2/§7.1
 * - GrantVariantId = 服务端 id（number）或临时 UUID（string，任务构建时生成，非 replay）
 * - 存储 Map<GrantVariantId, V2DraftPermission> + 索引 Map<PermCellKey, GrantVariantId[]>
 * - 子权限键 = parentVariantId + "|" + childPermCellKey（含父变体，避免不同父分支碰撞）
 * - conditionCode 内部 null=无条件；"" 仅保存适配层表达"清除条件"（T-FE-033）
 *
 * GrantVariantId 类型只在此文件定义一次，其余模块 import 复用。
 */
import type { GrantScopeMode } from "@/api/permission-grant";
import type { PermCellKey } from "@/utils/permission-grant-types";

/** 授权变体身份：服务端 id（number）或临时 UUID（string） */
export type GrantVariantId = number | string;

/** PermCellKey 字符串化（六维，不含 conditionCode） */
export type PermCellKeyStr = string;

/** 子权限键 = parentVariantId + "|" + childPermCellKeyStr（含父变体，避免不同父分支碰撞） */
export type ChildPermCellKeyStr = string;

/** 草稿权限项（方案 A：以 variantId 为身份） */
export interface V2DraftPermission extends PermCellKey {
  variantId: GrantVariantId;
  /** null=无条件（内部规范化）；"" 仅保存层表达清除 */
  conditionCode: string | null;
  canGrant: boolean;
  /** 父权限 variantId（子权限），null=主权限 */
  dependOn: GrantVariantId | null;
  grantSource: string;
  resourceName: string | null;
}

/** V2 草稿状态：变体存储 + PermCellKey 索引 */
export interface V2DraftState {
  mainMap: Map<GrantVariantId, V2DraftPermission>;
  mainIndex: Map<PermCellKeyStr, GrantVariantId[]>;
  childMap: Map<GrantVariantId, V2DraftPermission>;
  childIndex: Map<ChildPermCellKeyStr, GrantVariantId[]>;
}

/** 任务效果（供右栏分组 + 测试断言） */
export type TaskEffect =
  | "add"
  | "update"
  | "remove"
  | "noChange"
  | "redundantSkipped";

/**
 * 变体命令（replay 执行单元）。
 * 任务构建时（T-FE-032）生成，含稳定 proposedVariantId/targetVariantId；
 * replay 是纯函数，不生成 UUID。
 */
export type VariantCommand =
  | {
      kind: "grant";
      cell: PermCellKey;
      /** 新增分支的稳定身份（任务构建时 generateVariantId() 生成，string） */
      proposedVariantId: string;
      conditionCode: string | null;
      canGrant: boolean;
      resourceName: string | null;
      /** R11：被 ALL 覆盖且无直接记录时是否显式保留直接记录（false=跳过 redundantSkipped） */
      keepDirectWhenAllCovered: boolean;
    }
  | {
      kind: "update";
      /** 编辑已有分支（保持 variantId，更新 conditionCode/canGrant） */
      targetVariantId: GrantVariantId;
      conditionCode: string | null;
      canGrant: boolean;
    }
  | {
      kind: "remove";
      /** 移除具体变体（按 variantId，非 PermCellKey） */
      targetVariantId: GrantVariantId;
    }
  // 子权限命令（T-FE-033）：dependOn 关联具体父变体，逐 command 撤销
  | {
      kind: "child-grant";
      /** 父变体（须在 mainDraft 投影中，否则 replay 跳过防孤儿） */
      parentVariantId: GrantVariantId;
      cell: PermCellKey;
      proposedVariantId: string;
      conditionCode: string | null;
      canGrant: boolean;
      resourceName: string | null;
    }
  | {
      kind: "child-update";
      targetVariantId: GrantVariantId;
      conditionCode: string | null;
      canGrant: boolean;
    }
  | {
      kind: "child-remove";
      targetVariantId: GrantVariantId;
    };

/** 任务意图 */
export type GrantTaskIntent = "grant" | "adjust" | "remove";

/** 资源（ALL 时 resourceCode/codeType null） */
export interface TaskResource {
  resourceCode: string | null;
  codeType: string | null;
  resourceName: string | null;
}

/** V2 授权任务快照（外层批量语义供展示 + 内部显式变体命令供 replay） */
export interface V2GrantTaskSnapshot {
  taskId: string;
  domainCode: string;
  roleExternalId: string;
  roleTypeCode: string;
  resourceTypeCode: string;
  scopeMode: GrantScopeMode;
  intent: GrantTaskIntent;
  /** 展示用操作码集 */
  operationCodes: string[];
  /** 展示用资源集 */
  resources: TaskResource[];
  /** replay 执行的变体命令序列（任务构建时生成，含稳定 variantId） */
  commands: VariantCommand[];
  createdAt: number;
}

/** 命令执行结果（供右栏分组 + 测试断言） */
export interface CommandEffect {
  commandIndex: number;
  variantId: GrantVariantId;
  effect: TaskEffect;
  /** 阻断/跳过原因（noChange/redundantSkipped 时） */
  reason?: string;
}

/** 任务执行结果 */
export interface TaskReplayEffect {
  taskId: string;
  commands: CommandEffect[];
}

/** replay 结果 */
export interface V2ReplayResult {
  mainDraft: Map<GrantVariantId, V2DraftPermission>;
  mainIndex: Map<PermCellKeyStr, GrantVariantId[]>;
  childDraft: Map<GrantVariantId, V2DraftPermission>;
  childIndex: Map<ChildPermCellKeyStr, GrantVariantId[]>;
  taskEffects: Map<string, TaskReplayEffect>;
}

/** 聚合有效态（§1.1；aggregate 只产 DIRECT/CONDITIONAL/UNAUTHORIZED，ALL_COVERED/INHERITED/DERIVED 正交/不可达） */
export type SummaryEffective =
  | "UNAUTHORIZED"
  | "DIRECT"
  | "CONDITIONAL"
  | "ALL_COVERED"
  | "INHERITED"
  | "DERIVED";

/** 草稿副状态 */
export type SummaryDraftChange = "ADD" | "REMOVE" | "MODIFY" | null;

/** 单元格聚合摘要 */
export interface CellSummary {
  effective: SummaryEffective;
  draftChange: SummaryDraftChange;
  variants: V2DraftPermission[];
}

/**
 * 失败子权限操作 overlay（T-FE-033 验收⑦）。
 * saveAll 主成功子失败时保留，叠加在 childDraft 上（add->set / remove->delete），
 * 供 retryFailedChildren 重试。childKey 为展示用，parentVariantId 用于解析父 id。
 */
export interface V2FailedChildOp {
  op: "add" | "remove";
  child: V2DraftPermission;
  childKey: ChildPermCellKeyStr;
  parentVariantId: GrantVariantId;
}
