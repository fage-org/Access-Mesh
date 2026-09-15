---
doc_type: plan
title: 跨能力 mapper 直读收敛（Q-009 转出）
status: active
domain: access-service
design_refs:
  - docs/design/access-service-capability-structure.md#8-架构断言重建设计（§8.4 豁免 6 冻结白名单）
  - docs/design/project-rules.md#8-2-调用方向规范（能力包 Mapper 边界）
tasks:
  - T-ACCESS-043
  - T-ACCESS-044
  - T-ACCESS-045
  - T-ACCESS-046
acceptance: "冻结白名单 19 类 30 边全量收敛至零；QueryBoundaryArchitectureTest 白名单退役为绝对断言（能力包间 mapper 依赖零容忍）且负向自证有牙；全量回归 mvn test -T 1C（含 E2E）全绿；capability-structure §8.4 / project-rules §8.2 / access-service-architecture / permission-coding-standards §8 白名单口径回写完成；Q-009 移入已收敛索引"
last_updated: 2026-09-15
---

# 跨能力 mapper 直读收敛（Q-009 转出）

## 目标

将 T-ACCESS-032 裁决⑨冻结的存量跨能力 mapper 直读（19 类 30 边，闭合清单=capability-structure §8.4 豁免 6 表 = `QueryBoundaryArchitectureTest.FROZEN_WHITELIST` 30 行）全量收敛：每条边改走被读方 DomainService 封装，白名单退役为绝对断言。方向定案与实施契约见 decision-registry 2026-09-15 行（含外评 claude+grok 双通道修订要点）。

## 非目标

- 不把投影 writer 迁入 projection 包求豁免；不把 type 读全挂 engine 求零环（稀释能力所有权）——两条替代路线已否决。
- engine/projection/bootstrap/sync 的 mapper 直读（§8.4 豁免 1~4）不在本计划范围。
- 事务边界、缓存策略、SQL 形态一律语义保持——本计划是结构收敛，不是行为变更。

## 实施契约（全部新服务/新方法硬约束）

1. **宿主原则**：role/user 主体事实与主体 id 解析 4 边（R6/U2/U4/M7）收敛到 engine.core 既有 `SubjectDomainService`/`TypeResolutionService`（主体域服务既有宿主，属 §8.4 豁免 1 面——registry 已登记口径，不建能力包内 DomainService）；其余边落被读方能力包内**实体命名 DomainService**。
2. 新服务 bean **只依赖本包 mapper、不注入任何他能力包 bean**（Spring 无环；包级环 grant↔type、grant↔rule、domain→type→grant→domain 以 bean 级无环为准，type↔resource 双向为现行既有先例）。
3. 新读方法一律**无缓存直读**（同事务可见性语义保持；`PermissionConditionDomainService` 的新读禁止复用走 CONDITION_RULES 缓存的 `loadRules`）；不声明独立事务（REQUIRED 跟随调用方，事务边界仍在 AppService）。
4. 操作定义读**拆两个口径**：`selectAllOperationsByTenant(tenantId)`（= mapper `selectByTenantAndResourceType(tenantId, null)` 全租户直传，保 knownOperationCodes 的 20008/20005 契约区分）+ `selectByTenantAndResourceTypes(tenantId, types)`（IN 形态，**空集短路返回空列表**禁 `IN ()`）。
5. 白名单删行与代码收敛**同 commit**（双向锁：代码收敛 ⇒ 行删除、行删除 ⇒ shape 计数断言同步 30→21→15→4→0）。
6. 行为锁依赖既有测试（级联删 PgIT、applyGrantPlan 套件、投影 writer IT、createUser-带-org 链路、互斥/条件守卫用例）；`InOrder`/`verify(mapper)` 级联锁改挂新服务**不删锁**。

## 边清单（定位=「消费类:行号 → mapper#方法」；行号为 2026-09-15 立卡时点）

### 批次①（T-ACCESS-043，9 边）——既有服务直换 + 死边清理

