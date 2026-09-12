---
doc_type: task
id: T-ACCESS-003
title: 收敛单数据源、MyBatis、Redis、JSON等运行基础配置
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
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
last_updated: 2026-08-14
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
| 3 | LoginResp.expiresIn 口径 | 做成配置（单一权威来源，评审 P2 修订）：`login/smsLogin` 的 expiresIn 直接读 `SaManager.getConfig().getTimeout()`（即 `sa-token.timeout=7200`，真实会话 TTL，配置驱动且永不漂移）；独立配置键 `access.session.expires-in-seconds` 已移除（双源耦合风险：Nacos 只覆盖一项即漂移）；自动续期由 `active-timeout=1800` 滑动机制提供（Sa-Token 校验时自动更新 last-active-time）；前端不再展示剩余时间（登记前端任务）；OAuth2 `/oauth2/token` 的 accessTokenTtl 继续用客户端注册 TTL（架构 §6.1） |
| 4 | 业务侧 StringRedisTemplate 直接操作 | **全部保留**（用户决策，原子性澄清后确认）：验证码/短信码 Lua GET+DEL 一次性消费、登录失败计数 Lua INCR+EXPIRE、OAuth2 授权码/刷新令牌/黑名单（含 Lua 原子脚本）均依赖原子语义，CacheService 仅 get/put/evict 无法表达，多实例（架构目标 2+ 实例）下收敛会破坏安全语义 |
| 5 | login-type 配置键（实施中核实） | Sa-Token 无 `login-type` 配置键（编译验证 `SaTokenConfig` 无该属性）；登录类型为 `StpUtil.login()` 默认 `"login"`，两侧一致——文档口径而非配置项，未写入 yml |

**实施**：

1. **Sa-Token 统一（access-service application.yml）**：`timeout` 86400→7200、`active-timeout` -1→1800、`token-style` jwt→uuid，注释声明权威值；`jwt-secret-key` 保留并注明仅 OAuth2 使用
2. **Sa-Token 统一（gateway bootstrap.yml）**：sa-token 块显式 `token-style: uuid` + 权威值注释（timeout=7200/active-timeout=1800 两侧原已一致）
3. **删除重复配置**：`admin/config/CacheConfig.java`（Spring Cache + RedisCacheManager + Caffeine + @EnableCaching，全仓无 @Cacheable 使用者）、`permission/config/RedisConfig.java`（自定义 `RedisTemplate<String,Object>` 无注入点 + 裸 `ObjectMapper` 顶掉 Boot 自动配置使 `spring.jackson` 失效）；pom 删除 `spring-boot-starter-cache`；application.yml 删除死配置 `cache.caffeine.spec`
4. **expiresIn 单一来源（AuthServiceImpl，评审 P2 修订）**：改读 `SaManager.getConfig().getTimeout()`（即 sa-token.timeout=7200，真实会话 TTL），移除 `client.getAccessTokenTtl()` 分支、`@Value` 注入与未使用局部变量；`access.session.expires-in-seconds` 独立键已删除
5. **Context 测试强化（AccessServiceApplicationTest，+5 断言）**：基础设施 Bean 唯一（DataSource / PlatformTransactionManager=FlexTransactionManager / CacheService / ObjectMapper）、ObjectMapper JavaTimeModule 生效（LocalDateTime 精确断言 ISO-8601）、Sa-Token 权威配置生效（timeout=7200 / active-timeout=1800 / token-name=Authorization / token-prefix=Bearer / token-style=uuid）、租户上下文设置与清理可用（TenantContextHolder + MybatisFlexTenantConfig 装配）、expiresIn 无独立配置键（防旧配置残留）

**验证结果**：全量 **378 测试 0 失败 19 跳过**（基线 372 + 新增 6 个 Context 断言）；Context 测试 8/8；Gateway 全量 74 测试 0 失败（新增 5 个配置加载/日志断言）；`mvn clean compile test-compile` 通过。

**设计回写**：`access-service-architecture.md` §6.1 回写 Sa-Token 权威配置值（token-style=uuid、login-type 文档口径、jwt-secret-key 仅 OAuth2、两端一致性由部署配置约束保障且代码不实现跨进程启动校验）。

### 评审修复（2026-08-13，ultracode 四维度评审 + 对抗性核实）

AI 评审（4 维度并行 + verify 对抗核实）发现 4 项问题，全部修复：

