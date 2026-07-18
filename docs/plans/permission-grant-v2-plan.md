---
doc_type: plan
title: 权限授予页 V2（方案A多条件分支模型）
status: active
domain: frontend
last_reviewed: 2026-07-17
---

# 权限授予页 V2（方案A多条件分支模型）

> 状态：active
> 关联设计：`docs/design/frontend/permission-grant-state-model.md`、`permission-grant-interaction.md`、`permission-grant-error-flow.md`

## 目标

新增权限授予页 V2（`/system/permission-grant-v2`），基于三份新设计文档实现方案 A（多条件分支模型 + 直接操作矩阵 + fetchBaseline 原子恢复），与现有 permission-grant 页面并存供对比。旧页面保持不变。

## 非目标

- 不修改现有 permission-grant 页面（T-FE-014/025~028 产出）。
- 不改后端契约（临时 ID 契约采用选项1：按 `PermCellKey + normalized conditionCode` 匹配 save 响应）。
- 不实现 T-PERM-034 后端改造；V2 仍 mock 驱动。**mock 隔离方案（修订）**：只读端点（abstract-role/tree、resource-entity/tree、operation-permission/list、permission-condition/list）V2 **复用共享**，不复制第二事实源；可变授权（role-resource-permission/*）在 mock 环境用 **dev-only transport**（独立拦截，不注册生产路径），生产构建关闭 fake-server 后 V2 走真实 `/api/perm/role-resource-permission/*`（后端已支持多条件，方案 A）。V2 不注册任何 fake route：dev 环境直接注入内存 transport（不经全局 http client，绕开旧 mock 对 canonical URL 的接管），生产走标准 http client。
- 不实现 INHERITED/DERIVED 态真实数据（依赖 T-PERM-034，V2 预留渲染能力）。
- 不实现并发 configVersion 乐观锁（CONCURRENT_MODIFIED 态预留不可达）。

## 阶段拆分

| 任务 | 标题 | 依赖 |
|---|---|---|
| T-FE-029 | V2 页面骨架 + 路由 + 三栏 + 角色树 + 能力门控 | - |
| T-FE-030 | 方案A 前端模型（GrantVariantId + replay + 聚合摘要） | T-FE-029 |
| T-FE-031 | 中栏直接操作矩阵 + 单元格聚合摘要 + 分支列表就地展开 | T-FE-030 |
| T-FE-032 | 授权交互（点击/添加分支/逐分支编辑撤销/批量新增分支）+ R11 | T-FE-031 |
| T-FE-033 | 子权限矩阵展开（parentVariantId）+ 两步保存 + 条件清除 wire | T-FE-032 |
| T-FE-034 | 保存前总览 + 失败两子态 + STALE_WITH_CHILD_FAILURE + fetchBaseline + 离开保护 | T-FE-033 |
| T-FE-035 | 扩展V2 transport（多条件+失败模拟）+ 失格降级 + 回归验证 + 设计回写 | T-FE-034 |

## 验收标准

- V2 页面独立路由可访问，旧页面不受影响。
- 方案 A 多条件分支：同单元格多 OR 分支展示/逐分支编辑撤销。
- 直接操作矩阵 + 分支列表就地展开（非弹窗）。
- 保存流程：SAVE_PREVIEW -> SAVING -> CLEAN / SAVE_FAILED(两子态) / SAVE_OUTCOME_UNKNOWN / STALE / STALE_WITH_CHILD_FAILURE。
- fetchBaseline+reconcile 原子恢复（不丢 grantTasks/failedChildren）。
- 子权限按 parentVariantId 挂载；保存按 PermCellKey+conditionCode 匹配响应。
- 条件清除发 `""`（wire 约定，修复 P2-3 代码 bug）。
- V2 mock 分工：T-FE-029 搭 transport 骨架 + 基础 fixture，T-FE-035 扩展多条件分支返回 + 失败模拟。
- 提交前通过 `pnpm build && pnpm typecheck && pnpm lint && pnpm test`（Vitest 由 T-FE-030 引入）。

## 当前进度

active，门禁已关闭，可启动 T-FE-029。

## 准入门禁（转 active 前必须关闭，方案确认即可，不要求任务执行）

- [x] 交互设计 Q1~Q6 逐项关闭（`permission-grant-interaction.md` §12），尤其 **Q4：SAVE_PREVIEW 强制开启**——T-FE-034 验收为强制预览，Q4 已决策：强制开启，不提供"不再提示"开关（与 T-FE-034 一致）；Q1/Q2/Q3/Q5/Q6 采用 §12 默认建议，已确认。
- [x] 三份设计文档经设计方案评审确认（可作为实现依据）；正式 `status: adopted` 留待 T-FE-035 完成时回写。
- [x] mock 隔离方案确认（复用共享只读端点 + 可变授权 dev-only transport）。
- [x] Vitest 方案确认（依赖/配置确认，实施归 T-FE-030）。

> 门禁为**方案确认**，不要求 T-FE-030/035 任务执行；避免循环依赖。

## 归档条件

V2 验收通过 + 设计回写 done + 与旧页面对比结论沉淀后，可归档至 `docs/archive/`。
