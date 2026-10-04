# 网关重连确定性验证（2026-10-03）

PermInvalidationSubscriber 生产文件与 HEAD `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236` 逐字一致，默认 5 秒重连、恢复 clearAll、stop 取消订阅与调度保持。仅 Gateway 增加 `reactor-test` test scope，版本由现有 BOM 管理，实际解析 3.6.4，与 reactor-core 一致；不新增生产注入点或配置项。

## 案例映射

| 原 case | 保留证据 |
|---|---|
| complete/error 两个方法各等待 7s | 同结构 EnumSource 的 ON_COMPLETE/ON_ERROR 两个独立案例，Sinks 驱动真实终止信号；推进到 4.999s 仍一次订阅/未 clearAll，再推进 1ms 恰好第二次订阅与 clearAll |
| shouldNotReconnect_whenSubscriberStopped 等待 7s | 推进虚拟 10s，仍仅首次订阅，未 clearAll，running=false |
| 消息解析与畸形消息 | 原断言保留 |

每例先安装虚拟调度器，组装 Mono 在此后发生；afterEach 先 stop，在 finally 中 reset Reactor 工厂并 dispose 时钟，断言全局虚拟工厂已关闭。不将 SnapshotSafetyBoundaryTest 的真实跨层时间源虚拟化，也未替代会话任务 T-ACCESS-067。

## 测量与验证

同一 Windows/JDK 21.0.12.1/Maven 3.9.16，均非模块并行；每次 Maven 独立 JVM，Mockito 初始化计入类时间。

| 运行 | 目标 testcase | 测试类时间 | 构建 wall time/范围 |
|---|---:|---:|---|
| 原始基线 | 5 | 28.569s | Gateway 模块 31.884s；与 QueryMapper 验证同条 reactor 命令，模块顺序执行 |
| 首次虚拟化 | 5 | 4.782s | 独立 Gateway 18.053s，包含测试重编译与首次依赖下载 |
| 编译/依赖就绪后的测量 | 5 | 5.083s | Gateway 模块 8.610s；同条命令另跑 AdminXmlPaginationPgIT，未把它的成本算入 Gateway |
| 同 JVM 相邻类共跑 | 20 合计 | subscriber 0.403s（初始化已被前类承担） | 整条命令 35.897s；其余是 marker/invalidator/race/SnapshotSafetyBoundary，全部通过 |

原三段共 21 秒的固定等待已删除；实际收益还受 JVM/Mockito 初始化、编译与整体关键路径影响，不把类耗时差等同 reactor 提速。原/新均是 5 个展开案例，不以参数化减少方法数冒称少跑。

命令与日志（`.tmp/testing-simplification/`）：

- 原：`mvn test -pl access-service,gateway -Dtest=QueryMapperXmlContractTest,QueryMapperPgIT,PermInvalidationSubscriberTest`，`query-final-reconnect-before.log`。
- 新：`mvn test -pl gateway -Dtest=PermInvalidationSubscriberTest`，`reconnect-after.log`。
- 依赖就绪测量：`mvn test -pl gateway,access-service -Dtest=PermInvalidationSubscriberTest,AdminXmlPaginationPgIT`，`reconnect-measured-mapper-before.log`。
- 相邻：`mvn test -pl gateway -Dtest=PermInvalidationSubscriberTest,InterfaceSnapshotCacheInvalidatorTest,InvalidationMarkerTest,GatewayInvalidationRaceTest,SnapshotSafetyBoundaryTest`，`reconnect-neighbors.log`。

全部按 XML testcase 核对，无 failure/error/skip；根 tests=0 的嵌套报告未被漏计。

## 错误反例与本地两轨

[结果](reconnect-mutations.json)：不执行重连使两个终止案例在订阅次数断言失败；不 clearAll 使两个终止案例在缓存清理断言失败；保留挂起任务并允许 stop 后重订阅，使停止案例在订阅次数断言失败。三次均真实发现 5 个案例，零 error/skip；临时生产变体在 finally 中逐字恢复。

代码轨：时间推进在实际 Mono.delay 组装前接管，前后边界与停止负向行为都有独立断言；重连成功后返回未终止流，不让无意义循环干扰次数。调度与订阅必清理。文档轨：任务范围、现役缓存预算、隔离约束、test scope 与测量局限一致；无 P0–P3 发现，无新增待决项。完整回归仍由 T-ACCESS-076 承担。
