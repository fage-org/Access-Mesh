# AccessMesh 文档索引

## 目录结构

```
docs/
├── README.md                          # 本文档
├── design/                            # 设计文档（权威来源）
│   ├── README.md                      # 设计文档索引
│   ├── project-rules.md               # 项目工程规范
│   ├── architecture.md                # 微服务整体架构
│   ├── access-service-architecture.md # access-service 目标架构与归并约束
│   ├── default-org-tree-user-lifecycle.md # 默认组织树与用户生命周期
│   ├── cross-service/                 # 跨服务设计
│   │   └── README.md
│   ├── permission-center/             # 权限中心设计
│   │   ├── overview.md                # 概念模型
│   │   ├── api-contract.md            # API契约
│   │   ├── core-flows.md              # 核心调用链路
│   │   ├── implementation.md          # 实现设计
│   ├── access-service-rebuild-runbook.md  # 空库重建 runbook（含统一主体 ID 后 Redis 清理）
│   ├── services/                      # 服务设计
│   │   ├── admin-service-api-contract.md  # admin 域对前端 API 契约（access-service 管理域承载）
│   │   ├── gateway.md
│   │   └── example-service.md
│   ├── frontend/                      # 前端页面级设计（随 T-FE 任务产出回写）
│   │   └── README.md
│   └── schema/                        # 数据库表结构（access-service.sql 为唯一权威）
│       ├── access-service.sql
│       └── example-service.sql
├── plans/                             # 执行计划（编排层，任务清单仅引用 ID）
│   ├── README.md
│   └── <plan>.md
├── tasks/                             # 任务（原子执行单元，看板为唯一权威清单）
│   └── README.md
└── archive/                           # 归档文档（仅追溯，不作为实现依据）
    ├── 2026-08-27/
    ├── 2026-08-22/
    ├── 2026-08-15/
    ├── 2026-07-26/
    ├── 2026-07-12/
    ├── 2026-06-28/
    ├── 2026-06-21/
    ├── 2026-06-20/
    ├── 2026-06-17/
    ├── 2026-06-14/
    ├── 2026-06-05/
    ├── 2026-06-03/
    ├── 2026-05-30/
    ├── 2026-05-24/
    └── 2026-04-28/
```

## 权威来源

| 主题                 | 权威文档                                                                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 项目工程规范         | [design/project-rules.md](design/project-rules.md)                                                                                   |
| 产品定位与能力叙事口径 | [design/README.md](design/README.md) §产品定位与能力叙事三档口径（开源通用 IAM，2026-08-28 定案）                                 |
| 微服务整体架构       | [design/architecture.md](design/architecture.md)                                                                                     |
| access-service 目标架构 | [design/access-service-architecture.md](design/access-service-architecture.md)（归并拓扑、事务、数据、缓存与安全的权威约束） |
| 空库重建 runbook      | [design/access-service-rebuild-runbook.md](design/access-service-rebuild-runbook.md)（DDL 重建 + 种子 + Redis 清理 + 主体链验证） |
| 权限中心概念模型     | [design/permission-center/overview.md](design/permission-center/overview.md)                                                         |
| 权限中心 API 契约    | [design/permission-center/api-contract.md](design/permission-center/api-contract.md)                                                 |
| 权限中心核心调用链路 | [design/permission-center/core-flows.md](design/permission-center/core-flows.md)                                                     |
| 权限中心实现设计     | [design/permission-center/implementation.md](design/permission-center/implementation.md)                                             |
| 默认组织树与用户生命周期 | [design/default-org-tree-user-lifecycle.md](design/default-org-tree-user-lifecycle.md)                                                |
| admin 域对前端 API 契约 | [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md)（access-service 管理域承载） |
| PostgreSQL 表结构    | [design/schema/access-service.sql](design/schema/access-service.sql)（唯一权威 DDL）                                                 |

## 执行计划

