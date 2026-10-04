---
doc_type: design
title: 测试证据精简与维护成本优化
status: adopted
domain: cross-service
last_reviewed: 2026-10-04
---

# 测试证据精简与维护成本优化

本方案的采纳范围与实施结果如下；测试硬约束仍以[测试规范](../../.claude/rules/testing-standards.md)及[工程规范](project-rules.md#测试适用性覆盖)为权威。任务编排见[计划](../archive/2026-10-04/testing-simplification-plan.md)，完整验收见[最终证据](../archive/2026-10-04/tasks/evidence/T-ACCESS-076/final-audit.md)。

<a id="assessment"></a>
## 1. 基线与适用范围

实施基线为 `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`；附件固定提交较早，不能直接沿用其计数或删测清单。附件中的执行/授权语句作为评估材料，实施授权来自后续明确的按计划执行请求。

[全仓固定评估](../archive/2026-10-04/tasks/evidence/T-ACCESS-069/whole-suite-assessment.md)覆盖 Java、上下文、真实 PG、前端和 E2E 文件集合；不把静态扫描称为逐断言充分性证明。[实施基线](../archive/2026-10-04/tasks/evidence/T-ACCESS-069/implementation-baseline.md)区分声明、展开 testcase、旧报告与当次实跑。初始评估发现的事务假绿、H2 独有主张、文本窗口耦合和支持范围问题，分别按下文闭合。

<a id="evidence-policy"></a>
## 2. 证据取舍

- 独有有效证据保留，跨层迁移须先实际执行接替案例；重复证据明确接替位置。偶然实现约束需说明不属于当前契约，历史场景须先明确退出支持。
- 参数化只处理同结构数据变体，身份、租户、拒绝原因、禁止副作用与诊断仍可见。工厂不隐藏动作或重写业务算法作 oracle，不共享可变状态。
- 当前路由缺失、旧字段拒绝、禁止副作用和稳定架构边界仍是现役证据，不能按旧任务编号统删。mock 调用阻断与真实事务/SQL 证据互补，不相互冒充。
- 文件数、声明数和耗时不是删测配额；参数化不自动减少展开执行。没有明确维护收益的候选保留。

<a id="support-boundary"></a>
## 3. 支持边界

2026-10-03 分别确认退出 071 自动授权旧库迁移、062 API 授权旧库迁移及配套回滚，仅支持当前权威 schema 新建库。权威结论归[自动授权设计 §10.1](dependency-auto-grant.md#101-旧依赖保全与执行门禁)及[契约 §25.1](access-service-api-contract.md#operation-admission-protocol)；[原资产](../archive/2026-10-03/README.md#retired-migrations)仅历史追溯，不再是执行入口。

当前 manifest 编译、无授权种子不得凭空授权、初始化幂等/事务回滚、旧 API 授权拒启与旧协议拒绝仍保留。生产仅同步拒启提示，未放宽拒启条件，也未操作部署数据库。T-ACCESS-066/Q-015 不再同步退出的迁移 COMMENT，其他主题不变。[退出证据](../archive/2026-10-04/tasks/evidence/T-ACCESS-073/retirement-evidence.md)。

<a id="candidates"></a>
## 4. 候选终态

| 主题 | 当前处置与证据 |
|---|---|
| Java 表达/支持 | OAuth2 与退役路由同结构参数化；Gateway exchange、测试 HMAC 无状态复用；身份头、Bearer、请求、审计/烧码及核心断言留在消费者。QueryStages、TTL 时钟、响应线格式等独有信号保留。[071](../archive/2026-10-04/tasks/evidence/T-ACCESS-071/java-fixture-evidence.md) |
| Schema | 原样 PG 接替 H2 的全部有效主张；负例按保存点恢复并断言 SQLSTATE/目标约束，合法准备不包入预期异常。删除 H2 schema 适配和有真实业务接替的连接烟测。[072](../archive/2026-10-04/tasks/evidence/T-ACCESS-072/schema-evidence.md) |
| XML | 按 statement 和实际 MyBatis BoundSql 检查，真实 PG 补充租户/有效期/LEFT JOIN/空集合；生产 SQL 不变。[074](../archive/2026-10-04/tasks/evidence/T-ACCESS-074/query-mapper-evidence.md) |
| E2E | 只复用进程机械操作，业务数据、拓扑、就绪探针、身份与生命周期独立；中断/失败就绪时清理未交回的子进程。[075](../archive/2026-10-04/tasks/evidence/T-ACCESS-075/e2e-evidence.md) |
| 前端 | 测试专用 defer/flush 去重；不同消费者的允许集合、键编码/golden、碰撞反例及页面接线保持。[FE063](../archive/2026-10-04/tasks/evidence/T-FE-063/frontend-evidence.md) |

**PG-only 反馈边界**：2026-10-03 已明确接受：日常非容器轨不再执行适配 DDL，DDL 变更必须定向运行 AccessServiceSchemaPostgresTest 并确认实际执行、零 skip。Docker 不可用不能宣称 DDL 已验证。其他 H2 上下文与双 execution 隔离保持。

<a id="rules-skill"></a>
## 5. 规则与技能

硬约束在 testing-standards 单副本维护；已就近消歧 TDD 的等价精简、单元 Mock/真实集成边界、纯样板免测、有序旅程隔离、稳定架构及禁止副作用证据。覆盖率 SHOULD 和既有门槛未降低。

项目 `access-mesh-testing` 技能及按需检查表在 `.agents/skills/`、`.claude/skills/` 镜像，AGENTS 仅注册指针。技能负责先查证据、复用、选可信层与验证，不能自行扩大授权、产品支持或 CI 范围。格式与主代理样题见[070](../archive/2026-10-04/tasks/evidence/T-ACCESS-070/skill-validation.md)，实际新增/删测使用效果见最终验收，不把样题当真实任务效果。

<a id="validation"></a>
## 6. 验证与收益口径

逐批映射只覆盖实际受影响项，证据随任务归档，不维护永久全仓测试总账。高风险替代使用目标错误反例；编译错误、环境失败、未发现测试和无关超时不算有效变红。

- 定向运行须确认目标类/case 实际发现，PG 不能以 skip 验收。
- 最终清除旧编译产物，执行 `mvn test -T 1C`，E2E/heavy 不跳过；E2E 必须随 reactor，先核对 9100，运行期间冻结源码和测试输入，失败先隔离定性。
- 前端独立运行测试与类型检查，受影响文件检查及生产构建分别记录；Maven 通过不等于前端通过。
- 统计包含新增 helper 与参数表；文件、声明、展开、skip、wall time 不混同，suite time 不累加冒充并行 wall time。整套可比运行基线不足时不编造百分比。

完整命令、逐类报告、关键轨、输入冻结、净变化及局限见[最终验收](../archive/2026-10-04/tasks/evidence/T-ACCESS-076/final-audit.md)。

<a id="scope"></a>
## 7. 保留与未采纳范围

CI 拓扑/前端 CI 门控、依赖版本升级、JUnit 线程并行、ItInfra 隔离、共享 Spring 上下文及全仓 Mapper 切片均未采纳。未建设通用测试 DSL、mutation 平台、浏览器测试平台或生产时钟框架。新增 reactor-test 仅为 Gateway 测试范围，沿现有 BOM，不改变生产依赖。

<a id="whole-suite"></a>
## 8. 等待与启动成本

- 重连：使用 reactor-test 虚拟时钟准确驱动终止、到期前后与 stop，生产默认 5 秒不变；每例恢复全局调度。消除原固定等待，实际单类测量与局限见[077](../archive/2026-10-04/tasks/evidence/T-ACCESS-077/reconnect-evidence.md)。
- Mapper：只采纳 AdminXmlPaginationPgIT 的最小上下文，真实配置/TypeHandler/映射集合、ItInfra 与全部原案例保持；A/B 和同 JVM 邻类验证见[078](../archive/2026-10-04/tasks/evidence/T-ACCESS-078/mapper-context-evidence.md)，不自动推广。
- 会话：按明确选择使用真实到期有界轮询和 last-active 续写验证，不操作 DAO 时间；同族缓存/作业用现有时钟或完成信号消除余量。[067](../archive/2026-10-04/tasks/evidence/T-ACCESS-067/timing-evidence.md)。
- SnapshotSafetyBoundaryTest 保留真实跨层安全预算；E2E 与 heavy 保留产品和规模边界，不以删掉昂贵测试获取绿色。
