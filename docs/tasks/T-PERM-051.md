---
doc_type: task
id: T-PERM-051
title: TYPE_DEFINITION 实例投影与业务键统一——type-definition 写路径联动维护 resource_entity + 门禁消费方迁移（实例级授权可配 + list 实例级门禁通路）
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md#§12.3
depends_on: []
blocks: []
acceptance:
  - "投影链路核心范围（执行时细化）：type_definition 创建/更新/软删写路径同事务维护 resource_entity(TYPE_DEFINITION) 投影——落位 LocalProjectionDomainService（T-ACCESS-019 upsertRoleResource 模式：事实由调用方编排维护、本域服务只写投影，owner_service_code=access-service；同事务联动先例=创建 resource_type 同事务预置 CRUD 四操作位 insertPresetOperations）；存量有效行补投影迁移语句（rebuild-runbook 订正语句先例，幂等可重跑）【已落地：createType 联动 upsertTypeDefinitionResource / updateType name 变更同步 / deleteTypesByIds 级联软删；存量回填按 2026-09-07 定案改 bootstrap 启动自愈 + runbook FAQ 订正兜底】"
  - "投影 code 与范围设计定案（2026-09-05 定案）：投影范围=**全部三族**（user_type/role_type/resource_type 皆为 TYPE_DEFINITION 实例，一条规则无特例，对齐本地投影全量行先例）；投影与实例业务键=不可变复合键 `{typeKey}:{typeCode}`（typeCode 仅 tenant+type_key 内唯一——种子 user_type 与 resource_type 均有 USER/SERVICE 同名行，resource_entity uk(tenant, resource_type, code, code_type) 下裸 code 跨族撞唯一索引）；编码规则与长度上限随实现核对【已落地：三族全量投影；复合键经 BusinessKeys.typeInstanceBusinessKey 构造（T-PERM-019 预留方法）；长度核对命中最坏 129 > 旧列宽 128，2026-09-07 定案加宽 resource_entity.code 至 VARCHAR(256)】"
  - "实例业务键统一与既有门禁消费方迁移（键已定 `{typeKey}:{typeCode}` 复合）：现状四处键形态不一致——list/count 门禁 requireTypeViewPermission 按裸 typeCode 判实例权限，detail/update 把 type_definition.id 字符串当业务编码传编码轨（ID 空间错位形态，getType L204/updateType L303），remove 把 type_definition.id 直传实体轨 getDeniedEntityIds（deleteTypesByIds，同款错位）——全部消费方迁移到统一复合键（批量删除改批量解析到统一键），否则任一 code 形态选择都至少一组调用方无法命中；各消费方补 VIEW/MANAGE 的 DB 级通路测试【已落地：四处全部迁移；载行前置构键，行缺失退化为类型级校验保持 fail-closed 可观察行为；TypeDefinitionProjectionPgIT DB 级通路用例 + 单测复合键回归锁】"
  - "实例级通路 DB 级验证：投影落地后 type-definition/list 实例级门禁路径（requireTypeViewPermission 第二段 getDeniedResourceCodes）从「无自动产出」变可达——现有用例系 mock engine 锁定语义，须补 DB 级通路用例（构造仅实例级 TYPE_DEFINITION:VIEW 的角色实测 list 通过、全实例拒绝仍 403）；授权页可对 TYPE_DEFINITION 实例配置实例级授权（资源选择器/矩阵按现有 resource_type-实例模式呈现）【已落地：instanceLevelViewShouldUnlockListGate 等 PgIT 用例；授权页走资源选择器既有通用链路，前端零改动（类型经 list/tree 可读，SYNC 只拒写）】"
  - "引用面协调（2026-09-05 定案：加入保留清单）：TYPE_DEFINITION 加入 create/batch-create 类型保留清单（在 {USER,ORG,MENU,ROLE} 基础上新增）——人工不得绕过管理事实链路手工构造投影（投影 owner=access-service，与资源同步双向所有权 T-PERM-052 口径同向；update 不查清单、按本地投影所有权保护的既有口径不变）；T-PERM-050 类型删除级联的引用面盘点新增「类型定义自身投影行及其下授权行」处置语义，两侧排期协调【已落地：2026-09-07 定案按机制现状落为种子声明 SYNC+access-service（第六个事实链路类型，清单机制已被 T-PERM-052 收编不复活）；T-PERM-050 卡已登记协调注记】"
  - "回归测试：投影同生共死（创建联动同事务、失败回滚、软删级联含该投影行下授权行处置语义）、name 变更同步、存量迁移幂等重跑；类型级门禁语义与 bootstrap 固定图不变（固定图无 TYPE_DEFINITION 实例级条目、不因投影新增）【已落地：TypeDefinitionProjectionPgIT（自愈幂等/同生共死回滚/list 通路/detail+update 复合键/删除级联三表/20055）+ TypeDefinitionAppServiceImplTest 复合键回归锁组；固定图零改动】"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-07
