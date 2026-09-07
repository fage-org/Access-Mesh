# AGENTS.md — AccessMesh 项目上下文

## 项目概述

**AccessMesh** 是基于 Spring Cloud 微服务架构的通用访问控制平台，支持 SaaS 多租户模式。

- **当前阶段**：产品定位已定案（开源通用 IAM，2026-08-28）。实时任务状态、阶段进度与待办以 `docs/tasks/README.md` 为准；**不要根据本文件中的历史任务编号推断当前任务状态**。
- **当前分支**：`feat-permission-center`
- **文档入口**：`docs/README.md`

## 技术栈

| 层面      | 技术                                                                            |
| --------- | ------------------------------------------------------------------------------- |
| 语言      | Java 21                                                                         |
| 框架      | Spring Boot 3 + Spring Cloud                                                    |
| ORM       | MyBatis-Flex                                                                    |
| 数据库    | PostgreSQL（多租户，软删除）                                                    |
| 注册/配置 | Nacos                                                                           |
| 缓存      | Caffeine (L1) + Redis (L2)                                                      |
| 消息队列  | RocketMQ（预留未启用；缓存失效经 Redis pub/sub 广播，服务间同步为同库事务/API 调用） |
| 认证      | Sa-Token + OAuth2                                                               |
| JSON      | Jackson（禁止 FastJSON；DTO 优先 Java 21 Record，Lombok 按需精确使用）       |
| 工具库    | Apache Commons / Guava（**禁止 Hutool 整库**，理由与替代见 project-rules §5.2） |
| 日志      | SLF4J + Log4j2                                                                  |
| 前端      | Vue 3 + Element Plus                                                            |

## 服务架构

```text
Gateway (8080) -> access-service (9100)    admin 域（用户/组织/菜单/认证）+ permission 域（权限引擎）
               -> example-service (9300)   对接演示
```

`admin-service` 与 `permission-center` 已归并为 `access-service`（T-ACCESS-001~010）。整体架构见 `docs/design/architecture.md`，归并后目标架构见 `docs/design/access-service-architecture.md`。

## 权威来源

| 主题               | 权威文档                                   |
| ------------------ | ------------------------------------------ |
| 文档入口与阅读顺序 | `docs/README.md`                           |
| 工程规范           | `docs/design/project-rules.md`             |
| 定案登记表         | `docs/design/decision-registry.md`（定案消费与评审豁免唯一入口；用户定案当轮登记） |
| 整体架构           | `docs/design/architecture.md`              |
| 归并后目标架构     | `docs/design/access-service-architecture.md` |
| 权限中心概念模型   | `docs/design/permission-center/overview.md` |
| 权限中心 API 契约  | `docs/design/permission-center/api-contract.md` |
| 权限中心核心流程   | `docs/design/permission-center/core-flows.md` |
| 权限中心实现设计   | `docs/design/permission-center/implementation.md` |
| 服务设计           | `docs/design/services/*.md`                  |
| 表结构             | `docs/design/schema/access-service.sql`（唯一权威；旧 admin/perm DDL 已归档 `docs/archive/2026-08-22/schema/`） |

`docs/archive/` 只用于历史追溯，不作为实现依据。

## 核心编码规范（高频硬约束）

完整规范见 `docs/design/project-rules.md`：

- 所有业务 API **POST + JSON Request DTO**：禁 GET/PUT/DELETE、`@RequestParam`（文件上传除外）与 RESTful 路径参数。
- Controller -> Service -> DomainService -> Mapper，禁止跳层；新逻辑先查 DomainService 可复用方法（§8.4）。
- 循环内禁止单条数据库查询，必须批量（禁 N+1，§8.4.8）。
- DTO 优先 Java 21 Record；禁止 `@Data`/`@Value` 等隐式生成过多逻辑的 Lombok 注解。
- 日期统一 `java.time.LocalDateTime`（禁 `java.util.Date`），时间语义全链路 UTC（TypeHandler 显式换算，§7.4）。
- 权限校验必须走 `PermQueryEngine`，禁止绕过引擎直查 `rolePermMapper`。

统一响应体、错误码分段、路径格式、Lombok 细则、同层横向调用边界、实体类约束等以 `project-rules.md` 为准。

## 权限中心实现提醒

