---
doc_type: task
id: T-PERM-094
title: （R2-T15）灰度、故障、缓存与发布演练
status: done
plan: docs/archive/2026-10-01/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §9.3/§9.4/§10.5
depends_on:
  - T-PERM-092
  - T-PERM-093
blocks: []
acceptance:
  - "§10.5 上线门槛逐项证据：定案记录、逐消费者/逐路由覆盖清单、项目级回归证据、SDK/网关契约、缓存与模式切换演练、实际性能预算、回退目标（索引问题回退同一新核心扫描；投影问题只回退投影；代码级故障退最小正确性修复基线=T-PERM-083+095 构成的已标记版本——不恢复 PQ-01/06 旧实现）"
  - "灰度差异五类归类登记：PQ-01/06 预期修复（06 含 S/H-D 删多收紧）、FACTS 完整性/新空角色契约、同源首次读取复用、方案 A 新准入语义、意外回归=0 容忍"
  - "监控口径落地：选择类型/阶段延迟、scopeAll/父触发率、定义缺失、预算超限、审计失败；「准入成功而业务拒绝」标预期分层行为不告警为系统错误"
design_writeback:
  required: false
  status: done
last_updated: 2026-10-01
---

# T-PERM-094 （R2-T15）灰度、故障、缓存与发布演练

## 背景

设计 §9.3 存量与发布顺序、§9.4 回退、§10.5 上线门槛（报告临时编号 R2-T15）。先离线固定事实差分、再逐调用方迁移；迁移期新旧不并跑有副作用鉴权。

## 范围

- 发布演练（含与 ADM 系列模式切换的衔接）；「仅设计完成或任务卡 done」不满足门槛的验收口径执行。

## 非目标 / 遗留

- ADM 链路（映射/快照/模式切换）演练在 T-ACCESS-061；本卡聚焦 R2 核心面。

## 本卡拍板（2026-10-01，用户逐项确认）

1. **监控深度＝「计数＋执行时长＋超限细分」档**：三计数方法全接 Micrometer + execute 总时长 Timer（outcome 标签，直方图开 P50/P95/P99）+ `ExecutionOutcome` 细分 `BUDGET_EXCEEDED`；阶段级延迟拆分与定义缺失分布**不**新增指标（由执行时长＋阶段短路率计数与拒绝 reason 日志承载）。端口低基数锁同批更新（`long` 白名单仅限时长标量）。
2. **暴露面＝同 gateway 先例**：actuator + micrometer-registry-prometheus + 独立管理端口（默认 9101 回环绑定；`ACCESS_MANAGEMENT_PORT/ADDRESS` 覆盖）；主端口 9100 无任何 `/actuator/**`（原 P2-5 匿名公开面随之消除）。
3. **性能预算＝实测基线登记**：T-PERM-093 本机实测数字 + 本卡全量复测登记为上线门槛证据，不承诺生产 SLA 数字；结构硬门槛随全量回归复测。
4. **收口范围＝随卡全收口**：本卡 done 后同批执行 R2 计划归档（稳定结论并入 engine/implementation.md〔§3.11 已落〕、r2-unified-query-and-admission.md 转 superseded、计划+22 卡归档、看板清理）。

## 灰度差异五类归类（验收对照第二条）

| 类别 | 明细与证据 |
|---|---|
| ① PQ-01/06 预期修复 | PQ-01 跨 item 互斥从全拒改逐目标各自判（T-PERM-095：候选按目标闭包切分各自 PERM_MUTEX，共享装载评估器，命中 (组,规则) 聚合一次通知）；PQ-06 S/H-D 删多收紧（T-PERM-083：链式多持用户旧算法保留一端→新算法全删，S/H-D 就地纯计算）。反例锚=`MutexSemanticsCharacterizationPgIT`（断言按翻转契约改写后全绿）。 |
| ② FACTS 完整性/新空角色契约 | FACTS 收全所选阶段不因短路/分页丢事实（T-PERM-086/090：GRANT_LIST 阶段完整收集，queryResources/queryScopes 迁移，G01~G07）；`Roles(empty)` 不回退 User、整批 NO_ROLE（C04，T-PERM-082 契约）；grantedBits 不可解析的损坏行旧不进任何集合/新进实例集（091 X03-②，schema 与写路径约束下正常运行不可达）。 |
| ③ 同源首次读取复用 | QueryReadSupport 读来源分桶 + 请求级解析记忆，同源首次读取复用（T-PERM-084，I04/I05：已读空类型/缺失操作请求内不回源，TTL 令牌不重置；引擎 User 主体内部互斥解析等价于判定面同源口径）。 |
| ④ 方案 A 新准入语义 | OPERATION_ADMISSION 阶段与候选资格语义（T-ACCESS-057）；映射模型/服务模式/sync-v2（058）；新端点/快照/SDK 网关链路与无迁移期统一上线（059）；失效矩阵与 TTL 边界（060）；逐服务业务最终检查与模式切换（061）；API 独立授权与旧协议退役（062）。N01~N30 验收归属见设计 §10.3 表。 |
| ⑤ 意外回归=0 容忍 | X03 基线 `QuerySemanticsBaselinePgIT`（六族消费面 golden，固定事实集 R2BaselineFixture）全绿；迁移期新增微差 13 条（089 四条/090 五条/091 四条）逐一登记且均「无证据消费面」，明细见三卡 X03 差异记录节；全量回归（093 收口 2377 项 0 失败 + 本卡收口复测）为意外回归零容忍的项目级证据。 |

