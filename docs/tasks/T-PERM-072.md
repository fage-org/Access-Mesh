---
doc_type: task
id: T-PERM-072
title: 自动授权物化器（AUTO_DEP + support + 触发面 + 锁模型）
status: proposed
plan: —（无所属计划；T-PERM-035 实现序列 035B）
domain: permission-center
design_refs:
  - docs/design/dependency-auto-grant.md#§3.5（role_permission_auto_support）
  - docs/design/dependency-auto-grant.md#§3.6（role_resource_permission 零改动论证）
  - docs/design/dependency-auto-grant.md#§6（AutoGrantMaterializer：种子/闭包/压制/多变体/反链/support）
  - docs/design/dependency-auto-grant.md#§7（rebuild 触发面 7 项）
  - docs/design/dependency-auto-grant.md#§8（锁模型与事务边界）
  - docs/design/engine/core-flows.md（§12 场景九回写）
  - docs/design/engine/implementation.md（grantDepId 清理链回写）
  - docs/design/schema/access-service.sql（auto_support DDL + INLINE 注释回写）
  - docs/design/access-service-api-contract.md（⑦ 引用拒绝错误码与操作生命周期段）
depends_on:
  - T-PERM-071
blocks: []
acceptance:
  - "schema：role_permission_auto_support（uk 含 source_grant_id 与 COALESCE 组、WHERE delete_flag=0；路径条件/parent_support_id）——四类回归用例齐备"
  - "物化器核心：种子定义（§6.1 含条件行与判定面继承不展开边界）；闭包批量装载内存图（禁 N+1、跨 owner ADMIN_UI 边时装载租户级图）；触发证明=单操作×条件组（NULL 组吸收非空、多变体并存）；覆盖压制含条件比较；反链压缩含「条件不更窄」；INLINE 1:N 派生引用（T-PERM-048 口径修订）"
  - "support 二遍路径追踪 035B 一步到位（闭包稳定后同事务补全，含窄路径下游与被压制节点推导档案）"
  - "applyGrantPlan 挂接 + 触发面 7 项全量落地（含⑦ binaryBit 变更/操作删除引用拒绝、inheritMask 变更重编译、③④⑦ manifest 置脏）；⑦ 引用拒绝为**新增对外拒绝语义**——错误码排号 + 契约总册 §5.3 操作生命周期段回写（沿用/扩写 20059 措辞需明示）"
  - "INLINE 回收统一模式：①③④⑤收集候选→删除+rebuild→事务末尾一次回收；既有各入口回收调用时序统一后移"
  - "两级锁全序（树锁族内次序→编译锁→角色锁升序）+ 七入口取锁矩阵 + 锁序断言；缓存失效 afterCommit；v1 全同步"
  - "设计回写：engine/core-flows.md §12 场景九、engine/implementation.md grantDepId 清理链、schema permission_condition.source INLINE 注释（1:1→1 属主+N 派生引用）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-19
---

# T-PERM-072 自动授权物化器（035B）

## 背景

T-PERM-035 v1 设计定稿（2026-09-19，用户确认）。授权/依赖变更在写路径同事务物化 AUTO_DEP（单 canonical 操作位/行、canGrant=false、条件多变体），鉴权热路径零改动（三条 SQL 无 grant_source 谓词，已核实）。

## 范围

设计稿 §3.5/§3.6/§6/§7/§8 全部：auto_support 表、物化器算法、applyGrantPlan 挂接、触发面 7 项、INLINE 回收、两级锁与事务边界。

## 非目标 / 遗留

- 熔断/队列/异步重建（§14.4）、scope_all 种子、条件路径级追踪（§14.3）、类型级依赖（§14.2）：演进项不做。
- 存量树锁反序（TypeDefinitionAppServiceImpl 混合批删）收敛：随本卡锁序断言一并统一（设计稿 §8.1 登记）。
- explain/Reconciler/Admin UI：T-PERM-073。

## 验收对照

见 acceptance；核心语义（证明/多变体/压制/反链）回归锁用例清单见设计稿 §6.2。
