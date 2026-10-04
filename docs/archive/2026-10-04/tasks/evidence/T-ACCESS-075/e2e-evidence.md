# E2E 支持精简验收（2026-10-04）

从三条链路提取 E2eProcessSupport 的进程 launch、ServiceHandle 清理/日志、类路径排除 test-classes、随机端口与端口参数解析。各类保留独立容器/句柄/业务状态、环境与启动参数、类路径优先级、HTTP 客户端、就绪探针（不同 POST body）及所有业务断言。未新增服务模块反向依赖、共享上下文或并行策略。

startService 的就绪异常以前可能在 handle 赋给调用方前抛出；现在 readyOrDestroy 确保中断/异常时清理未交回的进程。E2eProcessSupportTest 覆盖中断清理和优雅退出超时后的强制终止等待，正常路径由实际三链路覆盖。没有给不同业务探针套通用参数开关。

定向命令：`mvn test -pl e2e -am -Dtest=E2eProcessSupportTest,BasicRoleGrantVerticalSliceE2EIT,ExampleProtectedApiE2EIT,ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false`。2026-10-03 23:57 完成，约 4:45；[实际报告](e2e-directed.json)为原业务 8+11+8、清理证据 2，全部零失败/错误/跳过。未匹配的上游模块用于 reactor 构建，未把空匹配算作 E2E 通过。日志 `.tmp/testing-simplification/e2e-after.log`。

源码与新 helper/清理测试合计 3027→2922 行，净减 105；不宣称拓扑启动数或 wall time 按源码同比减少。最终包含 E2E 的完整并行回归及进程扫描见[076](../T-ACCESS-076/final-audit.md)：全部业务与新增清理案例再次执行，业务子 JVM 残留为零。

代码轨：命令参数、堆大小、工作目录、日志重定向和清理等待保持；异常有明确资源所有者，恢复失败附加到原异常；无共享业务数据。文档轨：原三条独立生命周期、reactor 约束与当前失败定位保持；无新增待决项或 P0–P3 发现。
