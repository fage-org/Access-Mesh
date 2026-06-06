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
| 项目诊断与完善计划    | [improvement-plan.md](improvement-plan.md)                                             |
| 组织与用户·权限契约    | [org-user-permission-contract.md](org-user-permission-contract.md)                     |
| 组织与用户·实现计划    | [org-user-page-impl-plan.md](org-user-page-impl-plan.md)                               |
| API 核对清单            | [api-gap-analysis.md](api-gap-analysis.md)                                             |
| PostgreSQL 表结构     | [schema/](schema/)                                                                     |

## 推荐阅读顺序

1. 先读 [project-rules.md](project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [architecture.md](architecture.md)，理解 Gateway、admin-service、permission-center、example-service 的边界。
3. 开发权限中心前，按顺序读 [permission-center/overview.md](permission-center/overview.md)、[permission-center/api-contract.md](permission-center/api-contract.md)、[permission-center/core-flows.md](permission-center/core-flows.md)、[permission-center/implementation.md](permission-center/implementation.md)。
4. 开发具体服务时，读取 [services/](services/) 下对应服务设计。
5. 涉及表字段、索引、约束时，以 [schema/](schema/) 下 SQL 为准。

## 目录说明

| 路径                 | 说明                                             |
| -------------------- | ------------------------------------------------ |
| `permission-center/` | 权限中心的概念、API、流程、实现设计              |
| `services/`          | Gateway、admin-service、example-service 设计     |
| `schema/`            | 当前有效 PostgreSQL schema                       |
| `../archive/`        | 旧版长文档和讨论清单，仅用于追溯，不作为实现依据 |

Claude 按需技能位于 `.claude/skills/`。

## 归档记录

| 归档批次              | 说明                                                                                                                                            |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `../archive/2026-06-05/` | 编码与创作风格分析报告，可操作知识已合并到项目规范；报告保留作历史追溯 |
| `../archive/2026-06-03/` | Round 1-7 模块与接口核对诊断记录，问题已修复 |
| `../archive/2026-05-30/` | 统一权限查询引擎重构文档（2 篇 PlantUML）、重构实施完成记录、前端集成计划（前端已删除）、Service 层重构审查（Phase 1-5 完成）、跨模块影响分析 |
| `../archive/2026-05-24/` | DDD 重构完成后归档：PermQueryEngine 分析/设计文档（3 篇）、过期 skills（细粒度权限检查）、未落地的服务权限设计、已完成的 PRPs plan 文件（2 篇） |
| `../archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单                                                                                                              |

`../archive/` 下文档可能包含旧接口、旧字段或已废弃设计，例如 `includeDataScope`、`query-data-scopes`、`AuthorizationService`、`ConfigManageServiceImpl` 等。实现时不要直接引用归档文档；如归档内容与权威文档冲突，以本页”权威来源”列出的文档为准。