---

# T-PERM-051 TYPE_DEFINITION 实例投影与业务键统一——type-definition 写路径联动维护 resource_entity + 门禁消费方迁移

> 状态：done（2026-09-07 收口）
> 依赖：无硬依赖（与 T-PERM-050 引用面协调已在本卡收口时同步登记至对方任务卡）
> 前置验收：见 acceptance（各条内嵌落地状态）

## 背景

2026-09-03 T-FE-018 决策修订实施（type-definition/list 门禁经用户决策放宽为「类型级**或任一实例级** VIEW 均可查询」，`TypeDefinitionAppServiceImpl.requireTypeViewPermission`）中查库核实：TYPE_DEFINITION 实例在 resource_entity 表 **0 行投影**。

- **配置侧**：无自动产出链路（type-definition 写路径无投影联动）；人工经 resource-entity 管理入口**可手工构造**实例（TYPE_DEFINITION 不在 create/batch-create 类型保留清单），构造后授权页可选、实例级授权可配——本任务缺环是自动联动与消费方业务键统一，非「可配性」本身。
- **存储侧**：`role_resource_permission.resource_entity_id` 引用 resource_entity.id 空间，type_definition.id 直填属 ID 空间错位（与 CONDITION/CONFLICT_RULE/DEPENDENCY 同款，T-PERM-048 登记类）——且现状四处门禁消费方键形态互不一致（裸 typeCode / id 字符串编码轨 / id 实体轨，见 acceptance 第 3 条）。

后果边界（2026-09-03 修正）：实例级路径无自动产出、当前库 0 行投影，实例级判定实际落入全拒分支——全拒按**去重码集**比较 fail-closed（跨 type_key 重码曾致 denied(去重)≥codes(含重复) 恒 false 的 fail-open，2026-09-03 修复并以重码全拒用例锁定）；类型级授权行为不变；当前库无实例级 TYPE_DEFINITION 授权存量（0 行核实），无受影响账号。本任务为功能债：自动投影联动 + 消费方业务键统一落地前，「按类型粒度分配类型管理权限」不成立。

与 T-PERM-048 登记类同根因家族但方向相反：那三域（CONDITION/CONFLICT_RULE/DEPENDENCY）实例级门禁声称系 ID 空间错位、已收窄类型级废弃；本域门禁经用户决策放宽，投影与业务键统一是缺环。

## 范围

- TYPE_DEFINITION→resource_entity 投影链路：创建联动 / name 变更同步 / 软删级联（含投影行下授权行处置语义）/ 存量补齐迁移。
- 投影按定案执行：三族全量投影、`{typeKey}:{typeCode}` 复合业务键、TYPE_DEFINITION 纳入事实链路类型族（SYNC 声明）。
- 实例级门禁通路 DB 级验证 + 授权页实例级授权可达。
- 引用面协调：T-PERM-050 级联范围同步。
- 不改类型级门禁语义；不动 bootstrap 固定图（不新增 TYPE_DEFINITION 实例级条目）。

## 优先级依据

无越权风险（重码 fail-open 已修复，见背景）、当前库无存量受影响账号；价值为支撑「按类型粒度分配类型管理权限」场景（如仅授某几个资源类型的类型管理员）。属低风险功能债，随 T-PERM-044~048/050 加固批次排期。

## 实现记录（2026-09-07 收口）

**执行定案（同批 AskUserQuestion，已登记 decision-registry 2026-09-07）**：

1. **键长溢出**：`{typeKey}:{typeCode}` 最坏 64+1+64=129 > resource_entity.code 旧列宽 128 → 加宽至 **VARCHAR(256)**（schema 唯一权威一列，零行为变化；手工/同步入口编码长度语义不变）。
2. **删除级联**：类型软删对「投影行下授权行」=**级联软删**（deleteResources 同款：软删前 selectRoleIdsByResourceIds→markRoles、apiMappingMapper 查引用→markServiceCodes、selectValidPermIdsByResourceIds；类型行→投影行→授权行同事务软删；deleteTypesByIds 补 `@PermissionChange`）。
3. **保留机制现代形态**：2026-09-05「保留清单新增」定案按机制现状落为**种子声明 SYNC+access-service**（DDL 种子 UPDATE IN 清单第六项；20055 message 补「类型定义管理」字样；清单机制已被 T-PERM-052 收编，不复活）。
4. **存量回填**：**bootstrap 启动自愈**（`LocalProjectionDomainService.backfillTypeDefinitionProjections`，置于三状态检测前 no-op 路径同样执行，对齐 T-ADMIN-025 文件夹投影先例）+ runbook FAQ 订正语句兜底（覆盖 bootstrap 未启用/多租户场景；自愈仅覆盖 bootstrap 租户 1）。