| 消费类:行号 → mapper#方法 | 替代 |
|---|---|
| resource.service.domain.impl.ResourceEntityDomainServiceImpl:37/46/48 → grant.RoleResourcePermissionMapper（**死注入零调用**） | 删字段/构造参数/import |
| grant.service.impl.PermissionGrantAppServiceImpl:115 → role.AbstractRoleMapper#selectValidById | SubjectDomainService.selectValidRoleById（1:1 直传） |
| grant.service.domain.impl.GrantOriginDomainServiceImpl:127 → role.AbstractRoleMapper#selectValidById | 同上 |
| PermissionGrantAppServiceImpl:380（batchLoadResources）→ resource.ResourceEntityMapper#selectValidByIds | ResourceEntityDomainService.selectValidByIds（已存在） |
| grant.service.domain.impl.PermissionGrantPlanDomainServiceImpl:332 → resource.ResourceEntityMapper#selectValidByIds | 同上 |
| user.service.impl.UserManageAppServiceImpl:177/725/852/872 → role.AbstractRoleMapper（selectEnabledIdsByIds + selectValidByIds×3） | SubjectDomainService.selectValidRolesByIds×3 + **新增 selectEnabledRoleIds**（DB 侧过滤保持） |
| user.service.impl.UserAppServiceImpl:294/407/481/491/519/610 → org.SysUserOrgMapper | UserOrgDomainService.findByUserId/findByUserIds/findByOrgIds（已注入未用，1:1） |
| role.service.domain.impl.UserRoleProjectionWriter:63/99/134/245 → user.AbstractUserMapper#selectByTypeAndExternalId(s)（只消费 id） | TypeResolutionService.resolveUserId/batchResolveUserIds（行读取直查同事务可见；类型值一步 TYPE_VALUE 缓存为既有形态、四调用点已先 requireType；**resolve* 禁接结果缓存**） |
| rule.service.impl.ConflictRuleAppServiceImpl:102（内联 FQCN 字段）→ role.AbstractRoleMapper#selectValidByIds | SubjectDomainService.selectValidRolesByIds |

### 批次②（T-ACCESS-044，6 边）——grant 授权事实服务 + rule 条件读

| 消费类:行号 → mapper#方法 | 替代 |
|---|---|
| type.service.impl.TypeDefinitionAppServiceImpl:694/706/713/717/725/741/746 → grant.RoleResourcePermissionMapper（**7 点=5 读+2 写**） | 新建 RoleResourcePermissionDomainService（mapper-only） |
| resource.service.impl.ResourceManageAppServiceImpl:534/547/551/557 → grant.RoleResourcePermissionMapper（3 读+1 写） | 同上 |
| rule.service.impl.ConditionAppServiceImpl:303/315/356 → grant.RoleResourcePermissionMapper（3 读） | 同上 |
| rule.service.domain.impl.PermissionConditionDomainServiceImpl:581 → grant.RoleResourcePermissionMapper#selectReferencedConditionIds（**同事务写后读红线**） | 同上（无缓存直读 + REQUIRED，三调用方事务内可见软删后状态） |
| grant.service.impl.PermissionGrantAppServiceImpl:235 → rule.PermissionConditionMapper#selectValidByIdsNoTenant（**同事务新鲜读红线**：读 apply 刚 INSERT 的内联条件行） | PermissionConditionDomainService **新增** selectValidByIdsNoTenant（禁复用 loadRules 缓存路径） |
| grant.service.domain.impl.PermissionGrantPlanDomainServiceImpl:210/256 → rule.PermissionConditionMapper（selectValidByCodes/selectValidByIds，INLINE 过滤口径随迁） | 同服务**新增**两读方法 |

### 批次③（T-ACCESS-045，11 边）——type/resource/domain 供给读服务

| 消费类:行号 → mapper#方法 | 替代 |
|---|---|
| PermissionGrantAppServiceImpl:283 → type.OperationPermissionMapper#selectByTenantAndResourceType(tenantId,null) | 新建 OperationPermissionDomainService.**selectAllOperationsByTenant**（全租户口径保持） |
| PermissionGrantPlanDomainServiceImpl:194 → 同上（knownOperationCodes，**保 20008/20005 区分**） | 同上 |
| grant.service.domain.impl.PermissionGrantDomainServiceImpl:209 → type.OperationPermissionMapper#selectByTenantResourceTypesAndOpCodes（刻意缓存回避面） | 同服务直传（**无缓存**，勿接 OPERATION_PERMISSIONS_BY_TYPE） |
| PermissionGrantDomainServiceImpl:416 → type.TypeDefinitionMapper#selectValidByTenant | 新建 TypeDefinitionDomainService |
| GrantOriginDomainServiceImpl:225 → type.OperationPermissionMapper#selectByTenantAndResourceTypes | OperationPermissionDomainService IN 形态（空集短路） |
| resource.service.impl.DependencyAppServiceImpl:866 → type.OperationPermissionMapper#selectValidByIds | 同服务 |
| rule.service.domain.impl.PermissionConflictDomainServiceImpl:400/421 → type.OperationPermissionMapper（逐类型循环，合并单 IN 属改善） | 同服务 IN 形态（无缓存直读语义保持） |
| domain.service.domain.impl.DomainClassifyServiceImpl:312 → type.TypeDefinitionMapper#selectByTenantAndTypeKey | TypeDefinitionDomainService |
| TypeDefinitionAppServiceImpl:698 → resource.ResourceApiMappingMapper#selectByResourceEntityIds | 新建 ResourceApiMappingDomainService |
| type.service.domain.ResourceTypeOwnershipGuard:202/320 → resource.ServiceConfigMapper#selectByTenantAndServiceCode | 新建 ServiceConfigDomainService |
| PermissionGrantPlanDomainServiceImpl:628/683 → domain.DomainConfigMapper#selectByTenantId（SUB_PERM 策略） | 新建 DomainConfigDomainService |

