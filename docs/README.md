# AccessMesh 文档索引

## 目录结构

```
docs/
├── README.md                          # 本文档
├── design/                            # 设计文档（权威来源）
│   ├── README.md                      # 设计文档索引
│   ├── project-rules.md               # 项目工程规范
│   ├── architecture.md                # 微服务整体架构
│   ├── frontend-integration.md        # 前端集成方案
│   ├── permission-center-refactor-impact-analysis.md  # DDD重构影响分析
│   ├── permission-center/             # 权限中心设计
│   │   ├── overview.md                # 概念模型
│   │   ├── api-contract.md            # API契约
│   │   ├── core-flows.md              # 核心调用链路
│   │   ├── implementation.md          # 实现设计
│   │   └── service-layer-review.md    # Service层重构审查
│   ├── services/                      # 服务设计
│   │   ├── admin-service.md
│   │   ├── gateway.md
│   │   └── example-service.md
│   └── schema/                        # 数据库表结构
│       ├── admin-service.sql
│       ├── example-service.sql
│       └── permission-center.sql
└── archive/                           # 归档文档（仅追溯，不作为实现依据）
    ├── 2026-04-28/
    └── 2026-05-24/
```

## 权威来源

| 主题                  | 权威文档                                                    |
| --------------------- | ----------------------------------------------------------- |
| 项目工程规范          | [design/project-rules.md](design/project-rules.md)         |
| 微服务整体架构        | [design/architecture.md](design/architecture.md)            |
| 权限中心概念模型      | [design/permission-center/overview.md](design/permission-center/overview.md) |
| 权限中心 API 契约     | [design/permission-center/api-contract.md](design/permission-center/api-contract.md) |
| 权限中心核心调用链路  | [design/permission-center/core-flows.md](design/permission-center/core-flows.md) |
| 权限中心实现设计      | [design/permission-center/implementation.md](design/permission-center/implementation.md) |
| Service 层重构审查    | [design/permission-center/service-layer-review.md](design/permission-center/service-layer-review.md) |
| PostgreSQL 表结构     | [design/schema/](design/schema/)                            |
| 前端集成方案          | [design/frontend-integration.md](design/frontend-integration.md) |
| DDD 重构影响分析      | [design/permission-center-refactor-impact-analysis.md](design/permission-center-refactor-impact-analysis.md) |

## 推荐阅读顺序

1. 先读 [design/project-rules.md](design/project-rules.md)，确认接口、分层、DTO、异常、数据库等通用约束。
2. 再读 [design/architecture.md](design/architecture.md)，理解 Gateway、admin-service、permission-center、example-service 的边界。
3. 开发权限中心前，按顺序读 [design/permission-center/overview.md](design/permission-center/overview.md)、[design/permission-center/api-contract.md](design/permission-center/api-contract.md)、[design/permission-center/core-flows.md](design/permission-center/core-flows.md)、[design/permission-center/implementation.md](design/permission-center/implementation.md)。了解重构历史可读 [design/permission-center/service-layer-review.md](design/permission-center/service-layer-review.md)。
4. 开发具体服务时，读取 [design/services/](design/services/) 下对应服务设计。
5. 涉及表字段、索引、约束时，以 [design/schema/](design/schema/) 下 SQL 为准。

## 归档说明

`archive/` 下文档仅用于历史追溯，不作为实现依据。可能包含旧接口、旧字段或已废弃设计。如归档内容与权威文档冲突，以本页"权威来源"列出的文档为准。
