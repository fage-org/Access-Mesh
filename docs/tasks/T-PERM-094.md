---
doc_type: task
id: T-PERM-094
title: （R2-T15）灰度、故障、缓存与发布演练
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §9.3/§9.4/§10.5
depends_on:
  - T-PERM-092
  - T-PERM-093
blocks: []
acceptance:
  - "§10.5 上线门槛逐项证据：定案记录、逐消费者/逐路由覆盖清单、项目级回归证据、SDK/网关契约、缓存与模式切换演练、实际性能预算、回退目标（索引问题回退同一新核心扫描；投影问题只回退投影；代码级故障退最小正确性修复基线=T-PERM-083+095 构成的已标记版本——不恢复 PQ-01/06 旧实现）"
  - "灰度差异五类归类登记：PQ-01/06 预期修复（06 含 S/H/D 删多收紧）、FACTS 完整性/新空角色契约、同源首次读取复用、方案 A 新准入语义、意外回归=0 容忍"
  - "监控口径落地：选择类型/阶段延迟、scopeAll/父触发率、定义缺失、预算超限、审计失败；「准入成功而业务拒绝」标预期分层行为不告警为系统错误"
design_writeback:
  required: false
  status: pending
last_updated: 2026-09-25
---

# T-PERM-094 （R2-T15）灰度、故障、缓存与发布演练

## 背景

设计 §9.3 存量与发布顺序、§9.4 回退、§10.5 上线门槛（报告临时编号 R2-T15）。先离线固定事实差分、再逐调用方迁移；迁移期新旧不并跑有副作用鉴权。

## 范围

- 发布演练（含与 ADM 系列模式切换的衔接）；「仅设计完成或任务卡 done」不满足门槛的验收口径执行。

## 非目标 / 遗留

- ADM 链路（映射/快照/模式切换）演练在 T-ACCESS-061；本卡聚焦 R2 核心面。
