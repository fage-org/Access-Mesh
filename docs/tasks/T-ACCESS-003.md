---
doc_type: task
id: T-ACCESS-003
title: 收敛单数据源、MyBatis、Redis、JSON等运行基础配置
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/access-service-architecture.md#71-统一缓存框架
  - docs/design/project-rules.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-001
  - T-ACCESS-002
blocks: []
acceptance:
  - "access-service 只装配 access_db 数据源、一个事务管理器和一套 MyBatis-Flex 租户配置"
  - "Redis 统一为 logical DB 0；业务缓存只暴露一个 CacheService；Sa-Token 使用与业务缓存隔离、但由 Gateway 与 access-service 共享的唯一会话键命名空间"
  - "盘点并消除 Gateway 与原 admin 的 Sa-Token 配置差异，统一 token-name、login-type（文档口径，Sa-Token 无该配置键）、Token解析模式（token-style=uuid）、密钥（uuid 模式无会话密钥，jwt-secret-key 仅 OAuth2 使用）和会话键命名空间；平台用户会话固定 timeout=2小时、active-timeout=30分钟。两端关键配置一致性由部署配置约束保障，代码不实现跨进程启动校验（用户决策 2026-08-13：运维部署部分不影响代码逻辑）；jwt-secret-key 无默认值，缺失时 Spring 占位符解析失败天然启动失败"
  - "删除重复 TenantContextHolder、MybatisFlexTenantConfig、自定义 ObjectMapper 和重复 Redis 序列化配置"
  - "删除 admin Spring Cache/裸 Caffeine 与自定义 RedisTemplate<String,Object> 序列化配置；业务侧 StringRedisTemplate 直接操作保留（验证码/登录计数/OAuth2 的 Lua 原子语义无法用 CacheService 表达，属基础设施 Bean，用户决策 2026-08-13）"
  - "配置属性、profile 与测试配置均不再引用 admin_db、perm_db 或两套 Redis DB"
  - "应用上下文测试证明基础设施 Bean 唯一且序列化、租户解析可用"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-13
---

# T-ACCESS-003 收敛单数据源、MyBatis、Redis、JSON等运行基础配置

## 背景

物理归并后，重复基础设施配置会造成 Bean 冲突和不一致的租户、缓存及 JSON 行为。

## 范围

- 收敛数据源、事务、MyBatis、Redis、缓存和 Jackson 配置。
- 删除旧服务特有且与统一框架冲突的配置。
- 增加唯一性和启动验证。

## 完成记录

### 实施摘要（2026-08-13）

**用户决策**（AskUserQuestion，4 项 + 1 项原子性澄清）：

| # | 决策点 | 结论 |
|---|---|---|
| 1 | Sa-Token token-style 统一方向 | 统一 uuid：access-service `token-style: jwt`→`uuid`（uuid 模式无会话密钥概念，会话有效性以共享 Redis 条目为唯一事实，Redis 清空后两端一致失效 fail-closed）；`sa-token-jwt` 依赖与 `jwt-secret-key` 配置保留（OAuth2 访问令牌经 `SaJwtUtil.createToken` HS256 独立签发，与平台会话 token-style 无关） |
| 2 | 「两端关键配置缺失或不一致时启动失败」 | **不实现代码校验**（用户决策：运维部署部分，不应影响代码逻辑）；两端配置一致性由部署配置约束保障；`jwt-secret-key` 为 `${JWT_SECRET_KEY}` 无默认值，缺失时 Spring 占位符解析失败天然启动失败 |
| 3 | LoginResp.expiresIn 口径 | 做成配置：新增 `access.session.expires-in-seconds: 7200`（与 `sa-token.timeout=7200` 一致），login/smsLogin 返回该值；自动续期由 `active-timeout=1800` 滑动机制提供（Sa-Token 校验时自动更新 last-active-time）；前端不再展示剩余时间（登记前端任务）；OAuth2 `/oauth2/token` 的 accessTokenTtl 继续用客户端注册 TTL（架构 §6.1） |
| 4 | 业务侧 StringRedisTemplate 直接操作 | **全部保留**（用户决策，原子性澄清后确认）：验证码/短信码 Lua GET+DEL 一次性消费、登录失败计数 Lua INCR+EXPIRE、OAuth2 授权码/刷新令牌/黑名单（含 Lua 原子脚本）均依赖原子语义，CacheService 仅 get/put/evict 无法表达，多实例（架构目标 2+ 实例）下收敛会破坏安全语义 |
| 5 | login-type 配置键（实施中核实） | Sa-Token 无 `login-type` 配置键（编译验证 `SaTokenConfig` 无该属性）；登录类型为 `StpUtil.login()` 默认 `"login"`，两侧一致——文档口径而非配置项，未写入 yml |

