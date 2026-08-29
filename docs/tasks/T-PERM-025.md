---
doc_type: task
id: T-PERM-025
title: 7.1 操作日志后端——action 字典/枚举接口 + 补充筛选维度（前端筛选能力闭环）
status: done
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
  status: done
last_updated: 2026-08-28
---

# T-PERM-025 7.1 操作日志后端——action 字典接口与筛选能力闭环

> 状态：done（2026-08-28 收口）
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

## 完成记录（2026-08-28）

- **action 字典接口**：`POST /api/perm/log/operation/action-options`（`{module?}` → `ItemsResp<String>`，字典序去重集合）——端点落位 `/log/operation/` 子路径（与 operation/list 同资源分组，任务卡示例路径为"如"非绑定）；`selectDistinctActions` 返回实际存在值（非维护端枚举，避免与 @OperationLog 注解清单双轨漂移）；前端 ACTION_OPTIONS 硬编码 12 项子集移除，hook 动态拉取（filterable 下拉，label=value=code），LogDetailDrawer action 展示改原始编码。
- **action 筛选语义（决策点，设计定案）**：动态字典 + 精确匹配——字典含全部实际存在事件码检索已闭环，保持等值索引语义；不做自由输入/模糊匹配，后端查询语义未改。
- **筛选维度扩展（operation-log.md §8 第 2 项）**：`OperationLogListReq` 补 `operatorId`/`since`/`until`（created_at 闭区间）/`targetType`，均精确匹配；Mapper 方法更名 `selectPageByCondition`/`countByCondition`（共享 listCondition 片段）；前端筛选表单同步扩展（操作者 ID / datetimerange 时间范围 / 目标类型）。
- **审计分离（operation-log.md §8 第 3 项确认型，设计定案）**：新增独立 `OPERATION_LOG:VIEW`——schema 种子 `OPERATION_LOG=30`（CRUD 预置组自动覆盖 VIEW，operation_permission 117→121、type_definition 31→32）、`ResourceTypeCode.OPERATION_LOG` 枚举、`LogQueryAppServiceImpl` 操作日志查询（list/count/action-options）门禁切换（旧 SYSTEM_CONFIG:VIEW）；bootstrap 固定图管理角色补授（§14.4 第 9 项、固定图 21→22 条，`GRANT_RESOURCE_TYPES` 集合同步补项——新权限码须固定图持否则无授予起点死锁）；前端 `OPERATION_LOG_PERMS.LOG_VIEW` 切换。**边界**：变更日志（log/change/list）与权限视图（permission-view/recent-changes）门禁仍为 SYSTEM_CONFIG:VIEW，随 T-PERM-032/033 各自页面任务处置。（均已收口：T-PERM-032 切 PERMISSION_CHANGE_LOG:VIEW；T-PERM-033 切被查目标实例 USER:VIEW/ROLE:VIEW）
- **契约路径**：§5.8 路径误写经查已于 T-ACCESS-007 第五轮修正（当前即实现路径），本任务补 operation-log 契约要点（五维筛选/action-options/门禁）。
- **测试**：`LogQueryAppServiceImplOperationLogTest` 新增 4 用例（OPERATION_LOG:VIEW 门禁回归锁——旧实现下会失败、字典去重与 module 规整、多维过滤透传与空白规整）；`HttpApiPathSnapshotTest` 快照 185→186 条（action-options）；`AccessBootstrapPgIT` 固定图计数 21→22/8→9；`AccessServiceSchemaH2Test` 种子计数 31→32/117→121。
- **回归**：access-service `mvn test` 全量绿（见看板提交记录）+ 前端 typecheck 干净 + vitest 全绿。
