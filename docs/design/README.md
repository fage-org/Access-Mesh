# AccessMesh 设计文档索引

本文档是 `docs/design/` 目录的入口。后续查阅设计时优先从这里进入，避免误用归档文档中的旧接口或旧字段。

## 权威来源

| 主题                  | 权威文档                                                                               |
| --------------------- | -------------------------------------------------------------------------------------- |
| 项目工程规范          | [project-rules.md](project-rules.md)                                                   |
| 微服务整体架构        | [architecture.md](architecture.md)                                                     |
| 权限中心概念模型      | [permission-center/overview.md](permission-center/overview.md)                         |
| 权限中心外部 API 契约 | [permission-center/api-contract.md](permission-center/api-contract.md)                 |
| 权限中心核心调用链路  | [permission-center/core-flows.md](permission-center/core-flows.md)                     |
| 权限中心实现设计      | [permission-center/implementation.md](permission-center/implementation.md)             |
| 权限中心 v3.5 端到端设计 | [permission-center-v3.5-design.md](permission-center-v3.5-design.md)（`status: adopted`）|
| 权限中心 v3.5.1+ 演进方向 | [permission-center-v3.5.1-evolution.md](permission-center-v3.5.1-evolution.md)（`status: evolution`，非约束）|
| 默认组织树与用户生命周期 | [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)               |
| 组织与用户·权限契约    | [org-user-permission-contract.md](org-user-permission-contract.md)                     |
| 跨服务设计              | [cross-service/](cross-service/)                                                       |
| admin-service 与 permission-center 同步 | [cross-service/admin-permission-sync.md](cross-service/admin-permission-sync.md)       |
| admin-service 对前端 API 契约 | [services/admin-service-api-contract.md](services/admin-service-api-contract.md) |
| PostgreSQL 表结构     | [schema/](schema/)                                                                     |

## 推荐阅读顺序

1. 先读 [project-rules.md](project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [architecture.md](architecture.md)，理解 Gateway、admin-service、permission-center、example-service 的边界。
3. 开发权限中心前，按顺序读 [permission-center/overview.md](permission-center/overview.md)、[permission-center/api-contract.md](permission-center/api-contract.md)、[permission-center/core-flows.md](permission-center/core-flows.md)、[permission-center/implementation.md](permission-center/implementation.md)。
4. 开发具体服务时，读取 [services/](services/) 下对应服务设计；涉及组织与用户页 admin 接口契约时, 读取 [services/admin-service-api-contract.md](services/admin-service-api-contract.md)。
5. 涉及组织与用户、多组织树、用户生命周期和成员关系时，先读 [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)，再读 [org-user-permission-contract.md](org-user-permission-contract.md)。
6. 涉及 admin-service 与 permission-center 同步模块时，读取 [cross-service/admin-permission-sync.md](cross-service/admin-permission-sync.md)。
7. 需要跟进执行计划、API 核对清单或阶段路线图时，读取 [../plans/](../plans/)。
8. 涉及表字段、索引、约束时，以 [schema/](schema/) 下 SQL 为准。

## 目录说明

| 路径                 | 说明                                             |
| -------------------- | ------------------------------------------------ |
| `permission-center/` | 权限中心的概念、API、流程、实现设计              |
| 根目录 `*-design.md` / `*-evolution.md` | 端到端设计契约（`status: adopted`）与演进方向（`status: evolution`，非约束）|
| `cross-service/`     | 跨越多个服务边界、描述服务之间职责/契约/数据流/一致性约束的当前有效设计 |
| `services/`          | Gateway、admin-service、example-service 设计     |
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
| `../archive/2026-06-14/` | 同步模块重构执行计划归档；长期有效设计已沉淀到 `cross-service/admin-permission-sync.md` |
| `../archive/2026-06-05/` | 编码与创作风格分析报告，可操作知识已合并到项目规范；报告保留作历史追溯 |
| `../archive/2026-06-03/` | Round 1-7 模块与接口核对诊断记录，问题已修复 |
| `../archive/2026-05-30/` | 统一权限查询引擎重构文档（2 篇 PlantUML）、重构实施完成记录、前端集成计划（前端已删除）、Service 层重构审查（Phase 1-5 完成）、跨模块影响分析 |
| `../archive/2026-05-24/` | DDD 重构完成后归档：PermQueryEngine 分析/设计文档（3 篇）、过期 skills（细粒度权限检查）、未落地的服务权限设计、已完成的 PRPs plan 文件（2 篇） |
| `../archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单                                                                                                              |

`../archive/` 下文档可能包含旧接口、旧字段或已废弃设计，例如 `includeDataScope`、`query-data-scopes`、`AuthorizationService`、`ConfigManageServiceImpl` 等。实现时不要直接引用归档文档；如归档内容与权威文档冲突，以本页”权威来源”列出的文档为准。
