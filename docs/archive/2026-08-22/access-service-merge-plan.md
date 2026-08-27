---
doc_type: plan
title: admin-service 与 permission-center 归并为 access-service
status: archived
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/project-rules.md
  - docs/design/permission-center/api-contract.md
tasks:
  - T-ACCESS-001
  - T-ACCESS-002
  - T-ACCESS-003
  - T-ACCESS-004
  - T-ACCESS-005
  - T-ACCESS-006
  - T-ACCESS-007
  - T-ACCESS-008
  - T-ACCESS-009
  - T-ACCESS-010
  - T-ACCESS-011
  - T-ACCESS-012
acceptance: "T-ACCESS-001~012 全部 done 或经确认 cancelled；access-service 成为唯一部署单元；空库、契约、事务、安全、双实例与架构门禁通过；设计与存量任务完成回写和重基线"
last_updated: 2026-08-22
---

# admin-service 与 permission-center 归并为 access-service

> 状态：completed（已归档 2026-08-22；T-ACCESS-001~012 全部 done）
> 关联设计：[access-service 目标架构与归并约束](../../design/access-service-architecture.md)

## 目标

- 将两个后端服务收敛为唯一 `access-service` 部署单元。
- 按权威设计完成工程、数据库、事务、安全、缓存、任务和生态切换。
- 在不扩展业务功能的前提下保持既有 HTTP 契约行为；已退役的内部同步管理接口 `/admin/sync-task/*` 明确排除。
- 建立可验证的空库、回滚、双实例和架构门禁。

## 非目标

- 不实现当前任务看板中尚未完成的新业务功能。
- 不保留旧服务部署兼容或旧数据迁移能力。
- 不在本计划引入数据库 migration 框架、权限版本号或完全扁平化。
- 不重定义 admin/permission 现有产品契约；设计变化必须先回写 `docs/design/`。

## 准入条件

- [access-service 目标架构](../../design/access-service-architecture.md) 保持 `adopted`。
- 尚未开始的 T-PERM/T-ADMIN 后端功能任务不得在旧模块上进入 `in-progress`。
- Java 21、PostgreSQL、Redis 和 Maven 构建环境可用。
- 实施前记录当前全量构建与测试基线，区分迁移回归与既有失败。

## 阶段编排

| 阶段 | 任务 | 出口 |
|---|---|---|
| 1. 容器与数据基线 | T-ACCESS-001~003 | 新模块、单库和单运行基础设施可编译启动 |
| 2. 业务边界归并 | T-ACCESS-004~007 | 安全上下文、强事务投影、只读模型、共享配置审计落地 |
| 3. 多实例运行语义 | T-ACCESS-008~009 | 缓存与任务在双实例下具备明确一致性和故障语义 |
| 4. 生态切换与收口 | T-ACCESS-010~012 | 唯一部署单元、验收门禁、文档和任务治理闭环 |

## 任务清单

