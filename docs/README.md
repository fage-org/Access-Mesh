# AccessMesh 文档索引

## 目录结构

```
docs/
├── README.md                          # 本文档
├── design/                            # 设计文档（权威来源）
│   ├── README.md                      # 设计文档索引
│   ├── project-rules.md               # 项目工程规范
│   ├── architecture.md                # 微服务整体架构
│   ├── default-org-tree-user-lifecycle.md # 默认组织树与用户生命周期
│   ├── cross-service/                 # 跨服务设计
│   │   ├── README.md
│   │   └── admin-permission-sync.md   # admin-service 与 permission-center 同步设计
│   ├── permission-center/             # 权限中心设计
│   │   ├── overview.md                # 概念模型
│   │   ├── api-contract.md            # API契约
│   │   ├── core-flows.md              # 核心调用链路
│   │   ├── implementation.md          # 实现设计
│   ├── services/                      # 服务设计
│   │   ├── admin-service.md
│   │   ├── gateway.md
│   │   └── example-service.md
│   └── schema/                        # 数据库表结构
│       ├── admin-service.sql
│       ├── example-service.sql
│       └── permission-center.sql
├── plans/                             # 执行计划与核对清单（进行中，非权威设计）
│   ├── README.md
│   ├── improvement-plan.md
│   ├── org-user-page-impl-plan.md
│   └── api-gap-analysis.md
└── archive/                           # 归档文档（仅追溯，不作为实现依据）
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
| 微服务整体架构       | [design/architecture.md](design/architecture.md)                                                                                     |
| 权限中心概念模型     | [design/permission-center/overview.md](design/permission-center/overview.md)                                                         |
| 权限中心 API 契约    | [design/permission-center/api-contract.md](design/permission-center/api-contract.md)                                                 |
| 权限中心核心调用链路 | [design/permission-center/core-flows.md](design/permission-center/core-flows.md)                                                     |
| 权限中心实现设计     | [design/permission-center/implementation.md](design/permission-center/implementation.md)                                             |
| 默认组织树与用户生命周期 | [design/default-org-tree-user-lifecycle.md](design/default-org-tree-user-lifecycle.md)                                                |
| admin-service 与 permission-center 同步 | [design/cross-service/admin-permission-sync.md](design/cross-service/admin-permission-sync.md)                                      |
| admin-service 对前端 API 契约 | [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md) |
| PostgreSQL 表结构    | [design/schema/](design/schema/)                                                                                                     |

## 执行计划

| 主题 | 文档 |
|------|------|
| 项目诊断与完善计划 | [plans/improvement-plan.md](plans/improvement-plan.md) |
| 组织与用户融合页实现计划 | [plans/org-user-page-impl-plan.md](plans/org-user-page-impl-plan.md) |
| API 核对清单 | [plans/api-gap-analysis.md](plans/api-gap-analysis.md) |

## 推荐阅读顺序

1. 先读 [design/project-rules.md](design/project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [design/architecture.md](design/architecture.md)，理解 Gateway、admin-service、permission-center、example-service 的边界。
3. 开发权限中心前，按顺序读 [design/permission-center/overview.md](design/permission-center/overview.md)、[design/permission-center/api-contract.md](design/permission-center/api-contract.md)、[design/permission-center/core-flows.md](design/permission-center/core-flows.md)、[design/permission-center/implementation.md](design/permission-center/implementation.md)。了解重构历史可读 [archive/2026-05-30/service-layer-review.md](archive/2026-05-30/service-layer-review.md)。
4. 开发具体服务时，读取 [design/services/](design/services/) 下对应服务设计；涉及组织与用户页 admin 接口契约时, 读取 [design/services/admin-service-api-contract.md](design/services/admin-service-api-contract.md)。
5. 涉及组织与用户、多组织树、用户生命周期和成员关系时，先读 [design/default-org-tree-user-lifecycle.md](design/default-org-tree-user-lifecycle.md)。
6. 涉及 admin-service 与 permission-center 同步时，读取 [design/cross-service/admin-permission-sync.md](design/cross-service/admin-permission-sync.md)。
7. 跟进仍在推进的任务时，读取 [plans/README.md](plans/README.md)。
8. 涉及表字段、索引、约束时，以 [design/schema/](design/schema/) 下 SQL 为准。

## 文档治理规范

### 权威文档与归档文档

| 类型 | 目录 | 用途 |
|------|------|------|
| **权威文档** | `docs/design/` | 当前有效的设计、规范、API 契约，是实现的唯一依据 |
| **执行计划** | `docs/plans/` | 仍在推进的阶段计划、核对清单、路线图；用于跟踪任务，不作为契约来源 |
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
| `archive/2026-06-14/` | 同步模块重构执行计划归档；稳定设计已沉淀到 `design/cross-service/admin-permission-sync.md`。 | [archive/2026-06-14/README.md](archive/2026-06-14/README.md) |
| `archive/2026-06-05/` | 编码与创作风格分析报告。可操作知识已合并到项目规范（项目规范§17、权限中心规范§19、文档治理规范），报告仅作历史追溯。 | [archive/2026-06-05/README.md](archive/2026-06-05/README.md) |
| `archive/2026-06-03/` | 模块与接口逐轮核对及问题诊断。记录 Round 1 到 Round 7 的现状核对、目标模型、问题诊断表与建议实施顺序。 | [archive/2026-06-03/README.md](archive/2026-06-03/README.md) |
| `archive/2026-05-30/` | 前端集成方案、Service 层重构审查、权限中心重构影响分析等阶段性文档。                                   | [archive/2026-05-30/README.md](archive/2026-05-30/README.md) |
| `archive/2026-05-24/` | PermQueryEngine 设计与重构阶段归档。                                                                   | [archive/2026-05-24/README.md](archive/2026-05-24/README.md) |
| `archive/2026-04-28/` | 文档重整前的旧版长文档和讨论清单。                                                                     | [archive/2026-04-28/README.md](archive/2026-04-28/README.md) |
