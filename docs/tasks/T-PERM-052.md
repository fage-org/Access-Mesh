---
doc_type: task
id: T-PERM-052
title: 资源同步双向所有权边界——sync 接管拒绝 + 管理面 SYNC 行只读 + FULL 删除归属核验
status: proposed
plan: docs/plans/design-audit-followup-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.3
  - docs/design/permission-center/api-contract.md#§6.2
  - docs/design/access-service-architecture.md#§4.3
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "sync 接管拒绝：ResourceEntitySyncAppServiceImpl.doSyncOneInternal 在 localProjectionGuard.rejectIfLocalResource 之后、applyVersion 之前增加同源归属校验（UserRoleSyncAppServiceImpl.ownedByCurrentSource 同款先例，但**位置取 applyVersion 之前**——user_role 先例实际在 applyVersion 之后、冲突时版本已推进；本处对齐本文件既有注释「所有权是安全边界，必须先于任何可提交的提前返回」）——existing 非空且不由当前 sourceService+scopeKey+businessKey 的 sync_metadata 指向（target_id 不匹配）**或 maintain_source 非 'SYNC'**（叠加维度：挡修复前已被接管的存量行，INSERT 分支写死 'SYNC'；亦挡 service-config 通道 SERVICE_SYNC 行）→ OWNERSHIP_CONFLICT non-retryable；UPSERT/DISABLE/DELETE 三分支统一前置；single 走 resolveTargetId 单条、full-sync 阶段 C 批量预加载 Map 传入（N+1 禁令，对齐 ownedTargetIdsByBusinessKeyHash 先例）"
  - "管理面只读（守卫须覆盖级联全集，复评审补强）：资源本体管理写路径对 maintain_source 非 MANUAL 的行前置拒绝，语义=「资源由外部来源维护，请到来源系统操作」；判定基于 maintain_source 白名单（MANUAL 可写）而非黑名单枚举；**remove 的后代级联删除必须对 allIdsToDelete 全集（含 batchGetDescendantIds 展开的后代，非仅请求根集合）执行同一守卫**——否则删一个 MANUAL 根会连带清掉子树中的 SYNC/本地投影行（现存 rejectIfLocalResource 同样未护后代，一并收口）；入口=updateResource/moveResource/deleteResources（同在 ResourceManageAppServiceImpl）+ 完整入口清单执行时盘点；新错误码 perm 段顺延（执行时与 T-PERM-046 全局域新码统一排号避免撞号）；授权（apply-grant-plan）/资源依赖/API 映射操作不受限——不触碰资源本体字段"
  - "FULL diff 删除归属核验（2026-09-05 复评审补强机制）：差异校准 deactivate 前核验目标行——maintain_source='MANUAL' 直接拦（异源 SYNC 行无法靠该列分辨，**新增 sync_metadata 按 target_id 反查**：同一 target_id 存在其他 sourceService 的有效 metadata 指向=异源接管残留，不软删）；命中拦截的行不软删、记 WARN 列明——修复上线前可能已发生存量接管的防御；**跳过软删时对应 metadata 不标 DELETED、保持原状**（标 DELETED 会造成行 ACTIVE 而 metadata DELETED 的反向漂移；每轮 FULL 重复 WARN 提示人工处置存量）；对齐 service-config §6.3「FULL diff 只软删同一 ownerServiceCode+SERVICE_SYNC 范围」先例"
  - "maintain_source 值域收口：resource-entity/sync 写入的 'SYNC' 值不在 schema 注释值域 {MANUAL, SERVICE_SYNC, SDK_SCAN, MANIFEST, ADMIN_UI} 内（列无 CHECK 约束静默落库）——值域与判定条件配套定案（注释收口登记 'SYNC' 值或统一取值，含存量行核对），与第 2 条白名单判定一致"
  - "契约回写：api-contract §6.2/§6.2.2 补 OWNERSHIP_CONFLICT 错误分类与接管拒绝语义；§5.3 update/move/remove 补「SYNC 行管理面只读」口径与新错误码"
  - "回归锁（须在旧实现下失败）：MANUAL 行与异源 SYNC 行 × {UPSERT、DISABLE、DELETE、FULL 差异删除} 的接管全部拒绝 + verify never 到 update/softDeleteBatch；管理面 update/move/remove 命中 SYNC 行拒绝 + never；**MANUAL 根 + SYNC 后代的级联删除拒绝**（旧实现会连后代一并软删）+ never 到后代 softDeleteBatch；同源行正常同步、本地投影既有拒绝行为不回归"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-05
