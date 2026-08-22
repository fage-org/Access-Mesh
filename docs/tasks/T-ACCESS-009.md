---
doc_type: task
id: T-ACCESS-009
title: 建立数据库任务租约、幂等和异步执行治理
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#81-多实例任务协调
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/archive/2026-08-22/admin-service.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "多实例继续各自触发 Spring Scheduler；所有实例为同一计划时刻生成相同执行键，只有数据库原子抢占成功者进入业务执行，不以条件化调度或 Redis 锁承担正确性"
  - "动态任务与保留的系统维护任务使用数据库执行键和唯一约束竞争同一次计划执行"
  - "复用 T-ACCESS-002 已纳入最终 DDL 的任务执行表、实体及基础 Mapper/XML，不在运行代码或独立脚本中临时创建第二套持久层"
  - "只扩展原子抢占、续租、租约接管、条件完成/失败等并发 SQL 与 Mapper 方法，不重复创建基础 CRUD 和字段映射"
  - "执行记录包含 lease_owner、lease_until、状态与必要的执行/幂等标识，抢占和续租使用数据库时间及原子条件"
  - "同一执行键同时最多一个活动执行者；实例故障后可接管；旧租约持有者不能覆盖新执行结果"
  - "外部副作用携带幂等键，重试语义明确为至少一次且不会产生重复业务结果"
  - "仅为内部同步丢失兜底的维护任务被删除；保留任务能够说明继续存在的外部场景"
  - "异步执行器有界、命名、可观测且显式传播可信上下文"
  - "双实例测试覆盖并发抢占、续租、租约过期接管和幂等重试"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-21
---

# T-ACCESS-009 建立数据库任务租约、幂等和异步执行治理

## 背景

多实例下每个 JVM 都会调度相同任务。Redis 锁不承担正确性，本任务用 `access_db` 建立可审计的执行权和故障接管。

## 范围

- 设计并实现执行键、抢占、续租、接管和结果写回。
- T-ACCESS-002 负责最终 DDL、实体和基础 Mapper/XML；本任务只负责扩展原子并发 SQL、对应 Mapper 方法与执行编排。
- 收敛动态任务、维护任务和异步执行器。
- 删除因旧内部同步存在的冗余补偿任务。

## 完成记录

2026-08-21 完成（8 项用户决策：invokeTarget 真实执行 / 接管扫描器 / 删除 TenantAwareScheduled 残留 / 专用任务执行器 / @JobInvocable 注解白名单 / 必须接收上下文（一轮复评）/ advisory lock 协调（一轮复评）/ 周期对账+触发时重读（二轮复评））。

**实现**（设计回写见 `docs/design/access-service-architecture.md` §8.1 落地实现）：

- **原子并发 SQL**（`SysTaskExecutionMapper.xml`，复用 T-ACCESS-002 的 `sys_task_execution` 表与实体，不建第二套持久层）：`tryClaimExecution`（INSERT ... ON CONFLICT 部分唯一索引推断 + 条件 DO UPDATE，数据库 now() 基准）、`renewLease`、`completeExecution`（条件完成/失败）、`selectExpiredRunning`、`failExpiredOverMaxAttempts`。
- **执行编排**：调度层 `JobServiceImpl` 承担编排（抢占 → 立即续租覆盖排队期 → 专用执行器异步执行 → 出队租约校验 → 后台续租 → attempt fencing 条件写回 → sys_job_log 独立短事务）；`TaskExecutionDomainService`（执行键构建/租约原子操作，租约 60s、续租 20s、最大尝试 3 次）、`JobInvokeDomainService`（`beanName.methodName` 反射执行，@JobInvocable 白名单，唯一签名单 `TaskExecutionContext` 参数携带幂等执行键，ARCH-DEBT-001 关闭）。
- **执行键**：计划触发 `job:{jobId}:{秒级时刻}`（`JobServiceImpl.ExecutionKeyCronTrigger` 捕获，部署约定各实例同时区），手动触发 `job:{jobId}:manual:{epochMilli}-{UUID}`。
- **接管**：`TaskLeaseTakeoverScheduler` 每 30s 收敛超限过期执行 + 原子接管重试；扫描并发由 PG advisory lock 协调（效率优化，正确性由原子 SQL 保证）。
- **异步**：专用 `accessTaskExecutor`（有界/命名/`access.task.executor.*` 配置源），拒绝时告警放弃由接管兜底；执行线程显式绑定 TASK 可信上下文。
- **删除**：`TenantAwareScheduled` 注解 + `TenantScheduledAspect`（无使用者）；gateway `cleanupOrphanedMarkers` 保留（本地缓存治理，理由见 §8.1）。

