---
doc_type: plan
title: 登录审计事务边界收敛
status: archived
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#login-audit-transaction-boundary
  - docs/design/access-service-api-contract.md#contract-section-6-4
tasks:
  - T-ACCESS-117
acceptance: 登录与审计不同时占用连接，既有行锁和认证语义保持，验证完成并收敛 Q-067
last_updated: 2026-10-18
---

# 登录审计事务边界收敛

## 目标

消除 Q-067 的嵌套借连接等待环，保留失败登录留痕和密码重置串行保护。具体行为见 design_refs，不在计划中重定义协议。

## 非目标

不通过扩池、异步化或新基础设施替代边界修复；不自动启动外部评审或其他计划。

## 准入条件

2026-10-18 用户解除 Q-067 暂缓并授权实施，确认事务结束异常按失败记账。新发现的行为取舍先举例解释并由用户决定；事实性修复直接执行。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-117](tasks/T-ACCESS-117.md) | 登录事务结束后同步记录独立审计 | ✅ | — |

## 归档条件

先补反例并运行定向测试，再实现与类推检查；单连接池及用户行锁使用现有 ItInfra 真实 PG/Redis，非数据库分支用既有低层测试。定向通过后进行代码轨和文档轨核对，冻结源码运行 `mvn test -T 1C`，包含 E2E/heavy，完整日志落盘。任务完成、设计回写及 Q-067 验收收敛后统一归档。

## 当前进度

已完成并归档，见[验收记录](tasks/evidence/T-ACCESS-117/verification.md)。其余待启动计划保持原状态。
