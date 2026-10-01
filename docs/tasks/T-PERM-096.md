---
doc_type: task
id: T-PERM-096
title: 资源复合键内存索引结构化元组化（共享解析与转授索引）
status: done
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
  status: done
last_updated: 2026-10-01
---

# T-PERM-096 资源复合键内存索引结构化元组化（共享解析与转授索引）

## 完成记录（2026-10-01 收口）

- **实现**：`TypeResolutionServiceImpl.batchResolveResourceIds` 的 resourceLookup 改私有 record `ResourceCodeTypeKey(code, codeType)`（两侧 null→DEFAULT 归一后入键）；`PermissionGrantDomainService.checkCanGrant` 结果映射键改为入参 `GrantCheckKey` 本身（接口签名 `Map<String,...>`→`Map<GrantCheckKey,...>`），实例解析映射改私有 record `ResourceTripleKey`（保留原 `resourceTripleCodeKey` 的 typeCode 大写归一语义）；`PermissionGrantPlanDomainServiceImpl.verifyDelegation` 按元组直接回读。batchResolveResourceIds 返回值本为 `Map<ResourceResolveKey, Long>`，UserMenuQuery/ResourceEntitySync/ResourceManage 三调用方零改动即完成迁移。
- **helper 退役**：`BusinessKeyUtil.resourceCodeTypeKey`/`resourceTripleCodeKey`/`grantCheckKey` 删除（生产调用点仅剩上述索引用途，全仓 gateway/example/e2e 零引用）；类推清扫退役第四个零生产调用死方法 `resourceTripleValueKey`（消费方 ResourceManageAppServiceImpl 已先期改 `TripleKey` record）。ParityTest 对应 golden 同批删除，其余 golden 与 SyncKeyCodecUtil 不动。
- **20040 消息**：拒绝 message 的 `<key>` 部分改 `GrantCheckKey` record toString——契约总册只锁 `Cannot delegate <key>; reason=<REASON>` 形态；前端 grant-store 按 `includes("TYPE_GRANT_ORIGIN_MISSING")` 子串匹配（reason 段不变），核实零影响。
- **回归锁（旧实现实证红）**：TypeResolutionServiceImplTest 碰撞对 2 用例（临时退化 equals 复刻旧拼接语义红跑：`expected: <100> but was: <200>`——旧键后写覆盖先写）+ null→DEFAULT 归一分支用例；PermissionGrantDomainServiceImplTest t05（退化 equals 红跑：碰撞键 `Set.of` 直接抛 duplicate element——旧逻辑下两键连共存都不行）。
- **类推扫描**：`roleKey`/`subjectKey`/`roleProjectionIndexKey` 核实无碰撞形态（受限格式段在前、自由文本仅尾段，domainCode/subjectTypeCode 等均受 @Pattern 约束无分隔符）；`apiRouteResourceKey`（method|path|resourceCode——path 为 URL 自由文本处中段）存在理论碰撞面，属 Q-044「内部消费者全量迁移分开评估」非目标范围，用户拍板登记 [Q-056](../pending-problems.md#q-056)。
- **验证**：单测轨道 `-DskipTestcontainers=true` BUILD SUCCESS（228 报告文件）；perm-common ParityTest 22/22；收口全量 `-T 1C` 含 E2E/heavy 结果见看板行。
- **文档**：engine/implementation.md 新增 §8.4 内存索引元组边界（消费面表+归一语义）、§8.1 补退役登记；accessmesh-patterns skill 双副本同步（键族描述更新+元组边界口径）。

## 背景

承接 [Q-044](../pending-problems.md#q-044) 后端半边：`code`/`codeType` 可含分隔符（`:` 等），内存索引把字段直接拼成字符串，不同元组同键——`(sys:user,default)` 与 `(sys,user:default)` 碰撞，共享批量解析可能错取资源 ID，转授资格判断可能串扰或误拒；数据库完整元组唯一性不拦此形态。前端半边（展示/决策键）由 T-FE-060 承接，修法面独立。

## 范围

- `TypeResolutionServiceImpl.batchResolveResourceIds` 的 `BusinessKeyUtil.resourceCodeTypeKey` 内存索引用途。
- `PermissionGrantDomainServiceImpl` 的 `resourceTripleCodeKey`/`grantCheckKey` 实例与结果索引、`PermissionGrantPlanDomainServiceImpl.verifyDelegation` 按串回读。
- 上列活跃消费面同批迁移；回归锁用 MANAGED 资源合法可建的碰撞对。

## 当前口径

现状：单条 `resolveResourceId` 为精确列查询不受影响；新 QueryReadSupport 已用元组。任务内要定的：共享映射改结构化元组（record 键/Map 元组键）后，旧拼接 helper 若仅剩索引用途则退役，持久化与对外协议键格式不动（golden 锁继续有效）。

## 非目标 / 遗留

- 前端键编码（T-FE-060，同日完成）。
- BusinessKeyUtil 对外格式演进与「内部消费者全量迁移」的更大收口（Q-044 设想「分开评估」部分）——`apiRouteResourceKey`（path 中段自由文本）理论碰撞面经用户拍板登记 [Q-056](../pending-problems.md#q-056) 随清单批次排期。