**测试**：`TaskExecutionLeaseConcurrencyTest`（PostgreSQL Testcontainers，建表于 `@BeforeAll` 先于 Spring 上下文，10 用例：并发抢占唯一赢家 / 续租仅持有者且仅当前尝试号 / 过期接管 + 旧持有者不可覆盖 / 同实例接管被 attempt fencing 挡住 / SUCCESS 不可重抢占 / 抢占超限阻断 / FAILED 至少一次重试 / 僵尸收敛 / abandon fencing / 编排级幂等与接管重试同幂等键，本机无 Docker 自动跳过待 CI 执行）+ `JobInvokeDomainServiceTest`（7 用例，含 CGLIB 代理）+ `JobServiceImplTest`（6 用例）+ `TaskExecutorConfigTest` / `TaskExecutionDomainServiceImplTest`。基线：access-service 610 测试通过（37 跳过为 Docker IT）。

**AI 复评修复（2026-08-21，8 条结论 7 实 1 部分实，新增 2 项用户决策）**：

- P1 attempt 级 fencing：`attempt_count` 改为抢占时 RETURNING 返回的 fencing token，续租/完成按「owner + attempt」双条件，同实例接管自己的过期任务时旧尝试无法续租/覆盖（新增专项测试）。
- P1 排队期租约：续租改为抢占成功后立即启动（覆盖执行器排队等待），执行线程出队后先做租约校验，丢失则跳过执行。
- P1 FAILED 重试与次数上限：`tryClaimExecution` 增加 `attempt_count < MAX_ATTEMPTS` 上限；扫描候选扩为「RUNNING 租约过期 ∪ FAILED 未超限」，业务失败获得至少一次重试（新增测试）。
- P1 跨时区：属实但按用户指示不处理（非全球项目），已修正代码/文档中「多时区执行键一致」的误导表述，明确部署约定各实例同时区。
- P1 无参任务无幂等键通道：用户决策删除无参签名支持，`@JobInvocable` 唯一受支持签名为单 `TaskExecutionContext` 参数。
- P1 PG 测试建表过晚：确认 `AdminTenantIdProvider` 启动即查 `sys_user`（`@PostConstruct` try 之外），建表移至 `@BeforeAll`（容器启动后、上下文创建前）裸 JDBC 执行。
- P2 advisory lock：抢锁失败（false）改为跳过本轮扫描（此前等于没协调）；用户决策维持 advisory lock 方案——「执行键竞争」由被接管的任务执行本身承载，扫描协调仅为效率优化并写入设计文档。
- P2 分层：删除 `JobExecutionDomainService`（DomainService 横向注入 + 调度层职责违规），编排上移调度层 `JobServiceImpl`（`takeoverExpiredExecutions` 为扫描器入口），DomainService 仅保留原子租约/反射调用/日志短事务三个。

**AI 二轮复评修复（2026-08-22，6 条结论全部属实，1 项用户决策）**：

- P1 执行器拒绝吞异常：拒绝处理器改为告警后抛 `RejectedExecutionException`（经 ThreadPoolTaskExecutor 转 `TaskRejectedException` 通知提交方），提交方停续租并写回 FAILED——原实现只记日志导致续租永续、任务永久 RUNNING 无法接管（`TaskExecutorConfigTest` + `JobServiceImplTest` 拒绝路径覆盖）。
- P1 动态配置只更新当前实例：按用户决策实现「周期对账 + 触发时重读」——新增 `JobScheduleReconciler`（60s 重载启用任务 diff 重调度、取消停用/删除调度）+ 计划触发时重读任务行（已删除/停用跳过、新 invokeTarget 即时生效）；漂移窗口 ≤60s，不引入 MQ 实时广播（避免过度设计）。
- P1 僵尸执行阻塞接管批次：任务删除/停用或执行键无法解析的候选经 `abandonExecution` 收敛（attempt 拉满，退出重试候选），不再每轮占据 ORDER BY updated_at LIMIT 20 批次（PG 测试覆盖）。
- P2 白名单代理兼容：注解/签名在 `AopProxyUtils.ultimateTargetClass` 目标类解析，调用经 `getMostSpecificMethod` + `BridgeMethodResolver` 换回代理可调用方法，保留 @Transactional 等代理语义（CGLIB 代理测试覆盖）。
- P2 接管 scheduledTime 漂移：计划时刻改由执行键反解（`parseScheduledTime`，手动键恒为 null），同一执行键各次尝试上下文一致。
- P2 手动键碰撞：后缀改 UUID，同毫秒并发触发不再可能因唯一约束静默丢失。

