---
doc_type: task
id: T-PERM-100
title: sync 通道 codeType 归一与存量空白行处置
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §19（sync 同步通道）
depends_on: []
blocks: []
acceptance:
  - "sync/full-sync 写入 codeType 与管理面同口径归一（trim；空值回退口径按契约 §19 拍板记录），同步写入 \" BIZ \" 后管理面按 BIZ 可达（回归锁实证旧实现下不可达）"
  - "存量带空白 codeType 行处置定案（订正语句入 runbook 或维持现状+登记），不假称只修新写入就消除了存量"
  - "detail/update/remove 业务键寻址链路对归一后形态可达；20004 误报或同码另建不再发生"
  - "契约 §19 写入/寻址归一口径同步"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-100 sync 通道 codeType 归一与存量空白行处置

## 背景

承接 [Q-031](../pending-problems.md#q-031)：sync/full-sync 的 codeType 仅归一空值不 trim；管理创建与 `ResourceKeyReq.normalizedCodeType` 会 trim。同步写入 `" BIZ "` 后，管理面按 `BIZ` 查询不到——同步自查找仍可达，但 detail/update/remove 业务键不可达，可能返回 20004 或另建同码资源。

## 范围

sync/full-sync 写入归一统一为管理面同口径；存量带空白行处置定案（含 runbook 订正语句或明确维持现状）；契约 §19 口径同步。

## 当前口径

写入侧与寻址侧归一必须同源；差异只在 sync 通道。空值回退口径沿用现行契约（不为本任务改语义）。

## 非目标 / 遗留

- code 本身的归一策略（维持现状，问题仅登记 codeType）。
