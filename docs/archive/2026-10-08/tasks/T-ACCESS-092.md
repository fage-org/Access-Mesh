---
doc_type: task
id: T-ACCESS-092
title: 租户 Redis 即时门禁共享协议
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md#redis-gate
depends_on: []
acceptance:
  - 共享读写协议按实际 Redis 进程判定状态可信性，状态未知时拒绝
  - 状态发布仅能消费当前租户和当前 Redis 进程的本次阻断令牌，旧操作不得覆盖新状态或重建丢失记录
  - 用户会话按启用状态和代次判定，服务调用只消费启停状态
  - 最小充分测试覆盖旧发布、记录丢失、旧进程记录、畸形状态和租户隔离
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

[Q-063](../../../pending-problems.md#q-063) 的 Redis 路线、故障拒绝与自动重建、单主部署已确认；共享状态协议可以先于平台页面及租户初始化细节实施。

## 范围

common 中的共享状态表示、Redis 原子脚本及同步访问适配；Gateway 后续消费同一读取脚本。真实 Redis 行为验证放在 access-service 的 ItInfra 容器轨，协议解码放 common 单元轨。

## 当前口径

按[Redis 门禁设计](../../../design/tenant-lifecycle.md#redis-gate)实现，不引入 L1 放行状态。协调状态不走普通业务缓存 TTL 或失效广播。数据库串行化、入口接线与自动修复由后续实施任务承接，本卡不声称 Q-063 已完整交付。

## 验收对照

- [x] 可信进程与未知状态拒绝。
- [x] 本次阻断令牌与旧发布隔离。
- [x] 用户会话代次与服务启停判定。
- [x] 单元与真实 Redis 的定向行为证据。

## 非目标 / 遗留

本卡限定共享协议；注册、事务协调、后台修复、Gateway 与认证入口及完整产品验收由同计划其他任务承接。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