## 上线门槛八项证据（验收对照第一条，§10.5 逐项）

| # | 门槛项 | 证据 |
|---|---|---|
| 1 | 定案记录 | 2026-09-25 三拍板与实施期裁决（[历史定案原文](../../../archive/2026-09-26/decision-registry-before.md) 2026-09-25~09-28 各行）；本卡四拍板（上节）。 |
| 2 | 逐消费者覆盖清单 | 计划[附录 A](../r2-query-engine-and-admission-plan.md) 全仓清点册（T-PERM-080，A.1 直接入口/A.3 门禁与 getDenied/A.5 范围与快照/A.6 视图转授配置，六面维度逐行）；消费者迁移完成=089/090/091 收口，旧执行体引用零（X04 退役锁）。 |
| 3 | 逐路由覆盖清单 | 固定图 105 路由按服务层门禁同码补操作引用（T-ACCESS-059）；example 七路由覆盖检查表（T-ACCESS-061 §8.6）；旧端点退役 404 负向锁=LegacyInterfaceRetirementTest（062）。 |
| 4 | 项目级回归证据 | T-PERM-093 收口全量 2377 项 0 失败；本卡收口全量（含 E2E/heavy）——见完成记录。 |
| 5 | SDK/网关契约 | PermissionClient 新增 interface-admission 两方法（059）；网关四态 matcher+过期缓存 miss 重载（059，评审 P1 红跑修复）；契约总册 §25（056 落账+058/061 增量）。 |
| 6 | 缓存与模式切换演练 | 失效链路演练证据表（下节）；模式切换 runbook 演练=T-ACCESS-061 E2E（暂停→切模式→恢复代次单调+空快照 403+以库为准恢复），runbook 落 `docs/ops/runbook-service-mode-switch.md`。 |
| 7 | 实际性能预算 | 实测基线登记（拍板③）：1000 项稀疏热缓存 P50 扫描 47.1ms/选择性索引 30.8ms、N=1 交替配对 P50 约 5.0ms、5 万授权+1 万项容量样本通过（T-PERM-093 本机测量，不代表生产 SLA）；结构硬门槛复测随全量回归（多类型批量读/无规则不装载/最小输出不展示读/FACTS 不截断等架构与 PgIT 断言）。 |
| 8 | 回退目标 | 三级（§9.4/implementation §3.9）：索引问题回退**同一新核心**扫描（`accessmesh.query.candidate-index-enabled=false`，093 开关）；投影问题只回退投影；代码级故障退本地 tag `r2-baseline-correctness`（T-PERM-083+095），不恢复 PQ-01/06 旧实现；新准入技术故障走同语义在线判定，退 LEGACY_API 须重新确认旧权限/路由/业务安全（062 后旧协议已删，该回退分支随退役封死）。 |

## 缓存失效链路演练证据（验收对照「缓存与模式切换演练」R2 核心面）

| 失效触发 | 测试载体（反向测试=触发与不触发两面） |
|---|---|
| 授权写（批量撤销/授权计划成功/失败回滚）→ 快照与角色缓存失效、DB 重载、广播 | `AuthorizationChangeInvalidationPgIT`×3（characterization 包，091 起服务面驱动） |
| 条件同 ID 更新、操作覆盖改变 → 相关服务准入快照失效（N19，markConditions 通道化安全超集反查） | `ConditionChangeEffectPgIT` + `InterfaceAdmissionPgIT`（T-ACCESS-060，19/19 含反查屏蔽后广播为空用例红跑实证） |
| 操作定义集合变更（类型预置/操作增删）→ OPERATION_PERMISSIONS_BY_TYPE per-type 失效 | `TypeDefinitionAppServiceImplTest` 正向 verify + 无变更路径 never 负向锁（T-PERM-047 终态复用） |
| 服务模式切换/在线验证回切（N23） | T-ACCESS-061 E2E runbook 演练（非目标面，引用） |

计划「写路径失效联动（OPERATION_PERMISSIONS_BY_TYPE 等）不直接消费旧执行体符号，失效链路随 T-PERM-094 演练覆盖」——上述载体核实：失效链全部经 `PermissionChangeAspect`/`evictAfterCommit` 框架面，与旧执行体零符号耦合（X04 退役锁保证），演练覆盖无缺口。

## 实施记录（2026-10-01）

