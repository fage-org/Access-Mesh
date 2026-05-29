# Permission Center Frontend 计划一致性矩阵

## Purpose

本文件用于记录权限中心前端计划体系的对齐状态，作为审计参考文档使用，不计入 14 个执行计划文件数量。

一致性判断基线：

- `README.md` 的执行顺序、依赖和验收口径。
- 当前后端 controller / DTO 的真实契约。
- `frontend-perm-api-mock.plan.md` 对最终演示收口的要求。
- `frontend-perm-backend-api-gap-tracker.md` 对当前范围和未来增强的边界划分。

## Status Legend

| Status    | Meaning                                                                |
| --------- | ---------------------------------------------------------------------- |
| `Aligned` | README、模块计划、契约锚点、下游交接基本一致，无明显结构漂移           |
| `Partial` | 核心范围基本成立，但依赖、mock 交接、前置条件或 gap tracker 关系不完整 |
| `Drift`   | 与 README、真实契约或当前拆分结构存在明确冲突，需要优先修复            |

## Summary Snapshot

| Scope               | Aligned | Partial | Drift |
| ------------------- | ------- | ------- | ----- |
| 14 个执行计划       | 5       | 8       | 1     |
| 3 个 Phase 协调计划 | 1       | 2       | 0     |
| 合计                | 6       | 10      | 1     |

---

## 执行计划矩阵

