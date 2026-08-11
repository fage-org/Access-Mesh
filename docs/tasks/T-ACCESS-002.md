---
doc_type: task
id: T-ACCESS-002
title: 建立 access_db 最终 DDL 并收敛持久层模型
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#4-管理事实与权限投影
  - docs/design/access-service-architecture.md#5-数据库与共享表
  - docs/design/access-service-architecture.md#81-多实例任务协调
depends_on:
  - T-ACCESS-001
blocks: []
acceptance:
  - "创建可从空 PostgreSQL 执行的 docs/design/schema/access-service.sql，目标为 access_db.public"
  - "access-service.sql 创建后、本任务进入 review 前，将该文件补入本卡 design_refs，确保 T-ACCESS-002 自身的 design_writeback 直接覆盖最终 DDL；当前不创建悬空引用"
  - "合并两份现有 DDL且无表名、索引名和种子冲突；sys_sync_task 不进入最终结构"
  - "system_config 与 operation_log 使用权威设计定义的合并结构，其他明确保留的表维持独立语义"
  - "本地权限投影具备 access-service 来源/所有权标识和稳定外部键约束"
  - "最终 DDL 预建 T-ACCESS-009 所需的任务执行持久化结构，覆盖稳定 execution key 及唯一约束、lease_owner、lease_until、状态和幂等标识"
  - "实体、基础 Mapper/XML 映射和表名引用与最终 DDL 一致，不包含旧数据库名；任务执行实体及基础字段映射由本任务创建，原子抢占/续租/条件完成 SQL 留给 T-ACCESS-009"
  - "自动化空库测试验证表、索引、约束与种子数据；不新增历史数据迁移脚本或 migration 框架"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-002 建立 access_db 最终 DDL 并收敛持久层模型

## 背景

项目无部署和历史数据，本任务直接产出最终空库结构，为后续同库事务提供唯一事实基础。

## 范围

- 合并、校对和执行最终 DDL。
- 预建多实例任务租约与幂等执行所需的完整持久化结构、实体及基础 Mapper/XML 映射；原子并发 SQL 与运行逻辑由 T-ACCESS-009 实现。
- 调整持久层对象、索引、约束和种子引用。
- 建立空库结构测试。

## 非目标

- 不编写 `admin_db`/`perm_db` 到 `access_db` 的迁移程序。
- 不引入 Flyway 或 Liquibase。

## 完成记录

（待实施后填写。）
