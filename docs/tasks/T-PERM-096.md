---
doc_type: task
id: T-PERM-096
title: 资源复合键内存索引结构化元组化（共享解析与转授索引）
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md §8（业务键统一构造）/§2.6（TypeResolutionService）/§2.8（PermissionGrantDomainService）
depends_on: []
blocks: []
acceptance:
  - "内存索引不再用分隔符拼接串区分 (code, codeType) 复合键：共享批量解析（batchResolveResourceIds 的 resourceCodeTypeKey）与转授索引（resourceTripleCodeKey/grantCheckKey）改结构化元组或等价无歧义形态，(sys:user,default) 与 (sys,user:default) 在新实现下是两个键（回归锁以碰撞对实证旧实现失败）"
  - "全部活跃消费面迁移：授权域/计划、UserMenuQueryAppServiceImpl、ResourceEntitySyncAppServiceImpl、ResourceManageAppServiceImpl 父解析、PermissionGrantPlanDomainServiceImpl.verifyDelegation 按串回读处；死代码（无生产调用的 applyDialogResult 族）不迁移不扩面"
  - "BusinessKeyUtil 格式 golden 锁与持久化/外部协议键（SyncKeyCodecUtil percent-encoded）不动——本任务只收内存索引，不改对外键格式"
  - "implementation §8 增补元组边界口径（内存索引禁拼接、持久化键维持 BusinessKeyUtil 单源）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-096 资源复合键内存索引结构化元组化（共享解析与转授索引）

## 背景

承接 [Q-044](../pending-problems.md#q-044) 后端半边：`code`/`codeType` 可含分隔符（`:` 等），内存索引把字段直接拼成字符串，不同元组同键——`(sys:user,default)` 与 `(sys,user:default)` 碰撞，共享批量解析可能错取资源 ID，转授资格判断可能串扰或误拒；数据库完整元组唯一性不拦此形态。前端半边（展示/决策键）由 T-FE-060 承接，修法面独立。

## 范围

- `TypeResolutionServiceImpl.batchResolveResourceIds` 的 `BusinessKeyUtil.resourceCodeTypeKey` 内存索引用途。
- `PermissionGrantDomainServiceImpl` 的 `resourceTripleCodeKey`/`grantCheckKey` 实例与结果索引、`PermissionGrantPlanDomainServiceImpl.verifyDelegation` 按串回读。
- 上列活跃消费面同批迁移；回归锁用 MANAGED 资源合法可建的碰撞对。

## 当前口径

现状：单条 `resolveResourceId` 为精确列查询不受影响；新 QueryReadSupport 已用元组。任务内要定的：共享映射改结构化元组（record 键/Map 元组键）后，旧拼接 helper 若仅剩索引用途则退役，持久化与对外协议键格式不动（golden 锁继续有效）。

## 非目标 / 遗留

- 前端键编码（T-FE-060）。
- BusinessKeyUtil 对外格式演进与「内部消费者全量迁移」的更大收口（Q-044 设想中「分开评估」的部分）。
