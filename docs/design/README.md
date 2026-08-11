# AccessMesh 设计文档索引

本文档是 `docs/design/` 目录的入口。后续查阅设计时优先从这里进入，避免误用归档文档中的旧接口或旧字段。

## 权威来源

| 主题                  | 权威文档                                                                               |
| --------------------- | -------------------------------------------------------------------------------------- |
| 项目工程规范          | [project-rules.md](project-rules.md)                                                   |
| 微服务整体架构        | [architecture.md](architecture.md)                                                     |
| access-service 目标架构与归并约束 | [access-service-architecture.md](access-service-architecture.md)（`status: adopted`；归并拓扑、事务、数据、缓存和安全冲突时优先） |
| 权限中心概念模型      | [permission-center/overview.md](permission-center/overview.md)                         |
| 权限中心外部 API 契约 | [permission-center/api-contract.md](permission-center/api-contract.md)                 |
| 权限中心核心调用链路  | [permission-center/core-flows.md](permission-center/core-flows.md)                     |
| 权限中心实现设计      | [permission-center/implementation.md](permission-center/implementation.md)             |
| 权限中心 v3.5 端到端设计 | [permission-center-v3.5-design.md](permission-center-v3.5-design.md)（`status: adopted`）|
| 权限中心 v3.5.1+ 演进方向 | [permission-center-v3.5.1-evolution.md](permission-center-v3.5.1-evolution.md)（`status: evolution`，非约束）|
| 默认组织树与用户生命周期 | [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)               |
| 组织与用户·权限契约    | [org-user-permission-contract.md](org-user-permission-contract.md)                     |
| 跨服务设计              | [cross-service/](cross-service/)                                                       |
| admin-service 对前端 API 契约 | [services/admin-service-api-contract.md](services/admin-service-api-contract.md) |
| 前端页面级设计 | [frontend/](frontend/)（UI 设计，随 T-FE 任务产出回写） |
| PostgreSQL 表结构     | [schema/](schema/)                                                                     |

## 推荐阅读顺序

1. 先读 [project-rules.md](project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [architecture.md](architecture.md) 理解当前仓库基线；涉及服务归并或新增后端代码时，继续读 [access-service-architecture.md](access-service-architecture.md)，并以其目标拓扑和边界为准。
3. 开发权限能力前，按顺序读 [permission-center/overview.md](permission-center/overview.md)、[permission-center/api-contract.md](permission-center/api-contract.md)、[permission-center/core-flows.md](permission-center/core-flows.md)、[permission-center/implementation.md](permission-center/implementation.md)。
4. 开发具体服务时，读取 [services/](services/) 下对应服务设计；涉及组织与用户页 admin 接口契约时, 读取 [services/admin-service-api-contract.md](services/admin-service-api-contract.md)。
5. 涉及组织与用户、多组织树、用户生命周期和成员关系时，先读 [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)，再读 [org-user-permission-contract.md](org-user-permission-contract.md)。
6. 原 admin→permission 异步同步设计已被取代；归并实现读取 [access-service-architecture.md](access-service-architecture.md) §4，历史追溯才读取 [cross-service/admin-permission-sync.md](cross-service/admin-permission-sync.md)。
7. 需要跟进执行计划、API 核对清单或阶段路线图时，读取 [../plans/](../plans/)。
8. 涉及表字段、索引、约束时，以 [schema/](schema/) 下当前有效 SQL 为准；最终 `access-service.sql` 由 T-ACCESS-002 产出。

## 目录说明

| 路径                 | 说明                                             |
| -------------------- | ------------------------------------------------ |
| `permission-center/` | 权限中心的概念、API、流程、实现设计              |
| 根目录 `*-design.md` / `*-evolution.md` | 端到端设计契约（`status: adopted`）与演进方向（`status: evolution`，非约束）|
| `cross-service/`     | 跨服务设计及仍需保留原位的 superseded 追溯文档；当前归并约束见根目录 `access-service-architecture.md` |
| `services/`          | Gateway、admin-service、example-service 设计     |
| `frontend/`          | 前端页面级设计（布局/字段/交互/权限接线），随 T-FE 任务产出回写 |
| `schema/`            | 当前有效 PostgreSQL schema                       |
| `../plans/`          | 编排层计划：目标/非目标/准入 + 任务清单引用，不作为契约来源 |
| `../tasks/`          | 原子任务看板（唯一权威任务清单）                 |
| `../archive/`        | 旧版长文档和讨论清单，仅用于追溯，不作为实现依据 |

### Frontmatter 状态约定

每个设计文件头部含 `doc_type: design` + `status` 字段，取值：

| status | 语义 | 是否约束实现 |
|---|---|---|
| `adopted` | 当前权威设计 | 是 |
| `evolution` | 演进方向，非约束 | 否 |
| `draft` | 探索讨论稿，未定 | 否 |
| `superseded` | 被新设计取代，保留追溯（含 `superseded_by`）| 否 |
| `archived` | 历史归档，移入 `../archive/` | 否 |

设计状态变更触发任务依赖与回写核对，见 `.claude/skills/design-plan-task-lifecycle/SKILL.md`。

Claude 按需技能位于 `.claude/skills/`。

## 归档记录

| 归档批次              | 说明                                                                                                                                            |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `../archive/2026-06-28/` | 工作单 A/B/C 执行计划归档：权限缓存失效改造、scopeMode 协议迁移、Gateway 失联兜底全部完成。稳定结论已沉淀至本目录 v3.5-design §7.2 / api-contract scopeMode / gateway.md |
| `../archive/2026-06-21/` | API 核对清单 + 「组织与用户」融合页实现计划归档：16 个 🔧 接口已由 admin-service 实现；org-user-page P0/P1/P2 全 100%。契约权威以 `org-user-permission-contract.md` v1.2 + `services/admin-service-api-contract.md` v1.0 为准 |
| `../archive/2026-06-14/` | 同步模块重构执行计划归档；当时的稳定设计沉淀到 `cross-service/admin-permission-sync.md`，该设计已于 2026-08-10 被 access-service 单库强事务目标架构取代 |
| `../archive/2026-06-05/` | 编码与创作风格分析报告，可操作知识已合并到项目规范；报告保留作历史追溯 |
| `../archive/2026-06-03/` | Round 1-7 模块与接口核对诊断记录，问题已修复 |
| `../archive/2026-05-30/` | 统一权限查询引擎重构文档（2 篇 PlantUML）、重构实施完成记录、前端集成计划（前端已删除）、Service 层重构审查（Phase 1-5 完成）、跨模块影响分析 |
| `../archive/2026-05-24/` | DDD 重构完成后归档：PermQueryEngine 分析/设计文档（3 篇）、过期 skills（细粒度权限检查）、未落地的服务权限设计、已完成的 PRPs plan 文件（2 篇） |
| `../archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单                                                                                                              |

`../archive/` 下文档可能包含旧接口、旧字段或已废弃设计，例如 `includeDataScope`、`query-data-scopes`、`AuthorizationService`、`ConfigManageServiceImpl` 等。实现时不要直接引用归档文档；如归档内容与权威文档冲突，以本页”权威来源”列出的文档为准。
