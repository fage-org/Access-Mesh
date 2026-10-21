---
doc_type: task
id: T-ACCESS-148
title: 资源关联授权守卫与运行时一致消费
status: proposed
plan: docs/plans/resource-association-plan.md
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
  status: pending
last_updated: 2026-10-21
---

## 背景

候选过滤不等于权限约束；关系解绑、资源停用以及广泛授权都可能改变实际业务范围，必须由同源判定和写守卫承接。

## 范围

在定稿限定的共享查询/投影边界消费关系，接入授权计划和相应自动依赖写者；完成解绑、删除、停用的授权事实处理、审计和提交后失效。复用现有引擎，不复制一套关系鉴权。

## 当前口径

精确遵循 G1/G2：关联只限定适用对象，不自动授权；需要计算子权限时完整收集有效父授权来源，不让父 ALL 短路遮蔽实例父下子项。通用整类引用解绑清理当前主资源实例主授权下对应类型的子授权，保留跨报表共享来源及 DIRECT/类型 ALL；解绑期间保留来源停止在该主资源中贡献，重绑后可按当前条件恢复，已删除子授权不恢复。资源删除另按身份生命周期处理。直接查询无上下文和 Gateway 准入继续其各自契约，不虚构报表身份。

详细规则以[资源关系设计](../design/resource-association.md)及上列 design_refs 为准。本卡已获执行授权，保持 proposed，待直接依赖及实施契约门槛满足后启动。

## 验收对照

- [ ] 通过现有 apply-grant-plan 拒绝跨专属归属及未引用通用类型的授予；整类引用后仍允许部分实例授权；关系解除与授权提交并发后无可生效悬挂配置，计划失败整批回滚。
- [ ] query-scopes、带父上下文 check/batchCheck、树继承及相关投影在已声明类型上同源过滤，未声明类型结果保持。
- [ ] DIRECT、DEPENDENT、类型 ALL、父 ALL 与实例父子项并存、授权根/种子、条件/角色互斥均按 G1 验证；关系过滤不把 ALL 错报为无限范围或伪装成正常空态。
- [ ] 解绑重绑与删建同名、主资源/通用资源停用和删除按 G2 处理；A 解绑不清除 B 仍依赖的通用授权来源。
- [ ] 自动依赖与授权传递的适用写者、准入/快照与最终检查边界、日志/审计和受影响失效完成接线，技术故障不放宽权限。
- [ ] 批量加载无逐字段 execute/N+1；复用缓存框架与现有时效预算，未引入全局版本或无依据缓存；权威设计回写。

验证层：现有引擎/授权/失效测试补最小变体；真实 PG 证明事务与竞争，跨实例失效按现有设施验证，不新增专用测试平台。

## 非目标 / 遗留

不实现行列成对授权、通用 ABAC 解释器、独立求值核心或即时分布式撤权。
