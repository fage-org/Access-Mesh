# AccessMesh 设计文档索引

本文档是 `docs/design/` 目录的入口。后续查阅设计时优先从这里进入，避免误用归档文档中的旧接口或旧字段。

## 权威来源

| 主题                  | 权威文档                                                                               |
| --------------------- | -------------------------------------------------------------------------------------- |
| 项目工程规范          | [project-rules.md](project-rules.md)                                                   |
| 微服务整体架构        | [architecture.md](architecture.md)                                                     |
| access-service 目标架构与归并约束 | [access-service-architecture.md](access-service-architecture.md)（`status: adopted`；归并拓扑、事务、数据、缓存和安全冲突时优先） |
| access-service 能力包融合目标结构 | [access-service-capability-structure.md](access-service-capability-structure.md)（`status: adopted`，2026-09-13 T-ACCESS-032 完成 §8 归属清单与边界断言定稿、十项裁决登记 decision-registry 同日行；结构迁移已由 T-ACCESS-033 于 2026-09-13 完成，代码即 17 顶层包能力包结构） |
| access-service API 契约总册 | [access-service-api-contract.md](access-service-api-contract.md)（`status: adopted`，2026-09-13 T-ACCESS-040 两册合一——管理面家族（T-ACCESS-040 时称「管理域家族」，裸路径族）与 perm 家族同册分列、按能力分章；原两契约册转 superseded 留原位，锚点对照见总册附录 C） |
| 引擎子系统概念模型      | [engine/overview.md](engine/overview.md)                                             |
| 引擎子系统核心调用链路  | [engine/core-flows.md](engine/core-flows.md)                                         |
| 引擎子系统实现设计      | [engine/implementation.md](engine/implementation.md)                                 |
| 权限查询统一引擎（演进终态） | 已 `status: superseded`（2026-09-10 随 permission-query-unification 计划归档转正，四任务全 done；实现以 [engine/implementation.md](engine/implementation.md) §3 为唯一权威，原文件留 permission-center 目录仅存定案过程追溯、待后续归档） |
| 权限中心 v3.5 端到端设计 | [permission-center-v3.5-design.md](permission-center-v3.5-design.md)（`status: adopted`）|
| 权限中心 v3.5.1+ 演进方向 | [permission-center-v3.5.1-evolution.md](permission-center-v3.5.1-evolution.md)（`status: evolution`，非约束）|
| 默认组织树与用户生命周期 | [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)               |
| 组织与用户·权限契约    | [org-user-permission-contract.md](org-user-permission-contract.md)                     |
| 跨服务设计              | [cross-service/](cross-service/)                                                       |
| 扩展指南（接入与二开全景） | [extension-guide.md](extension-guide.md)（T-FE-023 产出，2026-09-12 定稿 adopted——双通道外评两轮处置收口；场景驱动的接入方导引层，契约细节以 [access-service-api-contract.md](access-service-api-contract.md) 等权威文档为准） |
| ~~admin 域对前端 API 契约~~ | 已 `status: superseded`（2026-09-13 T-ACCESS-040 并入契约总册；原文件在 services 目录保留原位作历史锚点，锚点对照见总册附录 C） |
| 前端页面级设计 | [frontend/](frontend/)（UI 设计，随 T-FE 任务产出回写） |
| PostgreSQL 表结构     | [schema/access-service.sql](schema/access-service.sql)（唯一权威 DDL）；[schema/example-service.sql](schema/example-service.sql)（演示库） |

## 推荐阅读顺序

