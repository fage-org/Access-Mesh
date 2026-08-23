---
doc_type: task
id: T-ACCESS-019
title: USER/ROLE 全写路径同事务资源投影
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/services/admin-service-api-contract.md
depends_on: [T-ACCESS-018]
blocks: [T-ACCESS-020, T-PERM-043]
acceptance:
  - "以全量 grep 用户/角色写路径为准（含已核实缺口：RoleManageAppServiceImpl 创建仅写 abstract_role 无投影；UserManageAppServiceImpl 用户创建路径无投影），创建/更新/启停/删除/软删全部在同事务维护 resource_entity(USER/ROLE) 投影，任一步失败整体回滚"
  - "投影一次到位：直接使用统一后主体 ID（T-ORG-001 已完成）与收敛后类型码（T-ACCESS-018 已完成）作为 resource_entity(USER/ROLE) code，无过渡键、无二次切换"
  - "复用 LocalProjectionDomainService 与 TypeResolutionService，不新增第二套同步任务、MQ 最终一致性链路或独立 Projection Manager 框架"
  - "双创建链路收敛：application 域与 permission 域管理入口的用户/角色写路径不重复实现投影（复用优先于重实现，规范 §8.4）"
  - "真实 USER/ROLE 写入后实例授权端到端测试转绿（T-PERM-042 引擎语义测试的生产写路径闭环：业务写路径产生投影 → 实例门禁按业务编码命中/拒绝正确）"
  - "缓存失效不回归：投影写路径的 evictAfterCommit/PermissionChange 标注齐全；单测 + PostgreSQL Testcontainers 验证投影与业务写同事务成功/回滚两种路径"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-019 USER/ROLE 全写路径同事务资源投影

## 背景

核心对象资源注册不完整：菜单投影已由 T-ACCESS-015 收口（五值全量、同事务）；组织投影存在（OrgWriteAppServiceImpl）；但角色创建（RoleManageAppServiceImpl 仅写 abstract_role）与部分用户创建路径（UserManageAppServiceImpl，与 UserWriteAppServiceImpl 的已有投影并存为双链路）缺同事务 resource_entity 投影——类型级 scopeAll 可放行而实例级授权无从命中。T-PERM-042 用手工装配的投影 fixtures 验证了引擎语义，本任务让真实业务写路径产出投影，补上实例授权的写侧闭环。

本任务在主体 ID 统一（T-ORG-001）与类型收敛（T-ACCESS-018）之后实施，投影直接以最终主体 ID 与最终类型码一次写成，无过渡转换层。

## 范围

- 用户/角色全部写路径的投影补齐与双链路收敛。
- 启停/删除路径的投影同步维护（禁用主体/角色时投影状态与授权可用性一致）。
- 投影写入与缓存失效标注核对。

## 当前口径

- 投影与管理事实同事务（access-service 单库强事务模型，不走异步补偿）。
- code 语义：resource_entity(USER).code = 统一主体 ID、(ROLE).code = roleId（T-ACCESS-016 定稿）。

## 非目标 / 遗留

- 不改菜单/组织既有投影链路（仅核对与新类型码一致）。
- 主体 ID 统一与类型收敛分别归前置任务 T-ORG-001 / T-ACCESS-018。
