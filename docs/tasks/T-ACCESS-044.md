---
doc_type: task
id: T-ACCESS-044
title: 跨能力 mapper 收敛批次②——grant 授权事实服务 + rule 条件读（6 边）
status: proposed
plan: docs/plans/capability-mapper-convergence-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 冻结白名单行缩减）
depends_on: [T-ACCESS-043]
blocks: []
acceptance:
  - "新建 grant/service/domain/RoleResourcePermissionDomainService(+Impl)：仅依赖 RoleResourcePermissionMapper（selectRoleIdsByResourceIds/selectValidPermIdsByResourceIds/selectValidPermIdsByResourceTypes/selectConditionIdsByPermIds/selectRoleIdsByResourceTypes/selectReferencedConditionIds/selectServiceCodesByConditionIds/softDeleteBatch），无缓存直读直写、不声明事务"
  - "E1（TypeDefinitionAppServiceImpl 7 点=5 读+2 写）、E4（ResourceManage 4 点）、E7（ConditionApp 3 读）、E8（PermissionConditionDomainServiceImpl recycle，同事务写后读）全部改走新服务"
  - "PermissionConditionDomainService 新增 selectValidByIdsNoTenant/selectValidByCodes/selectValidByIds 三读，禁复用 loadRules（CONDITION_RULES 缓存 + enabled 过滤）；#2 同事务新鲜读语义保持"
  - "FROZEN_WHITELIST 同 commit 删 6 行（30→21→15），shape 断言同步；InOrder 级联 verify 改挂新服务不删锁"
  - "mvn test -pl access-service 全绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次②：grant 是最大被读方（RoleResourcePermissionMapper 的 4 条入边）与 rule 条件读消费方（2 边）。含两条同事务可见性红线（E8 recycle 写后读、#2 内联条件新鲜读）。

## 范围

计划文件「批次②」表 6 边 + 新服务 + PermissionConditionDomainService 三读方法 + 测试装配适配（计划清单批次②节）。

## 非目标 / 遗留

- grant↔rule 包级环成立但 bean 级无环（新服务零 rule bean 依赖）——registry 已登记口径，不再评审讨论。

## 完成记录

（未开始）
