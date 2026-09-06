---
doc_type: task
id: T-ACCESS-030
title: 容器测试轨道提速——单例容器 + 按类建库 + 复用 + fork 级并行
status: done
plan: ""
domain: access-service
design_refs: []
depends_on: []
blocks: []
acceptance:
  - "access-service 28 个容器类全部迁至 ItInfra 单例基础设施：PG/Redis 每会话各起一次（withReuse）；按类库 `CREATE DATABASE ... TEMPLATE it_tpl`（全量 DDL 每会话仅模板构建时执行一次；会话首启清理上次会话遗留 it_ 类库，兼容 reusable 容器跨 JVM 存活）；Redis 按类逻辑库索引（1..15 轮转，取用时 FLUSHDB）——隔离语义与「每类独容器」等价（独立库表 + 独立键空间）"
  - "偏差类保留自建语义：DualInstanceContainerTest / TaskExecutionLeaseConcurrencyTest / SyncMetadataConcurrencyTest 空库自建局部表（实例 B / 裸 JDBC 建表链路改造对接类库 URL 与 Redis 索引）；AccessServiceSchemaPostgresTest 空库原样执行权威 DDL（其被测对象即 DDL 本身，禁用模板克隆）；PermissionCenterIntegrationTest 模板克隆（纯连通性，无建表）"
  - "容器组时长对比收口登记：基线约 420s/7 分钟（2026-09-06 T-ADMIN-025 收口会话观测、当时仓库无落盘载体，作约数参考），登记串行 / fork 2 进程并行实测"
  - "reuse 开关只落本机 ~/.testcontainers.properties（testcontainers.reuse.enable=true），仓库不引入该开关：CI/他人环境无此文件时 withReuse 自动失效，行为回退普通起停"
  - "容器组 fork 级并行（surefire testcontainers execution forkCount=${it.forkCount} 默认 2 + reuseForks，类随 fork 均衡串行执行；-Dit.forkCount=1 串行逃生门——2026-09-06 用户定案）：sa-token 的 SaManager 是 JVM 级静态单例（SaBeanInject 每上下文启动重注入），同 JVM 线程级类并发不可用（两轮实证：邻类上下文关闭使静态 dao 指向已 shutdown 的 Redisson，login 路径 500）；fork 隔离与 T-ACCESS-017 双 execution 分轨同哲学。ItInfra 以 user.home 文件锁抢占 1..4 槽位作 fork 标记（surefire ${surefire.forkNumber} 在 systemPropertyVariables 插值为空串、java.io.tmpdir 每 fork 不同，均不可用——第四/五/八轮实证；探测失败 fail-fast 不回退）；Redis 容器开 --databases 64，槽位 s 独占 16 索引段（(s-1)*16..+15，消除槽间段共享——2026-09-06 用户定案：利用 Redis 多 database）；类库/模板按槽位隔离，会话首启清理仅清本槽位遗留。@Isolated 独占名单：DualInstanceContainerTest（真实端口占用）与 AuthorizationChangeInvalidationPgIT（pub/sub 广播敏感、负载时序敏感——T-ADMIN-025 实证隔离复跑恒绿；@Isolated 仅串行化同 fork 内邻类，跨 fork 广播理论上仍可互扰、两轮全绿下接受现状）；连续两轮并行全绿收口，抖动按登记协议隔离复跑定性"
  - "AGENTS.md 增补测试运行纪律：全量回归仅收口时执行、-DskipTestcontainers=true 单测轨道快速反馈、全量前停 9100 dev 服务、mvn 运行中禁改源码、全量日志整文件落盘解析"
design_writeback:
  required: false
  status: none
last_updated: 2026-09-06
---

# T-ACCESS-030 容器测试轨道提速——单例容器 + 按类建库 + 复用 + fork 级并行

> 状态：done（2026-09-06 收口）
> 依赖：无
> 来源：2026-09-06 用户决策「按推荐顺序落地 1-5 项」（T-ADMIN-025 收口后测试耗时分析：容器组 ≈7 分钟为单模块回归主要成本，28 类各自起 PG+Redis 容器 + 各自全量 DDL + 各自 Spring 上下文）