- API 路径、请求体、响应体、错误原因以 `docs/design/permission-center/api-contract.md` 为准。
- 表字段、索引、约束以 `docs/design/schema/access-service.sql` 为准（admin/perm 旧 schema 已 superseded）。
- 核心场景链路以 `docs/design/permission-center/core-flows.md` 为准。
- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`；内部表继续使用 `type_value` 数字值。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一；不要按业务域重复分配相同内部值。
- `query-scopes`、`scope_all` 是当前范围权限模型；不要恢复旧的 `query-data-scopes`、`includeDataScope`、`dataScopes`。
- `resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
- **资源类型级所有权**：每个 resource_type 单一所有权，声明于 `type_definition.extra`（`managedMode` MANAGED/SYNC + `syncSourceService` 来源服务）。sync/full-sync 入口做类型门禁（非 SYNC、来源不匹配或来源服务未注册/停用 → `RESOURCE_TYPE_OWNERSHIP_DENIED`；`syncTypes` 维度已退役，保存含此字段拒绝；API 类型禁止声明 SYNC）；管理面对 SYNC 类型 create/batch-create/update/move/remove 拒绝（20055，remove 覆盖级联删除全集含跨类型后代）；类型下有有效资源行时声明不可变更（20056）。USER/ORG/MENU/ROLE/ADMIN_FILE 为内部事实链路 SYNC（access-service；ADMIN_FILE 文件夹实例由 bootstrap 预置+上传惰性登记产出，T-ADMIN-025），外部同步一律拒；`resource_entity.maintain_source/owner_service_code` 为 service-config 通道行归属标记（读取面封闭）。演进历史（退役机制与收编清单）见 `docs/design/decision-registry.md` 与 `docs/design/access-service-architecture.md`。
- **业务域分类模型**：角色、资源等实体不再内嵌 `bizDomainId` 列，域分类通过 `domain_config` 表的 `CLASSIFY` 配置实现（按 `resourceTypeCode` 关联）。全局域(`global=true`)的范围隐式包含未被其他域认领的资源类型。权限查询管线不感知业务域。管理查询通过 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按三种模式(ALL/GLOBAL_PLUS/DOMAIN_ONLY)过滤。

## 项目级 Skills（自动加载）

项目技能维护于 `.claude/skills/` 与 `.agents/skills/` 双副本（改一份须同步全部）；正文以 skill 文件为唯一权威，此处仅留指针，详细触发条件与规范见各 SKILL.md：

| 技能                          | 定位                                                                                                        |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `dual-layer-cache-framework`  | 统一缓存框架规范：涉及 CacheService/缓存目录/L1+L2 存储/失效广播/TTL/evictAfterCommit 等缓存代码时读 skill   |
| `permission-query-pipeline`   | 权限查询引擎规范：涉及 PermQueryEngine/权限校验/批量检查/OperationCodeConstants 时读 skill；禁止绕过引擎直查 rolePermMapper |
| `accessmesh-patterns`         | 仓库级开发模式速查：分层边界/API 路径/DTO 命名/审计字段/N+1/禁止依赖/提交规范                                  |
| `dual-track-local-review`     | 任务本地双轨评审与收口 checklist（收口默认动作；不自动串联 codex）                                             |
| `codex-external-review`       | codex 外部评审执行规范（仅用户显式触发）                                                                     |
| `design-plan-task-lifecycle`  | 设计/计划/任务三层文档生命周期治理                                                                            |
| `grill`                       | 访谈式计划压力测试（当前仅 .claude 侧；ZCode 用户级另有 grill-me/grilling）                                  |

## 项目级 Rules（按场景先读）

规则文件维护于 `.claude/rules/`（**单副本**，无 .agents 镜像）。Claude Code 侧自动加载；**其余环境（ZCode/Codex 等）不会自动注入——凡命中下表场景，必须先读对应 rule 文件并遵守**（2026-09-06 定案：决策提问协议曾因未挂本表而在非 Claude Code 环境失效）。正文以 rule 文件为唯一权威，此处仅留指针：

| 规则                                 | 定位                                                                                                     |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `decision-question-protocol`         | 决策提问协议：任何要求用户决策/表态的提问（AskUserQuestion 选项、评审存疑上报、修法选择、定案讨论）必须举例——场景+数据示例+实际后果（含「现在为什么没出事」）；事实性最小修正直接修并汇报，不过度提问 |
| `permission-center-coding-standards` | 权限中心编码规范：access-service permission 域代码改动必读（分层/PermQueryEngine/命名/事务边界/批量加载/操作日志/域分类/类型解析） |
| `security-standards`                 | 安全标准：全部后端服务与 API 端点（密钥管理/HTTPS/CSRF/限流/安全头/依赖扫描）                                |
| `testing-standards`                  | 测试标准：全部测试代码（TDD/覆盖率/独立性/mock/命名/边界用例/行为测试/测试数据工厂；§10 项目级轨道归属——容器测试用 ItInfra、跨服务 E2E 只进 e2e 模块、时序用例禁裸 sleep 余量） |
| `frontend-coding-standards`          | 前端编码规范（pure-admin-thin）：pnpm 强制等工程约束                                                        |
| `frontend-layout-patterns`           | 前端布局规范（pure-admin-thin）：动手写 CSS 前先确认实际 DOM 结构，布局模式与陷阱                            |
| `css-design-system`                  | 前端样式 CSS 设计系统：禁魔法数字、CSS 变量化等                                                             |
| `docs-governance`                    | 文档治理加载入口：权威在 `docs/design/project-rules.md` §文档治理，rule 文件不重复正文                        |

