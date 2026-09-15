---
doc_type: task
id: T-ACCESS-044
title: 跨能力 mapper 收敛批次②——grant 授权事实服务 + rule 条件读（6 边）
status: done
plan: docs/archive/2026-09-15/capability-mapper-convergence-plan.md
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
  status: done
last_updated: 2026-09-15
---

## 背景

Q-009 转出批次②：grant 是最大被读方（RoleResourcePermissionMapper 的 4 条入边）与 rule 条件读消费方（2 边）。含两条同事务可见性红线（E8 recycle 写后读、#2 内联条件新鲜读）。

## 范围

计划文件「批次②」表 6 边 + 新服务 + PermissionConditionDomainService 三读方法 + 测试装配适配（计划清单批次②节）。

## 非目标 / 遗留

- grant↔rule 包级环成立但 bean 级无环（新服务零 rule bean 依赖）——registry 已登记口径，不再评审讨论。

## 完成记录

2026-09-15 收口。新建 `grant/service/domain/RoleResourcePermissionDomainService(+Impl)`（mapper-only 八方法，javadoc 焊死硬契约：无缓存直读直写、REQUIRED、selectReferencedConditionIds 同事务写后读红线）；E1（TypeDefinitionAppServiceImpl 7 点=5 读+2 写）、E4（ResourceManageAppServiceImpl 4 点）、E7（ConditionAppServiceImpl 3 读）、E8（PermissionConditionDomainServiceImpl recycle）全部换挂新服务；`PermissionConditionDomainService` 新增 selectValidConditionsByIds/ByCodes/ByIdsNoTenant 三读（独立于 loadRules 缓存轨），#2（App 同事务新鲜读）与 #6（Plan 两调用点）换挂。grant↔rule 包环成立、bean 级无环（新服务零 rule bean 依赖）。

测试装配适配 11 文件：TypeDefinitionAppServiceImplTest（InOrder 级联 verify 改挂新服务不删锁）、ResourceManageAppServiceImplTest、ResourceDeletePermChangeRegistrationTest、ConditionAppServiceImplTest、PermissionConditionDomainServiceImplTest、BatchConditionEvaluatorTest、PermissionGrantAppServiceImplTest、PermissionGrantPlanDomainServiceImplTest、OperationLogRuntimeContextAppServiceTest、ResourceOperationKeyPgIT、ServiceConfigCascadePgIT（类型换血保变量名，stub/verify 零改写）。白名单同 commit 删 6 行（21→15 边/19→12 类）。

回归证据：`mvn test -pl access-service -DskipTestcontainers=true` → 1249/0/0（2026-09-15，t044_unit.log）；`mvn test -pl access-service` → 1249+210 双 fork 全绿 BUILD SUCCESS（2026-09-15，t044_full.log；9100 端口空闲）。设计回写：capability-structure §8.4 收敛进度注记更新至批次②。
