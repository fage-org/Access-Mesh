# example 权限演示计划最终验收

日期：2026-10-20。本文件整合现有可重复证据；最终联合、可执行 JAR、标准完整回归及文档收口均已完成。

## 场景与证据入口

| 验收面 | 可重复证据 |
|---|---|
| 同一普通用户授权、导入、行列查询、任务、撤权与重启 | `ExampleBusinessFinalCheckE2EIT` 的 `ordinaryUserPermissionsControlRealRowsColumnsAndWrites` 与 `persistentTasksRecheckRealGrantsAndPreserveCompletedArtifactsAcrossRestart`，加上 `jointJourneyUsesSeparatePreparerAndOneOrdinarySubjectThroughEveryBoundary`，共用真实用户、角色和 A/B/C/D 数据；最后的完整主线由独立准备身份导入并收回普通用户准备权限，所有新增授权经管理 API |
| 本人、团队、部门、地区；动态改配置即时改变实际结果 | 同一 E2E 主线验证 SELF=A、T1=A/B、D1=A/B/C，R1=A/C；修改 regionCode 映射为 R2 得 B/D，再恢复；没有重启或改 Java |
| 行列四态、同名字段跨报表、隐藏字段推测、字段身份重建 | 在线 E2E 主线及 `DemoDataPgIT`；行 ALL 不扩大列、字段 ALL 不扩大行，EMPTY 无记录查询；显式选列/筛选/排序保存 fieldId，删后同名不复活 |
| 导入/撤销全行管理边界、字段写权限与事务 | 普通用户真实链路先持 ALL 导入，改为局部编辑范围后导入/撤销均 30004；`DemoDataPgIT` 补字段拒绝、同号并发、原样 PG 原子回滚及批次绑定证据 |
| 策略原子生效、版本冲突与 SQL 安全 | `DemoPolicyPgIT` / `PolicyCompilerTest` / `DemoDataPgIT`；失败保持旧版本，值绑定、有限目录、参数与执行时间预算；真实链路验证配置改变结果 |
| 条件、互斥与父上下文 | E2E 既有 `step8_subViewRealParentBinding` / `step10_mutexBusinessHalves` 保留原探针的独有证据；新数据主线验证未来日期 EMPTY、报表字段父上下文、在线 IP 白名单通过但后台不带 IP 为 EMPTY，复用唯一权限引擎 |
| 租户、主体与服务凭证反例 | E2E 伪造直连身份、服务停用恢复、共享实例按租户选凭证，以及双真实租户同 recordCode/importKey 隔离；任务定义/执行/下载均本人归属 |
| 执行前撤权与成功后归属 | 新任务 E2E 先关闭 worker 排队，撤销字段后重启得 DENIED；已成功 CSV 原字节仍可领取；后台缺 IP 不伪造用户环境 |
| 自动/手动触发、冻结配置、重启及幂等 | 新任务 E2E 实际分钟 cron 自动触发；PG 验证同计划时间唯一、同键重放、配置 CAS/冻结选择、停用 SKIPPED、恢复只重算未来计划；真实进程重启保留任务、业务数据、策略和结果 |
| 提交前后故障、旧 attempt、有限重试及期限 | `DemoDataPgIT` 在结果事务提交前抛错验证回滚，提交后恢复不重做；旧 attempt 无法覆盖新结果；三次/10-30 秒退避、明确认证失败终态、SQL 异常分类、到期无文件但保留键 |
| 可执行脚本与 HTTP 接线 | 初始化脚本、CSV 导入脚本、`export-example-snapshot.ps1`；有 pwsh 的定向 E2E 实跑初始化/导出，保存 CSV 只含授权 A/B；Controller/manifest 测试验证 DTO、路径、权限声明和下载例外 |

## 执行方式

- 原样 PG：`mvn test -pl example-service -Dtest=DemoDataPgIT,DemoPolicyPgIT,ExampleSchemaPgIT`，共用 ExamplePgInfra，不启动第二套 IAM 模板。
- 真实链路：`mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false`；可加 `-Dexample.demo.scriptHost=<pwsh 绝对路径>` 实跑脚本。独占隔离拓扑及临时数据，不接候选栈。
- 标准完整回归：根 `mvn test -T 1C`，含 E2E/heavy，不使用跳过开关。

证据计数与执行时间引用 [T-ACCESS-141](T-ACCESS-141.md)、[T-ACCESS-143](T-ACCESS-143.md)；不会把只读检查、编译、跳过或仅 HTTP 200 当作行为验收。

## 本地代码轨

核对同一最终代码范围中的任务状态、attempt 条件提交、只读快照及锁序；复用数据/权限执行器，没有按角色名推断、替代权限引擎或新增业务页面。以结果行/列、CSV 字节、执行状态和实际数据库变化判定，不只验信封。没有新增待解决问题或需要用户裁决的取舍。

## 本地文档轨

核对设计/API/schema/指南/manifest 与任务依赖，T-FE-079 维持 cancelled。保持单实例、UTC 分钟 cron、后台无 IP、7 天结果保留、仅当前 schema 空库初始化及无跨版本升级承诺。完成前须确认标准全量结果并执行计划连带归档、链接校验；归档不撤销既有运行边界。

