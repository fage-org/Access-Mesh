---
doc_type: task
id: T-ACCESS-020
title: 空库 bootstrap（一键基础设施 + 幂等首管理员种子）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/architecture.md
depends_on: [T-ACCESS-019]
blocks: [T-FE-041, T-ACCESS-021]
acceptance:
  - "根目录新增真实 docker-compose.yml：一条命令启动 PostgreSQL、Redis、Nacos；compose 仅承诺一键基础设施，README.md 写明 access-service → Gateway → 前端的启动顺序（不宣传整套产品一键部署）；README.md 与 AGENTS.md 快速开始命令与实际文件一致（修复当前引用不存在文件的断链）"
  - "唯一权威 DDL 自动/显式执行到位（无 Flyway，开发期重建模式维持）；类型种子保持租户 1 现状（首期固定租户 1，不做租户开通）"
  - "载体为 access-service 内默认关闭（access.bootstrap.enabled）的幂等 ApplicationRunner：仅负责触发一个事务化 initializer；复用现有领域服务（用户/主体/投影/角色创建与授权绑定链）与 BCrypt 哈希环境变量密码，不走带操作者权限校验的管理 AppService、不向通用授权链加入 bootstrapBypass——需要无操作者写入的授权环节使用包内可见、bootstrap 专用的写入组件（复用 DomainService 内部逻辑），不给通用授权服务增加公开的无操作者入口；不维护 SQL bootstrap 种子链路，不建 bootstrap 框架/独立模块/分布式锁（仅单实例启用）"
  - "幂等语义（三状态）：① 种子图完全不存在——单事务创建完整固定图；② 完整存在且身份、角色、关联与授权完全匹配——整体 no-op，绝不重置密码；③ 部分存在、关联缺失或固定业务键被其他数据占用——启动失败并报告具体冲突，不自动修复、不补权、不扩权；enabled=true 时密码缺失或空白 fail-fast，密码不写日志；不新增 ownership 字段、种子版本表或通用 bootstrap 框架（唯一约束仅作并发兜底）"
  - "双角色双用户模型（T-ACCESS-016 定稿）：bootstrap 创建首个管理员（abstract_user/sys_user 同主体 ID、resource_entity 投影）并绑定一个管理用功能角色，按 T-ACCESS-016 的 bootstrap 管理 API 清单执行种子——幂等创建清单内管理 API 的 resource_entity(API) 与 resource_api_mapping，并给管理角色精确授予实例级 API:ACCESS 及对应业务门禁权限（禁 scopeAll 大包/临时白名单）；目标接口 POST /admin/role/my-info 仅预建 resource_entity(API) 并预授 API:ACCESS+canGrant，不创建映射（归 E2E 真实创建）；不创建 E2E 目标用户与普通功能角色（归 T-ACCESS-021）；登录验证通过"
  - "首管理员密码经环境变量注入（Java 侧 BCrypt 哈希落库，无明文）；重复执行 bootstrap 不重复建号（幂等验证用例）"
  - "空库 → 一键基础设施 → 首管理员可登录的完整链路在外部 Docker 主机验证一次并登记证据"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-24
---

# T-ACCESS-020 空库 bootstrap

## 背景

权威 DDL 仅为租户 1 写类型种子（全部 INSERT 写死 tenant_id=1），无任何管理员账号种子（sys_user/abstract_user 零 INSERT），代码中无任何 CommandLineRunner/ApplicationRunner/开通接口；仓库根目录及全库无 docker-compose.yml，而 README.md:57 与 AGENTS.md 均引用它——空库执行 DDL 后无法完成首次登录，快速开始命令是断链。本任务在模型收敛（T-ORG-001）之后实施，种子直接按统一主体 ID 与新类型码终态编写，避免二次返工。

## 范围

- 根目录 docker-compose.yml（nacos/redis/postgresql）与 README/AGENTS 快速开始对齐。
- 幂等种子：默认租户 + 首管理员主体链 + BASIC_ROLE 绑定；密码环境变量注入。
- 幂等种子执行（事务化 initializer + 领域服务复用）与 compose 基础设施编排。

## 当前口径

- 最小 bootstrap：一键基础设施（compose）→ 权威 DDL → 固定租户 1 → 首管理员 + 管理用功能角色（幂等 ApplicationRunner，默认关闭）；管理角色按 bootstrap 管理 API 清单双层最小授权，目标接口仅预授 API:ACCESS+canGrant 且无映射（首管理员自身也无法经 Gateway 调用），目标用户初始 403 起点由此保证。
- 不建独立 bootstrap 模块、分布式锁或 bootstrap 框架；幂等按三状态口径执行（全图不存在则单事务创建 / 全图完整匹配则整体 no-op、绝不重置密码 / 部分存在、关联缺失或固定业务键被其他数据占用则 fail-fast 报告冲突），唯一约束仅并发兜底。
- 种子遵循统一主体 ID（T-ORG-001 后 sys_user.id = abstract_user.id）与收敛后类型码。

