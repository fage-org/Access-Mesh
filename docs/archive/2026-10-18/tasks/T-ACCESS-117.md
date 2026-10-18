---
doc_type: task
id: T-ACCESS-117
title: 登录事务结束后同步记录独立审计
status: done
plan: docs/archive/2026-10-18/login-audit-transaction-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#login-audit-transaction-boundary
  - docs/design/access-service-api-contract.md#contract-section-6-4
  - docs/ops/deployment.md
depends_on: []
acceptance:
  - 登录事务完整结束后同步调用既有独立审计，单连接池下成功与失败审计可落库
  - 保留用户锁读到会话创建完成的事务范围，密码重置不得穿过该锁窗
  - 认证失败原因和日志失败隔离保持，未解析租户不写虚构租户日志
  - 事务结束抛出技术异常时不记录成功，固定失败原因且原异常不被审计覆盖
  - 定向反例、真实 PG/Redis、本地双轨核对及完整回归通过，设计与 Q-067 回写
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

承接 [Q-067](../../../pending-problems.md#q-067)，用户已解除暂缓并授权按事务外同步审计实施。外层登录事务内调用 REQUIRES_NEW 需要同时持有两条连接，满池交错可能形成等待环。

## 范围

调整 AuthAppServiceImpl 的事务编排与请求内审计数据传递；复用 LoginLogDomainService 的独立事务；核对 Bearer 与浏览器登录共享入口及现有调用方，补充最小充分的真实数据库和事务边界证据。

## 当前口径

执行[登录审计事务边界](../../../design/access-service-architecture.md#login-audit-transaction-boundary)。使用项目既有 TransactionTemplate；不新增队列、线程池、事件总线、数据源或通用审计框架。技术异常审计按已确认规则处理，不承诺识别事务管理器未向调用方抛出的数据库故障。

## 验收对照

- [x] 事务编排与审计隔离。
- [x] 单连接池与登录行锁实证。
- [x] 失败分支及事务异常验证。
- [x] 本地双轨核对、完整回归、设计与问题收敛。

## 非目标 / 遗留

不改变验证码、密码比对、失败计数、OAuth2/平台登录、会话协议与有效期；不引入 Redis/数据库跨资源补偿，不修改事务管理器，不顺带移动密码校验到行锁外。不推进其他待启动计划或 T-PERM-036。

## 完成记录

2026-10-18 完成实现、定向反例与行为验证、本地代码/文档双轨核对及完整回归。实际命令、计数、源码清单与验证边界见[验收记录](evidence/T-ACCESS-117/verification.md)。
