# T-ACCESS-144 验证记录

日期：2026-10-20（Asia/Shanghai）。初始基线：`8e4088abd`，工作树初始干净。范围为用户贴回的代码问题与契约错误，并类推核对同类入口。未调用外部评审 CLI，未使用子代理。

## 核实与处置

| 原结论 | 核实 | 修正与证据 |
|---|---|---|
| P2：一致读隔离未传到 JDBC | 属实 | 当前依赖 Flex 1.11.8 的 `doBegin` 只启动 XID 并保存 timeout；旧实现 PG 实测物理隔离为 READ_COMMITTED。common Mapper 拦截器在实际连接准备 SQL 时应用 Spring 的显式隔离，保留 Flex 的绑定、提交和回滚协议。 |
| P2：完成写入失败后长期 RUNNING | 属实 | 旧 scheduler 正常 tick 不恢复；提交前故障注入使两次 finish 都回滚后，下个 tick 仍 RUNNING。现每个串行周期先 recover，恢复失败阻止新工作；沿用同一执行键、attempt、退避与成功终态保护。 |
| P2：导入/撤销初始取锁无五秒期限 | 属实 | 持有数据集行锁时旧实现超过测试的 12 秒外层期限仍未返回。抽取数据写守卫，先设置 statement_timeout，再锁数据集并核对版本，备注更新、导入及撤销复用。 |
| P3：发布 retry 请求字段引用错误 | 属实 | `PublicationGetReq` 接收资源身份；契约展开为 `resourceTypeCode,resourceCode,codeType`。 |

隔离类推范围：example 的数据读、后台快照、执行列表、任务列表、发布列表，以及 access 的授权预览、依赖解释、自动授权对账。它们继续使用原有事务声明，共享 common 接线；默认隔离不升级，嵌套 REQUIRED 仍服从外层事务，REQUIRES_NEW 保持独立连接/隔离。

恢复类推范围：`runNext` 的生产调用者只有单实例同步 scheduler；恢复位于两个 tick 之间，没有活跃尝试跨周期。每周期恢复还覆盖 Flex 仅记录提交异常而未向上抛出的情形，不依赖 catch 标记。`recover` 只改未完成记录，`finish` 保留 RUNNING + attempt 条件；不引入租约或多实例执行协议。