> 任务详情与唯一状态以 [任务看板](../../tasks/README.md) 和对应任务卡为准。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-001](../../tasks/T-ACCESS-001.md) | 建立 access-service 工程骨架并物理归并源码 | ✅ | — |
| [T-ACCESS-002](../../tasks/T-ACCESS-002.md) | 建立 access_db 最终 DDL 并收敛持久层模型 | ✅ | T-ACCESS-001 |
| [T-ACCESS-003](../../tasks/T-ACCESS-003.md) | 收敛单数据源、MyBatis、Redis、JSON等运行基础配置 | ✅ | T-ACCESS-001, T-ACCESS-002 |
| [T-ACCESS-004](../../tasks/T-ACCESS-004.md) | 实现可信请求上下文和统一安全策略矩阵 | ✅ | T-ACCESS-003 |
| [T-ACCESS-005](../../tasks/T-ACCESS-005.md) | 实现强事务权限投影并删除内部同步子系统 | ✅ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-006](../../tasks/T-ACCESS-006.md) | 建立跨域只读查询模型 | ✅ | T-ACCESS-002, T-ACCESS-005 |
| [T-ACCESS-007](../../tasks/T-ACCESS-007.md) | 合并系统配置与操作审计并落实日志事务分级 | ✅ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-008](../../tasks/T-ACCESS-008.md) | 统一缓存并实现多实例失效及30秒安全边界 | ✅ | T-ACCESS-003, T-ACCESS-005 |
| [T-ACCESS-009](../../tasks/T-ACCESS-009.md) | 建立数据库任务租约、幂等和异步执行治理 | ✅ | T-ACCESS-002, T-ACCESS-004 |
| [T-ACCESS-010](../../tasks/T-ACCESS-010.md) | 切换 Gateway、SDK、Nacos和部署配置 | ✅ | T-ACCESS-004, T-ACCESS-005, T-ACCESS-008 |
| [T-ACCESS-011](../../tasks/T-ACCESS-011.md) | 完成契约、回滚、架构、空库和双实例验收 | ✅ | T-ACCESS-006, T-ACCESS-007, T-ACCESS-009, T-ACCESS-010 |
| [T-ACCESS-012](../../tasks/T-ACCESS-012.md) | 删除残留引用、回写设计并重基线任务看板 | ✅ | T-ACCESS-011 |

## 验收标准

- 12 个任务均为 `done` 或经确认 `cancelled`，且所有必需设计回写均为 `done`。
- `access-service` 是唯一可运行的管理与权限服务；旧模块、旧服务名和内部同步运行链路已删除。
- 空库初始化、API 契约（排除并负向验证已退役的 `/admin/sync-task/*`）、事务回滚、安全隔离、缓存故障、双实例任务和架构测试全部通过。
- 原有未完成后端任务已更新到新模块/新 schema，或因范围消失而取消并完成依赖重连。
- 稳定结论全部位于 `docs/design/`，计划正文没有形成第二套契约。

## 当前进度

- 2026-08-10：grill 决策讨论完成，目标设计 adopted；建立 proposed 计划与 T-ACCESS-001~012 任务卡。
- 2026-08-12：T-ACCESS-001 done（工程骨架与物理归并完成，347 测试基线）。
- 2026-08-13：T-ACCESS-002 done（权威 DDL `schema/access-service.sql` 34 表 + 139 条操作种子 + 持久层收敛 + 空库测试双轨，四轮评审收口，372 测试基线）。
- 2026-08-13：T-ACCESS-003 done（Sa-Token 两端统一 + 删除重复缓存/序列化配置 + expiresIn 配置化，ultracode 评审 4 项问题修复含 P1 token-prefix，377 测试基线；阶段 1 出口达成：单模块、单库和单运行基础设施可编译启动）。
- 2026-08-14：T-ACCESS-004 done（唯一可信上下文 AccessRequestContext + 统一安全链 + 安全策略矩阵，4 项用户决策 + 双评审 1 P1 修复 + 对抗核实，408 测试基线；修复 G1~G4：内部凭证不隐式获全权限 / sourceService 可信化 / admin 显式门禁 / actuator 匿名契约）。
- 2026-08-15：T-ACCESS-005 done（同事务本地权限投影 + 删除内部同步/Feign/sys_sync_task；权限管理与外部 sync 拒绝本地投影；设计 §3/§4/§6/§7 回写完成）。
- 2026-08-21：T-ACCESS-008 done（缓存框架 Duration 硬迁移 + 单次有效 TTL/读取令牌剩余 TTL 回填 + 普通 L1 跨实例失效广播；快照链路 6 目录 L2_ONLY≤10s + 启动边界校验 10s/5s/15s≤30s；Gateway 迁统一 CacheService、固定 fail-closed、5s 全链路硬截止；4 项用户决策；gateway.md/project-rules §12/双 skill 镜像回写）。同日 AI 复评 2 P1 + 4 P2 全部确认属实并修复（失效先递增代际再清缓存、用户级候选纳入在途回源注册表、L2 命中回填 L1 按 remainTimeToLive 门控、跟踪索引跟随有效配置 + TTL 兜底口径（用户决策）、L2 失效失败计入失效失败指标、真实 10+5+15 组合边界与代际竞态回归测试）。第二轮复评 3 P2 + 2 P3 修复（catalog 级跨租户 evictAll 支撑重连真正全量清空——用户决策、evict/evictBatch 计入失效失败指标、default-config 绑定前缀全仓更正 + 绑定契约测试、L2 命中回填门控回归测试、组合测试余量放宽）。

