---
doc_type: task
id: T-ACCESS-045
title: 跨能力 mapper 收敛批次③——type/resource/domain 供给读服务（11 边）
status: done
plan: docs/archive/2026-09-15/capability-mapper-convergence-plan.md
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
  status: done
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次③：type 是最大供给缺口（8 条出边），另有 resource 两新服务与 domain SUB_PERM 读。外评 P2 修订点：操作定义读拆全租户/IN 两口径（防 20008→20005 退化与 IN() 500）。

## 范围

计划文件「批次③」表 11 边 + 三个新包共 6 新服务 + 测试装配适配（计划清单批次③节）。

## 非目标 / 遗留

- grant↔type、domain→type→grant→domain 包级环成立但 bean 级无环（新服务 mapper-only）——registry 已登记口径。

## 完成记录

2026-09-15 收口。新建五服务十二文件：`OperationPermissionDomainService`（selectAllOperationsByTenant 全租户口径 + selectByTenantAndResourceTypes IN 空集短路 + selectValidByIds + selectByTenantResourceTypesAndOpCodes，全部无缓存——GrantDom 刻意缓存回避面语义保持）、`TypeDefinitionDomainService`、`ResourceApiMappingDomainService`、`ServiceConfigDomainService`、`DomainConfigDomainService`（均 mapper-only）。11 边全收敛：grant 三类 5 边（knownOperationCodes 走全租户口径，20008/20005 契约区分由 PermissionGrantPlanDomainServiceImplTest 733-828 既有用例锁定）、#5 SUB_PERM、E6 Dependency、E10 两处逐类型循环合并单 IN（批量改善）、D8 DomainClassify、E2 TypeDefApp→ApiMapping、E3 Guard→ServiceConfig。grant↔type、domain→type→grant→domain 包环成立、bean 级无环。

测试装配适配 14 文件（grant 四测试 + Dependency/PermissionConflict/BatchPermMutexEvaluator/DomainClassify/TypeDefinition/Guard/OperationLog/两 PgIT + 架构测试）；本包自有 mapper mock 四处误换血回退（TypeDefinition 测试 typeDefinition/operationPermission、DomainClassify 测试 domainConfig、Guard 测试 typeDefinition、OperationLog/PgIT 本包字段）；全租户 null 形态 stub 改名 selectAllOperationsByTenant（8+3 处）、逐类型 stub 合并 Set 形态（2+3 处）；shape 断言 15→4 边/3 消费类、负向自证样例改取 UserManage→UserRoleMapper 行。

回归证据：`mvn test -pl access-service -DskipTestcontainers=true` → 1249/0/0（2026-09-15，t045_unit2.log）；`mvn test -pl access-service` → 1249+210 双 fork 全绿 BUILD SUCCESS（2026-09-15，t045_full.log；9100 端口空闲）。设计回写：capability-structure §8.4 收敛进度注记更新至批次③。