框架依据：[FlexTransactionManager 1.11.8 源码](https://github.com/mybatis-flex/mybatis-flex/blob/v1.11.8/mybatis-flex-spring/src/main/java/com/mybatisflex/spring/FlexTransactionManager.java)、[PostgreSQL 16 隔离语义](https://www.postgresql.org/docs/16/transaction-iso.html)。本地对应 JAR 经 `javap -p -c` 核对，业务后果由真实 PG 测试验证。

## 实际验证

所有 Maven 输出整文件保存在 `.tmp/review-fixes/`，未使用管道截断原始输出；测试运行期间未修改源码。

| 验证 | 状态 | 结果 |
|---|---|---|
| 旧实现物理连接/恢复/取锁复现 | failed，预期缺陷信号 | READ_COMMITTED（2）与预期 REPEATABLE_READ（4）不符；下个 tick 仍 RUNNING；导入及撤销均超出外层等待期限。 |
| 旧实现并发 SELECT/COUNT 对照 | failed，预期缺陷信号 | 2 项均复现 SELECT 4 条、并发导入提交后 COUNT 5 条；分别覆盖在线分页与后台 CSV。 |
| common 上游刷新 | passed | `mvn install -pl common -am -DskipTests`；仅构建与安装，不计为行为测试。 |
| 定向回归 | passed | 49 项 PG + 6 项执行器单测，0 failed / 0 error / 0 skipped。 |
| 完整 reactor | passed | `mvn test -T 1C`：3,157 项，0 failed / 0 error / 0 skipped；357 个 XML 报告内共 3,157 个 testcase，与 Maven 各 execution 汇总相符。包含 E2E 与 heavy，耗时 27:28，2026-10-20 22:28:14 +08:00 完成。 |

完整日志为 `.tmp/review-fixes/full.log`，SHA-256：`43837E79889FC52412916ADC768852D7612CE27B736F2FE2453F7047674FC5D4`。Maven 各 execution 汇总与 XML testcase 数交叉核对，不混加逐类日志和聚合行。E2E 共 41 项（包含进程支持测试）；资源发布 heavy 1 项实际运行 467.4 秒，接口准入 heavy 2 项也实际通过。

定向命令（PowerShell）：

```powershell
mvn test -pl example-service -am '-Dtest=DemoDataPgIT,DemoExecutionSchedulerTest,DemoExecutionFailureTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

修复前并发对照命令：

```powershell
mvn test -pl example-service -am '-Dtest=DemoDataPgIT#shouldKeepItemsAndCountInOneSnapshotWhenImportCommitsBetweenThem' '-Dsurefire.failIfNoSpecifiedTests=false'
```

首次复现运行中，并发用例错误地对 Mapper 接口 spy 使用 `callRealMethod`，产生夹具错误；该结果未作为并发缺陷证据。修正为同一受管 SqlSession 的真实 Mapper 后，单独重跑上述命令，得到 2 项明确的 4/5 不一致断言失败。其他物理隔离、恢复及锁等待信号独立成立。Maven 参数首次未加引号导致的启动失败同样不计为行为证据。

定向通过后，scheduler 从“异常后恢复”简化为“每个串行周期先恢复”；最终版本由完整回归覆盖，不将之前定向结果替代最终版本验收。

## 回归证据分工

- `DemoDataPgIT.shouldKeepItemsAndCountInOneSnapshotWhenImportCommitsBetweenThem`：在数据 SELECT 返回之后、COUNT 之前，通过不同线程/连接执行真实 CSV 导入并等待提交；断言在线 total、CSV 行数/recordCount 与原快照一致，并确认并发记录确已落库。
- `shouldApplyIsolationToMapperConnectionAndRestorePoolDefault`：核对受管 Mapper 真实连接的隔离级别、REQUIRES_NEW 的不同连接与默认隔离、外层恢复；提交和回滚后重借同一 backend PID，确认回到 READ_COMMITTED。
- `shouldRecoverFailedCompletionOnNormalTicksWithoutRestart`：真实 finish 写入后的 beforeCommit/afterCommit 故障注入；前者两次回滚后无需重启即可恢复并再次成功，后者保存原成功字节与 attempt，不重做。
- `shouldBoundInitialDatasetLockWaitForImportAndRevoke`：独立连接先持有数据集行锁；导入和撤销均在 SQL 期限内返回 30106，独立查询确认无批次/记录副作用。
- `DemoExecutionSchedulerTest`：恢复失败时禁止触发/抢占，后续 tick 重试恢复；验证恢复先于 expire、scheduleNext、runNext。
- `AccessServiceApplicationTest`：检查公共拦截器实际装入 SqlSessionFactory，原事务管理器仍唯一且为 Flex。
- 既有 PG 用例继续保护旧 attempt fencing、SUCCESS 不覆盖、三次尝试预算、回滚和备注更新 SQL 超时；不复制整套等价用例。

## 本地代码轨

按代码轨独立核对生产改动、失败路径、物理连接与池归还、全部显式隔离声明、数据写入口和 scheduler 唯一调用者。核对后未留 P0/P1/P2/P3 代码问题。

实证通过项：SELECT/COUNT 并发窗口、真实连接隔离、提交/回滚后复用、独立事务、完成提交前后故障、取锁期限和无副作用、旧尝试不能覆盖新尝试。

过度设计可裁剪项：无。新增 common 拦截器服务多个实际调用者；数据写守卫合并已有同款顺序；周期恢复复用原 SQL，没有新增配置、任务状态或后台线程。

存疑待用户决策项：无。本次按已授权范围修正既有契约，不引入产品范围取舍。

## 本地文档轨

代码轨结束后独立核对 project-rules §9.4、example 设计/API 契约、DTO、任务卡与看板。隔离接线、单实例周期恢复、SQL 单语句期限和发布请求字段已对齐；schema、外部响应 DTO、授权词汇无变化。

实证通过项：发布 retry 字段逐项对应 DTO；恢复描述保留执行键、attempt、退避和 SUCCESS 边界；取锁期限不误写为整个请求五秒；新任务使用未占用编号；本次文档无过程轮次词或错误“同上”引用。

过度设计可裁剪项：无。存疑待用户决策项：无。完整回归通过，任务及本记录按无活跃计划的单卡规则归档。