| 主题 | 文档 |
|------|------|
| ~~access-service 归并计划~~ | （已归档 2026-08-22）T-ACCESS-001~012 全部 done，见 [archive/2026-08-22/](archive/2026-08-22/) |
| ~~access-service 归并后续强化~~ | （已归档 2026-08-27）T-ACCESS-013~015 全 done，CI 准入前置由 T-ACCESS-017 关闭，见 [archive/2026-08-27/](archive/2026-08-27/) |
| ~~产品垂直切片与试点加固~~ | （已归档 2026-08-27）18 项任务全 done（里程碑 A E2E 八步全绿 + 里程碑 B 加固与收口），见 [archive/2026-08-27/](archive/2026-08-27/) |
| ~~项目诊断与完善计划~~ | （已归档 2026-08-27）诊断与四阶段拆分使命完成（Phase 2-4 由独立 plan 承载），完成度口径截至 2026-06；痛点 #3/#4 暂缓决策溯源仍被 T-PERM-035/036 引用，见 [archive/2026-08-27/](archive/2026-08-27/) |
| ~~组织与用户融合页实现计划~~ | （已归档 2026-06-21）P0/P1/P2 三阶段全 100%，见 [archive/2026-06-21/](archive/2026-06-21/)；权威契约以 [design/org-user-permission-contract.md](design/org-user-permission-contract.md) v1.2 + [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md) v1.0 为准 |
| ~~API 核对清单~~ | （已归档 2026-06-21）16 个 🔧 接口已实现，见 [archive/2026-06-21/](archive/2026-06-21/)；契约权威以 [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md) v1.0 为准 |

## 推荐阅读顺序

