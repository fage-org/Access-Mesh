---
doc_type: task
id: T-PERM-045
title: 内部管理门禁统一启用子级继承（项目规则「父级有权限子级即有权限」）
status: cancelled
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.2
  - docs/design/frontend/role-manage.md#§8
  - docs/design/permission-center/implementation.md#§3.1
depends_on: []
blocks: []
acceptance:
  - "明确启用范围：内部管理门禁统一走向（forAuthCheck 走线的 hasPermissionByCode/getDeniedResourceCodes）默认启用子级继承（inheritChildren——授于父资源的实例权限覆盖子孙资源）；运行时 /auth/check 按调用方显式传参不变；外部 SDK 查询语义不受影响"
  - "继承展开与既有机制的一致性核实：条件权限/冲突规则对 INHERITED 克隆条目的评估语义、缓存与快照链路（expandByInheritMode 每查询加载全量资源实体构图的性能面）、scopeAll 条目不参与展开的既有不变量"
  - "全量回归：角色/用户/域/日志等全部内部实例门禁行为核对 + 容器轨验证 + 文档回写（api-contract 门禁语义、architecture §14.x、implementation §3.1）"
  - "deleteRoles 级联注释同步：本任务落地后删除「不对子孙做独立权限过滤」的局部实现口径，回归统一门禁语义"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-045 内部管理门禁统一启用子级继承

> 状态：cancelled（2026-09-09 取消——范围整体并入 [T-PERM-057](T-PERM-057.md) 权限查询统一引擎重构：2026-09-09 grill 定案将「门禁默认启用子级继承」扩展为统一引擎全模型（判定面/展示面两语义拆分 + 默认值矩阵 + 评估拉平），本卡范围为子集；终态设计见 [query-engine-unification.md](../design/permission-center/query-engine-unification.md)。取消时无下游依赖（grep depends_on 零命中）。）
> 依赖：无（独立权限语义收口任务）
> 前置验收：见 acceptance（随取消作废，被 T-PERM-057 acceptance 覆盖）

## 背景

项目权限规则（2026-08-28 设计定案）：**父级有权限，子级即有权限**——授于父资源的实例级权限应覆盖其子孙资源。`expandByInheritMode` 的多种继承模式服务于不同查询场景（如前端仅查选中节点不需子级展开）与外部系统按需校验，不改变项目自身规则。

现状缺口：内部管理门禁统一走 `forAuthCheck` 工厂（`hasPermissionByCode`/`getDeniedResourceCodes`），**不启用继承**；全仓仅运行时 `/auth/check`（`PermissionCheckAppServiceImpl`）按请求传入可选 `inheritMode`。因此「操作者对父资源有实例级权限、对子资源无」在管理路径是常态。

T-PERM-022 外部复评暴露的具体后果（已按局部最小修处置，见 role-manage.md §8）：deleteRoles 级联删除若对子孙做独立权限过滤，会出现「删父留子」的悬挂子树——已改为「级联根有权即整棵子树可删」（局部实现项目规则）。本任务将该规则统一到引擎层，消除各内部门禁的口径分叉。

## 范围

- 引擎工厂默认值与调用方语义梳理：哪些门禁启用 `inheritChildren`（资源树型类型如 ROLE/RESOURCE 收益明确；扁平类型如 USER 无子级、启用与否等价）。
- 继承条目（grantSource=INHERITED）与条件/冲突评估、缓存链路的相互作用核实。
- 快照/Gateway 链路若涉及实例门禁的同步评估。

## 关联

- 局部先行实现：`RoleManageAppServiceImpl.deleteRoles`（级联根有权=整棵子树可删）。
- 遗留登记：[[T-PERM-044]]（四棵树并发成环窗口，2026-08-30 扩入 resource_entity）为独立维护债，不与本任务互依赖。
