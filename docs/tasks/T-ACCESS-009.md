---
doc_type: task
id: T-ACCESS-009
title: 建立数据库任务租约、幂等和异步执行治理
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#81-多实例任务协调
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/design/services/admin-service.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "动态任务与保留的系统维护任务使用数据库执行键和唯一约束竞争同一次计划执行"
  - "复用 T-ACCESS-002 已纳入最终 DDL 的任务执行表、实体及基础 Mapper/XML，不在运行代码或独立脚本中临时创建第二套持久层"
  - "只扩展原子抢占、续租、租约接管、条件完成/失败等并发 SQL 与 Mapper 方法，不重复创建基础 CRUD 和字段映射"
  - "执行记录包含 lease_owner、lease_until、状态与必要的执行/幂等标识，抢占和续租使用数据库时间及原子条件"
  - "同一执行键同时最多一个活动执行者；实例故障后可接管；旧租约持有者不能覆盖新执行结果"
  - "外部副作用携带幂等键，重试语义明确为至少一次且不会产生重复业务结果"
  - "仅为内部同步丢失兜底的维护任务被删除；保留任务能够说明继续存在的外部场景"
  - "异步执行器有界、命名、可观测且显式传播可信上下文"
  - "双实例测试覆盖并发抢占、续租、租约过期接管和幂等重试"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-009 建立数据库任务租约、幂等和异步执行治理

## 背景

多实例下每个 JVM 都会调度相同任务。Redis 锁不承担正确性，本任务用 `access_db` 建立可审计的执行权和故障接管。

## 范围

- 设计并实现执行键、抢占、续租、接管和结果写回。
- T-ACCESS-002 负责最终 DDL、实体和基础 Mapper/XML；本任务只负责扩展原子并发 SQL、对应 Mapper 方法与执行编排。
- 收敛动态任务、维护任务和异步执行器。
- 删除因旧内部同步存在的冗余补偿任务。

## 完成记录

（待实施后填写。）
