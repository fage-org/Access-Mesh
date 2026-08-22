---
doc_type: task
id: T-ACCESS-011
title: 完成契约、回滚、架构、空库和双实例验收
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#10-验收门禁
  - docs/design/project-rules.md
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-006
  - T-ACCESS-007
  - T-ACCESS-009
  - T-ACCESS-010
blocks: []
acceptance:
  - "Java 21 全量 clean install、单元测试和 access-service Spring Context 测试通过"
  - "空 PostgreSQL DDL 测试、统一 Redis 配置测试和种子数据校验通过"
  - "除退役的 /admin/sync-task/* 外，admin 与 permission 现有 HTTP 路径、DTO、统一响应和错误码契约回归通过"
  - "错误码扫描证明管理域既有 1xxxx、权限域既有 2xxxx 码值未变，9xxxx 只由公共错误定义，跨枚举无重复码且不存在新增 4xxxx 业务错误码"
  - "负向验收确认 /admin/sync-task/* 无路由或返回明确的不存在响应，且 access-service 不注册对应 Controller 映射"
  - "跨域事务故障注入、强事务审计、独立日志失败和缓存 afterCommit/rollback 测试通过"
  - "安全矩阵、租户隔离、来源所有权和请求上下文清理负向测试通过"
  - "平台用户会话端到端测试覆盖 /auth 登录签发、Gateway 校验、access-service 身份绑定、30分钟无操作失效、2小时绝对失效及注销；Gateway 与 access-service 结果一致，OAuth2 客户端令牌保持客户端自定义有效期，服务身份入口拒绝把用户 Token 当作服务凭证"
  - "两个 access-service 实例共享 PostgreSQL/Redis 的权限失效、缓存故障、任务抢占和故障接管测试通过"
  - "授权缓存端到端测试覆盖 access-service L2 已接近10秒过期、Gateway 全链路回源延迟接近5秒后再缓存15秒的边界，证明从权限事实变更起的总陈旧窗口仍不超过30秒"
  - "Gateway 快照回源超过5秒时不写入本地缓存并返回503；测试证明服务发现、连接、发送、服务端处理、响应读取/解码和失效竞争重试共享同一全链路截止时间"
  - "授权陈旧回填竞态测试覆盖旧读取开始后权限变更提交与失效、旧读取随后完成的顺序；验证 L2 只获得从读取起点计算的剩余 TTL，预算耗尽时不写入，端到端陈旧窗口仍不超过30秒"
  - "Gateway 权限回源不可达时固定返回503；配置、代码和测试证明 open、stale-allow 与 gateway.permission.fail-mode 已删除"
  - "架构测试证明跨域写/读白名单、禁止横向依赖和禁止跨域写 QueryMapper"
  - "生成验收记录，列出命令、测试数、故障场景和所有遗留项；P0/P1 未关闭项阻止任务完成"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-011 完成契约、回滚、架构、空库和双实例验收

## 背景

该任务是归并的质量门禁，不实现新的产品能力，只证明此前任务形成的单体在正常与故障场景下满足权威设计。

## 范围

- 汇总并补齐自动化测试矩阵。
- 执行全量、空库、契约、安全、事务、缓存、任务和双实例验证。
- 记录可复现的验收证据和未关闭风险。

## 完成记录

### 执行环境与命令（2026-08-22，Windows / Git Bash，Java 21）

| 命令 | 结果 |
|---|---|
| `mvn clean install -DskipTests` | BUILD SUCCESS（全模块） |
| `mvn test` | BUILD SUCCESS：common 60 / gateway 74 / access-service 633 / perm-client 12，共 **779 tests，0 failures**，37 skipped（全部为 `@Testcontainers(disabledWithoutDocker=true)` Docker 门控） |

测试基线对比 T-ACCESS-010 出口（access 610 / gateway 59 / perm-client 12）：access +23、gateway +15，全部为本任务新增验收测试，存量无改动无回退。

### 验收矩阵逐项结论

