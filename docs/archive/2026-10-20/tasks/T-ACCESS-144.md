---
doc_type: task
id: T-ACCESS-144
title: Mapper 隔离级别接线与演示任务恢复、导入期限
status: done
plan: null
domain: cross-service
design_refs:
  - docs/design/project-rules.md#backend-concurrency
  - docs/design/example-permission-demo.md#scheduled-tasks
  - docs/design/example-service-api-contract.md
depends_on: []
acceptance:
  - Mapper 真实连接应用显式隔离级别，分页和快照的 SELECT 与 COUNT 不受中间提交影响
  - 完成写入失败后由正常 tick 恢复同一执行键，已提交成功不重做
  - 导入和撤销的初始数据集取锁受到 SQL 期限约束且失败无副作用
  - 发布重试请求字段与 DTO 一致，针对性验证和完整回归通过
design_writeback:
  required: true
  status: done
last_updated: 2026-10-20
---

## 背景

核实基线 `8e4088abd` 的一致读、执行恢复、CSV 导入与发布重试契约问题。

## 范围

common Mapper 隔离级别接线及平台服务装配；example 数据写守卫、单实例调度恢复；相关 PG 测试和契约勘误。相同隔离接线覆盖 example 数据/任务/执行/发布列表及 access-service 声明显式隔离的读取入口。

## 当前口径

实现与边界见 design_refs；沿用 Flex 事务管理器、Hikari 连接池、单实例同步 worker 与现有 attempt fencing，不改变授权算法、重试预算或响应 DTO。

## 验收对照

- [x] Mapper 隔离、并发提交和连接归还/独立事务通过真实 PG 验证。
- [x] 完成提交前失败与提交后确认丢失均由正常 tick 验证。
- [x] 导入/撤销初始锁等待有界失败且无写入副作用。
- [x] 契约字段、本地代码与文档两轨、完整 reactor 回归通过。

## 非目标 / 遗留

不增加多实例 worker、分布式快照或新的通用调度系统。

## 完成记录

2026-10-20：定向 49 项 PG + 6 项执行器单测通过；最终 `mvn test -T 1C` 为 3,157 passed / 0 failed / 0 error / 0 skipped（含 E2E/heavy，27:28）。[验证记录](evidence/T-ACCESS-144/verification.md)包含修复前对照、类推范围与本地双轨结论。