## 非目标 / 遗留

- 不做计费、套餐、租户配额；不做平台超管跨租户代管；不做完整租户运营后台；不做租户开通（首期固定租户 1）。
- 不做租户创建/停用接口与租户表（SaaS 对外宣称前另行立项；Gateway 已从可信会话解析租户，现状无任意指定风险）。
- 不引入 Flyway/Liquibase。

## 实施记录（2026-08-24）

### 交付物

- **根目录 `docker-compose.yml`**：`postgresql`（postgres:16-alpine，`POSTGRES_DB=access_db` + trust 认证，挂载 `docs/design/schema/access-service.sql` 至 `docker-entrypoint-initdb.d/` 首启自动执行 DDL）/ `redis`（redis:7-alpine）/ `nacos`（v2.3.2 standalone，8848+9848 gRPC）；服务名与 AGENTS.md 既有命令一致，README/AGENTS 断链修复。
- **bootstrap 代码**（包 `access.application.bootstrap`，跨域写编排层——需同时依赖 admin/permission 两域领域服务，两域互依赖为 ArchUnit 所禁）：
  - `AccessBootstrapProperties`（`access.bootstrap.enabled` / `admin-password`，默认关闭）；
  - `AccessBootstrapRunner`（`@ConditionalOnProperty` 装配；密码缺失/空白 fail-fast 不写日志；绑定租户 1 上下文后触发 initializer）；
  - `AccessBootstrapInitializer`（`@Transactional + @PermissionChange` 单事务；三状态检测 + 固定图创建；类型值经 TypeResolutionService 解析、操作位从 operation_permission 读取，零硬编码数值）；
  - `BootstrapGraphDefinition`（固定图唯一定义源：13 API 清单 + 20 条授权 = 13 实例级 API:ACCESS + §14.4 的 7 条业务门禁）；
  - `BootstrapSeedWriter`（接口，`permission.service.domain`）+ `BootstrapSeedWriterImpl`（**包内可见实现**，非 public 类）：user_role 绑定、resource_entity(SERVICE/API)、resource_api_mapping 写入与授权落库（复用 `PermissionGrantPlanDomainService.apply(PreparedGrantPlan)` 纯写入管线 + `validateSingleManualGrants`/`validateGrantAttributes` 校验）——未给通用授权服务新增无操作者公开入口。
- **测试**：`AccessBootstrapRunnerTest`（密码 fail-fast/委托/上下文清理/默认关闭装配语义，3 用例）；`AccessBootstrapPgIT`（Testcontainers PG16+Redis7，14 用例：事务性——创建链最后一步注入故障全表回滚、状态①全图断言+BCrypt 校验、真实登录（验证码经 Redis、clientId=admin-web）、状态② no-op 绝不重置密码+行数快照、状态③绑定缺失/授权缺失/映射 serviceCode 不匹配/ROLE 投影缺失/主体禁用/主体身份漂移（user_type、external_id）/API·SERVICE 资源停用/业务键被其他角色类型占用/SERVICE-only 部分图 fail-fast、类型种子缺失显式报错）。
- **文档回写**：README 快速开始（compose 命令/DDL 首启说明/bootstrap 启用方式/启动顺序）、runbook（临时验证 fixture 节整体删除、前置条件与重建步骤改 bootstrap 口径、章节重排）、architecture §14.7 实施终态。

### 设计决策（2026-08-24 用户确认）

1. **DDL 执行**：compose 首启自动执行（initdb.d 挂载）；runbook 手动重建模式保留。
2. **幂等状态②口径**：固定图子集匹配——只校验 bootstrap 自建固定图（20 授权/12 映射/绑定/身份）完整匹配即 no-op；图外数据（E2E 创建的用户/角色/授权）与管理角色上的额外授权行不构成冲突（T-ACCESS-021 第⑦步"重启后权限仍生效"的前提）；canGrant 参与匹配，name/密码不参与。
3. **首管理员 `force_reset_pwd=false`**：密码经环境变量自设非随机分发，与 runbook 旧 fixture 口径一致，不挡 E2E/前端登录链。
4. **compose 仅初始化 access_db**：example-service 演示库不在链路，README 保持单独执行说明。

### 评审收口（2026-08-24，codex gpt-5.6-sol xhigh 评审后修复）

外部模型评审发现 4 项检测语义缺陷 + 1 项防扩散缺口，核实后全部修复：