| README 序号 | 计划文件                                              | 核心交付                                                               | 契约 / 实现锚点                                                                                               | 前端落点                                                             | 上游依赖         | 下游交付                                       | Mock 交接                                     | Gap Tracker | Status    | 审计备注                                                                                  |
| ----------- | ----------------------------------------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- | ---------------- | ---------------------------------------------- | --------------------------------------------- | ----------- | --------- | ----------------------------------------------------------------------------------------- |
| 01          | `frontend-perm-structure-analysis.plan.md`            | 规范审查与结构分析基线                                                 | `frontend/src/views/perm/**`、`frontend/src/api/perm/**`、`router/modules/perm.ts`、`constants/permission.ts` | 审计型文档，不直接产出页面                                           | 无               | 02-14 全部计划的上游依据                       | 无直接交接                                    | 否          | `Drift`   | API 文件数量统计与阶段归属仍带旧状态，尤其把 explain 的完整能力写回了旧阶段               |
| 02          | `frontend-perm-tree-component-abstraction.plan.md`    | `RePermissionTree` 通用树组件                                          | `RoleTree.vue`、`ResourceTree.vue`                                                                            | `components/RePermissionTree/`、角色树 / 资源树组件                  | 01               | 角色 / 资源页复用基础设施                      | 间接支撑角色、资源 mock 展示                  | 否          | `Aligned` | 当前未发现与 README 或模块拆分结构直接冲突                                                |
| 03          | `frontend-perm-completed-modules-improvement.plan.md` | 共享 API、权限码、路由、view/explain 壳层基线                          | 现有 `api/perm/*.ts`、`views/perm/view/index.vue`、`views/perm/explain/index.vue`                             | 共享 API 与页面壳层                                                  | 01               | 04-14 的共享层前置                             | 已要求向 mock 计划输出共享基线                | 否          | `Aligned` | 已明确不抢占完整 view / explain 范围                                                      |
| 04          | `frontend-perm-domain-type-quick.plan.md`             | `/perm/domain`、`/perm/type`、`domainConfig.ts`                        | Domain / DomainConfig / Type / Operation 相关 controller 与现有 API 文件                                      | `views/perm/domain/`、`views/perm/type/`、`api/perm/domainConfig.ts` | README 为 01、03 | 为条件、用户、冲突等模块提供基础数据           | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | 前置条件仍写成旧 phase 口径，且尚未显式把 domain / type / domainConfig 场景交给 mock 计划 |
| 05          | `frontend-perm-condition-module.plan.md`              | 条件 CRUD 与前端模板层                                                 | ConditionController + DTO                                                                                     | `api/perm/condition.ts`、`views/perm/condition/`                     | 01、03           | 为角色授权等场景提供条件能力                   | 已明确输出条件模块 mock 场景                  | 是          | `Aligned` | 当前范围与后端真实字段已对齐，旧 `conditionType` / `status` / `config` 已被排除           |
| 06          | `frontend-perm-user-module.plan.md`                   | 抽象用户 CRUD + sync                                                   | UserController + `UserSyncReq`                                                                                | `api/perm/user.ts`、`views/perm/user/`                               | 01、03           | 为用户角色链路和 explain / view 提供用户基础   | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | 主体契约基本成立，但尚未把 user / sync 示例下游交给 mock 计划                             |
| 07          | `frontend-perm-log-module.plan.md`                    | 变更日志 / 操作日志查询页面                                            | LogQueryController                                                                                            | `api/perm/log.ts`、`views/perm/log/`                                 | 01、03           | 为审计与排查提供日志能力                       | 当前未显式写出 mock 场景交付                  | 否          | `Partial` | 路径锚点正确，但计划尚未承担向 mock 计划输出 change / operation log 场景的职责            |
| 08          | `frontend-perm-explain-module.plan.md`                | 单次权限 explain 页面                                                  | `PermissionViewController#/explain` + `PermissionExplainReq/Resp`                                             | `permissionView.ts` explain 类型、`views/perm/explain/`              | 01、03、13       | 为最终演示提供 explain 页面与可读场景          | 已要求输出 allow / deny / recent changes 场景 | 是          | `Aligned` | 当前计划已收敛到 explain endpoint 可支撑范围，矩阵 / 对比转为未来增强                     |
| 09          | `frontend-perm-conflict-module.plan.md`               | 冲突规则 CRUD + detect                                                 | ConflictRuleController + detect DTO                                                                           | `api/perm/conflictRule.ts`、`views/perm/conflict/`                   | 01、05、06       | 为高阶授权治理提供冲突能力                     | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | 规则级 detect 范围与后端一致，但尚未把命中 / 未命中场景下游交给 mock 计划                 |
| 10          | `frontend-perm-api-mapping-module.plan.md`            | 资源页中的 API Mapping 面板                                            | ResourceApiMappingController                                                                                  | `api/perm/apiMapping.ts`、资源页组件                                 | 01、03           | 为资源管理补全接口映射配置                     | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | 当前范围与后端路径一致，但 mock 交接仍缺失                                                |
| 11          | `frontend-perm-version-module.plan.md`                | query-only 版本查询                                                    | `PermissionVersionController#/query`                                                                          | `api/perm/version.ts`、可选轻量页面                                  | 01、03           | 为系统诊断提供只读版本查询                     | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | query-only 范围清晰，但尚未把 query 场景纳入模块侧交付清单                                |
| 12          | `frontend-perm-system-config-module.plan.md`          | 系统配置 list / detail / save 与前端元数据层                           | SystemConfigController                                                                                        | `api/perm/systemConfig.ts`、`views/perm/system-config/`              | 01、03           | 为系统级配置展示和编辑提供能力                 | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | metadata 边界正确，但缺少向 mock 计划输出只读 / 可编辑场景                                |
| 13          | `frontend-perm-view-improvement.plan.md`              | `effective-roles`、`resource-users`、`resource-tree`、`recent-changes` | PermissionViewController 当前 4 个子视图接口                                                                  | `api/perm/permissionView.ts`、`views/perm/view/`                     | 01、03、06       | 为 explain 和最终展示提供 permission view 基础 | 当前未显式写出 mock 场景交付                  | 是          | `Partial` | 当前 scope 与 controller 对齐，但 mock handoff 和对 explain 的下游关系仍是隐式            |
| 14          | `frontend-perm-api-mock.plan.md`                      | 全量 mock、菜单权限、按钮权限、演示场景                                | `frontend/src/api/perm/`、`mock/login.ts`、`mock/asyncRoutes.ts`                                              | `frontend/mock/perm/*.ts`                                            | 03、04-13        | 最终演示与评审环境                             | 自身即最终收口                                | 是          | `Aligned` | 当前已成为所有模块的终点文档，覆盖矩阵和验收标准清晰                                      |