| # | 评审问题 | 严重度 | 处理 |
|---|---|---|---|
| 1 | **access-service 缺 `token-prefix: Bearer`**（3 个维度独立发现，sa-token-core 1.38 源码验证）：前端恒发 `Authorization: Bearer <token>`（frontend/src/utils/auth.ts formatToken），Gateway 配置 token-prefix 剥离前缀后查 Redis 通过，而 access-service 默认空前缀会把整个 `"Bearer <uuid>"` 当 token 查键 `Authorization:login:token:Bearer <uuid>` → MISS → access-service 内全部 StpUtil 校验 401（TenantInterceptor 会话租户解析、AuditLogAspect userId、AdminAuthController `/auth/userinfo`/`/auth/user-menu`/`/auth/logout` 白名单直连、UserServiceImpl、NoticeController、OAuth2Controller、AdminPermissionValidatorImpl） | P1 | 修复：application.yml 补 `token-prefix: Bearer`（两侧权威值注释声明）；Context 测试补 token-prefix 断言（回归保障）。既有缺陷（归并前存在），本任务"统一会话"落地点修复 |
| 2 | expiresInSeconds 默认值三重维护（yml 7200 + @Value 默认 7200 + 字段初始化 =7200，且与 sa-token.timeout 双源耦合无测试捕捉） | P3 | 修复：删除字段初始化死代码（@Value 注入恒覆盖），注释明确 yml 为唯一权威来源 |
| 3 | 新增注释 "satoken:* 默认命名空间" 与 sa-token 1.38.0 实际键格式不符（`splicingKeyTokenValue()` = `tokenName:loginType:token:tokenValue`，即 `Authorization:login:token:*`，无 "satoken:" 前缀常量） | P3 | 修复：application.yml 与 gateway bootstrap.yml 注释改为实际键格式（排障时按正确键找） |
| 4 | `gateway/gateway-cp.txt` 被 git 跟踪的 IDE 类路径转储（含本机绝对路径与 starter-cache 条目；核实确认 starter-cache 条目为 loadbalancer 传递依赖，内容真实，但 IDE 转储本不应提交） | P3 | 修复：删除该文件（IDE 生成物不入库） |

**评审修复验证**：Context 测试 7/7（含 token-prefix 断言）；全量回归见下文。

### 第二轮评审修复（2026-08-14，Request Changes：1 P1 + 2 P2 + 1 P3）

外部 AI 评审发现 4 项问题，经逐项核实全部成立（3 项用户决策）：

| # | 评审问题 | 严重度 | 核实结论 | 用户决策 | 处理 |
|---|---|---|---|---|---|
| 1 | **Gateway 的 bootstrap.yml 实际没有加载**（Boot 3 默认不加载 bootstrap.yml；无 spring-cloud-starter-bootstrap、无 bootstrap.enabled=true；运行时 sa-token/Redis/路由/Nacos 属性全部 null；Gateway 用默认 token-name=satoken 读不到 access-service 的 `Authorization:login:*` 会话） | P1 | 成立（gateway 无 application.yml、测试全为 mock 单测从不加载配置） | 迁移 application.yml（Boot 3 标准 ConfigData） | bootstrap.yml→application.yml + `spring.config.import: optional:nacos:gateway.yml`；新增 `GatewayApplicationConfigTest`（4 用例：sa-token 权威值/Redis DB 0/4 条路由/gateway.* 配置）。**上下文启动暴露并修复 7 个 Gateway 既有启动缺陷**：①GatewayApplication 排除 CommonAutoConfiguration（common 的 WebMvc GlobalExceptionHandler 与 gateway 同名冲突）②自定义 GatewayProperties 指定 Bean 名 accessGatewayProperties（与 Spring Cloud Gateway 自带类默认 bean 名冲突）③`#{@gatewayProperties...}` SpEL 引用同步更新 ④pom 排除 spring-webmvc 传递依赖（SCG MvcFoundOnClasspathException：Gateway 不允许 MVC/WebFlux 共存）⑤删除 SaTokenConfig（sa-token-redis-jackson 自动配置无条件创建 SaTokenDaoRedisJackson，重复 Bean）⑥路由前缀 `spring.data.gateway.*`→`spring.cloud.gateway.*`（原前缀错误，路由从未加载）⑦测试用 WebEnvironment.MOCK（reactive 上下文，ServerProperties 加载） |
| 2 | expiresIn 多权威来源（sa-token.timeout + access.session.expires-in-seconds + @Value 默认值三处 7200；Nacos 只覆盖一个即漂移） | P2 | 成立（第一轮评审曾以 P3 提过，本轮升级） | 读 sa-token.timeout 单一来源（sa-token.timeout 本身即配置，符合"做成配置"决策） | AuthServiceImpl `expiresInSeconds` 字段删除，改 `SaManager.getConfig().getTimeout()`；删除 `access.session.expires-in-seconds` 键；Context 测试改断言独立键已移除（防旧配置残留） |
| 3 | 「Boot 唯一 ObjectMapper」断言与实际不符（条件报告证实全局实例是 common 的 cacheObjectMapper，Boot 的 jacksonObjectMapper 回退，spring.jackson.* 不驱动全局） | P2 | 成立（cacheObjectMapper 有 JavaTimeModule，功能完备；LocalDateTime 输出与 Boot mapper 无差异——date-time-format 仅作用 java.util.Date，响应 DTO 几乎全 LocalDateTime，实际影响很小） | 接受现状+修注释断言 | Context 测试注释改述实际装配（common cacheObjectMapper 提供全局）；序列化断言精确化为 ISO-8601（2026-08-13T10:30:00）；application.yml spring.jackson 配置加说明（未来装配顺序调整时生效） |
| 4 | 完成记录测试数字过期（376/6/6/4 vs 实际 377/7/7/5） | P3 | 成立 | — | 完成记录统一为最终基线：377 测试、Context 7/7、新增 5 断言；Gateway 全量 72 测试 |