1. **状态②匹配强度不足**：检测补 `abstract_user.enabled`（禁用主体引擎有效角色置空，no-op 判定必须视为冲突）、USER/ROLE 资源投影 `status=1`、管理角色 `resource_entity(ROLE)` 投影存在性校验（此前创建有写、检测没查）。
2. **映射匹配键缺 `service_code`**：`mappingKey` 补齐为 `serviceCode|resourceId|METHOD|path`（与唯一索引同构；Gateway 快照按 serviceCode 过滤，其他服务的同路径映射不能冒充）。
3. **授权匹配键缺可变属性**：`GrantKey` 补 `conditionId`/`dependOn`/`grantSource`（条件授权或 AUTO_DEP 派生行不得冒充无条件 MANUAL 直接授权）；绑定检测补 `relation_id=null` 直绑校验（组角色 relation 绑定不算固定图直绑）。
4. **部分存在误入状态①**：任一固定图对象存在而其余缺失时报告"固定图部分存在"冲突（此前会走创建链撞唯一约束，报不可诊断的数据库异常）。
5. **BootstrapSeedWriter 防扩散（用户决策）**：ArchUnit 新增守护规则——`BootstrapSeedWriter(Impl)` 仅允许 `application.bootstrap` 与所属领域包依赖，违反即架构测试失败（public 接口无法 package-private 的补偿强制）。
6. 风格：业务门禁操作码改用 `OperationCodeConstants` 常量、删除未用常量；PgIT 补 4 个边界用例（映射 serviceCode 不匹配/ROLE 投影缺失/主体禁用/SERVICE-only 部分图），共 11 用例。

评审附带发现并修复（用户决策）的**既有配置缺陷**：Redisson 对空串密码也发 AUTH，无密码 Redis 无法连接——compose redis 固定开发密码 `accessmesh-dev`，access-service/gateway/example-service 的 `REDIS_PASSWORD` 占位符默认值同步统一，一键链路零参数可用。

### 评审收口第二轮（2026-08-24，codex gpt-5.6-sol xhigh 复审后修复）

外部模型复审 6 项发现（2 P1 + 4 P2）全部核实属实并处置：

1. **P1 主体身份键校验缺失**：检测未校验 `abstract_user.user_type=LOCAL_USER` 与 `external_id=主体 ID`（§14.2 固定图身份键；原 ID 相等比较因按 ID 查询恒成立，属死代码）——补两条身份键漂移 fail-fast，删除死代码分支。
2. **P1 SERVICE/API 资源停用漏检**：固定 SERVICE/API 资源仅按存在性参与完整判定，未校验 `status=1`（上一轮只补了 USER/ROLE 投影）——补 SERVICE/API 停用 fail-fast，与投影状态校验对齐。
3. **P2 ArchUnit 防扩散范围过宽**：`..permission.service.domain..` 整包放行使领域内任意 DomainService 可依赖 BootstrapSeedWriter——收紧为仅 `..permission.service.domain.impl..`（实现落位包）。
4. **P2 默认关闭无装配验证**：补 ApplicationContextRunner 用例（enabled 缺省 → Runner 不装配；enabled=true → 装配），注解删除/属性名漂移即报警。
5. **P2 缺单事务回滚注入**（project-rules §测试适用性覆盖"一条事务故障注入"）：PgIT Order(0) 以 `@SpyBean` 在创建链最后一步 `insertGrants` 注入故障，断言全部固定图表零残留。
6. **P2 compose 边界**：三服务端口改绑 `127.0.0.1`（不暴露外部接口）；Redis 密码改 `${REDIS_PASSWORD:-accessmesh-dev}` 同源插值（command/healthcheck），"生产可覆盖"注释名实相符。PG trust 认证维持既有用户决策（2026-08-24 实施决策①），回环绑定后不对外暴露。

PgIT 扩至 14 用例（+回滚注入、+身份漂移 user_type/external_id、+API/SERVICE 资源停用）。

### 验收证据（WSL2 Docker 真实链路，2026-08-24）

- **一键基础设施**：`docker compose up -d`（WSL2 docker-desktop）→ postgresql/redis/nacos 三容器 healthy。
- **DDL 首启自动执行**：`information_schema.tables=33`、`type_definition=31`、`operation_permission=117`，与权威 DDL 一致。
- **bootstrap 创建**：启动日志 `Bootstrap graph created: tenant=1, adminSubjectId=1, roleId=1, apiResources=13, mappings=12, grants=20`。
- **首管理员登录**：`POST /auth/captcha` → Redis 读码 → `POST /auth/login`（tenantId=1/admin/环境变量密码/admin-web）→ `code=200`、accessToken 签发、`forceResetPwd=false`。
- **重启幂等 no-op**：重启后日志 `Bootstrap graph already present and matching — no-op (password untouched)`；`sys_user=1/abstract_role=1/grants=20` 无重复建号。
- **容器测试**：`AccessBootstrapPgIT` 14 用例真实 Testcontainers 执行全部通过（含事务回滚注入、三状态与边界用例）；单测全绿（含 ArchUnit 防扩散规则与 Runner 装配语义）。

### 遗留登记

- E2E 目标用户、普通 BASIC_ROLE、目标 API 映射与授权/撤权 30 秒时效验证归 T-ACCESS-021。
