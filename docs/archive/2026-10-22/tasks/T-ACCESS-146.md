---
doc_type: task
id: T-ACCESS-146
title: 资源关系存储与生命周期领域基础
status: done
plan: docs/archive/2026-10-22/resource-association-plan.md
domain: access-service
design_refs:
  - docs/design/resource-association.md#model
  - docs/design/resource-association.md#ownership
  - docs/design/resource-association.md#publication
  - docs/design/resource-association.md#lifecycle
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/redis-transaction-locks.md#transaction-lock-contract
depends_on:
  - T-ACCESS-145
acceptance:
  - "原样 access DDL 可在 PostgreSQL 建库；专属实例最多一个有效归属、通用类型可被多个主资源整体引用、关系去重、租户与有效身份约束有真实 SQL 证据。"
  - "专属实例与归属原子建立；主资源缺失、错类型、跨租户、专属二次归属及非法模式切换均按契约拒绝。"
  - "管理、single/FULL、树移动、删除、类型/服务/租户清理的领域守卫接线清单完成；通用实例、被引用类型与主资源的删除行为符合定稿。"
  - "双重绑定、绑定与删除、模式变更与创建等竞争可重复验证；统一锁序、锁内新鲜读取及事务回滚得到证据。"
  - "关系领域入口按能力边界复用，批量加载无 N+1；DDL、架构和公共锁接入文档回写，授权联动接口交接清楚。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-22
---

## 背景

专属唯一归属和通用多处引用需要持久化边界，不能依赖页面筛选或非事务性的先查后写。

## 范围

实现类型声明、关系持久化和资源能力包内可复用领域入口；更新唯一 DDL、必要初始化与删除/停用/类型/树结构生命周期接线。原子性所需的既有 single/FULL 关系字段与来源身份同批落地，T-ACCESS-147 接续候选/只读查询、SDK 与发布接入验证；权限贡献与授权清理由 T-ACCESS-148 完成，部分链路不作为完整功能交付。

## 当前口径

沿用既有资源类型和操作身份，未声明类型保持原契约。单层关系，不复用 parent_id、depend_on 或维护服务字段。数据来源与唯一归属守卫以 T-ACCESS-145 定稿为准，禁止在两处存可独立修改的归属。

详细规则以[资源关系设计](../../../design/resource-association.md)及上列 design_refs 为准。存储、类型守卫、原子发布与生命周期基础已实现，定向验证及本地双轨核对通过；完整功能须待候选/SDK、授权消费及联合验收完成。

## 验收对照

- [x] 原样 access DDL 可在 PostgreSQL 建库；专属实例最多一个有效归属、通用类型可被多个主资源整体引用、关系去重、租户与有效身份约束有真实 SQL 证据。
- [x] 专属实例与归属原子建立；主资源缺失、错类型、跨租户、专属二次归属及非法模式切换均按契约拒绝。
- [x] 管理、single/FULL、树移动、删除、类型/服务/租户清理的领域守卫接线清单完成；通用实例、被引用类型与主资源的删除行为符合定稿。
- [x] 双重绑定、绑定与删除、模式变更与创建等竞争可重复验证；统一锁序、锁内新鲜读取及事务回滚得到证据。
- [x] 关系领域入口按能力边界复用，批量加载无 N+1；DDL、架构和公共锁接入文档回写，授权联动接口交接清楚。

验证层：复用现有资源/类型测试与 ItInfra；新增约束、并发和原样 DDL 用真实 PG，时序用闩锁/有界条件等待。

## 非目标 / 遗留

不新建关系图平台、查询引擎或分布式锁框架；候选/只读查询、SDK 与授权消费按直接依赖后的对应卡实施。

## 完成记录

验证与双轨结论见[证据](evidence/T-ACCESS-146/verification.md)。`ResourceAssociationPublication.Plan` 向 T-ACCESS-148 交接被解绑类型集合；主资源删除已扩展专属实例并清理关系与发布确认，角色授权回收及快照失效仍由 T-ACCESS-148 完成，不在本卡宣称已生效。
