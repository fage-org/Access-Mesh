# 会话与同族时序证据（2026-10-03）

2026-10-03 已明确选择真实会话到期轮询，不操作 Sa-Token DAO 时间；同日确认一并处理明确同族残留，SnapshotSafetyBoundaryTest 保留真实跨层预算。完整 `-T 1C` 回归已于 2026-10-04 通过，任务完成，见[综合验收](../T-ACCESS-076/final-audit.md)。

## 实施

- PlatformSessionIdleTimeoutTest：只读活动剩余时间，轮询到实际冻结后请求 401，并确认绝对 TTL 仍有效。持续活动案例逐请求核对 last-active ≥ 请求开始时间，直到越过原始闲置窗口；不再等固定 1.2s。
- PlatformSessionAbsoluteTimeoutTest：持续请求直到真实 401，确认 token timeout=-2；成功请求不延长绝对 TTL，并核对活动时间实际续写。取消约 3.6s 的压线成功断言；成功响应后恰好到期的读取竞态按过期状态处理，仍等待后续真实 401。
- AuthTokenFilterTest：同样只读观察冻结；持续使用逐请求证明下游放行和 last-active 续写，不以连发成功就冒称滑动续期。
- GatewayInvalidationRaceTest：先放行旧提交，清理等待重试已开始，真实清理完成后才放行新提交；替代 400ms 清理/600ms 回源的余量配合。原代际、重试次数、最终缓存断言不变。
- TaskExecutionLeaseConcurrencyTest：先等 finally 中的真实执行日志写入，再清理 executor 的调用记录；成功键重触发返回后不得再次 execute 入队，保留调用计数、日志及真实 PG 断言。取代固定 500ms 后检查“未发生”。
- DualInstanceCacheInvalidationTest：L1 丢广播失效改条件轮询，保留原 400ms 观察上限；L2 缩放预算复用 DefaultCacheService 既有 LongSupplier 与当前 FakeRedis 的时钟，精确推进 1500→2300ms，并断言实际过期时刻为读取起点+2000ms。未新增生产时钟/配置/框架。

## 定向验证

原会话基线与修改后命令均为：`mvn test -pl access-service,gateway -Dtest=PlatformSessionIdleTimeoutTest,PlatformSessionAbsoluteTimeoutTest,AuthTokenFilterTest -DskipTestcontainers=true`；原/新 2+3+13 testcase 均通过。日志 `.tmp/testing-simplification/session-before.log` / `session-after.log`。

同族完整定向：`mvn test -pl gateway,access-service -Dtest=GatewayInvalidationRaceTest,AuthTokenFilterTest,PlatformSessionIdleTimeoutTest,PlatformSessionAbsoluteTimeoutTest,DualInstanceCacheInvalidationTest,TaskExecutionLeaseConcurrencyTest`，38 testcase，零 failure/error/skip，23:37 完成，约 118s；日志 `session-family-after.log`。随后保持 L1 原观察上限的最终定向 `mvn test -pl access-service -Dtest=DualInstanceCacheInvalidationTest -DskipTestcontainers=true`：7 testcase 通过，23:42，日志 `cache-family-final.log`。[逐类摘要](directed-validation.json)。这些次数不重复合计成全仓测试数。

目标错误反例：临时省略 Gateway 每请求 updateLastActiveToNow，只跑 perRequestRenewal_keepsContinuousUsageAlive；last-active 未达到本次请求开始时间，目标断言失败，零 error/skip，见[结果](renewal-mutation.json)。生产 AuthTokenFilter 在 finally 中逐字恢复，再随后续定向绿色执行；不是靠等待超时、编译失败或环境错误证明变红。

## 同族残留分类与本地核验

rg 扫描 Thread.sleep/TimeUnit.sleep/Mono.delay/delayElement，有源码阳性对照：

- 会话三类、重连类与本批 cache/race 固定余量均已移除。
- TaskExecution 的剩余 100/200ms 均在带 deadline 的条件轮询；CaffeineSecondsPrecision、DualInstanceContainer 及 E2E 就绪/状态等待也是条件轮询，保留。
- PermissionFilterTest/Metrics 的 delayElement 是模拟慢远端触发硬截止，不靠它推断相邻线程先后，保留。
- SnapshotSafetyBoundaryTest 的真实跨层墙钟预算按明确范围保留；T-ACCESS-071 仅复用 exchange 安排，没有虚拟化此测试。

代码轨：只读观察不续命，续写检查独立于是否放行；负向异步行为观察提交点，PG 行为未换成 mock。文档轨：扩展范围已回写任务；无裸 sleep 余量与合法轮询混同、无擅自移除预算验证。无新增 P0–P3 发现；最终 `-T 1C` 已通过，Q-014 已收敛。