### 批次④（T-ACCESS-046，4 边 + 收口）——user-role 原始行/投影读写 + 白名单退役

| 消费类:行号 → mapper#方法 | 替代 |
|---|---|
| UserManageAppServiceImpl:362/468/597/743/842（5 读，含**单数** selectValidByUserIdsAndTargetId）+ :365/530/638/794（4 写）→ role.UserRoleMapper | SubjectDomainService 扩展 user_role **无缓存原始行层**（4 读含单数直传 + 3 写；保留 :180 batchResolveEffectiveRoles 缓存路径在 insert 前的既有顺序） |
| user.service.domain.impl.BatchAdminUserProjectionWriter:68 → role.UserRoleMapper#softDeleteByAbstractUserIds | 同上写方法 |
| 同 writer:72/94/116/180/187 → resource.ResourceEntityMapper（code_type=default 投影读 + 4 写） | ResourceEntityDomainService 扩展投影轨方法（softDeleteBatch 已有） |
| menu.service.impl.UserMenuQueryAppServiceImpl:341 → role.UserRoleQueryMapper#selectUserRoleProjections（保留 try-catch 登录容错，勿走带 VIEW 门禁 AppService） | SubjectDomainService.selectUserRoleProjections（无门禁投影直传） |

## 测试装配适配清单（外评 claude 实测 26 文件直接构造受影响类，位置定参无 @InjectMocks；以实施时 rg 实测为准）

- **批次①**：PermissionGrantAppServiceImplTest、PermissionGrantPlanDomainServiceImplTest、GrantOriginDomainServiceImplTest、UserManageAppServiceImplTest、UserAppServiceResetPasswordGateTest、OperationLogRuntimeContextAppServiceTest、ResourceOperationKeyPgIT、ConflictRuleAppServiceImplTest、SubjectDomainServiceImplTest（新增 selectEnabledRoleIds 用例）、LocalProjectionDomainServiceImplTest（writer 构造改线）、AuthorizationCacheBypassToDbTest、StaleBackfillLatchTest。
- **批次②**：TypeDefinitionAppServiceImplTest（InOrder 级联 verify 改挂新服务不删锁）、ResourceManageAppServiceImplTest、ResourceDeletePermChangeRegistrationTest、ConditionAppServiceImplTest、PermissionConditionDomainServiceImplTest、PermissionGrantAppServiceImplTest、PermissionGrantPlanDomainServiceImplTest。
- **批次③**：DependencyAppServiceImplTest、PermissionConflictDomainServiceImplTest、BatchConditionEvaluatorTest、BatchPermMutexEvaluatorTest、DomainClassifyServiceImplTest、ResourceTypeOwnershipGuardTest、ServiceConfigCascadePgIT、grant 三测试；20008/20005 区分用例（PermissionGrantPlanDomainServiceImplTest 733-828）装配改线后仍锁。
- **批次④**：UserManageAppServiceImplTest、UserMenuQueryAppServiceImplTest、LocalProjectionDomainServiceImplTest、SubjectDomainServiceImplTest。

## 归档条件

四任务全 done：白名单退役为绝对断言 + fixture 负向自证（`src/test/java/.../architecture/fixture/` 违规样例包 + 专用 ClassFileImporter 单独导入，绕开 DO_NOT_INCLUDE_TESTS）、治理回写完成（capability-structure §8.4 豁免 6 表+§4 裁决表 row9、project-rules §8.2 白名单指针句、access-service-architecture「存量 19 类 30 边」句、permission-coding-standards §8、全仓残留清扫）、Q-009 收敛、全量回归含 E2E 绿。

## 当前进度

- 2026-09-15：计划立项（Q-009 转出）；claude+grok 双通道外评处置完毕（P2×3+P3×4 / P2×2+P3×1，全采纳），用户确认修订版后开工。
- 2026-09-15：T-ACCESS-043 批次① done（9 边收敛、白名单 30→21、模块双 fork 1249+210 全绿）；批次② in-progress。
- 2026-09-15：T-ACCESS-044 批次② done（6 边收敛、RoleResourcePermissionDomainService + 条件三读落地、白名单 21→15、模块双 fork 全绿）；批次③ in-progress。