## 联合与正式包实证

`mvn package -pl example-service -am -DskipTests`：2026-10-20 BUILD SUCCESS（最终构建 8.213s；构建不计为测试）。可执行 JAR 的 Main-Class 为 Spring Boot JarLauncher，Start-Class 为 ExampleServiceApplication；SHA-256：`a895b0f9fb153bb7e8fd0cf1b3debe46ca65ca72d1e42a9cc5a51f1c317f64b5`。

`mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT,E2eProcessSupportTest -Dexample.demo.executableJar=<上述 JAR> -Dexample.demo.scriptHost=<pwsh> -Dsurefire.failIfNoSpecifiedTests=false`：20 passed / 0 failed / 0 error / 0 skipped（18 项真实链路 + 2 项进程支持）；日志 `.tmp/T-ACCESS-142/jar-verified.log`（6:09 min）。example 的初次启动及全部重启均实际执行 `java -jar`。初始化与导出 PowerShell 也实际执行。

完整同一用户主线从准备/收回准备权到显式列 cron、T1→SELF、新执行 A、字段撤权 DENIED、改默认列 recordCode、恢复两列、他人 get/update/trigger 拒绝、SELF 备注单改与混合批量回滚、在线/后台 EMPTY，再到持久排队重启和撤 EXPORT 后已完成键/attempt/字节不变。事务内故障精确窗口由前述 PG 用例承担；E2E 不引入生产调试后门。

原始执行观测保存在 [joint-demo-observations.json](joint-demo-observations.json)，包含实际 executionKey、status、attempt、policyVersion、catalogVersion、outputColumns、recordCount。记录集合由测试对真实 CSV 与查询结果断言；场景请求与预期见[联合场景包](../../../../../../example-service/examples/permission-demo-scenarios.md)。数据与身份属于一次隔离测试，不是运行环境中的长期 ID。

本地代码轨再次核对 JAR/classpath 两种启动入口复用进程收尾，以及联合场景的权限前提和无副作用断言；文档轨核对当前交付描述与所有设计/计划/任务入口。无未决设计取舍或新增待解决问题。

## 收口核对与环境边界

动态配置观测见 [policy-change-observations.json](policy-change-observations.json)：JAR 前后 SHA-256 相同、PID/启动时间相同，实际 policyVersion 1→2，记录由 A/C→B/D；证明配置更新没有通过重新部署或隐藏代码分支实现。

代码轨发现并修复冻结字段状态分类：删除/停用的选择引用按现行设计为 FAILED，身份有效但撤回读权仍为 DENIED。后台复用 DataDomain 的字段装载并先校验冻结 ID（包括筛选/排序），不增加 SQL 循环查询，不改变在线未知/无权统一拒绝。修复前用例实际失败（期望 FAILED、实际 DENIED），修复后 `DemoDataPgIT,DemoExecutionFailureTest` 共 45 passed / 0 failed / 0 error / 0 skipped（41 PG + 4 单测）。最终 JAR 已包含此修正。

首个本卡根回归中，example 的一个测试进程未发现 Docker，四类测试被跳过（64 个未展开声明）；该运行已停止，不计验收通过。按原配置隔离复跑实际展开 70 项，全部通过、零跳过（`docker-isolation.log`）。后续新增字段停用参数使当前 DataPgIT 为 41 项，已实际执行。未关闭任何门禁、修改 CI 或豁免跳过。

文档轨同步修正架构册仍称 example 无数据源/BI schema 未消费的旧描述，以及 quickstart/索引中的过时进度；保持冻结候选的历史边界和通用 SQL 自动改写未交付边界。没有新增待决策项。

标准全量另有一次在最后的 ExampleProtectedApiE2EIT 租户准备请求收到 HTTP 503；新的 18 项业务联合用例此前已经通过。该次仍记失败，不将其计为完整验收。原配置隔离复跑该组 12 passed / 0 failed / 0 error / 0 skipped（`protected-isolation.log`）；没有据单次复跑宣称根因已解决，也未放宽断言，只补充失败信封的受控 code/message 诊断。失败全量保存在 `full-tenant-503.log`，随后按原参数重新全量验证。

## 最终状态

2026-10-20 标准根 `mvn test -T 1C`：**3,148 passed / 0 failed / 0 error / 0 skipped，23:30 min，427 个测试类结果，BUILD SUCCESS**。包含 E2E/heavy；最后的 ExampleProtectedApiE2EIT 12 项也实际通过。完整输出为 `.tmp/T-ACCESS-142/full-verified.log`，按全部日志级别的类结果汇总。此前跳过、HTTP 503 失败及主动停止的运行均未被计作本次成功。

任务和证据随计划归档；业务页面任务 T-FE-079 保持 cancelled。无新增待裁决项，不自动恢复其他暂缓工作。



归档自检：2026-10-20，计划状态 archived，九张执行卡 done、T-FE-079 cancelled；142 个迁移相关文件链接与 20 个章节锚点通过，活动 tasks/plans 仅留 README 文件，旧任务路径与过时待办描述扫描无残留，git diff --check 通过。docs/tasks/evidence 空目录因终端策略拦截清理而保留，目录内无活动证据文件，Git 不跟踪该空目录。