---

## Phase 协调计划矩阵

| 计划文件                                   | 聚合范围                                   | 上游依赖                                                                                          | 下游模块计划                                                                                                                   | Mock 交接                    | Status    | 审计备注                                                         |
| ------------------------------------------ | ------------------------------------------ | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------- | --------- | ---------------------------------------------------------------- |
| `frontend-perm-phase1-development.plan.md` | condition / user / log                     | `frontend-perm-completed-modules-improvement.plan.md`                                             | `frontend-perm-condition-module.plan.md`、`frontend-perm-user-module.plan.md`、`frontend-perm-log-module.plan.md`              | 已显式要求输出接口与场景清单 | `Aligned` | 当前已收敛为纯协调文档，且承担了 mock handoff                    |
| `frontend-perm-phase2-development.plan.md` | conflict / api-mapping                     | `frontend-perm-phase1-development.plan.md`                                                        | `frontend-perm-conflict-module.plan.md`、`frontend-perm-api-mapping-module.plan.md`                                            | 目前缺失                     | `Partial` | 聚合职责基本正确，但未像 Phase 1 一样把 mock 交接写入协调任务    |
| `frontend-perm-phase3-development.plan.md` | version / system-config / view-improvement | `frontend-perm-phase2-development.plan.md`、`frontend-perm-completed-modules-improvement.plan.md` | `frontend-perm-version-module.plan.md`、`frontend-perm-system-config-module.plan.md`、`frontend-perm-view-improvement.plan.md` | 目前缺失                     | `Partial` | 当前范围与 README 一致，但缺少向最终 mock 计划输出场景清单的职责 |

---

## 关键对齐规则

### Rule 1: README 是唯一执行入口

- 执行顺序、依赖和最终验收口径以 `README.md` 为准。
- 模块计划允许比 README 更细，但不得反向改写 README 已明确的优先级与依赖结构。

### Rule 2: 模块计划必须锚定真实 controller / DTO

- 字段级契约必须以对应 controller / DTO 为真相来源。
- 聚合计划和结构分析文档不得再保留与当前 controller 不一致的请求 / 响应假设。

### Rule 3: mock 交接必须显式化

- 所有进入 `frontend-perm-api-mock.plan.md` 依赖链的模块计划，原则上都应显式输出“接口样例 + happy path + 边界场景”。
- 当前已显式承担 mock handoff 的主要是 `frontend-perm-completed-modules-improvement.plan.md`、`frontend-perm-phase1-development.plan.md`、`frontend-perm-condition-module.plan.md`、`frontend-perm-explain-module.plan.md`。
- 仍建议补齐 Phase 2、Phase 3 以及 domain / user / log / conflict / api-mapping / version / system-config / view 的模块级 handoff。

### Rule 4: future scope 先入 gap tracker 再入计划

- 如果某项能力需要新增 controller、DTO 或跨接口聚合契约，先登记到 `frontend-perm-backend-api-gap-tracker.md`。
- 未进入 gap tracker 的未来增强，不应直接写入当前计划的验收标准。

---

## 当前优先修复清单

| Priority | Target                                     | Why                                               |
| -------- | ------------------------------------------ | ------------------------------------------------- |
| P0       | `frontend-perm-structure-analysis.plan.md` | 仍持有旧 API 数量与旧阶段映射，会继续误导后续文档 |
| P1       | `frontend-perm-domain-type-quick.plan.md`  | 前置条件仍是旧 phase 口径，且缺少 mock handoff    |
| P1       | `frontend-perm-phase2-development.plan.md` | 聚合协调已收敛，但缺少 mock 交接任务              |
| P1       | `frontend-perm-phase3-development.plan.md` | 聚合协调已收敛，但缺少 mock 交接任务              |
