---
doc_type: plan
title: admin-service 与 permission-center 归并为 access-service
status: proposed
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/project-rules.md
  - docs/design/permission-center/api-contract.md
tasks:
  - T-ACCESS-001
  - T-ACCESS-002
  - T-ACCESS-003
  - T-ACCESS-004
  - T-ACCESS-005
  - T-ACCESS-006
  - T-ACCESS-007
  - T-ACCESS-008
  - T-ACCESS-009
  - T-ACCESS-010
  - T-ACCESS-011
  - T-ACCESS-012
acceptance: "T-ACCESS-001~012 全部 done 或经确认 cancelled；access-service 成为唯一部署单元；空库、契约、事务、安全、双实例与架构门禁通过；设计与存量任务完成回写和重基线"
last_updated: 2026-08-11
---

# admin-service 与 permission-center 归并为 access-service

> 状态：proposed
> 关联设计：[access-service 目标架构与归并约束](../design/access-service-architecture.md)

## 目标

- 将两个后端服务收敛为唯一 `access-service` 部署单元。
- 按权威设计完成工程、数据库、事务、安全、缓存、任务和生态切换。
- 在不扩展业务功能的前提下保持既有 HTTP 契约行为；已退役的内部同步管理接口 `/admin/sync-task/*` 明确排除。
- 建立可验证的空库、回滚、双实例和架构门禁。

## 非目标

- 不实现当前任务看板中尚未完成的新业务功能。
- 不保留旧服务部署兼容或旧数据迁移能力。
- 不在本计划引入数据库 migration 框架、权限版本号或完全扁平化。
- 不重定义 admin/permission 现有产品契约；设计变化必须先回写 `docs/design/`。

## 准入条件

- [access-service 目标架构](../design/access-service-architecture.md) 保持 `adopted`。
- 尚未开始的 T-PERM/T-ADMIN 后端功能任务不得在旧模块上进入 `in-progress`。
- Java 21、PostgreSQL、Redis 和 Maven 构建环境可用。
- 实施前记录当前全量构建与测试基线，区分迁移回归与既有失败。

## 阶段编排

| 阶段 | 任务 | 出口 |
|---|---|---|
| 1. 容器与数据基线 | T-ACCESS-001~003 | 新模块、单库和单运行基础设施可编译启动 |
| 2. 业务边界归并 | T-ACCESS-004~007 | 安全上下文、强事务投影、只读模型、共享配置审计落地 |
| 3. 多实例运行语义 | T-ACCESS-008~009 | 缓存与任务在双实例下具备明确一致性和故障语义 |
| 4. 生态切换与收口 | T-ACCESS-010~012 | 唯一部署单元、验收门禁、文档和任务治理闭环 |

## 任务清单

> 任务详情与唯一状态以 [任务看板](../tasks/README.md) 和对应任务卡为准。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-001](../tasks/T-ACCESS-001.md) | 建立 access-service 工程骨架并物理归并源码 | ✅ | — |
| [T-ACCESS-002](../tasks/T-ACCESS-002.md) | 建立 access_db 最终 DDL 并收敛持久层模型 | ✅ | T-ACCESS-001 |
| [T-ACCESS-003](../tasks/T-ACCESS-003.md) | 收敛单数据源、MyBatis、Redis、JSON等运行基础配置 | ✅ | T-ACCESS-001, T-ACCESS-002 |
| [T-ACCESS-004](../tasks/T-ACCESS-004.md) | 实现可信请求上下文和统一安全策略矩阵 | ⚙️ | T-ACCESS-003 |
| [T-ACCESS-005](../tasks/T-ACCESS-005.md) | 实现强事务权限投影并删除内部同步子系统 | ⚙️ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-006](../tasks/T-ACCESS-006.md) | 建立跨域只读查询模型 | ⚙️ | T-ACCESS-002, T-ACCESS-005 |
| [T-ACCESS-007](../tasks/T-ACCESS-007.md) | 合并系统配置与操作审计并落实日志事务分级 | ⚙️ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-008](../tasks/T-ACCESS-008.md) | 统一缓存并实现多实例失效及30秒安全边界 | ⚙️ | T-ACCESS-003, T-ACCESS-005 |
| [T-ACCESS-009](../tasks/T-ACCESS-009.md) | 建立数据库任务租约、幂等和异步执行治理 | ⚙️ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-010](../tasks/T-ACCESS-010.md) | 切换 Gateway、SDK、Nacos和部署配置 | ⚙️ | T-ACCESS-004, T-ACCESS-005, T-ACCESS-008 |
| [T-ACCESS-011](../tasks/T-ACCESS-011.md) | 完成契约、回滚、架构、空库和双实例验收 | ⚙️ | T-ACCESS-006, T-ACCESS-007, T-ACCESS-009, T-ACCESS-010 |
| [T-ACCESS-012](../tasks/T-ACCESS-012.md) | 删除残留引用、回写设计并重基线任务看板 | ⚙️ | T-ACCESS-011 |

## 验收标准

- 12 个任务均为 `done` 或经确认 `cancelled`，且所有必需设计回写均为 `done`。
- `access-service` 是唯一可运行的管理与权限服务；旧模块、旧服务名和内部同步运行链路已删除。
- 空库初始化、API 契约（排除并负向验证已退役的 `/admin/sync-task/*`）、事务回滚、安全隔离、缓存故障、双实例任务和架构测试全部通过。
- 原有未完成后端任务已更新到新模块/新 schema，或因范围消失而取消并完成依赖重连。
- 稳定结论全部位于 `docs/design/`，计划正文没有形成第二套契约。

## 当前进度

- 2026-08-10：grill 决策讨论完成，目标设计 adopted；建立 proposed 计划与 T-ACCESS-001~012 任务卡。
- 2026-08-12：T-ACCESS-001 done（工程骨架与物理归并完成，347 测试基线）。
- 2026-08-13：T-ACCESS-002 done（权威 DDL `schema/access-service.sql` 34 表 + 139 条操作种子 + 持久层收敛 + 空库测试双轨，四轮评审收口，372 测试基线）。
- 2026-08-13：T-ACCESS-003 done（Sa-Token 两端统一 + 删除重复缓存/序列化配置 + expiresIn 配置化，ultracode 评审 4 项问题修复含 P1 token-prefix，377 测试基线；阶段 1 出口达成：单模块、单库和单运行基础设施可编译启动）。

## 归档条件

- 所有任务完成或取消，任务看板无 dangling 依赖。
- 目标架构、整体架构、服务设计、权限设计、API 契约和最终 schema 已按实现结果回写。
- 计划稳定结论已经沉淀到设计层，且完成归档自检。