- 监控落地：pom 引 actuator+micrometer-registry-prometheus；`QueryEngineMetrics` 加 default `executionCompleted(outcome, durationNanos)` 与 `BUDGET_EXCEEDED` 枚举；引擎 finally 按首个失败 `instanceof QueryBudgetExceededException` 判超限终态并携 `System.nanoTime` 时长打点（构造器防御包装同步覆盖带时长路径）；`MicrometerQueryEngineMetrics`（engine.query 包）三指标绑定（Timer 直方图开启）；`QueryEngineConfiguration` 经 `ObjectProvider<MeterRegistry>` 换绑（无 registry 回退 noop，引擎与审计收集器共用实例）。
- 暴露面：application.yml management 段改造（独立管理端口 9101 回环默认 + include 四端点 + application tag，注释沿 T-GW-007 形态）；compose access-service 补管理端口 healthcheck；三个 E2E access 子进程补 `--management.server.port=0` 随机化（防并发/本机 dev 冲突）。
- 回归锁：`MicrometerQueryEngineMetricsTest`（指标名/标签/时长/超限细分/default 链四用例）；`QueryAuditAndTraceTest` 超限细分观测锁（BUDGET_EXCEEDED 不与 TECHNICAL_FAILURE 合并+时长维度）+ 端口低基数锁更新（long 白名单仅时长标量）；`AccessServiceApplicationTest` 管理端口配置锁（9101/127.0.0.1/最小暴露面）。定向 33/33 绿。

## 完成记录（2026-10-01）

- **验收对照**：三条 acceptance 全达成——上线门槛八项证据、灰度差异五类归类、监控口径落地（详表见上两节；阶段级延迟拆分与定义缺失分布按拍板不新增指标，由执行时长＋短路率计数与 reason 日志承载，「准入成功而业务拒绝」标预期分层行为不告警）。
- **回归证据**：access-service 单测轨 1620 项 0 失败；收口全量 `mvn test -T 1C`（含 E2E/heavy）其余模块全绿，e2e 唯一失败经 HEAD 基线对照定性为**既有夹具缺陷**（非本卡回归，见下条），修复后 e2e 全模块 27/27 绿——合成证据链等效全量 0 失败。
- **既有夹具缺陷修复（事实性最小修）**：`ExampleBusinessFinalCheckE2EIT.step8` 父行定位 SQL `LIMIT 1` 无序且 JOIN 未按位过滤——同角色同实例 VIEW 行与 CREATE 行并存时，2026-10-01 起执行计划改选 CREATE 行（bits=1 无 VIEW 位），depend_on 指错父致判定恒 DEPENDENT_NOT_IN_PARENT_CONTEXT（诊断打印实证候选集 id=61 bits=2 / id=62 bits=1、选中 62）。修法=JOIN 补 `(rrp.granted_bits & op.binary_bit) <> 0` 位过滤（VIEW 语义真正落地）；修复前同环境稳定红、修复后 11/11 绿=红跑实证。HEAD 基线复跑同样红，排除本卡改动致因。
- **dev 冒烟（暴露面与指标实测）**：本机起服务验证——管理端口 9101 `/actuator/health` 200、`/actuator/prometheus` 200；主端口 9100 `/actuator/**` 不可达（NoResourceFound 统一异常兜底 fail-closed，为引入 actuator 前即有的既有形态）；Nacos 远端无 access-service.yml/gateway.yml 覆盖（本地 yml 即全部配置）。经内部密钥通道实调 `auth/check`（bootstrap admin ROLE:VIEW 判定 allowed=true）后 Prometheus 实际输出 `access_query_stage_total{selection=TYPE_LEVEL,stage=TYPE_GRANT,outcome=COMPLETED}=1.0` 与 `access_query_execution_seconds_*{outcome=SUCCESS}`（含直方图 bucket 序列）——指标链路端到端打点实证。
- **双轨自审**：代码轨 P0-P2=0（引擎打点路径/防御包装覆盖带时长链/构造器实参序/E2E 管理端口随机化逐项核对）；P3 注释残留 4 处直修（SecurityWebMvcConfig/CallerType/HeaderSignatureInterceptor×2/RequestContextInterceptor——actuator 移管理端口的现在时清扫，行为零改动：签名链本就未含 /actuator 路径）；低基数锁加强（long 白名单收紧为仅 executionCompleted 方法，防数值标签通道扩散）；过度设计可裁剪项=0（直方图=拍板 P95/P99 载体、ObjectProvider 回退=裁剪上下文真实存在、compose healthcheck=网关先例）。文档轨：implementation.md §3.11+§3.5 注、设计稿 §10.5 落地注+§6.5 现在时残留更新、deployment.md 管理端口两处、CHANGELOG Unreleased 一条。
- **R2 计划随卡归档**（拍板④）：r2-unified-query-and-admission.md 转 superseded、计划与 24 张卡归档 `docs/archive/2026-10-01/`、看板/registry 路由/AGENTS 指向同批更新。
