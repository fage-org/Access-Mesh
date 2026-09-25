---
doc_type: task
id: T-PERM-085
title: （R2-T06）TYPE_GRANT/INSTANCE 单一阶段主体
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §4.1~§4.4
depends_on:
  - T-PERM-083
  - T-PERM-084
  - T-PERM-095
blocks: []
acceptance:
  - "唯一 execute 生命周期（§4.1 职责表）与 TYPE_GRANT/INSTANCE 两阶段落地；装载批与判定集合分离——SQL 合批不扩大 item+stage 候选；I08：SQL 分块跨同一 item 时候选合齐后再计算互斥（块边界不改变结果）；新核心不调用旧完整核心"
  - "D01~D14 阶段用例全绿：独立 item 双允许/共同集合按项拒绝、同目标互斥两端必拒（不见第一条授权即返回）、条件剔除互斥一端、类型级门禁不认实例/子 scopeAll、scopeAll 短路（最小输出不解析实例）、DISALLOW 不回退、scopeAll 评估清空后实例仍可命中、原因优先级（CONDITION_NOT_MET_OR_CONFLICT 优先于 DEPENDENT/NO_PERMISSION）、SELF 不消费他项闭包、跨类型同位值不泄漏、物理合批超集切回原配对"
  - "getDenied* 跨 item 冲突语义并入本阶段验收（PQ-01 修复面：每目标独立评估）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-085 （R2-T06）TYPE_GRANT/INSTANCE 单一阶段主体

## 背景

设计 §4.3/§4.4 阶段算法与候选公式 C(item)（报告临时编号 R2-T06）。充分决策允许阶段短路、不允许集合内不完整互斥。

## 范围

- TYPE_GRANT 阶段（类型覆盖掩码汇总装载→逐项切回候选→条件→PERM_MUTEX→DECISION 短路/FACTS 保留）与 INSTANCE 阶段（闭包×1、按 clause 精确候选、item 内合并去物理重复、跨 SQL 块候选合齐再算互斥）。
- 阶段结果去重键含真实候选/type-mask 配对/绑定与父结果/评估策略（不只按资源类型）。

## 非目标 / 遗留

- 父要求与 GRANT_LIST 在 T-PERM-086；消费者迁移在 T-PERM-089+。