## Agent 工作协议

开始修改前：

1. 识别本次任务涉及的模块与场景。
2. 按上方 Rules / Skills 表加载命中的规则。
3. 读取对应设计文档（见权威来源表），而不是扫描全部 docs。
4. 修改前搜索现有实现，优先复用 DomainService。
5. 完成后执行对应测试与本地评审。

## 常用命令（开发阶段预估）

```bash
# 全量构建（含 install，确保 SNAPSHOT 依赖刷新到本地仓库）
mvn clean install -DskipTests

# 仅编译（不刷新本地仓库，依赖方可能拿到旧 SNAPSHOT）
mvn clean compile

# 运行测试
mvn test

# 全量回归（收口形态，T-ACCESS-031）：模块并行 -T 1C + E2E 轨必跑（gateway 轻模块与
# access-service 并行是提速来源，勿去掉 -T）
mvn test -T 1C

# 全量回归（日常形态，T-ACCESS-031）：再跳过 e2e 模块（两条跨服务验收垂直切片）
mvn test -T 1C -DskipE2E=true

# 仅单测轨道（跳过 access-service 容器组，日常快速反馈；T-ACCESS-030）
mvn test -pl access-service -DskipTestcontainers=true

# 本地启动（需先启动 Nacos + Redis + PostgreSQL）
mvn spring-boot:run -pl <module>

# Docker Compose 启动基础设施
docker compose -f docker-compose.yml up -d nacos redis postgresql
```

> **⚠️ 测试运行纪律（T-ACCESS-030 / T-ACCESS-031）**：
> - 日常反馈用单测轨道 `-DskipTestcontainers=true`；**全量（含容器组）只在任务收口时跑**，不在中途反复全量。
> - 全量分两形态（T-ACCESS-031）：日常 `-T 1C -DskipE2E=true`（跳过 e2e 模块），收口 `-T 1C`（E2E 必跑）——E2E 是产品验收资产（T-ACCESS-021 垂直切片），**收口不得带 -DskipE2E**；ci.yml 单测 job 两个开关都必须带。
> - e2e 模块（reactor 末位）依赖三服务 artifact，**必须随 reactor 构建**（根构建或 `-pl e2e -am`）——单独 `-pl e2e` 会从本地仓库解析三服务的 repackaged boot jar，类路径为 BOOT-INF 布局必失败。
> - 全量回归前先停本机 9100 dev 服务（`DualInstanceContainerTest` 占真实端口，冲突即假失败）；mvn 运行期间**禁止改动源码**（并发编译快照污染会制造大面积假失败）。
> - 全量输出**整文件落盘**再解析（管道 `grep | tail` 会截断聚合统计）；失败先**隔离复跑**定性（已知抖动登记见 decision-registry），再决定是否重跑全量。
> - 容器组基建已单例化（`ItInfra`：**每 fork JVM 一份**单例 PG/Redis + 按类建库 + 按类 Redis 逻辑库索引 + fork 级 2 进程并行——sa-token 的 SaManager 是 JVM 级静态单例，同 JVM 线程级类并发下邻类上下文关闭会把静态 dao 指向已 shutdown 的 Redisson，故并行必须走进程隔离；本机开 `~/.testcontainers.properties` 的 `testcontainers.reuse.enable=true` 后各 fork 按配置哈希复用同一对容器，无该文件的环境（如 CI）每 fork 各起一对）；类库/索引由会话首启自动清理，无需手工维护。
> - `-T 1C` 模块并行下负载抬升曾击穿两个固定 sleep 余量的时序用例（TaskLease 同实例接管、Gateway 失效代际竞态），均已改确定性机制（轮询至可抢占 / CompletableFuture 提交闸门）；新增并发/时序用例**禁用裸 sleep 余量**表达时序。

> **⚠️ SNAPSHOT 依赖陷阱**：本项目使用多模块 SNAPSHOT 依赖（如 `perm-common` → `perm-client-spring-boot-starter` → `example-service`）。
> `mvn compile` 不会将上游模块 install 到本地仓库，依赖方编译时可能拿到**上次 install 的旧版本**。
> 当上游模块（`perm-sdk/*`、`common`、`perm-entity`）有 API 变更时，**必须**执行 `mvn install -pl <上游模块> -DskipTests` 或全量 `mvn clean install -DskipTests` 后再编译下游模块。

## 文档治理

文档分层职责 / 写入口清单 / 关键词扫描 / 测试适用性覆盖见 `docs/design/project-rules.md` §文档治理（仓库级权威）。phase plan/README 任务行只保留标题/状态/直接依赖/链接，详细范围写进任务卡。