**第二轮修复验证**：access-service 全量 378 测试 0 失败 19 跳过；Gateway 全量 74 测试 0 失败（含新 5 个配置加载用例）；Context 8/8。

**P1 修复的部署语义**（评审未列但核实附带）：Gateway 迁移后 `spring.config.import: optional:nacos:gateway.yml` 与 access-service 同模式，Nacos 配置（gateway.yml 若存在）开始真正加载——**归并前 Gateway 的所有配置（含 Nacos 远端）实际从未生效**，本任务修复后首次按配置运行，生产部署需核对 Nacos 中 gateway.yml 是否存在（不存在则 optional 静默跳过，仅本地 application.yml 生效）。

### 第三轮评审修复（2026-08-14，复评：1 P2 + 2 P3）

复评确认前两轮核心问题已修复，新发现 3 项（无决策点，方向明确直接处理）：

| # | 评审问题 | 严重度 | 核实结论 | 处理 |
|---|---|---|---|---|
| 1 | **Gateway 实际使用 Logback，Log4j2 配置完全未生效**（perm-gateway-spring-boot-starter → spring-boot-starter → spring-boot-starter-logging 传递引入，与显式 log4j2 双 Provider 并存，SLF4J 实际选择 Logback；log4j2-spring.xml 的 Configuration/Appenders/Loggers 被 Logback 当作未知属性忽略，JSON 日志/滚动策略/级别配置不可靠，违反 project-rules 日志规范） | P2 | 成立（dependency:tree 证实传递链） | gateway/pom.xml 对 perm-gateway-spring-boot-starter 排除 spring-boot-starter-logging（与 access-service 对 perm-client-spring-boot-starter 的处理同模式）；依赖树复核只剩 log4j2；`GatewayApplicationConfigTest` 新增日志实现回归断言（SLF4J 必须绑定 Log4j2LoggerFactory、不得为 Logback LoggerContext） |
| 2 | 保留的 Jackson「未来配置」使用无效属性名（`spring.jackson.date-time-format` 非 Boot 3.2.4 有效键，JacksonProperties 仅暴露 `date-format`；未来切换 Boot Mapper 也不会生效，注释不成立） | P3 | 成立（且即使改为有效键 date-format 也仅作用 java.util.Date，响应 DTO 几乎全 LocalDateTime 无实际意义） | 删除 application.yml 整个 spring.jackson 配置块（含上轮注释），不留无效配置 |
| 3 | 任务卡两处过期信息（last_updated 仍 2026-08-13；验收表「4 个新断言」未统一为 5 个） | P3 | 成立 | last_updated → 2026-08-14；验收表 → 5 个新断言 |

**第三轮修复验证**：Gateway 全量 73 测试 0 失败（新增日志实现回归断言）；access-service 全量回归见提交前验证。

### 第四轮评审修复（2026-08-14，复评：1 P2）

| # | 评审问题 | 严重度 | 核实结论 | 用户决策 | 处理 |
|---|---|---|---|---|---|
| 1 | **Gateway 的 Log4j2 配置解析失败，日志内容被替换为字面量 `${LOG_PATTERN}`**（`<Property>` 直接放根节点，Log4j2 报 Unknown object Property 后忽略；PatternLayout 输出字面量，实际消息/级别/时间/异常全部丢失；Provider 测试只验证绑定不验证配置解析，仍绿色通过） | P2 | 成立（access-service 的 xml 用 `<Properties>` 容器解析正常，对照确认） | 同步 JSON 化（access-service 同为文本 PatternLayout 不合规 §4.2，用户决策两端统一） | Gateway：`<Property>` 包进 `<Properties>` 容器并改 JsonLayout（compact + properties=true 输出 MDC + includeStacktrace + eventEol）；access-service：同样改 JsonLayout（用户决策）；两端删除无消费者的 LOG_PATTERN 死属性；回归测试：两端 Context 测试各新增断言（Console Appender 存在 + Layout 为 JsonLayout——配置错误或布局回退时失败，捕获"配置解析成功"） |

