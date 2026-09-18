---
doc_type: task
id: T-ACCESS-051
title: Q-013 时序用例裸 sleep 清扫——TaskExecutionLeaseConcurrencyTest 两方法改 5s 有界轮询
status: done
plan: —
domain: access-service
design_refs:
  - .claude/rules/testing-standards.md §10.3（时序/并发用例禁裸 sleep 余量——口径已载，本任务为对齐修复，无设计文档回写面）
depends_on: []
blocks: []
acceptance:
  - "takeoverAfterExpiryPreventsOldHolderFromOverwriting：sleep(1200) 改 5s 有界轮询（轮询 tryClaim 至接管成功），断言语义不变（attempt=2、旧持有者续租/写回 fencing、成功后不可再抢占）"
  - "takeoverReexecutesWithSameIdempotencyKey：sleep(1200)+单轮扫描 改 5s 有界轮询（每轮 takeoverExpiredExecutions + 终态检查），断言语义不变（SUCCESS、attempt=2、同执行键去重）"
  - "sameInstanceTakeoverFencedByAttempt 内「严格单次判定由 takeoverAfterExpiry…承担」陈旧交叉注释随批改写（该用例改轮询后单次判定表述不再成立）"
  - "本类容器轨定向回归全绿；收口全量 mvn test -T 1C 含 E2E 0 失败"
design_writeback:
  required: false
  status: —
last_updated: 2026-09-18
---

## 背景

[Q-013](../../../pending-problems.md)（2026-09-17 登记，来源 [T-PERM-068](../../2026-09-17/tasks/T-PERM-068.md) 收口全量首跑失败调查）：`takeoverReexecutesWithSameIdempotencyKey` 与 `takeoverAfterExpiryPreventsOldHolderFromOverwriting` 仍以 `sleep(1200)` 压 1s 短租约（200ms 余量）表达过期时序——当日全量首跑与隔离复跑共 3 次假失败（两方法轮换：awaitTerminal 15s 超时 / tryClaim 返回 null）。同文件 `claimBlockedOverMaxAttempts` 已于 2026-09-16 改 5s 有界轮询（registry 2026-09-16 收口行实施期加固①），两方法为漏改残留；纪律出处为 registry 2026-09-06 行（T-ACCESS-031：时序用例禁裸 sleep 余量）与 testing-standards rule §10.3。

## 范围

仅 `access-service` 测试类 `TaskExecutionLeaseConcurrencyTest`：两个方法的时序表达改造、`awaitTerminal` 终态判断抽取 `isTerminal` helper（新轮询循环与 awaitTerminal 共用条件防漂移）、`sameInstanceTakeoverFencedByAttempt` 内指向本类另一方法的陈旧交叉注释改写。不动生产代码、不动其余用例。

## 当前口径

- **轮询无副作用前提（已核 mapper SQL）**：`tryClaimExecution` 为 `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE 租约已过期 RETURNING`——未过期时 DO UPDATE 的 WHERE 不成立、RETURNING 空行，MyBatis 返回 null 且无任何写入；`selectRetryable` 仅选 `lease_until < now()` 的 RUNNING 行，接管扫描对未过期行整轮无副作用。
- **方法一**（跨实例接管）：轮询目标 `taskExecutionDomainService.tryClaim(owner-B)`，5s deadline；首次成功即 attempt=2，成功前的每次失败轮询即数据库过期边界的原子判定本身（边界逻辑仍被完整行使）。
- **方法二**（扫描轮接管）：轮询目标 `jobService.takeoverExpiredExecutions()` + 行级终态检查，5s deadline；重复扫描轮即生产 `TaskLeaseTakeoverScheduler`（PT30S 周期扫描器）多轮触发的形态，接管成功后行转入有效租约 RUNNING/终态、后续轮次对其无副作用。
- **后台扫描器干扰评估**：测试上下文含活跃的 `TaskLeaseTakeoverScheduler`（`@EnableScheduling` 随主应用类生效，`initialDelay 60s`、固定 30s 周期）——类常规耗时远小于 60s，常规运行不相交；负载下类总时长跨过 60s 边界时理论上可与用例窗口相交（方法一 key 的 jobId=9003 无任务行，被扫描器接管会 abandon 拉满 attempt），但该暴露面在 sleep 版本同样存在（窗口同为 claim 至接管成功），本改造不引入新竞态。
- deadline/轮询间隔形态对齐同文件 2026-09-16 定式（`System.nanoTime()` + 5s + 200ms 步进）。

## 验收对照

| 验收条目 | 结果 |
|---|---|
| 方法一改有界轮询、断言语义不变 | ✅ takeoverAfterExpiryPreventsOldHolderFromOverwriting：5s 有界轮询至 tryClaim 成功（nanoTime deadline + 200ms 步进）；attempt=2 与后续 fencing/幂等断言原样 |
| 方法二改有界轮询、断言语义不变 | ✅ takeoverReexecutesWithSameIdempotencyKey：每轮 takeoverExpiredExecutions + 终态检查（5s 有界）；SUCCESS/attempt=2/同执行键 containsExactly 断言原样 |
| 陈旧交叉注释改写 | ✅ sameInstanceTakeoverFencedByAttempt 注释改「跨实例接管与旧持有者 fencing 由 takeoverAfterExpiry… 承担（同为有界轮询）」 |
| 本类容器轨全绿 | ✅ 2026-09-18 `mvn test -pl access-service -Dtest=TaskExecutionLeaseConcurrencyTest` 10/10 绿 |
| 收口全量 -T 1C 含 E2E 0 失败 | ✅ 2026-09-18 `mvn test -T 1C` 八段聚合 1724 项 0 失败 0 跳过（t051_full_regress1.log） |

## 非目标 / 遗留

- `scheduledExecutionRunsOncePerKeyAndCarriesIdempotencyKey` 的 `sleep(500)`（重触发后断言不再执行）：负向断言的落定等待——重触发路径在 SUCCESS 行上 `tryClaim` 同步返回 null 即确定不执行，无异步面、非负载敏感余量，不属裸 sleep 余量形态，不动。
- 不改生产代码（租约/接管语义本身无缺陷，失败纯为测试时序表达）。
- 评审上报三处同族裸 sleep(1200)（PlatformSessionIdleTimeoutTest:156、PlatformSessionAbsoluteTimeoutTest:176~184、AuthTokenFilterTest:298——会话/网关测试时间轴构造形态）不在本任务范围，经用户拍板登记 Q-014。

## 完成记录

- 2026-09-18 定向容器轨：`mvn test -pl access-service -Dtest=TaskExecutionLeaseConcurrencyTest`——10/10 绿（21.71s）。
- 2026-09-18 收口全量：`mvn test -T 1C`（含 E2E）——八段聚合 1724 项 0 失败 0 跳过，BUILD SUCCESS（日志 t051_full_regress1.log；本类全量内 10/10 绿、E2E 两类 14 项绿）。
- 双轨评审处置：代码轨 P3×1（本卡「不相交」绝对化句改限定表述）、文档轨 P3×3（pending-problems frontmatter 括注刷新、Q-013 死行号锚点改方法名锚点、design_writeback.status=— 按看板图例维持不适用语义），零 P0-P2；上报三处同族裸 sleep 经用户拍板登记 Q-014；AGENTS.md 测试纪律块历史陈述句经核实不失实、维持不更新。
- Q-013 随卡收敛入 pending-problems 已收敛索引表。
