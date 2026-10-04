---
doc_type: task
id: T-ACCESS-067
title: 会话/网关时序用例固定 sleep 改造
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
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
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-067 会话/网关时序用例固定 sleep 改造

## 背景

承接 [Q-014](../../../pending-problems.md#q-014)：三个测试类用固定 sleep(1200) 构造活跃/超时时间轴；绝对超时用例的成功断言距 4s 边界仅约 400ms 余量。模块并行负载下延迟拉伸可能制造假失败（T-ACCESS-051 已修同族两方法，本项是 Q-013 收敛时登记的同族三处）。历史全量尚未实证击穿，但按 testing-standards §10.3 禁裸 sleep 余量纪律应改造。

## 范围

会话三类的时序构造，以及 GatewayInvalidationRaceTest、TaskExecutionLeaseConcurrencyTest、DualInstanceCacheInvalidationTest 中明确的同族固定余量残留；行为断言语义不变。2026-10-03 已确认本次一并处理。SnapshotSafetyBoundaryTest 保留真实跨层预算验证，不整体虚拟化。

## 当前口径

2026-10-03 已确认采用真实到期的有界轮询，保留少量必要等待；核对请求确实续写 last-active，移除固定 3.6 秒成功断言等临界竞速。不操作测试 DAO 的时间状态，不引入额外时钟机制，生产会话行为保持。

## 非目标 / 遗留

- 其他已用确定性机制的时序用例（不动）。

## 验收对照

- [x] PlatformSessionIdleTimeoutTest、PlatformSessionAbsoluteTimeoutTest、AuthTokenFilterTest 的 sleep(1200) 拼时间轴用法改可控会话时间或有界轮询（T-ACCESS-051 同形态：终态条件+有界轮询，保留原行为断言，避免固定余量）
- [x] 绝对超时用例 t≈3.6s 成功断言距 4s 边界仅约 400ms 的压线形态消除（改造后边界表达不依赖时钟竞速）
- [x] 定向轨道全绿；全量回归 -T 1C 并行负载下本三用例不再依赖 sleep 余量
- [x] Q-014 同族清扫完成（Q-013 已收敛形态外无残留裸 sleep 时序用例——rg 阳性对照）

定向、同族扫描及最终并行全量均完成；[时序证据](evidence/T-ACCESS-067/timing-evidence.md)。

## 完成记录

2026-10-04：`mvn test -T 1C` 完整并行负载下本卡全部案例通过，Q-014 收敛；全量零失败/错误/跳过及证据见[综合验收](evidence/T-ACCESS-076/final-audit.md)。