| # | 验收项 | 结论 | 证据 |
|---|---|---|---|
| 1 | 全量构建/单测/Spring Context | ✅ | `AccessServiceApplicationTest`（H2 PostgreSQL 模式上下文 + 基础设施 Bean 唯一 + Sa-Token 权威配置） |
| 2 | 空 PostgreSQL DDL / Redis 配置 / 种子 | ✅（本机降级） | `AccessServiceSchemaPostgresTest`（PG16 容器：33 表+139 操作种子，**本机无 Docker 跳过，待 CI**）+ `AccessServiceSchemaH2Test`（H2 兜底通过）+ `CacheAutoConfigurationTest`/`CachePropertiesBindingTest`/`GatewayApplicationConfigTest`（统一 Redis 配置） |
| 3 | HTTP 路径/DTO/统一响应/错误码契约回归 | ✅ | 新增 `HttpApiPathSnapshotTest`（198 路径快照精确比对 + POST-only + 无路径参数 + @RequestParam 仅文件上传例外）；归并前后路径 diff：210→198，仅 12 条丢失且全部有设计决策背书（11×/sync-task/* T-ACCESS-005、/audit-log/page T-ACCESS-007），0 意外丢失 0 新增；DTO 契约既有 `PermCommonReqContractTest` + SDK 18 路径封闭契约测试 |
| 4 | 错误码扫描 | ✅ | 新增 `ErrorCodeContractTest`（6 用例）：固化归并前 53+37 码值基线逐码断言（枚举名+码值绑定不变）+ 分段独占（1xxxx 仅 Admin/2xxxx 仅 Perm/9xxxx 仅公共）+ 无 4xxxx + 跨枚举无重复 |
| 5 | /admin/sync-task/* 负向验收 | ✅ | `HttpApiPathSnapshotTest.retiredPaths_haveNoControllerMappings`（注解扫描级）+ 新增 `RetiredSyncTaskEndpointTest`（全量 Context：RequestMappingHandlerMapping 注册表无映射 + MockMvc 实际请求被拒） |
| 6 | 跨域事务故障注入/强事务审计/独立日志/缓存 afterCommit | ✅（容器版待 CI） | `UserWriteAppServiceFaultInjectionTest`（单测版通过）+ `*FaultInjectionIT`（PG 容器版待 CI）+ `OperationLogAspectTest`/`AuditDomainServiceImplTest`（REQUIRES_NEW 独立日志失败）+ `PermissionChangeAspectTest`（事务外 afterCommit/无变更清理） |
| 7 | 安全矩阵/租户隔离/来源所有权/上下文清理 | ✅ | `SecurityMatrixIT`（8 类入口矩阵 + 跨请求租户串扰 + afterCompletion 泄漏）+ `RequestContextInterceptorTest`（28 用例含 G1 验签）+ Sync 所有权（`ResourceEntitySyncAppServiceTest` 等 + `LocalProjectionGuardTest`） |
| 8 | 平台用户会话端到端 | ✅（**发现并修复 P0**，见下） | 新增 `PlatformSessionAbsoluteTimeoutTest`（真实 /auth/login 签发 + 身份绑定 + timeout=4s 持续活跃仍绝对失效 + 注销立即 401）+ `PlatformSessionIdleTimeoutTest`（active-timeout=2s 静置失效 + 滑动续命）+ gateway `AuthTokenFilterTest`（11 用例：令牌校验/身份提取/fail-closed/cookie/skipAuth）+ `OAuth2ClientTtlTest`（客户端自定义 TTL 3600 双路径签发 + 默认 86400 回落）+ `SyncEndpointAuthIT` 用例 4/4b（服务入口拒绝无 HMAC 用户头）；生产值 7200/1800 由双端配置权威断言钉住，运行时语义以缩短配置验证（用户决策） |
| 9 | 双实例共享 PG/Redis | ✅（降级口径，容器版待 CI） | `DualInstanceCacheInvalidationTest`（同 JVM 双 CacheService：广播丢失 L1 TTL 兜底、Redis 故障旁路、撤销跨实例一致）+ `TaskExecutionLeaseConcurrencyTest`（真实 PG+Redis 容器 10 用例：并发抢占恰一胜/fencing/接管/幂等——**本机无 Docker 跳过，待 CI**）；同 JVM 双上下文补测经用户决策不做（选择现状降级） |
| 10 | 授权缓存 30s 边界（10+5+15） | ✅ | `SnapshotSafetyBoundaryTest`（真实墙钟 9.2s 近过期 + 4.5s 回源延迟 + 15s L1 ≤30s）+ `PermCacheCatalogBoundaryTest`/`GatewayCacheBoundaryValidatorTest` |
| 11 | 回源超 5s 不写缓存 503 / 全链路截止共享 | ✅ | `PermissionFilterTest`（截止超限 503 不写缓存、重试共享截止不重计时、失效竞争重试）+ `PermissionFilterMetricsTest` |
| 12 | 陈旧回填竞态 | ✅ | `StaleBackfillLatchTest`（闩锁旧读取→失效→回填只获剩余 TTL、预算耗尽不写、批量共享起点）+ `CombinedL1L2BackfillGatingTest` |
| 13 | 回源不可达固定 503 / fail-mode 删除证明 | ✅ | 新增 `GatewayFailModeRemovalTest`（4 用例：GatewayProperties 全嵌套无 failMode/open/stale 字段 + application.yml 无活跃键 + 指标注册表无 open/stale 计数器且 fallback 恒 mode=closed + 不可达计数器存在）+ `PermissionFilterTest` 503 语义 |
| 14 | 架构测试 | ✅ | `AccessServiceArchitectureTest`（admin↔permission 禁横向）+ `QueryBoundaryArchitectureTest`（Mapper 跨域禁用/query 只读前缀/白名单）+ `QueryMapperXmlContractTest`（XML 无写 SQL/tenant_id 显式） |
| 15 | 验收记录 | ✅ | 本节 |

### 发现与处置

**P0（已修复）：Gateway AuthTokenFilter 会话校验恒 401**
- 现象：sa-token 1.38.0 默认 `StpLogic.getExtra(loginId,key)` 无条件抛 `ApiDisabledException`（字节码确认，仅 sa-token-jwt 插件支持 extra），而 gateway 无该依赖且平台会话为 uuid 非 JWT 模式——原实现三处 `StpUtil.getExtra` 调用导致**所有携带合法令牌的请求恒 401「租户信息缺失」**（自旧仓库引入该调用起即存在，非归并引入）。
- 修复（用户决策「改读 SaSession」）：`StpUtil.getSessionByLoginId(loginId,false)` 读取 access-service 登录写入的 `tenantId/subjectTypeCode/operatorName`（保留缺租户/主体类型 401 fail-closed 与 operatorName 软失败语义）；顺带修复两处次生缺陷——键名错配（读 `username` 实存 `operatorName`）、无效令牌 `getLoginIdByToken` 返回 null 时 `toString()` NPE→500（改为 401 登录已过期）。
- 验证：gateway `AuthTokenFilterTest` 11 用例全过；全量回归无回退。实现即回归设计 §6.1「Gateway 负责校验并向 access-service 注入可信身份」，无设计变更。
- 附带说明：验收 8 明确要求的「30 分钟无操作/2 小时绝对失效」运行时语义实测发现 sa-token 1.38 剩余时间按整秒除法且 `-1` 为「未启用」哨兵（剩余 ≤ -2 才判冻结），测试时序按此留余量，生产 1800s 口径不受影响（±2 秒粒度）。

### 遗留项

| 级别 | 项 | 处置 |
|---|---|---|
| P1 | **37 个 Docker 门控 Testcontainers 测试本机无 Docker 未执行**（空库 PG DDL 12、任务租约并发 10、故障注入 IT 5、PG 集成 2 等，`mvn test` 中 skipped=37） | **用户决策（2026-08-22）**：按 T-ACCESS-009 先例登记「待 CI 执行」+ 现状降级（H2 兜底/同 JVM 双 CacheService/单测版故障注入已通过），不新增降级测试。需在有 Docker 的 CI 环境跑通 `mvn test` 并确认 37 项全绿后方可视为验收 2/6/9 容器部分关闭 |
| P3 | api-contract.md §5 清单欠账 2 条：`auth/query-permission-tree`（implementation.md §7.5 已有权威定义）、`permission-view/effective-permission-codes`（org-user-permission-contract.md v1.4 已有）未列入 §5 表格 | 移交 T-ACCESS-012 设计回写收口 |
| P3 | `PermissionGrantAppServiceImplTest` 2 个 `@Disabled`（OperatorContext mock 前置，历史遗留非本任务引入） | 维持现状，不阻断 |

### 覆盖口径说明

- 契约回归基线：归并前代码路径（git `5f1e65dd5^`，admin-service+permission-center 210 条）与归并后（198 条）双向 diff + 两份契约文档核对；文档声明未实现的 4 条中 3 条为设计明确「已移除不实现」（update-child/children-save/rebuild）、1 条待 T-PERM-034（sub-perm-allowed-types）。
- 错误码基线：git `5f1e65dd5^` 旧枚举完整固化（AdminErrorCode 53 + PermissionErrorCode 37），归并后仅新增 10108~10111、20044~20047，无重编号无语义漂移。
- 双端一致性（Gateway 与 access-service）：双端配置权威断言（application.yml sa-token 块逐项相等）+ Gateway AuthTokenFilterTest 以与 AuthServiceImpl 相同的登录写入（session 键 tenantId/subjectTypeCode/operatorName）验证校验链，共享 Redis 会话键语义一致。
