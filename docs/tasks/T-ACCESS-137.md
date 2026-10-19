---
doc_type: task
id: T-ACCESS-137
title: 权限演示数据库与持久化基础
status: in-progress
plan: docs/plans/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md#storage
depends_on:
  - T-ACCESS-136
acceptance:
  - "报表字段与资源映射持久化，租户/报表/字段身份约束和删后同名不复用身份通过真实 PG 验证。"
  - "真实 PG 验证 DDL、租户隔离、业务键唯一性、时间/金额类型与必要事务约束。"
  - "正式包独立业务库可启动并保留写入数据，秘密不入库代码或文档。"
  - "单测/PG/E2E execution 与跳过开关正确，复用基建且不破坏模块依赖。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-19
---

## 背景

example 当前仍为内存数据，需要独立持久化事实、配置与执行状态。

## 范围

example_db、数据源、MyBatis-Flex 分层、原样 DDL、审计/唯一性/CAS 等数据库约束，以及共享 PG 测试支持出口。

持久化报表字段归属、字段资源映射与目录版本，保持同名字段跨报表独立。

## 当前口径

不用旧未消费 DDL 直接启动，不读取 IAM 内部表，不建设销售分析字段或影子用户中心。单实例部署；测试支持避免让 example 依赖 access-service 测试包或重复自建容器组。 详细规则见[权限演示设计](../design/example-permission-demo.md)；计划已 active，T-ACCESS-136 已 done，本卡开始实施。

## 验收对照

- [ ] 报表字段与资源映射持久化，租户/报表/字段身份约束和删后同名不复用身份通过真实 PG 验证。
- [ ] 真实 PG 验证 DDL、租户隔离、业务键唯一性、时间/金额类型与必要事务约束。
- [ ] 正式包独立业务库可启动并保留写入数据，秘密不入库代码或文档。
- [ ] 单测/PG/E2E execution 与跳过开关正确，复用基建且不破坏模块依赖。

## 非目标 / 遗留

不建设页面、备份平台或跨版本升级系统。
