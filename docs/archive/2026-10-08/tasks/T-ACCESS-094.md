---
doc_type: task
id: T-ACCESS-094
title: 租户注册与统一模板开通
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md#tenant-initialization
  - docs/design/tenant-lifecycle.md#tenant-management
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md#142-载体终态
  - docs/design/access-service-api-contract.md
depends_on:
  - T-ACCESS-093
acceptance:
  - 租户注册、编码约束及不复用符合设计，列表详情与名称维护可用
  - 权威 DDL 参数化基础种子与 Java 固定图在开通事务内完成，不复制其他租户数据
  - 随机首管理员密码一次展示和受审计的平台重置符合设计
  - 真实 PG 覆盖完整开通、重复编码拒绝和初始化失败整事务回滚
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

原固定租户初始化无法承载产品化开通；按[目标设计](../../../design/tenant-lifecycle.md)建立统一入口。

## 范围

租户主表、运营接口、参数化 SQL 基础种子、Java 固定图复用及首管理员凭据交付。

## 当前口径

全部租户从运营台开通，没有租户 1 特例；不删除租户，不复用编码；详细规则见设计。

## 验收对照

- [x] 注册及运营读写接口。
- [x] 统一模板与原子开通。
- [x] 首管理员交付与审计重置。
- [x] PG 行为证据与规范回写。

## 非目标 / 遗留

即时停用和认证入口消费由 T-ACCESS-095 承接；不新增在线迁移或模板升级工具。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
