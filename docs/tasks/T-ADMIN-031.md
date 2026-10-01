---
doc_type: task
id: T-ADMIN-031
title: 菜单 create/delete 补树写互斥
status: proposed
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
  status: pending
last_updated: 2026-10-01
---

# T-ADMIN-031 菜单 create/delete 补树写互斥

## 背景

承接 [Q-048](../pending-problems.md#q-048)：`MenuWriteAppServiceImpl` 的 createMenu 读父后插入、deleteMenu 查子后软删，均无 SYS_MENU 锁；updateMenu 已在首次读取前持锁。有写权限的并发删父、挂子调用可留下指向软删父的存活子节点，正常树无法从根到达。当前无菜单管理 UI，风险限于满足门禁的写调用；未做并发复现（本任务补并发实证）。

## 范围

create/delete 两入口补锁（对齐 updateMenu 先例）；并发回归用例；§17 口径核对。

## 当前口径

树写锁约束（architecture §17）适用于四棵树；menu 树 update 已覆盖、create/delete 是漏网半边。锁实现以 architecture §17.1 现行定案为准（Redisson 树级可重入锁，复用 `TreeWriteLockSupport.lockTreeWrites`，afterCompletion 释放）；首版 pg_advisory_xact_lock/mapper 语句级锁是被替换的历史形态，不得采用。

## 非目标 / 遗留

- 树写锁机制本身的改造（2026-09-06 定案不加 tryLock 保险丝，维持）。