**交付清单**：

- `LocalProjectionDomainService`（+Impl）：`upsertTypeDefinitionResource`（复合键 upsert，parent 恒 null、status 恒启用、owner=access-service）/ `findTypeDefinitionResourceIds`（批量定位供级联）/ `backfillTypeDefinitionProjections`（自愈补种，批量 insert-if-absent）。
- `TypeDefinitionAppServiceImpl`：createType 同事务投影联动；getType/updateType 先载行构复合键再门禁（行缺失退化类型级校验，保持「无权限先于 NOT_FOUND 抛出」既有可观察行为）；deleteTypesByIds 批量复合键编码轨门禁（载行空集退化类型级 fail-closed）+ 级联；updateType name 变更同步投影（name 未提供不触发投影写）。
- `TypeDefinitionMapper`：`selectValidCodesByTenant`（裸 code）退役，新增 `selectValidByTenant`（TypeDefinition 行集，内存构复合键——格式不在 SQL 拼串）。
- `ResourceTypeOwnershipGuard`：内部来源 20055 message 与 javadoc 补 TYPE_DEFINITION。
- `AccessBootstrapInitializer.initialize`：自愈补种调用（无新增固定图条目）。
- schema：code 列宽 256 + 列注释；种子声明 IN 清单增 TYPE_DEFINITION + 注释。

**口径与边界**：

- 原跨 type_key 重码 fail-open 回归锁场景（裸 code 含重复）随复合键行级唯一**结构性消除**（复合键由 (typeKey,typeCode) 行级唯一推导），去重集合入参与 denied.size() 比较口径不变；新增 shouldPassCompositeKeysToInstanceGate 锁定引擎入参键形态。
- deleteTypesByIds 微行为变化：mixed 批（存在行+不存在行）下不存在行不再阻断整批（旧实体轨把不可解析 id 计入 denied）；纯不存在批保持 fail-closed（类型级退化校验抛 SecurityException），已用例锁定。
- 授权页零前端改动：TYPE_DEFINITION 类型经资源 list/tree 可读（SYNC 只拒写 20055），实例按选择器既有模式呈现。
- 固定图零改动：bootstrap 自愈只补投影行，不新增 TYPE_DEFINITION 实例级授权条目。

**测试**（以 surefire 报告为准）：TypeDefinitionAppServiceImplTest 新增投影联动/name 同步条件/复合键门禁回归锁（旧实现传 id 串必红）/批删引擎入参键集合 captor/级联三表软删 InOrder/不存在批 fail-closed 退化等用例；TypeDefinitionProjectionPgIT（真实 PG+Redis）覆盖自愈幂等、投影失败整体回滚、list 实例级通路（含零授权全拒）、detail+update 复合键命中与拒绝、删除三表级联+引擎复核、20055 只读；既有构造器调用点同步适配。

**文档回写**：api-contract §5.1（复合键/消费方迁移/六类型族口径+frontmatter）；architecture §4.3（事实链路族第六类型+两条产出链+级联）与 §12.3（TYPE_DEFINITION 复合业务键语义）+ frontmatter；runbook §1 步骤 4 自愈说明 + §3 FAQ 订正三件套（列宽/声明/投影兜底 SQL）；AGENTS.md 事实链路行；implementation.md §8.1 预留注记改已落地；T-PERM-050 卡引用面协调注记；本卡与看板收口。

## 关联

- T-FE-018（决策修订起因；api-contract §5.1 注记、frontend/permission-grant.md §10 注记指向本卡）
- T-PERM-048（无投影类家族：CONDITION 实例投影与双轨制构想）
- T-PERM-050（resource_type 删除级联与引用保护——引用面互需协调，协调注记已登记）
- T-PERM-052（类型级所有权：SYNC 声明机制即本卡保留机制落点）
- T-PERM-019（BusinessKeys.typeInstanceBusinessKey 预留方法，本卡消费）
