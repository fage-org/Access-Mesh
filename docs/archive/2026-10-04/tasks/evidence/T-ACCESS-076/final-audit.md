# 综合验收（2026-10-04）

实施基线 `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`；验证对象为当前工作树，包括全部计划代码、测试、依赖与 schema 注释改动。用户明确选择两条旧库迁移退出、PG-only、会话真实到期轮询及本次同族清扫，未代用户选择其他产品范围。

## 完成核对

| 要求 | 实证落点 |
|---|---|
| 基线、范围、支持边界 | [069](../T-ACCESS-069/implementation-baseline.md) |
| 规则/技能双副本、格式与样题 | [070](../T-ACCESS-070/skill-validation.md) |
| Java 参数化、身份/副作用保留、PG 请求支持 | [071](../T-ACCESS-071/java-fixture-evidence.md) |
| PG 假绿修正、H2 主张接替、烟测去重 | [072](../T-ACCESS-072/schema-evidence.md) |
| 两条支持分别退出、当前 no-seed/拒启/回滚保持 | [073](../T-ACCESS-073/retirement-evidence.md) |
| statement 结构与真实查询证据、错误变体 | [074](../T-ACCESS-074/query-mapper-evidence.md) |
| E2E 生命周期与失败清理 | [075](../T-ACCESS-075/e2e-evidence.md) |
| 重连确定性、生产 5 秒不变、邻类验证 | [077](../T-ACCESS-077/reconnect-evidence.md) |
| 单类上下文 A/B、真实配置/映射与邻类 | [078](../T-ACCESS-078/mapper-context-evidence.md) |
| 前端消费者独立、helper 与类型/格式检查 | [FE063](../T-FE-063/frontend-evidence.md) |
| 会话与明确同族时序、全量负载验收 | [067](../T-ACCESS-067/timing-evidence.md)及本记录；067 保持原计划归属 |

## 最终真实执行

2026-10-03 23:58 至 2026-10-04 00:16：先 `mvn clean` 成功，排除已删除测试的旧 class；再执行 **`mvn test -T 1C`**，未传 skipE2E/skipHeavyIT/skipTestcontainers 或串行 fork 覆盖。确认 9100 原本空闲，无需停止用户服务。完整日志 `.tmp/testing-simplification/final-clean.log` / `final-backend.log`，Maven wall time **17:58**。

[逐类报告摘要](backend-validation.json)：282 份报告，实际 testcase 子元素及唯一 `(classname,name)` 均为 **2429**，failure/error/skipped 均为 **0**。不使用根 testsuite@tests 独自统计，嵌套测试未漏计。清理后的报告集合与最终源码中的容器标签逐类对应，没有关键类空发现；70 个 testcontainers 标签类包含 E2E 的真实容器链路。

| Maven 模块 | 实际 testcase |
|---|---:|
| common | 69 |
| gateway | 124 |
| access-service | 2108 |
| example-service | 44 |
| perm-sdk/perm-common | 26 |
| perm-sdk/perm-client-spring-boot-starter | 15 |
| perm-sdk/perm-registration-spring-boot-starter | 14 |
| e2e | 29 |

关键轨：InterfaceAdmissionHeavyPgIT 2、ResourcePublicationHeavyPgIT 1（原 65,540 行规模保留）；BasicRoleGrantVerticalSliceE2EIT 8、ExampleBusinessFinalCheckE2EIT 11、ExampleProtectedApiE2EIT 8、E2eProcessSupportTest 2，全部零跳过。资源 heavy 在本次全量约 391.668s，期间数据库保持活动；未重启、缩量或改超时获得通过。

前端最终 `pnpm --dir frontend test`：41 文件、484 案例通过；`pnpm --dir frontend typecheck`（tsc/vue-tsc）通过；`pnpm --dir frontend build` 通过（构建约 43.52s）。完整日志 `final-frontend-test.log` / `final-frontend-typecheck.log` / `final-frontend-build.log` 同在上述目录。修改文件的 ESLint/Prettier 检查见 FE063。没有通过全仓 autofix 改写无关文件，未创建提交/PR。

[冻结输入](validated-inputs.json)覆盖 1455 个源码/测试/POM/schema 等输入，全量结束逐项 SHA-256 校验无变化。业务子进程扫描结果为 0，9100 无监听；Testcontainers 原有可复用基础设施按仓库规则保留，不把它们当作业务进程泄漏。

## 收益与局限

[净变化](source-statistics.json)：Java/前端测试及支持源码 **85691→84710 行，净减 981 行**；文件 330→331（加入有价值的 PG/清理证据与共享 helper，不追求删文件配额）。另删迁移 fixture 146 行；退出的 SQL/手册归档保留，不冒称从仓库净消失。

网关删除 21s 固定等待，单类可比测量及初始化差异见 077；Mapper 单类 A/B 18.274→8.545s、Bean 695→73，范围仅此类。全仓原报告没有一条绑定同环境/工作树的可比完整命令，因此不宣称整套加速百分比；本次 full wall time 是新基线，受 cold compile、heavy 数据库执行与调度影响。参数化没有减少业务展开案例，类时间不相加冒充并行时间。

## 技能真实任务效果

新增缺口：074 先定位现有 QueryReadSupport/LocalProjection 不能接替 QueryMapper 边界，再增加真实 PG 证据；075 针对子进程清理补独有反例，未制造重复空值测试。删测：072 逐项迁移 H2 主张、实跑 PG 和约束错误变体后删除；073 先获支持选择，迁入当前 no-seed 并清引用后退出历史资产。复用：071/FE063 只提取真实重复支持，保留不同消费者和独有黄金/审计信号。以上是本任务主代理实际使用，不声称独立盲测；格式/样题不冒充实际效果。

## 本地双轨结论

代码轨：按受影响契约核对参数身份、签名、SQL 约束恢复、异步提交/清理、调度恢复与原行为并集；新支持函数都有实际调用方，没有通用 DSL、跨类可变业务状态或新生产配置。全量发现与失败/跳过核对均通过。

文档轨：已采纳结论归测试规则及相应产品权威，未采纳的 CI/全仓切片等保留为非目标；最终设计只表达当前采用的范围。任务验收、依赖、问题收敛与归档引用同步。无未处理 P0–P3 发现或待用户决策项。该结论限定于本计划，不宣称全仓每条断言都已人工证明充分性。
