---
doc_type: task
id: T-PERM-051
title: TYPE_DEFINITION 实例投影——type-definition 写路径联动维护 resource_entity（实例级授权可配 + list 实例级门禁通路）
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md#§12.3
depends_on: []
blocks: []
acceptance:
  - "投影链路核心范围（执行时细化）：type_definition 创建/更新/软删写路径同事务维护 resource_entity(TYPE_DEFINITION) 投影——落位 LocalProjectionDomainService（T-ACCESS-019 upsertRoleResource 模式：事实由调用方编排维护、本域服务只写投影，owner_service_code=access-service；同事务联动先例=创建 resource_type 同事务预置 CRUD 四操作位 insertPresetOperations）；存量有效行补投影迁移语句（rebuild-runbook 订正语句先例，幂等可重跑）"
  - "投影 code 与范围设计定案（执行时决策）：typeCode 仅 tenant+type_key 内唯一（种子 user_type 与 resource_type 均有 USER/SERVICE 同名行），resource_entity uk(tenant, resource_type, code, code_type) 下跨 type_key 同名投影会撞唯一索引——候选 `{typeKey}:{typeCode}` 限定形态 vs 仅投影 resource_type 子集；同时定夺投影范围：全部 type_key 行（user_type/role_type/resource_type 皆为 TYPE_DEFINITION 实例）vs 仅 resource_type 行（实例级类型管理的实际语义面）"
  - "实例级通路 DB 级验证：投影落地后 type-definition/list 实例级门禁路径（requireTypeViewPermission 第二段 getDeniedResourceCodes）从「暂不可达」变可达——现有三用例系 mock engine 锁定语义，须补 DB 级通路用例（构造仅实例级 TYPE_DEFINITION:VIEW 的角色实测 list 通过、全实例拒绝仍 403）；授权页可对 TYPE_DEFINITION 实例配置实例级授权（资源选择器/矩阵按现有 resource_type-实例模式呈现）"
  - "引用面协调（执行时决策）：/perm/resource-entity/create|update 类型保留清单 {USER,ORG,MENU,ROLE} 是否扩 TYPE_DEFINITION（人工绕过管理事实链路禁入 vs 允许手工补投影，architecture §4.1/§12.3 口径）；T-PERM-050 类型删除级联的引用面盘点新增「类型定义自身投影行及其下授权行」处置语义，两侧排期协调"
  - "回归测试：投影同生共死（创建联动同事务、失败回滚、软删级联含该投影行下授权行处置语义）、name 变更同步、存量迁移幂等重跑；类型级门禁语义与 bootstrap 固定图不变（固定图无 TYPE_DEFINITION 实例级条目、不因投影新增）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-03
---

# T-PERM-051 TYPE_DEFINITION 实例投影——type-definition 写路径联动维护 resource_entity

> 状态：proposed（T-FE-018 决策修订落地时查库登记，2026-09-03 用户决策「记录成问题，后面处理」）
> 依赖：无硬依赖（与 T-PERM-050 引用面互需协调，见 acceptance 第 4 条）
> 前置验收：见 acceptance

## 背景

2026-09-03 T-FE-018 决策修订实施（type-definition/list 门禁经用户决策放宽为「类型级**或任一实例级** VIEW 均可查询」，`TypeDefinitionAppServiceImpl.requireTypeViewPermission`）中查库核实：TYPE_DEFINITION 实例在 resource_entity 表 **0 行投影**。实例级授权的两个环节因此均不可构造：

- **配置侧**：授权页资源选择器选不到 TYPE_DEFINITION 实例（无投影行可列）；
- **存储侧**：`role_resource_permission.resource_entity_id` 引用 resource_entity.id 空间，type_definition.id 直填属 ID 空间错位（与 CONDITION/CONFLICT_RULE/DEPENDENCY 同款，T-PERM-048 登记类）。

后果边界（已核实，无风险项）：放宽后的实例级路径**暂不可达**——`getDeniedResourceCodes` 对无投影码落 denied 集（fail-closed），不会经此路径误放行；类型级授权行为不变；当前不存在「仅实例级 TYPE_DEFINITION:VIEW」的存量账号（此类授权本来就配不出来），无受影响者。即本任务为**功能债**（门禁已放宽、投影是唯一缺环），非安全缺陷。

与 T-PERM-048 登记类同根因家族但方向相反：那三域（CONDITION/CONFLICT_RULE/DEPENDENCY）实例级门禁声称系 ID 空间错位、已收窄类型级废弃；本域门禁经用户决策放宽，投影成为唯一缺环。

## 范围

- TYPE_DEFINITION→resource_entity 投影链路：创建联动 / name 变更同步 / 软删级联（含投影行下授权行处置语义）/ 存量补齐迁移。
- 投影 code 形态与投影范围（全部 type_key vs 仅 resource_type）设计定案。
- 实例级门禁通路 DB 级验证 + 授权页实例级授权可达。
- 引用面协调：resource-entity 管理入口保留清单定夺、T-PERM-050 级联范围同步。
- 不改类型级门禁语义；不动 bootstrap 固定图（不新增 TYPE_DEFINITION 实例级条目）。

## 优先级依据

无越权风险（fail-closed 已核实）、无存量受影响账号；价值为支撑「按类型粒度分配类型管理权限」场景（如仅授某几个资源类型的类型管理员）。属低风险功能债，随 T-PERM-044~048/050 加固批次排期。

## 关联

- T-FE-018（决策修订起因；api-contract §5.1 注记、frontend/permission-grant.md §10 注记指向本卡）
- T-PERM-048（无投影类家族：CONDITION 实例投影与双轨制构想）
- T-PERM-050（resource_type 删除级联与引用保护——引用面互需协调）
