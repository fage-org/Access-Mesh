# 全仓测试评估证据（2026-10-03）

固定源码提交：`71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`。本次起始工作树仅有本会话前序制定的测试精简文档；未改生产/测试代码。方案见[设计](../../../../../design/testing-simplification.md#whole-suite)，编排见[计划](../../../testing-simplification-plan.md)。

## 范围与方法

全量读取 Git 跟踪的 Java `src/test` 源文件与前端 `*.spec/test.ts/js` 等测试文件，提取声明、上下文/容器标识、等待、反射、Mock、文件读取与重复代码窗口；按全部能力目录检查分布，再对发现、可靠性、时间成本、夹具重复和证据重叠候选定向深读。同步核对所有模块 POM、Vitest、CI、测试资源及现行测试规则。

**“全仓”指文件集合与结构扫描完整，不代表人工逐条证明所有断言的充分性/等价性，也不是全套行为重跑。** 未列入删除候选的测试默认保留。精简实施仍需本批 case/assert 级映射。

一次性[机器快照](test-inventory-2026-10-03.json)逐文件保存路径、内容 SHA-256、统计特征及现存报告摘要；随任务归档，不建设需要持续同步的测试总账。统计方法为源码模式匹配，声明含参数化方法一次；前端声明计数为词法估算，不展开 each/动态参数。

## 固定基线统计

| 模块 | 测试文件 | 声明/词法计数 | 测试源码行 |
|---|---:|---:|---:|
| access-service | 231 | 2,088 | 64,437 |
| common | 15 | 69 | 1,531 |
| gateway | 21 | 124 | 3,652 |
| example-service | 7 | 44 | 1,029 |
| e2e | 3 | 27 | 3,027 |
| perm-client starter | 3 | 15 | 379 |
| perm-common | 2 | 26 | 298 |
| perm-registration starter | 3 | 14 | 271 |
| frontend | 41 | 462 | 10,265 |
| 合计 | 326 | 2,869 | 84,889 |

另有测试支持 Java 文件 4 个、802 行。perm-gateway starter 未发现独立测试类，其 Gateway 消费侧有接线证据；不能仅据此认定必须另补一套同构测试。全仓未命中显式 `@Disabled` 或 `it/test.skip/todo`；条件跳过仍需运行报告核验。

Java 文件 285 个，其中标记为容器相关的 access-service 70 个、e2e 3 个；`@SpringBootTest` 77 个（真实容器相关 67 个、非容器 10 个）。这不是实际上下文或容器启动次数；手工小上下文、嵌套类、缓存与 fork 都使二者不能简单等同。

## 现存运行报告的可用性

读取本地 `target/surefire-reports` 的 285 份 XML，文件时间集中在 2026-10-03 本地 19:53–20:04；没有能将这些报告与当前 SHA/一条完整命令绑定的证据，因此只用来定位成本和报告格式，**不宣称本次测试通过**。

- 根 `testsuite@tests` 求和为 **2,100**；实际 `testcase` 子元素 **2,448**，`(classname,name)` 在该快照中也为 2,448 个唯一身份。
- 有 **19** 份报告根属性为 0、但含嵌套测试的 testcase，差额 348。例：`PermissionGrantPlanDomainServiceImplTest` 根 tests=0，包含 77 个 testcase；`PermInvalidationSubscriberTest` 根 tests=0，包含 5 个 testcase。
- 子元素未包含 failure/error/skipped。该结果只是读取旧报告的事实；不得推广成当前 HEAD 实跑。
- 新基线需按当次输出隔离报告，核对 testcase、唯一身份、失败/错误/skip 与完整 reactor 摘要。不能只加根属性，也不能把两次日志汇总或重试重复累计。
- 未取得可比前端计时报告；前端实际展开数、耗时未测。

### 成本定位（现存报告，秒）

| 测试 | suite time | 解释 |
|---|---:|---|
| BasicRoleGrantVerticalSliceE2EIT | 100.309 | 独立跨服务链路，包含拓扑/业务/等待成本 |
| ExampleBusinessFinalCheckE2EIT | 83.425 | 业务最终检查与切换链路 |
| ExampleProtectedApiE2EIT | 57.897 | Gateway/示例受保护 API 链路 |
| ResourcePublicationHeavyPgIT | 74.186 | 65,540 行上限验证；已有 generate_series 批量种数，不能误判成逐行 fixture N+1 |
| AccessBootstrapPgIT | 30.188 | 有序初始化/登录/冲突旅程 |
| FirstAdminUserTrackPgIT | 25.155 | 真实首管理员用户链 |
| PermInvalidationSubscriberTest | 21.476 | 三次固定 7 秒等待是明确可定位成本 |
| AccessServiceApplicationTest | 19.828 | 整应用装配，不是“纯单测” |
| SnapshotSafetyBoundaryTest | 13.721 | 真实时钟 9.2 秒等待 + 4.5 秒回源模拟 |
| PlatformSessionIdleTimeoutTest | 12.704 | 有固定等待，已有 T-ACCESS-067 |
| PlatformSessionAbsoluteTimeoutTest | 9.911 | 有固定等待，已有 T-ACCESS-067 |
| AuthTokenFilterTest | 7.769 | 会话等待，已有 T-ACCESS-067 |
| PermissionCenterIntegrationTest | 5.650 | 单独应用上下文承载基础连接烟测 |
| AdminXmlPaginationPgIT | 5.926 | 直接 Mapper SQL 测试启动完整应用，适合最小上下文试点 |
| QueryStagesTest | 0.310 | 复杂行为矩阵已经低成本，不宜为提速优先删除 |
| PermissionGrantPlanDomainServiceImplTest | 0.467 | 大文件不等于慢测试 |
| AccessServiceSchemaH2Test | 0.314 | 退出主要收益是减少方言模拟维护，不是显著提速 |

suite time 不等于 testcase time，也不是可以相加的 reactor wall time；首次 JVM/Mockito/上下文初始化还可能落在个别类上。表中数值不能用于承诺净节省。E2E 合并业务状态、缩小 heavy 数据或跳过重轨不属于优化建议。

## 代码轨结论

### P2：PG 约束测试存在错误事务状态掩盖后续断言的问题

位置：`access-service/src/test/java/cn/ac/fage/accessmesh/access/schema/AccessServiceSchemaPostgresTest.java:64–71,249–264`。

每个测试先 `setAutoCommit(false)`，`shouldKeepPartialUniqueIndexSemantics` 连续执行三个预期失败 INSERT，只断言 SQLException，中间无 savepoint/rollback。首次唯一冲突后事务中止，后续“同位异码”与“resource_type 为空”并未恢复到可执行状态；捕获到事务中止异常仍能通过。这不能证明后续目标约束。PostgreSQL 要求回滚到保存点或回滚整个失败事务后恢复，参见[官方事务说明](https://www.postgresql.org/docs/current/tutorial-transactions.html)。

**影响例子**：若移除 `ck_operation_permission_resource_type_required`，第三个 INSERT 仍可因前一次异常后的事务中止而抛 SQLException，原测试无法以这一断言捕获约束丢失。当前为何没有报错：只检查异常类型，现存报告也显示这个类通过；这不证明生产约束已经缺失。

建议拆成独立案例，或每个负例使用保存点并恢复；断言 SQLSTATE 和目标约束，并将合法前置写入放在异常捕获范围外。对全仓 SQLException/手工事务同模式扫描，本次明确命中该方法。其他宽泛异常断言（如 `shouldHaveTaskExecutionTable` 把首次合法 INSERT 也包在 assertThrows 中）需按目标约束加强，未声明全部都已出现假绿。由 T-ACCESS-072 承接，**PG 证据可信后才允许删除 H2**。本次为源码控制流与数据库机制核验，未执行故障变体。

### P2：时序证据仍有固定墙钟等待，应优先于参数化处理

`gateway/.../cache/PermInvalidationSubscriberTest.java:100,118,137` 每个重连分支等待 7 秒；真实实现 `PermInvalidationSubscriber.scheduleReconnect` 用 5 秒 Reactor 定时。方法少但耗时高，合并/参数化不消除等待。

建议可控调度驱动“到期前未重连→到期后清理重订阅→stop 后不再重连”，默认生产重连间隔不变。测试生命周期必须清理调度器，避免影响其他测试；异步任务有独立线程时不能简单替换时间源。Reactor 的[虚拟时间机制](https://projectreactor.io/docs/core/release/reference/testing.html)要求定时链在设置调度器后创建；该资料仅解释可选机制，不要求升级当前版本或默认新增依赖。

会话的 t≈3.6s 成功断言距离 4s 失效边界过窄已由 T-ACCESS-067/Q-014 承接，不新建重复任务。Caffeine 的有界轮询、闩锁等待、E2E 就绪轮询不是“搜到 sleep 就删”的对象。SnapshotSafetyBoundaryTest 的真实跨层时间语义独立保留，未经等价证明不整体改成 fake 时钟。

### 成本候选：仅 SQL 目标可试点减少应用启动范围

`AdminXmlPaginationPgIT.java:46–89` 启动完整应用，但注入对象为 JdbcTemplate 和 Mapper，主要验证真实 XML 分页、参数绑定与伴生 count。可试点只装配实际 MyBatis-Flex、数据源、TypeHandler、XML/插件依赖的上下文；仍使用真实 PG、ItInfra 独立类库与现有容器 execution。

对照 `LocalProjectionBatchSqlIT` 虽名含 Sql，却通过 LocalProjectionDomainService 验证级联；不能仅凭名字按纯 Mapper 降层。`QueryReadSupportPgIT` 则含缓存与多领域依赖，同样不是第一批轻量候选。试点先做一类，测得收益且保留真实绑定信号后再决定是否扩展；无收益可保留原状而完成评估。

### 维护候选：重复安排确实存在，但不支持整类删除

静态相同代码窗口加人工检查定位到：

- OAuth2 同能力的 service 构造、Redis mock 与客户端安排。
- Gateway 的 exchange/response/route 夹具，分布于 GatewayInvalidationRaceTest、PermissionFilterTest、PermissionFilterMetricsTest、SnapshotSafetyBoundaryTest。
- PG 用户任务切片的请求信封、HMAC 与登录准备，分布于多个 characterization/org/grant 测试。签名头和真实 Bearer 路径语义不同，工厂须显式区分，不能统一注入头掩盖认证接线。
- E2E 的 ServiceHandle/启动/请求/清理，在三条链路重复；抽代码不会自动减少进程启动次数。
- 前端 `defer/flush` 在 list-load 及页面 hook spec 重复；消费者错误提示、筛选参数、树上下文等证据仍各自保留。

建议只抽有真实调用方的小工厂/支持函数，输入显式、新对象按场景创建；不建统一测试 DSL、大型基类或共享可变数据库图。现有 R2BaselineFixture 已被引擎相关 PG 复用，不能再造同类设施。

### 实证通过项（静态）

- 默认非容器与容器 execution 分开，E2E 单独模块；ItInfra 已有容器复用/模板克隆，不能重复建设。
- 重型资源清理已用集合 SQL 准备数据；保留超数据库参数上限的实际规模。
- QueryStagesTest/QueryExecutionPgIT/QuerySemanticsBaselinePgIT 分别有组合算法、真实 SQL/缓存与消费适配证据；名称或输入交集不足以认定冗余。
- 跨服务 E2E、真实 PG 事务与 mock 禁止调用证据不能互相替代。
- SDK/公共模块多数是小型低成本契约或装配验证；保留不同实现的序列化、签名、golden，不按层强制删成一份。

## 文档轨结论

- C+ 的安全边界成立，但原先把规则/技能作为所有改造的硬前置、低风险文件整理排在最前，缺少成本排序。建议先解决报告可信与 PG 假绿、去等待，再试点缩小真正只测 SQL 的装配边界；技能根据实际试点沉淀，不阻塞现役规范已允许的修正。
- T-ACCESS-067 已在其他活跃计划；本计划引用其依赖与结果，不复制任务或搬走归属。
- 历史迁移退出仍须支持裁决；依赖关系/引用闭包按现行设计处理，不以全仓评估自动取消升级承诺。
- 原单例/类级数据库、每类 DynamicPropertySource、进程隔离保持；“缩小上下文”与“共享上下文/线程并行”明确区分。
- CI 当前 unit job + 全量容器 job 存在普通 Java 重复运行，且本 workflow 没有前端步骤；它是既有最小 CI 选择，不能包装成新发现的生产缺陷。若扩展目标到反馈覆盖，前端独立门控比小 spec 合并更有价值；CI 去重必须先证明发现并集与关键轨实际执行，不能直接试未知 skip 参数。
- frontend 的 node 环境覆盖函数/状态/路由 mock，不等于浏览器渲染验收。当前不建议为了精简顺手建设浏览器自动化平台。

### 机制重量与待采纳边界

建议不建设永久全仓用例登记平台、通用 SQL 解析器、PIT 全仓流水线、万能 fixture 基类或共享 Spring 上下文。它们目前没有优于现有设施的具体收益证据。

可独立推进的事实性方向是 PG 异常隔离、报告口径修正与已在办的时序消抖；上下文试点及调度改造保留默认行为。旧库支持、CI 重编排、新依赖/生产注入点属于具体实施前待采纳范围，本次只给候选，不假定已批准。以上不是当前实现已发生故障的统一清单。

## 推荐顺序与验证状态

建议采用“先确保证据可信，再按成本优化”的扩展方案：

1. T-ACCESS-069 校准报告与基线；T-ACCESS-072 先消除 PG 约束假绿，再决定 H2 替代。
2. 复用 T-ACCESS-067，会同 T-ACCESS-077 处理会话与网关固定等待。
3. T-ACCESS-078 小范围真实 Mapper 上下文试点；不全仓推广。
4. T-ACCESS-071/T-FE-063/T-ACCESS-075 处理有实证重复的安排；T-ACCESS-074 保留结构与 SQL 行为边界。
5. T-ACCESS-070 将实践收敛到规则/技能；T-ACCESS-073 按支持裁决处理历史资产；T-ACCESS-076 做完整验收。

本次全仓结构扫描、保存报告解析与定向静态核验已完成；Java/PG/Vitest/E2E 新运行、故障注入、性能 A/B 均 **not run**。方案、任务与索引为文档改动，未删测试或改生产/CI/规则。

