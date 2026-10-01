---
doc_type: task
id: T-ACCESS-067
title: 会话/网关时序用例固定 sleep 改造
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - .claude/rules/testing-standards.md §10.3（口径已载，无设计回写面——T-ACCESS-051 同形态）
depends_on: []
blocks: []
acceptance:
  - "PlatformSessionIdleTimeoutTest、PlatformSessionAbsoluteTimeoutTest、AuthTokenFilterTest 的 sleep(1200) 拼时间轴用法改可控会话时间或有界轮询（T-ACCESS-051 同形态：终态条件+有界轮询，保留原行为断言，避免固定余量）"
  - "绝对超时用例 t≈3.6s 成功断言距 4s 边界仅约 400ms 的压线形态消除（改造后边界表达不依赖时钟竞速）"
  - "定向轨道全绿；全量回归 -T 1C 并行负载下本三用例不再依赖 sleep 余量"
  - "Q-014 同族清扫完成（Q-013 已收敛形态外无残留裸 sleep 时序用例——rg 阳性对照）"
design_writeback:
  required: false
  status: pending
last_updated: 2026-10-01
---

# T-ACCESS-067 会话/网关时序用例固定 sleep 改造

## 背景

承接 [Q-014](../pending-problems.md#q-014)：三个测试类用固定 sleep(1200) 构造活跃/超时时间轴；绝对超时用例的成功断言距 4s 边界仅约 400ms 余量。模块并行负载下延迟拉伸可能制造假失败（T-ACCESS-051 已修同族两方法，本项是 Q-013 收敛时登记的同族三处）。历史全量尚未实证击穿，但按 testing-standards §10.3 禁裸 sleep 余量纪律应改造。

## 范围

三测试类的时序构造改造；行为断言语义不变。

## 当前口径

改造形态按 T-ACCESS-051 先例：可控会话时间参数化或有界轮询至终态条件。

## 非目标 / 遗留

- 其他已用确定性机制的时序用例（不动）。
