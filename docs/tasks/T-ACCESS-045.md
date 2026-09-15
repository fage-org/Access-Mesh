---
doc_type: task
id: T-ACCESS-045
title: 跨能力 mapper 收敛批次③——type/resource/domain 供给读服务（11 边）
status: in-progress
plan: docs/plans/capability-mapper-convergence-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 冻结白名单行缩减）
depends_on: [T-ACCESS-044]
blocks: []
acceptance:
  - "新建 type OperationPermissionDomainService（selectAllOperationsByTenant 全租户口径 + selectByTenantAndResourceTypes IN 形态空集短路 + selectValidByIds + selectByTenantResourceTypesAndOpCodes 无缓存新鲜面）与 TypeDefinitionDomainService（selectValidByTenant/selectByTenantAndTypeKey）"
  - "20008/20005 契约区分保持：Plan prevalidate knownOperationCodes 走全租户口径（selectByTenantAndResourceType(tenantId,null) 等价），PermissionGrantPlanDomainServiceImplTest 733-828 区分用例装配改线后仍锁"
  - "新建 ResourceApiMappingDomainService（selectByResourceEntityIds）、ServiceConfigDomainService（selectByTenantAndServiceCode 封装）、DomainConfigDomainService（SUB_PERM 读）"
  - "GrantDom 刻意缓存回避面（selectByTenantResourceTypesAndOpCodes/selectValidByTenant）不接 OPERATION_PERMISSIONS_BY_TYPE 缓存；E10 逐类型循环合并为单 IN（批量改善，无缓存直读语义保持）"
  - "FROZEN_WHITELIST 同 commit 删 11 行（21→15→4），shape 断言同步；mvn test -pl access-service 全绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次③：type 是最大供给缺口（8 条出边），另有 resource 两新服务与 domain SUB_PERM 读。外评 P2 修订点：操作定义读拆全租户/IN 两口径（防 20008→20005 退化与 IN() 500）。

## 范围

计划文件「批次③」表 11 边 + 三个新包共 6 新服务 + 测试装配适配（计划清单批次③节）。

## 非目标 / 遗留

- grant↔type、domain→type→grant→domain 包级环成立但 bean 级无环（新服务 mapper-only）——registry 已登记口径。

## 完成记录

（未开始）
