---
doc_type: task
id: T-PERM-095
title: （R2 基线）getDenied* 跨 item 互斥最小正确性修复——最小修复基线锚点
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §1.2/§9.3/§9.4/§10.2
depends_on:
  - T-PERM-081
blocks: []
acceptance:
  - "computeInstanceDenied 候选按 item（目标闭包集）切分后各自评估条件与 PERM_MUTEX，替代整批一次 filterPermMutex——T-PERM-081 的 PQ-01 红跑用例（D01~D03 差分锚）转绿，正常语义基线全部保持"
  - "与 T-PERM-083（S/H/D）共同构成最小正确性修复基线：PQ-01/PQ-06 两缺陷修复后、不依赖新核心的可回退版本，以 git tag/提交哈希落账并附基线验证证据（命令+日期+测试计数）"
  - "query/queryBatch 主链路既有语义与回归锁不受影响"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-095 （R2 基线）getDenied* 跨 item 互斥最小正确性修复

## 背景

设计 §9.3「先准备保留正常行为的最小正确性修复基线，再实施新内部契约和阶段」与 §9.4 回退目标「退到最小正确性修复基线，而不是恢复 PQ-01／06 的旧 SHA」。PQ-06 修复在 T-PERM-083（现行代码就地换 S/H/D）；PQ-01 此前只随新核心（T-PERM-085/089）解决，基线缺半边——本卡补齐（2026-09-25 外评处置）。

## 范围

- `PermQueryEngine.computeInstanceDenied` 最小修复：候选按目标闭包逐 item 评估（沿 queryBatch 逐 item 语义形态），不动其余管线与装载方式。
- 基线落账：T-PERM-083+本卡两个提交构成基线版本标记，供 T-PERM-094 回退演练引用。

## 非目标 / 遗留

- 不动 query/queryBatch 主链路；不做装载优化（I 系归新核心与 T-PERM-093）；新核心就位后本修复随旧执行体一并由 T-PERM-092 消化。
