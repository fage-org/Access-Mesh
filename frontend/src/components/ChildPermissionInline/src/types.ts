/**
 * ChildPermissionInline 的 binding 契约类型。
 *
 * 设计依据：permission-grant.md §16.4.5（授权弹窗步骤四，子权限逐项内联）+
 * §16.8 R9（T-FE-024 先内联化，T-FE-026 基于新组件）。
 *
 * 组件不直接 inject 页面 store，通过窄接口 binding 隔离草稿读写：
 * - T-FE-014 传基于 pgStore 的 adapter（直接读写页面草稿）。
 * - T-FE-026 传基于弹窗任务快照的 adapter（确认加入变更才合并到页面草稿，
 *   取消直接丢弃局部 adapter，满足"新建弹窗取消不产生草稿"）。
 *
 * binding 只抽象组件真正使用的两个动作：getCell（读渲染）+ toggleCell（写切换）。
 * childKey 算法不进 binding（属草稿模型知识），由使用方持有的 adapter 额外暴露。
 */
import type { GrantScopeMode } from "@/api/permission-grant";
import type {
  PermissionCellContext,
  DraftPermission,
  CellState
} from "@/utils/permission-grant-types";

/** 子权限单元格定位入参（不含 domainCode/parentKey，由 adapter 内部持有） */
export interface ChildCellInput {
  childResourceTypeCode: string;
  scopeMode: GrantScopeMode;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
}

/** 子权限草稿操作窄接口（组件契约） */
export interface ChildPermissionBinding {
  /** 读取单元格上下文（渲染 PermissionCell） */
  getCell(input: ChildCellInput): PermissionCellContext;
  /** 切换授权（新增/撤销/恢复） */
  toggleCell(input: ChildCellInput): void;
}

/** onOpenSetting 事件载荷（不含 childKey，由使用方通过 adapter.getCellKey 算） */
export interface ChildOpenSettingPayload {
  input: ChildCellInput;
  draft: DraftPermission | null;
  state: CellState;
  supportsCondition?: boolean;
  supportsDelegation?: boolean;
}
