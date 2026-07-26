---
doc_type: plan
title: 权限授予页授权弹窗与右栏变更重构
status: cancelled
domain: frontend
archived_at: 2026-07-26
design_refs:
  - docs/archive/2026-07-26/permission-grant.md#16-中栏展示授权弹窗右栏变更重构目标设计2026-07-12
tasks:
  - T-FE-025
  - T-FE-026
  - T-FE-028
  - T-FE-027
acceptance: "保持左中右三栏与左栏角色树；中栏完成资源权限概览与授权入口；授权弹窗完成批量授权任务；右栏完成本次变更常驻展示；三处草稿状态一致；§16.8 风险 R1~R11 已逐项确认；回归与设计回写完成。"
last_updated: 2026-07-12
---

# 权限授予页授权弹窗与右栏变更重构

> ⚠️ 已归档（2026-07-26）：权限授予页面交互不满意，v1 + v2 两套整体删除重做。代码删除，设计文档（permission-grant.md §16）归档至 `docs/archive/2026-07-26/`；任务 T-FE-024~026 保持 done（产出废弃）、T-FE-027/028 cancelled。T-FE-024 抽取的 `ReConditionEditor` / `condition-rules` 保留，被 `permission-condition` 页继续使用。本 plan 仅作历史追溯。

## 目标

- 保留现有左中右三栏和左栏角色树。
- 将中栏从“资源 × 操作矩阵直接编辑”调整为“资源树 + 当前权限摘要 + 授权入口”，点击授权打开授权弹窗。
- 将右栏调整为本次变更记录常驻区（授权弹窗确认加入的分组记录）。
- 复用现有 baseline/draft、附加设置、子权限、两步保存、失败重试和离开保护能力。

## 非目标

- 不改动后端授权 API、表结构和权限查询引擎。
- 不恢复原设计中栏批量入口（R5 已确认）；授权弹窗内的多操作+多资源多选属新交互模型，不属批量模式限制。不实现自动授权预览或 GROUP_ROLE 有效权限展开。
- 不重新设计左栏角色树。
- 不把条件编辑器或子权限树永久内嵌到右栏。

## 准入条件

- [x] `docs/archive/2026-07-26/permission-grant.md` §16.8 风险已逐项确认并回写；R1~R11 全部已确认。
- [x] R1/R7 前端降级已确认：本计划不等待 T-PERM-034；后端有效/派生来源能力在 T-PERM-034 完成时核对，并由 T-FE-018 联调回写。
- [x] T-FE-024 先于 T-FE-026，交叉文件边界已确认；T-FE-026 正式依赖 T-FE-024。
- [x] 明确本轮不恢复批量模式；若范围变化，先新增任务并同步本 plan 与看板。

## 执行门禁

- [x] T-FE-026 启动前 T-FE-024 已完成；T-FE-025 可在 T-FE-024 执行期间并行推进。

## 任务清单

| ID | 标题 | 状态快照 |
|---|---|---|
| T-FE-025 | 中栏资源权限概览与授权入口 | done |
| T-FE-026 | 授权弹窗（批量授权任务） | done |
| T-FE-028 | 右栏本次变更记录 | proposed |
| T-FE-027 | 三栏状态整合、回归验证与设计回写 | proposed |

任务详情和唯一状态以 `docs/tasks/README.md` 及对应任务文件为准。

## 归档条件

- T-FE-025~028 全部 `done`，且各任务 acceptance 全部满足。
- `docs/archive/2026-07-26/permission-grant.md` 已按最终实现回写，T-FE-027 `design_writeback.status=done`。
- T-FE-018 的联调依赖已确认指向 T-FE-027，且不存在 dangling 依赖。
- 权限授予页相关测试、构建和设计索引验证通过。

## 当前进度

- 2026-07-12：方案采纳并拆分 T-FE-025~027（后新增 T-FE-028）；计划保持 proposed，等待 §16.8 风险确认后启动。
- 2026-07-12：第二轮设计修订，从"右栏常驻编辑器"改为"授权弹窗 + 右栏变更记录"；新增 T-FE-028（右栏变更记录）；§16 重写为 §16.1~§16.9（R 表移至 §16.8）；R4 改写为弹窗设计；R1/R2/R3/R5/R6/R7/R9 已关，仅剩 R8；T-FE-024 扩大范围含 ChildPermissionDrawer 内联化。
- 2026-07-12：审核修正采用 1A/2A：R1/R7 使用前端降级，不再被 T-PERM-034 锁死；T-FE-024 先于 T-FE-026。其余未定风险关闭前计划仍保持 proposed。
- 2026-07-12：R8/R10/R11 关闭，§16.8 R1~R11 全关；§16.4 合并为四步（步骤三批量条件 + 步骤四子权限逐项）；状态改互斥；plan 转 active。
- 2026-07-12：T-FE-024 完成（转 done）。抽取 ReConditionEditor / ReConditionPicker / RePermissionCell / ChildPermissionInline + `utils/condition-rules` 共享模块（消除 `api/permission-grant.ts` 反向依赖）；ChildPermissionInline 窄接口 binding（getCell + toggleCell）不 inject store。T-FE-026 启动前置门禁满足，T-FE-025/026 可开始实现。
- 2026-07-12：T-FE-025 完成（转 done）。中栏重构为资源树 + 操作权限摘要 + 授权入口；新建 `PermissionSummaryCell` 共享组件（窄接口，`SummaryItem` 正交分解 effective + draftChange）；store `buildMainCellContext` 修正 `allCovered` 正交 + 加 `hasBaselineDirectRecord`；事件契约 `GrantTriggerPayload`/`AdjustTriggerPayload` 下沉 `@/utils/permission-grant-types`（携带 domainCode，draft 快照）；`index.vue` 移除 `AdditionalSettingDialog` + 子权限 drawer + `child-binding` adapter。T-FE-026 可启动实现。
- 2026-07-12：T-FE-026 完成（转 done）。授权弹窗（`GrantDialog.vue`）四步配置 + 调整/移除模式；引入 `grantTasks` 任务快照 + `replayGrantTasks` 纯函数（`utils/grant-task.ts`），`mainDraft`/`childDraft` 改 computed（baseline + 有序 grantTasks + failedChildren overlay）；store 原子操作 `commit/replace/remove/clearGrantTask`；R11 显式 `keepDirectWhenAllCovered` 开关；子权限完整集合替换（支持删除）；`childPermCellKey` 抽共享；移除旧 `toggleMainCell`/`setMainCellAttr`/`toggleChildCell`/`setChildCellAttr`/`revertDiff`/`applyChildOpsToDraft`；`saveAll`/`retryFailedChildren` 同步改造（先捕获 diff 再清 overlay）；右栏过渡移除单项撤销。T-FE-028 可启动。
