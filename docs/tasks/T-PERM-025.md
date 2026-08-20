---
doc_type: task
id: T-PERM-025
title: 7.1 操作日志后端——action 字典/枚举接口 + 补充筛选维度（前端筛选能力闭环）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.8
  - docs/design/permission-center/api-contract.md#§6.10.6
  - docs/design/access-service-architecture.md#§8.2
depends_on:
  - T-FE-005
blocks: []
acceptance:
  - "新增后端「操作日志 action 字典」只读接口（如 `POST /api/perm/log/action-options`），返回 operation_log 当前实际存在的全量 `action` 去重集合（按 module 可选过滤）；供前端操作日志筛选下拉动态拉取，替代前端硬编码的 12 个代表性子集——解决「102 个唯一 action 但下拉仅 12 个、且无自由输入、后端精确匹配」导致大量日志无法按 action 检索的问题（T-ACCESS-007 第五轮 P2#5 评审登记）"
  - "接口需走权限门禁（管理查询可直查 Mapper，本次为字典类只读查询，沿用 /api/perm/log/* 既有 QueryBoundary 门禁语义）；响应为操作日志 action 集合（可含 module 维度分组）"
  - "可选：前端 action 筛选支持自由输入/模糊匹配（本任务评估，若做则后端 select 由精确匹配改为前缀/包含匹配并保持索引可用；若不做则仅依赖 action 字典下拉，登记决策于任务卡）"
  - "design_writeback：api-contract.md §5.8/§6.10.6（新增 action-options 契约）、frontend 操作日志页（types.ts ACTION_OPTIONS 由硬编码改为动态拉取）、README 任务看板（本任务 done 回写）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-20
---

# T-PERM-025 7.1 操作日志后端——action 字典接口与筛选能力闭环

> 状态：proposed
> 依赖：T-FE-005（前端操作日志页基础）
> 归属：frontend-phase2（7.1 操作日志后端）

## 背景

T-ACCESS-007 已收敛 operation_log 的 `module` 三值（ADMIN/PERMISSION/ACCESS），`action` 为 `{业务对象}_{动作}` 大写事件码（由各业务方法 `@OperationLog` 注解维护，集合开放增长）。

前端操作日志筛选当前 `module` 下拉对齐三值，但 `action` 为**本地硬编码的 12 个代表性非穷尽子集**（`frontend/src/views/system/operation-log/utils/types.ts`），而后端实际存在约 102 个唯一 action 且**精确匹配**、无自由输入——大量 action（如 `USER_PASSWORD_RESET`、`ROLE_CREATE`、`SYSTEM_CONFIG_UPSERT` 等）无法通过筛选检索。

T-ACCESS-007 第四轮 P1#5 将「action 集合开放增长、无枚举接口」登记至本任务；第五轮 P2#5 评审再次确认需后端提供 action 字典接口。

## 范围

1. 新增只读字典接口 `POST /api/perm/log/action-options`（可加 `module` 过滤维度），返回 operation_log 当前实际存在的 action 去重集合；走既有日志查询门禁（管理查询直查 Mapper，不涉及权限判定）。
2. 前端操作日志筛选 `ACTION_OPTIONS` 由硬编码改为动态拉取；`LogDetailDrawer` 的 action→label 展示同步使用字典（无匹配时回退显示原始 code）。
3. 评估 action 筛选是否支持自由输入/模糊匹配（若做：后端 select 精确匹配改前缀/包含匹配并保持索引语义；若不做：仅动态下拉，登记决策）。
4. 补测试：字典接口返回去重集合、module 过滤、门禁行为；前端 typecheck/prettier。

## 非目标 / 决策记录

- 本任务不改变 action 事件码的生成（仍由 `@OperationLog` 注解维护，开放增长）；仅提供查询侧字典能力。
- action 字典接口返回**实际存在**的事件码而非维护端枚举，避免与业务方法注解清单双轨漂移。
- 若评估后决定「精确匹配 + 动态下拉」即足够（无自由输入），则后端不改查询语义，仅新增字典接口并回写本决策。

## 完成记录

（待实现后填写）