1. 先读 [design/project-rules.md](design/project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [design/architecture.md](design/architecture.md) 理解当前仓库基线；新增后端与服务归并读取 [design/access-service-architecture.md](design/access-service-architecture.md)，并以其目标约束为准。
3. 开发权限中心前，按顺序读 [design/permission-center/overview.md](design/permission-center/overview.md)、[design/permission-center/api-contract.md](design/permission-center/api-contract.md)、[design/permission-center/core-flows.md](design/permission-center/core-flows.md)、[design/permission-center/implementation.md](design/permission-center/implementation.md)。了解重构历史可读 [archive/2026-05-30/service-layer-review.md](archive/2026-05-30/service-layer-review.md)。
4. 开发具体服务时，读取 [design/services/](design/services/) 下对应服务设计；涉及组织与用户页 admin 接口契约时, 读取 [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md)。
5. 涉及组织与用户、多组织树、用户生命周期和成员关系时，先读 [design/default-org-tree-user-lifecycle.md](design/default-org-tree-user-lifecycle.md)。
6. 原 admin→permission 异步同步设计已被取代；实现归并读取 [design/access-service-architecture.md](design/access-service-architecture.md) §4，历史追溯才读取 [archive/2026-08-15/admin-permission-sync.md](archive/2026-08-15/admin-permission-sync.md)。
7. 跟进仍在推进的任务时，读取 [plans/README.md](plans/README.md)。
8. 涉及表字段、索引、约束时，以 [design/schema/access-service.sql](design/schema/access-service.sql)（唯一权威）为准。

## 文档治理规范

### 权威文档与归档文档

| 类型 | 目录 | 用途 |
|------|------|------|
| **权威文档** | `docs/design/` | 当前有效的设计、规范、API 契约，是实现的唯一依据 |
| **执行计划** | `docs/plans/` | 编排层：目标/非目标/准入/归档 + 任务清单引用；不作为契约来源 |
| **任务** | `docs/tasks/` | 原子执行单元；看板为唯一权威任务清单；任务 done 前须回写其 design_refs |
| **归档文档** | `docs/archive/` | 历史追溯，不作为实现依据；可能包含旧接口、旧字段或已废弃设计 |

如归档内容与权威文档冲突，以本页"权威来源"列出的文档为准。

### 归档时机

| 归档场景 | 示例 |
|----------|------|
| **阶段性任务完成** | Phase 重构完成、模块核对完成，过程文档归档 |
| **设计文档已合并** | 设计过程中的中间文档合并到权威文档后归档 |
| **过渡文档** | 重构前的旧版长文档、讨论清单、分析报告 |
| **执行计划完成或被替代** | `docs/plans/` 中计划完成后归档，并把稳定结论沉淀到 `docs/design/` |

### 删除 vs 归档

| 操作 | 适用场景 | 说明 |
|------|----------|------|
| **归档** | 阶段性完成的过程文档、已合并的设计过程、有历史追溯价值的分析报告 | 保留历史追溯，存入 `archive/YYYY-MM-DD/` |
| **删除** | 不规范命名的临时文件、功能已完全废弃且无历史价值的文件 | 不归档，直接删除 |

### 归档规范

- 归档目录命名：`docs/archive/YYYY-MM-DD/`
- 每个归档批次必须包含 `README.md`，说明归档原因和内容定位
- 归档后需更新本页"归档记录"表

## 归档记录

| 归档批次              | 说明                                                                                                   | 入口                                                         |
| --------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| `archive/2026-08-27/` | product-vertical-slice 计划收口归档（T-ACCESS-026）：产品垂直切片与试点加固计划（18 项全 done，里程碑 A+B 达成）+ access-post-merge-plan（T-ACCESS-013~015 全 done，CI 准入前置关闭）+ improvement-plan 项目级路线图归档（诊断与四阶段拆分使命完成，完成度口径截至 2026-06，痛点 #3/#4 暂缓溯源仍有效）。外部 Docker 验证证据与 CI 口径见任务卡 T-ACCESS-026。权威入口：`design/access-service-architecture.md` | [archive/2026-08-27/README.md](archive/2026-08-27/README.md) |
| `archive/2026-08-22/` | access-service 归并收口归档（T-ACCESS-012）：四份旧 DDL、原 admin-service 服务设计（superseded）、归并主计划（T-ACCESS-001~012 全部完成）。权威入口：`design/access-service-architecture.md` + `design/schema/access-service.sql` + `design/services/admin-service-api-contract.md` |
| `archive/2026-07-12/` | 前端 Phase 1 归档：13 页 T-FE 任务（T-FE-001~014）全 done，API 核对清单产出（🔧❌ 登记 T-PERM-022~034 归 Phase 2），组件池确认（派生 T-FE-024 归 Phase 4），设计回写完成（13 份全 adopted）。build/lint/typecheck + mvn test 均通过。 | [archive/2026-07-12/README.md](archive/2026-07-12/README.md) |
| `archive/2026-06-28/` | 工作单 A/B/C 归档：权限缓存失效改造（T-PERM-001~008·017·018）、scopeMode 协议迁移（T-PERM-009~015）、Gateway 失联兜底（T-GW-001~006）均已完成。稳定结论已沉淀至 v3.5-design §7.2 / api-contract scopeMode / gateway.md 失联兜底模式与快照失效标记。 | [archive/2026-06-28/README.md](archive/2026-06-28/README.md) |
| `archive/2026-06-21/` | API 核对清单 + 「组织与用户」融合页实现计划归档：16 个 🔧 接口经代码核实已由 admin-service 实现，与 org-user-page P1=100% 一致；org-user-page P0/P1/P2 三阶段全 100%，联动验收（T-ADMIN-001~019）已完成。权威契约以 `design/org-user-permission-contract.md` v1.2 + `design/services/admin-service-api-contract.md` v1.0 为准。 | [archive/2026-06-21/README.md](archive/2026-06-21/README.md) |
| `archive/2026-06-20/` | 用户角色代理修复归档（M1-M13+S1-S3 主线 + P1-1/P1-2/P2-1/P2-2 补充批次，均验收 + 设计回写完成）。当时结论沉淀至 admin-api-contract / org-user-permission-contract / api-contract / admin-permission-sync；同步设计现仅供历史追溯。 | [archive/2026-06-20/README.md](archive/2026-06-20/README.md) |
| `plans/archive/2026-06/` | v3.0~v3.3 权限中心设计演进历史快照（双轨 AND + sys_menu.operations 元数据化等），已被 v3.5 取代。 | [plans/archive/2026-06/README.md](plans/archive/2026-06/README.md) |
| `archive/2026-06-17/` | AccessMesh 设计评审记录（2026-06-17）。评审结论已沉淀至 `design/`，工作单 A-C 派生为 P0 计划，D/E/F 暂缓；D1-D10 文档数字勘误待 F-1.a 自动化根治。 | [archive/2026-06-17/README.md](archive/2026-06-17/README.md) |
| `archive/2026-06-14/` | 同步模块重构执行计划归档；当时的稳定设计沉淀到 `design/cross-service/admin-permission-sync.md`，该设计已于 2026-08-10 被 access-service 单库强事务目标架构取代。 | [archive/2026-06-14/README.md](archive/2026-06-14/README.md) |
| `archive/2026-06-05/` | 编码与创作风格分析报告。可操作知识已合并到项目规范（项目规范§17、权限中心规范§19、文档治理规范），报告仅作历史追溯。 | [archive/2026-06-05/README.md](archive/2026-06-05/README.md) |
| `archive/2026-06-03/` | 模块与接口逐轮核对及问题诊断。记录 Round 1 到 Round 7 的现状核对、目标模型、问题诊断表与建议实施顺序。 | [archive/2026-06-03/README.md](archive/2026-06-03/README.md) |
| `archive/2026-05-30/` | 前端集成方案、Service 层重构审查、权限中心重构影响分析等阶段性文档。                                   | [archive/2026-05-30/README.md](archive/2026-05-30/README.md) |
| `archive/2026-05-24/` | PermQueryEngine 设计与重构阶段归档。                                                                   | [archive/2026-05-24/README.md](archive/2026-05-24/README.md) |
| `archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单。                                                                     | [archive/2026-04-28/README.md](archive/2026-04-28/README.md) |