- 2026-08-21：T-ACCESS-009 done（sys_task_execution 数据库租约：原子抢占/续租/条件完成/接管 SQL + 执行编排 + @JobInvocable 白名单反射执行（ARCH-DEBT-001 关闭，幂等执行键经 TaskExecutionContext 透传）+ 专用 accessTaskExecutor + 接管扫描器（advisory lock 协调）+ 删除 TenantAwareScheduled 残留；5 项用户决策；592 测试基线，PG Testcontainers 双实例租约并发测试本机无 Docker 待 CI 执行；设计 §8.1 回写完成）。同日 AI 复评 8 条结论（6 P1 + 2 P2，核实 7 实 1 部分实）全部处置：attempt 级 fencing（RETURNING 尝试号 + owner/attempt 双条件）、抢占后立即续租覆盖排队期 + 出队租约校验、FAILED 至少一次重试 + 抢占 maxAttempts 上限、跨时区不处理（用户决策，修正误导表述）、删除无参 @JobInvocable 签名（用户决策：必须接收上下文）、PG 测试建表提前到 @BeforeAll、advisory lock 抢锁失败跳过本轮（用户决策维持 advisory lock）、编排从 DomainService 上移调度层 JobServiceImpl 消除同层注入；593 测试基线。二轮复评 3 P1 + 3 P2 全部属实并修复（8 项用户决策累计）：执行器拒绝改抛 RejectedExecutionException 通知提交方（原只记日志致续租永续任务悬挂）、多实例配置周期对账 JobScheduleReconciler 60s + 触发时重读任务行（用户决策，漂移窗口 ≤60s 不引入 MQ 广播）、僵尸执行 abandonExecution 收敛防接管批次饥饿、白名单经 ultimateTargetClass 代理兼容（保留 @Transactional 语义）、接管 scheduledTime 由执行键反解、手动键 UUID 防碰撞；605 测试基线。三轮复评 3 P1 + 1 P2 全部属实并修复：对账失败租户不参与删除判定（临时 DB 异常不再误删该租户调度）、续租专用 taskLeaseRenewalScheduler + scheduling pool 2（共享单线程调度器阻塞会停摆续租误触发接管）、abandon 按候选快照 fencing（不碰并发抢占/SUCCESS 行）、对账真 diff（cron 未变不重建、Trigger 先构造后取消）；608 测试基线。四轮复评 1 P1 + 1 P2 + 1 P3 属实并修复：显式声明共享 taskScheduler（声明任何 TaskScheduler Bean 会使 Boot 自动配置退让，隔离失效）+ ApplicationContextRunner 拓扑测试、对账/启动加载改跨租户单条批量查询 selectAllEnabledJobs（§8.4.8，删 TenantIdProvider 残留）、补真 cron 变更测试；610 测试基线。五轮复评建议通过（无阻断），3 项 P3 清理完成：共享调度器 removeOnCancelPolicy、删除无调用者的 selectEnabledJobs、修正文档 TenantIdProvider 残留描述；610 测试基线。
- 2026-08-22：T-ACCESS-010 done（Gateway /admin/**+/perm/** 合并单路由 → lb://access-service + auth-routes 同目标，service-url/指标/日志统一；perm-sdk PermissionFeignClient → access-service + 18 路径封闭契约测试 + 删除 SyncTaskFeignClient + perm.service-code 去默认值 fail-fast；主代码/测试/前端/mock/DDL 注释旧名全量清理，LEGACY_ADMIN_SOURCE 拒绝列表值与负向断言保留；死代码 RestTemplateConfig 删除；architecture §1/gateway.md/access-service-architecture §9/AGENTS/README 回写；4 项用户决策；阶段 4 启动）。同日 AI 复评 1 P1 + 3 P2 全部属实并修复：Starter @Import 显式装配 FeignInternalSyncInterceptor（原 @EnableFeignClients 不注册普通 @Component、组件扫描覆盖不到 SDK 包，拦截器从未装配）+ 装配测试 4 项（含 PropertyPlaceholderAutoConfiguration 复现 strict 占位符语义与 starter logging 桥接环排除两处附带发现）；SDK 契约测试改全方法封闭（不预过滤、唯一 @RequestBody、禁 Web 参数类型、路径防重复）；Gateway 路由测试补 auth-routes Path/StripPrefix 固化与路由总数恰 3；PermissionFilter 生产告警与 PermCenterUnreachableException→AccessServiceUnreachableException、copilot-instructions sourceService 示例等 4 处旧名清理。全量回归通过（access 610 / gateway 59 / perm-client 12）。二轮复评 2 P2 + 1 P3 属实修复（禁用注解断言改按注解实例判定——原参数类型检查对 @RequestBody+@RequestParam 共存无效；路由总数改直接断言 routes.size()==3——原 byId HashSet 折叠重复 ID；删未使用 import）。
- 2026-08-22：T-ACCESS-011 done（契约/回滚/架构/空库/双实例验收门禁：新增 45 个验收测试；验收期修复 Gateway 会话两项缺陷——P0 getExtra 恒 401 改读 SaSession、P1 无操作超时校验+滑动续期；40 个 Docker 门控容器测试按环境受限豁免待 CI，计划归档前必须跑绿。详见 [任务卡](../../tasks/T-ACCESS-011.md)）。同日评审修复：Gateway 阻塞 Redis 调用移出事件循环（boundedElastic）、契约快照封闭化（类级/方法级全路径枚举、DTO 全包路径消歧、统一响应包装断言）、新增双上下文共享容器 PG/Redis 双实例测试（缓存跨实例失效/任务抢占/接管，Docker 门控）。

- 2026-08-22：T-ACCESS-012 done（设计全量回写与残留清理收口）：architecture.md §2~§7 按归并后实现重写并收敛重复（快照模式鉴权、admin 域概览、同事务本地投影）；`services/admin-service.md` 标 superseded 并与四份旧 DDL 一并物理归档至 `docs/archive/2026-08-22/`，`admin-service-api-contract.md` 补归并定位与术语映射；permission-center 四件套、v3.5、example/default-org-tree 与前端设计文档 schema 权威统一改指 `access-service.sql`，双服务叙事与轮次标记清理；前端 mock/src 旧 schema 注释修正；proposed T-PERM/T-ADMIN 任务逐卡重基线（T-PERM-033 取消聚合层直连 /api/perm/*、T-PERM-034 裁 RoleProxyServiceImpl 失效子项并保留 SDK 契约面删除断任务、T-PERM-019 移除 D4、T-PERM-021 F1.e 收窄为外部 sync runbook、T-ADMIN-021 去远程故障语义、T-PERM-039 修正悬空引用）；任务看板门禁解除、设计变更待核对逐项收口；README/AGENTS 索引同步。后续任务 T-ACCESS-013/014 迁入 [access-post-merge-plan](../2026-08-27/access-post-merge-plan.md)（该计划已于 2026-08-27 完成并归档）；本计划 12 个任务全部 done，转 completed 并归档至 `docs/archive/2026-08-22/`。T-ACCESS-011 登记的「CI 跑绿 40 个 Docker 门控测试」原为本计划归档前置条件，经用户决策（2026-08-22）随归档转移至 access-post-merge-plan 准入条件。

## 归档条件

- 所有任务完成或取消，任务看板无 dangling 依赖。
- 目标架构、整体架构、服务设计、权限设计、API 契约和最终 schema 已按实现结果回写。
- 计划稳定结论已经沉淀到设计层，且完成归档自检。