---

# T-PERM-052 资源同步双向所有权边界——sync 接管拒绝 + 管理面 SYNC 行只读 + FULL 删除归属核验

> 状态：proposed（2026-09-05 设计体检 P1，逐条代码级核实后定案）
> 依赖：无硬依赖；与 T-PERM-051 的 TYPE_DEFINITION 保留清单、T-ADMIN-025 的 ADMIN_FILE 保留清单口径同向，排期互不阻塞

## 背景

resource-entity 外部同步与管理面当前都只有「本地投影」一道防线（`localProjectionGuard.rejectIfLocalResource`，owner=access-service 才拒绝），两个方向都能越界：

1. **sync → 人工/异源**：`doSyncOneInternal` 命中 MANUAL 行或他源 SYNC 行时直接走 UPDATE 分支覆盖字段并 `markStatus+backfillTargetId` 登记本源 sync_metadata——归属被夺走；随后 FULL 差异校准（他源 metadata 有、本次请求无）即软删；单条 `operation=DELETE` 更是一步直接软删。违反契约 §6.2.2「不删除 MANUAL 或其他维护来源创建的事实」。
2. **管理面 → sync**：`updateResource`/`removeResource` 等同样只拦本地投影，SYNC 行可被手工改（下次同步覆盖回，白改）或删（partial uk 下次同步重建**新 id**，旧 id 上授权全部悬挂失效）。

正确先例：user_role 同步每分支前有 `ownedByCurrentSource` 同源校验（OWNERSHIP_CONFLICT）；service-config 通道 FULL diff 只清自己 owner+SERVICE_SYNC 范围——resource-entity 通道两半都没搬全。

## 设计口径（2026-09-05 定案）

- **资源节点来源三分**：手工 `maintain_source=MANUAL` / 本系统投影 `owner=access-service` / 外部同步 `maintain_source='SYNC'+owner=NULL`（ownership 以 sync_metadata 为准）。**双向不可跨界**：同步不得接管人工/异源行；管理面不得写 SYNC 行。
- **SYNC 节点管理面完全只读**（含 name）：展示文案的本地调整走**类型名称**（type_definition.name，类型定义管理面另一对象，不受资源所有权限制）；名称归来源系统，同步 UPDATE 继续覆盖。
- 授权/资源依赖/API 映射是对 access-service 自有事实的操作，不触碰资源本体，不受限。

## 范围

- doSyncOneInternal 同源归属校验（single/full 两条路径）；
- 资源本体管理写路径全部入口的 SYNC 行拒绝（入口清单执行时盘点：update/move/remove/batch-remove 等）；
- FULL 差异删除归属核验 + 存量防御；
- maintain_source 值域与 schema 注释收口；
- 契约回写 + 双向回归锁。

## 非目标

- 不动 user_role 同步与 service-config 通道（先例本身正确）；
- 不做资源「显示别名」机制（2026-09-05 定案否决：SYNC 行连 name 也不开放本地改）；
- 不处理存量已接管数据的自动修复（FULL 删除防御核验挡住即可，修复留人工）。

## 已知边界（2026-09-05 复评审登记，另行评估）

- **资源同步通道删除无授权级联与快照失效**（存量行为，非本任务引入）：sync DELETE / FULL 差异校准只软删 resource_entity 行，不做该实体下授权行的级联软删、无 `@PermissionChange` 快照失效广播——对比管理面 `deleteResources` 的完整级联（授权软删 + 快照失效登记）。本任务的 FULL 防御会减少此类删除面，但不改变该行为；是否对齐管理面级联语义另行评估登记。
