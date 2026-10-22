---
doc_type: task
id: T-ACCESS-148
title: 资源关联授权守卫与运行时一致消费
status: done
plan: docs/archive/2026-10-22/resource-association-plan.md
domain: access-service
design_refs:
  - docs/design/resource-association.md#authorization
  - docs/design/resource-association.md#lifecycle
  - docs/design/access-service-api-contract.md#grant-contract
  - docs/design/engine/implementation.md#permission-query
  - docs/design/access-service-architecture.md#cache-boundaries
  - docs/design/dependency-auto-grant.md
depends_on:
  - T-ACCESS-146
acceptance:
  - "通过现有 apply-grant-plan 拒绝跨专属归属及未引用通用类型的授予；整类引用后仍允许部分实例授权；关系解除与授权提交并发后无可生效悬挂配置，计划失败整批回滚。"
  - "query-scopes、带父上下文 check/batchCheck、树继承及相关投影在已声明类型上同源过滤，未声明类型结果保持。"
  - "DIRECT、DEPENDENT、类型 ALL、父 ALL 与实例父子项并存、授权根/种子、条件/角色互斥均按 G1 验证；关系过滤不把 ALL 错报为无限范围或伪装成正常空态。"
  - "解绑重绑与删建同名、主资源/通用资源停用和删除按 G2 处理；A 解绑不清除 B 仍依赖的通用授权来源。"
  - "自动依赖与授权传递的适用写者、准入/快照与最终检查边界、日志/审计和受影响失效完成接线，技术故障不放宽权限。"
  - "批量加载无逐字段 execute/N+1；复用缓存框架与现有时效预算，未引入全局版本或无依据缓存；权威设计回写。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-22
---

## 背景

候选过滤不等于权限约束；关系解绑、资源停用以及广泛授权都可能改变实际业务范围，必须由同源判定和写守卫承接。

## 范围

在定稿限定的共享查询/投影边界消费关系，接入授权计划和相应自动依赖写者；完成解绑、删除、停用的授权事实处理、审计和提交后失效。复用现有引擎，不复制一套关系鉴权。

## 当前口径

授权保存守卫、完整父来源、关系同源过滤、解绑/删除清理与审计、历史用途锁定已实现；定向 PG、联合 E2E 和完整回归通过，见[实施验证](evidence/T-ACCESS-148/verification.md)与[联合验收](evidence/T-ACCESS-150/verification.md)。

详细规则以[资源关系设计](../../../design/resource-association.md)及上列 design_refs 为准。

## 验收对照

- [x] 通过现有 apply-grant-plan 拒绝跨专属归属及未引用通用类型的授予；整类引用后仍允许部分实例授权；关系解除与授权提交并发后无可生效悬挂配置，计划失败整批回滚。
- [x] query-scopes、带父上下文 check/batchCheck、树继承及相关投影在已声明类型上同源过滤，未声明类型结果保持。
- [x] DIRECT、DEPENDENT、类型 ALL、父 ALL 与实例父子项并存、授权根/种子、条件/角色互斥均按 G1 验证；关系过滤不把 ALL 错报为无限范围或伪装成正常空态。
- [x] 解绑重绑与删建同名、主资源/通用资源停用和删除按 G2 处理；A 解绑不清除 B 仍依赖的通用授权来源。
- [x] 自动依赖与授权传递的适用写者、准入/快照与最终检查边界、日志/审计和受影响失效完成接线，技术故障不放宽权限。
- [x] 批量加载无逐字段 execute/N+1；复用缓存框架与现有时效预算，未引入全局版本或无依据缓存；权威设计回写。

验证层：现有引擎/授权/失效测试补最小变体；真实 PG 证明事务与竞争，跨实例失效按现有设施验证，不新增专用测试平台。

## 非目标 / 遗留

不实现行列成对授权、通用 ABAC 解释器、独立求值核心或即时分布式撤权。
