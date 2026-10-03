---
doc_type: task
id: T-ADMIN-031
title: 菜单 create/delete 补树写互斥
status: done
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md §17（四棵树 parent 写并发与环防护）
  - docs/design/access-service-api-contract.md §9（menu 能力）
depends_on: []
blocks: []
acceptance:
  - "createMenu（读父后插入）与 deleteMenu（查子后软删）在首次树读取前持 SYS_MENU 树写锁，与 updateMenu 同形态（锁先于首次读取，T-PERM-044 定案的锁序约束）"
  - "并发「删父 + 挂子」不再产生指向软删父的存活子节点（并发用例实证；正常树从根可达性断言）"
  - "DomainService 层不补锁（Controller 写入口统一持锁）；architecture §17 的树清单补 menu 语义缺口语径（如需）"
  - "回归锁以并发交错场景在旧实现下失败实证"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ADMIN-031 菜单 create/delete 补树写互斥

## 背景

承接 [Q-048](../pending-problems.md#q-048)：`MenuWriteAppServiceImpl` 的 createMenu 读父后插入、deleteMenu 查子后软删，均无 SYS_MENU 锁；updateMenu 已在首次读取前持锁。有写权限的并发删父、挂子调用可留下指向软删父的存活子节点，正常树无法从根到达。当前无菜单管理 UI，风险限于满足门禁的写调用；未做并发复现（本任务补并发实证）。

## 范围

create/delete 两入口补锁（对齐 updateMenu 先例）；并发回归用例；§17 口径核对。

## 当前口径

树写锁约束（architecture §17）适用于四棵树；menu 树 update 已覆盖、create/delete 是漏网半边。锁实现以 architecture §17.1 现行定案为准（Redisson 树级可重入锁，复用 `TreeWriteLockSupport.lockTreeWrites`，afterCompletion 释放）；首版 pg_advisory_xact_lock/mapper 语句级锁是被替换的历史形态，不得采用。

2026-10-03 收口实现终态：

- `createMenu`/`deleteMenu` 各补一次 `lockTreeWrites(tenantId, SYS_MENU)`，位置对齐 `updateMenu`（tenantId 解析后、门禁前、任何树读取前——组织面 createOrg/deleteOrg 三锁先例与 §17.1「锁先于所有权门禁与首次实体读取」同款）。deleteMenu 原序「门禁→tenantId→读」随之调整为「tenantId→锁→门禁→读」（门禁失败时锁随事务回滚 afterCompletion 释放，无行为面差异）。修法无分叉：验收已钉死「与 updateMenu 同形态」。
- DomainService 层零改动（验收第 3 条：写入口统一持锁，锁属编排层职责）。
- 回归锁三面：①单测 `MenuWriteAppServiceTest` 新增 2 用例（create/delete 各一：verify 恰好一次 `lockTreeWrites(TENANT, SYS_MENU)` + InOrder 锁先于 `selectValidById` 首次树读取）——旧实现下 2 红（verify wanted but not invoked），新实现该类全绿；②PgIT `TreeCycleHardeningPgIT.concurrentCreateUnderDeletingParentCannotOrphan`——删除方经 TransactionTemplate 外层事务挂起于「hasChildren 已过、软删已写、未提交」点（锁实现下持 SYS_MENU 锁），挂子方进场；有界等待分流（`get(2s)` 直通=旧实现 / 超时=阻塞在锁）+ 汇合终态断言（挂子拒 `MENU_NOT_FOUND(10201)` + 无存活子行 + 父已软删）；红跑双证——禁锁临时补丁下该用例红（失败形态=挂子直通成功返回菜单 id、孤儿落库，即 Q-048 原窗口），恢复后该类全绿、REDRUN 标记零残留；③既有 `redisLockHeldUntilTransactionCompletion`（锁互斥与 afterCompletion 释放）与既有 hasChildren 顺序执行行为继续覆盖 10204 方向（挂子先提交、删父后进锁拒 `MENU_HAS_CHILDREN`），不重复造用例。
- 文档回写：architecture §17.1 无条件持锁清单「菜单」行补 `createMenu`/`deleteMenu` 与孤儿窗口口径；契约 §9.6 写链路语义补「并发语义」条目（三写入口持 SYS_MENU 树写锁串行、两方向交错收敛面 10201/10204，链接 §17.1）。

## 非目标 / 遗留

- 树写锁机制本身的改造（2026-09-06 定案不加 tryLock 保险丝，维持）。