## 背景

access-service 双 execution 测试轨道（T-ACCESS-017）中容器组 28 个类每个自起 `postgres:16-alpine` + `redis:7-alpine`（口令 `accessmesh-test`）、`@BeforeAll` 全量执行权威 DDL，单类基建成本 15-30s，容器组 ≈7 分钟。容器创建/启动与 DDL 重复执行是纯冗余成本，与断言数量无关。

## 设计口径

- **单例容器 + 按类建库**（非跨类共享库）：每类仍拥有独立数据库（模板克隆，DDL 仅执行一次）与独立 Redis 逻辑库索引（FLUSHDB），隔离语义与现状等价；Spring 上下文仍按类独立（类间 URL 必然不同）。
- **模板库** `it_tpl`：会话首启构建（原样整文件执行权威 DDL，与既有 setupSchema 同机制），类库 `CREATE DATABASE ... TEMPLATE it_tpl` 秒级克隆。
- **reuse**：单例容器 `withReuse(true)` + 本机 `~/.testcontainers.properties` 开关；配置哈希固定（镜像/环境/端口/命令不变）跨会话命中，重复运行免容器启动；会话首启清理遗留 `it_` 类库保证干净起点。
- **偏差类**：自建局部表的三类与自证 DDL 的 schema 测试走空库通道（`fromTemplate=false`），自建逻辑原地保留。
- **并行**：仅容器组、fork 级 2 进程（surefire forkCount=2 + reuseForks）——线程级类并发经两轮实证不可用（sa-token SaManager 静态单例被并发上下文关闭连带失效）；fork 标记以 user.home 文件锁槽位实现，按槽位隔离类库/模板/Redis 索引段。

## 范围

- 新增 `ItInfra`（access-service 测试支持类）+ 28 类迁移 + surefire testcontainers execution 并行配置 + `@Isolated` 名单 + AGENTS.md 运行纪律。

## 非目标

- gateway 两个 E2E 类不动（独立模块、子进程拓扑，非瓶颈）。
- 不删减任何测试项（testing-standards 覆盖率与回归锁纪律不变；耗时在重复基建不在断言）。
- 不做跨类共享库 / 共享 Spring 上下文：按类建库下类间 URL 必然不同，上下文跨类复用结构上不成立；按配置组合库共享需 28 类 fixture 冲突审计，收益风险比不佳，如需另立卡。
- 不做测试惰性初始化（掩盖装配问题，@SpringBootTest 的验证价值受损）。

## 完成记录

