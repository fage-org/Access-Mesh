---
doc_type: task
id: T-PERM-081
title: （R2-T02）PQ-01/06 反例与正常语义基线
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §1.2/§10.2
depends_on:
  - T-PERM-080
blocks: []
acceptance:
  - "PQ-01 反例（getDenied* 跨 item 整批互斥过拒 vs queryBatch 逐 item 评估）与 PQ-06 反例（角色互斥规则顺序依赖：{A,B,C,D}+规则 A-B/B-C 两种顺序两种结果）在真实规则/数据库夹具下复现并留证"
  - "正常语义基线（check/batch-check/范围四态/快照投影）固定为差分锚，供 T-PERM-089/090 的 X03 等价差分使用"
  - "不全用 passthrough mock——真实规则与真实数据库可复现"
design_writeback:
  required: false
  status: pending
last_updated: 2026-09-25
---

# T-PERM-081 （R2-T02）PQ-01/06 反例与正常语义基线

## 背景

设计 §1.2 已核实的缺陷需先有可复现基线，后续修复才有红跑锚点（报告临时编号 R2-T02）。PQ-01 锚点：`PermQueryEngine.computeInstanceDenied` 整批 filterPermMutex 后回映射；PQ-06 锚点：`PermissionConflictDomainServiceImpl.filterRoleMutex` 顺序遍历边删边判、规则无 ORDER BY。

## 范围

- characterization 夹具：真实冲突规则 + 真实授权数据复现 D01~D03、R01~R02 反例与正常语义。
- 差分基线数据集（固定 seed/时钟/规则/读入事实口径，设计 §9.3）。

## 非目标 / 遗留

- 不修实现——PQ-06 修复在 T-PERM-083、PQ-01 最小修复在 T-PERM-095（两者构成 §9.3 最小正确性修复基线）、新核心全面重写在 T-PERM-085。