**实施**：

1. **Sa-Token 统一（access-service application.yml）**：`timeout` 86400→7200、`active-timeout` -1→1800、`token-style` jwt→uuid，注释声明权威值；`jwt-secret-key` 保留并注明仅 OAuth2 使用
2. **Sa-Token 统一（gateway bootstrap.yml）**：sa-token 块显式 `token-style: uuid` + 权威值注释（timeout=7200/active-timeout=1800 两侧原已一致）
3. **删除重复配置**：`admin/config/CacheConfig.java`（Spring Cache + RedisCacheManager + Caffeine + @EnableCaching，全仓无 @Cacheable 使用者）、`permission/config/RedisConfig.java`（自定义 `RedisTemplate<String,Object>` 无注入点 + 裸 `ObjectMapper` 顶掉 Boot 自动配置使 `spring.jackson` 失效）；pom 删除 `spring-boot-starter-cache`；application.yml 删除死配置 `cache.caffeine.spec`
4. **expiresIn 配置化（AuthServiceImpl）**：`@Value("${access.session.expires-in-seconds:7200}")`，login()/smsLogin() 的 LoginResp.expiresIn 改传配置值，移除 `client.getAccessTokenTtl()` 分支与未使用局部变量
5. **Context 测试强化（AccessServiceApplicationTest，+4 断言）**：基础设施 Bean 唯一（DataSource / PlatformTransactionManager=FlexTransactionManager / CacheService / ObjectMapper）、ObjectMapper JavaTimeModule 生效（LocalDateTime 序列化可用）、Sa-Token 权威配置生效（timeout=7200 / active-timeout=1800 / token-name=Authorization / token-style=uuid）、租户上下文设置与清理可用（TenantContextHolder + MybatisFlexTenantConfig 装配）

**验证结果**：全量 **376 测试 0 失败 19 跳过**（基线 372 + 新增 4 个 Context 断言）；Context 测试 6/6；`mvn clean compile test-compile` 通过。

**设计回写**：`access-service-architecture.md` §6.1 回写 Sa-Token 权威配置值（token-style=uuid、login-type 文档口径、jwt-secret-key 仅 OAuth2、两端一致性由部署配置约束保障且代码不实现跨进程启动校验）。

### 评审修复（2026-08-13，ultracode 四维度评审 + 对抗性核实）

AI 评审（4 维度并行 + verify 对抗核实）发现 4 项问题，全部修复：

| # | 评审问题 | 严重度 | 处理 |
|---|---|---|---|
| 1 | **access-service 缺 `token-prefix: Bearer`**（3 个维度独立发现，sa-token-core 1.38 源码验证）：前端恒发 `Authorization: Bearer <token>`（frontend/src/utils/auth.ts formatToken），Gateway 配置 token-prefix 剥离前缀后查 Redis 通过，而 access-service 默认空前缀会把整个 `"Bearer <uuid>"` 当 token 查键 `Authorization:login:token:Bearer <uuid>` → MISS → access-service 内全部 StpUtil 校验 401（TenantInterceptor 会话租户解析、AuditLogAspect userId、AdminAuthController `/auth/userinfo`/`/auth/user-menu`/`/auth/logout` 白名单直连、UserServiceImpl、NoticeController、OAuth2Controller、AdminPermissionValidatorImpl） | P1 | 修复：application.yml 补 `token-prefix: Bearer`（两侧权威值注释声明）；Context 测试补 token-prefix 断言（回归保障）。既有缺陷（归并前存在），本任务"统一会话"落地点修复 |
| 2 | expiresInSeconds 默认值三重维护（yml 7200 + @Value 默认 7200 + 字段初始化 =7200，且与 sa-token.timeout 双源耦合无测试捕捉） | P3 | 修复：删除字段初始化死代码（@Value 注入恒覆盖），注释明确 yml 为唯一权威来源 |
| 3 | 新增注释 "satoken:* 默认命名空间" 与 sa-token 1.38.0 实际键格式不符（`splicingKeyTokenValue()` = `tokenName:loginType:token:tokenValue`，即 `Authorization:login:token:*`，无 "satoken:" 前缀常量） | P3 | 修复：application.yml 与 gateway bootstrap.yml 注释改为实际键格式（排障时按正确键找） |
| 4 | `gateway/gateway-cp.txt` 被 git 跟踪的 IDE 类路径转储（含本机绝对路径与 starter-cache 条目；核实确认 starter-cache 条目为 loadbalancer 传递依赖，内容真实，但 IDE 转储本不应提交） | P3 | 修复：删除该文件（IDE 生成物不入库） |