**第四轮修复验证**：Gateway 全量 74 测试 0 失败（新增配置解析+JsonLayout 断言）；access-service 378 测试 0 失败 19 跳过（新增 JsonLayout 断言）；全模块 BUILD SUCCESS。

**日志格式说明**：JsonLayout 输出 timeMillis（epoch 毫秒）与规范示例的 ISO `timestamp` 键名略有差异，但为标准 JSON 可被 ELK/Loki 采集（规范核心意图）；MDC 字段（traceId/userId/tenantId/serviceCode）由 properties=true 输出，应用侧 MDC 注入属 T-ACCESS-004 安全上下文范围（登记）。

### 验收项落实情况

| 验收项 | 结论 |
|---|---|
| 单数据源 / 单事务管理器 / 单套 MyBatis-Flex 租户配置 | 已满足（T-ACCESS-001/002 已收敛至 infrastructure）；Context 测试断言唯一 |
| Redis logical DB 0 / 唯一 CacheService / Sa-Token 隔离命名空间 | 已满足（DB 0；common 框架唯一 CacheService；Sa-Token 会话键 `Authorization:login:*`（tokenName:loginType 前缀）与业务缓存键 `{tenantId}:{catalogCode}:{identifier}`（首段数字租户 ID）天然隔离）；测试断言 |
| Sa-Token 差异消除 + 平台会话 2h/30min | 已消除（timeout=7200 / active-timeout=1800 / token-style=uuid 两侧统一）；「启动失败」按用户决策不实现代码校验（见决策 2），jwt-secret-key 缺失天然启动失败 |
| 删除重复配置（TenantContextHolder/MybatisFlexTenantConfig/自定义 ObjectMapper/重复序列化） | 已删除（前两者 T-ACCESS-002 已唯一化；本任务删除 RedisConfig 裸 ObjectMapper 与 RedisTemplate 序列化配置） |
| 删除 admin Spring Cache/裸 Caffeine 与业务侧 RedisTemplate 直接操作 | Spring Cache/Caffeine 已删；业务侧 StringRedisTemplate 按用户决策保留（见决策 4） |
| 配置不再引用 admin_db/perm_db/两套 Redis DB | 已满足（盘点零残留：代码/配置/测试均无 admin_db、perm_db；Redis 仅 gateway/access 两处 database: 0） |
| 应用上下文测试证明 Bean 唯一且序列化、租户解析可用 | 已强化（5 个新断言，见实施 5） |

### 范围外登记（后续任务输入）

- 双租户拦截器并存（admin `TenantInterceptor` 允许会话解析 vs permission `PermTenantInterceptor` 严格请求头，`/api/**` 上双写 TenantContextHolder）→ **T-ACCESS-004**（可信请求上下文与统一安全策略矩阵）
- Gateway 路由 `lb://admin-service`/`lb://permission-center`、perm-sdk Feign 目标（`PermissionFeignClient`/`SyncTaskFeignClient`/`FeignInternalSyncInterceptor` @Value 默认值）、access-service 内服务名常量（`SOURCE_SERVICE="admin-service"` 等 8 处，参与 `sys_sync_task.batch_key` 数据契约）→ **T-ACCESS-005/010**
- `perm.cache.*` 分钟字段（`l1.expire-minutes`/`l2.ttl-minutes`，无消费者）→ **T-ACCESS-008** 一次性迁移（架构 §7.1）
- `example-service` bootstrap.yml `database: 2` → example-service 独立参考服务（归并范围外，登记观察）
- 前端 expiresIn 剩余时间倒计时展示移除 → 前端任务登记（决策 3 配套）

### 已知限制

- uuid 模式下会话有效性依赖共享 Redis 条目（会话键 `Authorization:login:*`，DB 0）；Redis 清空后两端一致失效（fail-closed），与决策 1 一致
- OAuth2 access_token 有效期由客户端注册 TTL 决定（默认 86400），不套用平台会话 2h/30min 口径（架构 §6.1）
- expiresIn 改动无行为级单测（access-service 无 AuthServiceImpl 单测）；由单一权威来源（`SaManager.getConfig().getTimeout()` = sa-token.timeout）与 Context 测试断言（权威值 + 无独立键残留）保证（评审 P3 记录）