**AI 三轮复评修复（2026-08-22，4 条结论全部属实，无新增用户决策）**：

- P1 对账查询失败误删租户调度：租户 `selectEnabledJobs` 异常时记入「状态未知租户」，其已调度任务跳过本轮删除判定——一次临时数据库异常不再被解释成该租户全部停用（错过的计划触发不产生执行记录，接管无法补偿）。新增 `JobServiceImplTest.reconcileDoesNotUnscheduleJobsOfFailedTenant`。
- P1 续租与维护任务共享单线程调度器：新增专用 `taskLeaseRenewalScheduler`（单线程，正确性路径独立）；共享调度器池 `spring.task.scheduling.pool.size=2`（原为 Boot 默认单线程，对账/接管扫描的同步数据库 IO 阻塞会停摆续租、误触发接管）。
- P1 abandonExecution 绕过 fencing：收敛更新按候选快照（status + attempt 匹配）条件执行，且不碰 SUCCESS 行、RUNNING 候选要求租约仍过期——读取后被其他实例抢占/完成的行不受影响（PG 测试 `abandonFencedAgainstConcurrentClaimAndSuccess` 覆盖并发抢占与 SUCCESS 两个场景）。
- P2 对账无 diff：记录已调度任务的 cron 快照，未变化跳过重建（不再每 60s 全量取消/重建）；Trigger 先构造成功再取消旧调度，cron 非法时保留旧调度；新增/更新 `JobServiceImplTest` 对账用例。

**AI 四轮复评修复（2026-08-22，1 P1 + 1 P2 + 1 P3 + 2 注释偏差，无新增用户决策）**：

- P1 续租调度器未真正隔离：声明任何 TaskScheduler Bean 都会使 Boot TaskSchedulingAutoConfiguration 退让（@ConditionalOnMissingBean），容器只剩单线程续租调度器、`spring.task.scheduling.pool.size` 失效。修复：显式声明共享 `taskScheduler`（池跟随 `spring.task.scheduling.pool.size=2`，Bean 名保持供 @Scheduled 按名解析）+ 续租专用 `taskLeaseRenewalScheduler`，JobServiceImpl 按名限定注入；`TaskExecutorConfigTest` 经 ApplicationContextRunner 固化两 Bean 拓扑。
- P2 对账按租户循环查询：违反 §8.4.8（无「按租户」例外）。修复：新增跨租户单条批量查询 `selectAllEnabledJobs`，启动加载与对账共用；加载失败 = 状态未知，本轮全部调度保持不变（替代三轮的按租户未知集合，更简且更安全）；`TenantIdProvider` 接口与 `AdminTenantIdProvider`（唯一消费者消失）随循环一并删除。
- P3 测试名不符实：`reconcileReschedulesOnCronChange...` 未覆盖 cron 变更。拆分为真 cron 变更用例（旧 Future 取消、新调度建立）与停用取消用例。
- 注释偏差：对账漂移窗口口径改为「约为对账间隔 60s + 单轮耗时」；`TaskLeaseTakeoverScheduler` 「唯一系统维护调度任务」改为「之一（另一个 JobScheduleReconciler）」。

**AI 五轮复评（2026-08-22）：建议通过，无阻断问题**。3 个 P3 清理项已处置：

- 共享 `taskScheduler` 补 `removeOnCancelPolicy=true`（已取消的远期 cron 节点立即移出延迟队列，与续租调度器一致）。
- 删除无调用者的按租户查询 `selectEnabledJobs`（接口 + XML，避免两套查询口径重新引入循环查询）。
- 修正架构文档 §6 对已删除 `TenantIdProvider` 的两处残留描述（改为 MyBatis-Flex TenantFactory + 任务执行线程显式 `RequestContext.task` 口径）。