**评审修复验证**：Context 测试 6/6（含 token-prefix 断言）；全量回归见下文。

### 验收项落实情况

| 验收项 | 结论 |
|---|---|
| 单数据源 / 单事务管理器 / 单套 MyBatis-Flex 租户配置 | 已满足（T-ACCESS-001/002 已收敛至 infrastructure）；Context 测试断言唯一 |
| Redis logical DB 0 / 唯一 CacheService / Sa-Token 隔离命名空间 | 已满足（DB 0；common 框架唯一 CacheService；Sa-Token 会话键 `Authorization:login:*`（tokenName:loginType 前缀）与业务缓存键 `{tenantId}:{catalogCode}:{identifier}`（首段数字租户 ID）天然隔离）；测试断言 |
| Sa-Token 差异消除 + 平台会话 2h/30min | 已消除（timeout=7200 / active-timeout=1800 / token-style=uuid 两侧统一）；「启动失败」按用户决策不实现代码校验（见决策 2），jwt-secret-key 缺失天然启动失败 |
| 删除重复配置（TenantContextHolder/MybatisFlexTenantConfig/自定义 ObjectMapper/重复序列化） | 已删除（前两者 T-ACCESS-002 已唯一化；本任务删除 RedisConfig 裸 ObjectMapper 与 RedisTemplate 序列化配置） |
| 删除 admin Spring Cache/裸 Caffeine 与业务侧 RedisTemplate 直接操作 | Spring Cache/Caffeine 已删；业务侧 StringRedisTemplate 按用户决策保留（见决策 4） |
| 配置不再引用 admin_db/perm_db/两套 Redis DB | 已满足（盘点零残留：代码/配置/测试均无 admin_db、perm_db；Redis 仅 gateway/access 两处 database: 0） |
| 应用上下文测试证明 Bean 唯一且序列化、租户解析可用 | 已强化（4 个新断言，见实施 5） |

### 范围外登记（后续任务输入）

- 双租户拦截器并存（admin `TenantInterceptor` 允许会话解析 vs permission `PermTenantInterceptor` 严格请求头，`/api/**` 上双写 TenantContextHolder）→ **T-ACCESS-004**（可信请求上下文与统一安全策略矩阵）
- Gateway 路由 `lb://admin-service`/`lb://permission-center`、perm-sdk Feign 目标（`PermissionFeignClient`/`SyncTaskFeignClient`/`FeignInternalSyncInterceptor` @Value 默认值）、access-service 内服务名常量（`SOURCE_SERVICE="admin-service"` 等 8 处，参与 `sys_sync_task.batch_key` 数据契约）→ **T-ACCESS-005/010**
- `perm.cache.*` 分钟字段（`l1.expire-minutes`/`l2.ttl-minutes`，无消费者）→ **T-ACCESS-008** 一次性迁移（架构 §7.1）
- `example-service` bootstrap.yml `database: 2` → example-service 独立参考服务（归并范围外，登记观察）
- 前端 expiresIn 剩余时间倒计时展示移除 → 前端任务登记（决策 3 配套）

### 已知限制

- uuid 模式下会话有效性依赖共享 Redis 条目（会话键 `Authorization:login:*`，DB 0）；Redis 清空后两端一致失效（fail-closed），与决策 1 一致
- OAuth2 access_token 有效期由客户端注册 TTL 决定（默认 86400），不套用平台会话 2h/30min 口径（架构 §6.1）
- expiresIn 改动无行为级单测（access-service 无 AuthServiceImpl 单测）；由 Context 测试环境属性断言（`access.session.expires-in-seconds`）与 yml 唯一权威来源保证（评审 P3 记录）
