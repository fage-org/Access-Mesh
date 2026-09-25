---
doc_type: task
id: T-PERM-093
title: （R2-T14）候选/规则索引与性能测量
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.5/§10.4
depends_on:
  - T-PERM-091
blocks: []
acceptance:
  - "S（顺序扫描）与 I（类型+实体索引）在同一个 CandidateSelector 内实现并按基准切换；索引拼桶保留原始行序（不改变展示「第一条」来源）；不建新策略框架"
  - "基准覆盖 N=1/10/100/外部上限/内部实际规模 × 单/多类型 × 稀疏/密集 × 共享祖先 × 冷/热缓存 × 条件数 × 互斥规则数 × scopeAll 短路/完全拒绝 × 最小/完整输出；主要比较「已修正确性扫描基线」vs「新核心+索引」，N=1 回退不以上限收益掩盖"
  - "硬性验收复测全绿：多类型正常规模不逐类型查询、无规则不装载互斥专用操作、最小输出不做展示专用读取、独立 item 不混集合、FACTS 不静默截断；Mapper/实际 SQL/网络往返分开计数"
design_writeback:
  required: false
  status: pending
last_updated: 2026-09-25
---

# T-PERM-093 （R2-T14）候选/规则索引与性能测量

## 背景

设计 §5.5 候选算法与 §10.4 性能基准（报告临时编号 R2-T14）。不预填提速比例；具体预算基于实测审批。

## 范围

- 性能测量与索引启用决策（测量支持才引入）；记录 P50/P95/P99、候选访问、条件预载、缓存 get/put、分配与 GC、审计失败。

## 非目标 / 遗留

- 不改判定语义（纯性能轨道）；EngineLimits 数值定档随本卡实测产出建议、用户批准。