1. 先读 [project-rules.md](project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [architecture.md](architecture.md) 理解当前仓库基线；涉及服务归并或新增后端代码时，继续读 [access-service-architecture.md](access-service-architecture.md)，并以其目标拓扑和边界为准。
3. 开发权限能力前，先读契约总册 [access-service-api-contract.md](access-service-api-contract.md)，再按顺序读 [engine/overview.md](engine/overview.md)、[engine/core-flows.md](engine/core-flows.md)、[engine/implementation.md](engine/implementation.md)。
4. 开发具体服务时，读取 [services/](services/) 下对应服务设计；涉及组织与用户页 admin 接口契约时，读取契约总册对应用户/组织能力章（§7/§8）。
5. 涉及组织与用户、多组织树、用户生命周期和成员关系时，先读 [default-org-tree-user-lifecycle.md](default-org-tree-user-lifecycle.md)，再读 [org-user-permission-contract.md](org-user-permission-contract.md)。
6. 原 admin→permission 异步同步设计已被取代；归并实现读取 [access-service-architecture.md](access-service-architecture.md) §4，历史追溯才读取 [cross-service/admin-permission-sync.md](../archive/2026-08-15/admin-permission-sync.md)。
7. 需要跟进执行计划、API 核对清单或阶段路线图时，读取 [../plans/](../plans/)。
8. 涉及表字段、索引、约束时，以 [schema/access-service.sql](schema/access-service.sql)（唯一权威）为准；[schema/example-service.sql](schema/example-service.sql) 为 example 演示库。旧 admin/perm DDL 已归档至 `../archive/2026-08-22/schema/`。

## 产品定位与能力叙事三档口径

**产品定位（2026-08-28 定案）：开源通用 IAM** —— 通用多租户访问控制平台，面向任何团队/组织提供身份与访问管理能力；多租户底座、SDK 接入、类型体系为正式能力方向。

对外与设计文档中的能力声明统一按三档归位，**未实现能力不得以现在时态表述**：

| 档位 | 语义 | 常用等价标注 |
|---|---|---|
| 当前可用 | 已实现且有测试/验收证据 | 已实现、已交付 |
| 已规划 | 已立项任务承载（任务卡/计划可追溯） | 规划中、未交付（注明对应任务） |
| 仅演进方向 | 未排期设想，实现前须重新立项 | 演进方向、future |

适用范围：根 `README.md` 特性表、`architecture.md` 能力/模块表、`engine/*`、契约总册与 `services/*` 的能力概述章节。各文档标注用词可与本表等价标注互换，但必须能唯一归入一档。

## 目录说明

| 路径                 | 说明                                             |
| -------------------- | ------------------------------------------------ |
| `access-service-api-contract.md` | access-service API 契约总册（单命名空间 `/api/access/**`、按能力分章，T-ACCESS-040/042） |
| `engine/`            | 引擎子系统设计：概念模型、核心调用链路、实现设计（T-ACCESS-040 迁位） |
| `permission-center` 目录 | superseded 残件（旧 API 契约册与 query-engine-unification.md——目录已随 T-ACCESS-040 解散，留原位作历史锚点、物理归档随后续批次） |
| 根目录 `*-design.md` / `*-evolution.md` | 端到端设计契约（`status: adopted`）与演进方向（`status: evolution`，非约束）|
| `cross-service/`     | 跨服务设计及仍需保留原位的 superseded 追溯文档；当前归并约束见根目录 `access-service-architecture.md` |
| `services/`          | Gateway、example-service 设计；原 admin 域 API 契约册已随 T-ACCESS-040 并入契约总册转 superseded 留原位；原 `admin-service.md` 服务设计已 superseded 归档（`../archive/2026-08-22/`） |
| `frontend/`          | 前端页面级设计（布局/字段/交互/权限接线），随 T-FE 任务产出回写 |
| `schema/`            | PostgreSQL schema：`access-service.sql`（唯一权威）+ `example-service.sql`；旧 admin/perm DDL 已归档 |
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
| `../archive/2026-09-14/` | access-service 能力包融合计划归档（T-ACCESS-041 终态）：十任务全 done 后用户确认定稿归档。结构契约沉淀至本页 `access-service-capability-structure.md`（adopted，17 顶层包归属清单与边界断言），错误码/缓存目录/操作码单册与契约总册、engine 三册、permission-coding-standards 规则均为现行权威；遗留登记见 pending-problems（Q-006 滚动发布边界 / Q-007 / Q-008；Q-001/Q-009 已收敛——后者 30 边白名单退役为绝对禁断，T-ACCESS-043~046） |
| `../archive/2026-09-07/` | 计划治理收尾归档批次：product-positioning-landing-plan（三档叙事口径沉淀至本页「能力三档」入口）+ frontend-phase3-plan（九联调任务收口；归档门禁统一冒烟 21/21 通过）。 |
| `../archive/2026-08-27/` | product-vertical-slice 计划收口归档（T-ACCESS-026）：产品垂直切片与试点加固计划（18 项全 done，里程碑 A+B 达成）+ access-post-merge-plan（T-ACCESS-013~015 全 done）+ improvement-plan 项目级路线图归档（诊断与四阶段拆分使命完成，完成度口径截至 2026-06，痛点 #3/#4 暂缓溯源仍有效）。权威入口：`access-service-architecture.md` |
| `../archive/2026-08-22/` | access-service 归并收口归档（T-ACCESS-012）：四份旧 DDL（admin-service.sql / permission-center.sql / seed-admin-operations.sql / seed-perm-operations.sql）、原 admin-service 服务设计（admin-service.md，superseded）、归并主计划 access-service-merge-plan.md（T-ACCESS-001~012 全部完成）。权威入口：`access-service-architecture.md` + `schema/access-service.sql` + `access-service-api-contract.md`（契约总册） |
| `../archive/2026-06-28/` | 工作单 A/B/C 执行计划归档：权限缓存失效改造、scopeMode 协议迁移、Gateway 失联兜底全部完成。稳定结论已沉淀至本目录 v3.5-design §7.2 / 契约总册 scopeMode 协议 / gateway.md |
| `../archive/2026-06-21/` | API 核对清单 + 「组织与用户」融合页实现计划归档：16 个 🔧 接口已由 admin-service 实现；org-user-page P0/P1/P2 全 100%。契约权威以 `org-user-permission-contract.md` v1.2 + 契约总册 `access-service-api-contract.md`（原 admin 册 v1.0 已并入）为准 |
| `../archive/2026-06-14/` | 同步模块重构执行计划归档；当时的稳定设计沉淀到 `cross-service/admin-permission-sync.md`，该设计已于 2026-08-10 被 access-service 单库强事务目标架构取代 |
| `../archive/2026-06-05/` | 编码与创作风格分析报告，可操作知识已合并到项目规范；报告保留作历史追溯 |
| `../archive/2026-06-03/` | Round 1-7 模块与接口核对诊断记录，问题已修复 |
| `../archive/2026-05-30/` | 统一权限查询引擎重构文档（2 篇 PlantUML）、重构实施完成记录、前端集成计划（前端已删除）、Service 层重构审查（Phase 1-5 完成）、跨模块影响分析 |
| `../archive/2026-05-24/` | DDD 重构完成后归档：PermQueryEngine 分析/设计文档（3 篇）、过期 skills（细粒度权限检查）、未落地的服务权限设计、已完成的 PRPs plan 文件（2 篇） |
| `../archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单                                                                                                              |

`../archive/` 下文档可能包含旧接口、旧字段或已废弃设计，例如 `includeDataScope`、`query-data-scopes`、`AuthorizationService`、`ConfigManageServiceImpl` 等。实现时不要直接引用归档文档；如归档内容与权威文档冲突，以本页”权威来源”列出的文档为准。