- 2026-09-06 实施：新增 `ItInfra`（access-service 测试树 `access.it` 包）：单例 PG（postgres:16-alpine，perm/perm，it_hub）+ Redis（7-alpine，`--requirepass accessmesh-test --databases 64`）容器 withReuse；模板库 `it_tpl_fN` 每会话重建并整文件执行权威 DDL 一次；按类 `CREATE DATABASE ... TEMPLATE` 秒级克隆（`fromTemplate=false` 空库通道供偏差类）；Redis 逻辑库索引按 fork 槽位独占 16 段轮转 + 取用时 FLUSHDB；fork 槽位 = `user.home/.accessmesh/it-slot-{1..4}.lck` 文件锁（锁与通道静态持有，探测失败 fail-fast）；会话首启清理仅清本槽位 `it_fN_%` 遗留（`DROP ... WITH (FORCE)`）；`-Dit.forkNumber` 显式值护栏 1..4 + prepare/register fromTemplate 口径断言。28 个容器类迁移完成（24 标准模板克隆 + DualInstance/TaskLease/SyncMetadata 空库自建 + PermissionCenter 模板克隆 + AccessServiceSchemaPostgresTest 经 `createStandaloneDatabase` 空库自证 DDL），净删 1100+ 行重复样板；pom testcontainers execution 增 `forkCount=${it.forkCount}`（默认 2，`-Dit.forkCount=1` 串行逃生门）+ reuseForks；AGENTS.md 常用命令区增单测轨道命令与测试运行纪律块；本机 `~/.testcontainers.properties` 开 reuse（仓库不引入该开关，CI 无文件自动回退普通起停）。
- 并行机制演进（实证链）：JUnit 线程级类并发两轮共 43 失败——堆栈 `SaTokenDaoRedisJackson.get → StpLogic.login` + RedissonShutdownException，根因 = sa-token SaManager 为 JVM 级静态单例（SaBeanInject 每上下文启动重注入），并发邻类上下文关闭使静态 dao 指向已 shutdown 的 Redisson；27 个上下文各自独立（Hikari 池号 1..27 连续证实），排除上下文缓存共享 → 改 surefire fork 级进程隔离（与 T-ACCESS-017 双 execution 分轨同哲学）。fork 标记三连坑依次修复：`${surefire.forkNumber}` 在 systemPropertyVariables 插值空串（`it_f_` 库名实证）→ 文件锁槽位；surefire 每 fork 独立 java.io.tmpdir 致双 fork 同槽互删在用类库（双 pid 同 `it_f1_` 前缀实证）→ user.home 固定路径；槽位锁局部变量被 GC 关闭通道释放（同槽复现实证）→ 静态持有。另有 @BeforeAll 先于上下文装配的时序坑 → `ItInfra.prepare` 占位入口（DualInstance/TaskLease 空库类）。
- 实测（同机同日）：容器组 28 类基线约 420s（上一会话观测、当时仓库无落盘载体，作约数参考）→ 单例容器+模板建库串行 243s → fork×2 后 141s / 142s / 151s 连续三轮全绿（151s 轮含 Redis 容器因 `--databases 64` 命令变化的重建）；access-service 模块 `mvn test` 由约 9-10min 降至 4:00；全仓 `mvn test` BUILD SUCCESS 522s。日常反馈走 `-DskipTestcontainers=true` 单测轨道不变。
- 2026-09-06 双轨评审收口（两轨并行只读，无 P0、无代码轨 P1）：文档轨 P1×2 直修——「类级并行」旧口径 6 处全量改 fork 级（title/acceptance/README 行/两处类注释/ItInfra javadoc）、README T-ACCESS 计数器 030→031；P2/P3 直修——状态符号对齐图例、frontmatter 补 `plan: ""`、「已登记抖动类」指向订正（registry 登记的是 TaskExecutionLeaseConcurrencyTest，AuthorizationChangeInvalidationPgIT 的抖动实证在 T-ADMIN-025）、基线出处改约数标注、AGENTS「每 fork JVM 一份」限定、pom execution 缩进、AGENTS 双空行、TimestamptzDualTimezonePgIT `setupSchema` 改名 `pinDefaultZone`；代码轨直修——it.forkNumber 显式值 1..4 护栏、DROP WITH (FORCE)、prepare/register fromTemplate 断言、Redis 轮转 javadoc 失准改写（回绕必然发生、安全性依赖已完结类不再写 Redis——7 个无 @DirtiesContext 常驻上下文经核无后台 Redis 写入方，引入后台 Redis 任务时需重审）。两项定案（2026-09-06 用户）：Redis 容器 `--databases 64` 槽位独占 16 索引段（利用 Redis 多 database 消除段共享，含探测失败 fail-fast）；forkCount 属性化留 `-Dit.forkCount=1` 逃生门（冒烟验证通过）。评审实证通过项摘要：28/28 迁移完整、configure 替换保真（额外 registry 行保留）、偏差类通道逐一对齐 acceptance、旧 API 残留零命中、ItInfra 并发结构（volatile 双检锁/锁序单向/computeIfAbsent 防重建）、清理模式不误删邻 fork、库名 63 字符上限余量、forkCount 仅作用于 testcontainers execution、Docker 缺失跳过路径先于 ItInfra 触达。
- 运维注记：Redis 容器命令变化使 withReuse 配置哈希变更，落地后首轮自动重建容器对（一次性）；历史切换残留（旧复用 PG 容器上的 `it_f0_*`/`it_f_*`/`it_tpl_f0`/`it_tpl_f` 库）不被新清理模式命中，`docker rm` 该 PG 容器即整体清除或忽略（仅占盘）；gateway 两个 E2E 类维持自起容器（非目标，子进程拓扑）。
