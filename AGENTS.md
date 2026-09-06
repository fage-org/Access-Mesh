# AGENTS.md — AccessMesh 项目上下文

## 项目概述

**AccessMesh** 是基于 Spring Cloud 微服务架构的通用访问控制平台，支持 SaaS 多租户模式。

- **当前阶段**：access-service 归并完成（T-ACCESS-001~012，2026-08-22 收口归档）；产品定位已定案（开源通用 IAM，2026-08-28）；Phase 2 逐页后端改造全部收口（T-PERM-022~034 + 037/040/041，T-PERM-040/037 部分定案随 T-PERM-049 全局操作退役推翻）；**Phase 3 前端联调 9/9 全部收口（T-FE-015~022 + T-FE-037 组织联调二期收官 2026-09-04）**；看板待办以 `docs/tasks/README.md` 为准（T-PERM-045~054 加固/登记批次（044 已收口 2026-09-04）、design-audit-followup 设计体检批次（T-PERM-052/053 + T-ACCESS-029 已收口 2026-09-05：类型级所有权定案、同步 ApiItem.operationCode 退役、bootstrap 墓碑三分判定；余 T-PERM-054（暂缓）、T-API-002）、T-ADMIN-025/026、Phase 4 T-FE-023 + T-PERM-039 等）
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
| JSON      | Jackson（禁止 FastJSON / Hutool；DTO 优先 Java 21 Record，Lombok 按需精确使用） |
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

## 核心编码规范

完整规范见 `docs/design/project-rules.md`。常用约束：

- 所有接口使用 **POST + JSON Body**，禁止 GET/PUT/DELETE，禁止 RESTful 路径参数。
- 禁止 `@RequestParam`（文件上传除外），所有参数通过 `@RequestBody` + Request DTO。
- 路径格式：`/api/{module}/{resource}/{action}`；权限中心对外接口统一在 `/api/perm/*`。
- 统一响应体：`{ "code": 200, "message": "success", "data": {}, "requestId": "...", "traceId": "..." }`。
- 错误码分段：10001-19999(admin) / 20001-29999(perm) / 30001-39999(example) / 90001-99999(全局)。
- 分层：Controller -> 调度层 Service -> 逻辑级 DomainService -> Mapper。
- **Service 层复用规范**：新增/修改功能必须检查 DomainService 是否有可复用方法，禁止在调度层重新实现领域逻辑。详见 `docs/design/project-rules.md` §8.4。
- **N+1 查询禁止**：循环内禁止单条数据库查询，必须使用批量查询方法。详见 `docs/design/project-rules.md` §8.4.8。
- 禁止跳层调用；同层横向调用允许（2026-08-22 用户确认全局放开：仅限同层、禁循环依赖、复用优先于重实现，跨域 Mapper 直读边界不变，见 `docs/design/project-rules.md` §分层规范）。
- 不可变 DTO 优先使用 Java 21 Record。
- Lombok 允许精确导入并按需使用；`@Builder` 可用于复杂构造或测试数据装配，但禁止 `@Data`、`@Value`、`@EqualsAndHashCode` 等隐式生成过多逻辑的注解。
- 日期统一使用 `java.time.LocalDateTime`，禁止 `java.util.Date`。
- 时间语义全链路 UTC：JVM 默认时区由 common `UtcTimezoneEnvironmentPostProcessor` 启动即强制 UTC；`LocalDateTime` ↔ TIMESTAMPTZ 由 TypeHandler 显式按 UTC 换算（handler 落位 access-service，新服务引入 DB 实体时须复制）；JDBC URL 禁带 `serverTimezone`。详见 `docs/design/project-rules.md` §7.4。
- 实体类不含业务逻辑，审计字段由框架填充。

## 权限中心实现提醒

- API 路径、请求体、响应体、错误原因以 `docs/design/permission-center/api-contract.md` 为准。
- 表字段、索引、约束以 `docs/design/schema/access-service.sql` 为准（admin/perm 旧 schema 已 superseded）。
- 核心场景链路以 `docs/design/permission-center/core-flows.md` 为准。
- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`；内部表继续使用 `type_value` 数字值。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一；不要按业务域重复分配相同内部值。
- `query-scopes`、`scope_all` 是当前范围权限模型；不要恢复旧的 `query-data-scopes`、`includeDataScope`、`dataScopes`。
- `resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
- **资源类型级所有权（T-PERM-052 定案 2026-09-05，含同日内部来源统一）**：每个 resource_type 类型单一所有权，声明于 `type_definition.extra`（`managedMode`：MANAGED=缺省管理面维护 / SYNC=外部同步维护 + `syncSourceService` 来源服务）。resource-entity sync/full-sync 入口做类型门禁（非 SYNC、来源不匹配或来源服务未注册/停用 → `RESOURCE_TYPE_OWNERSHIP_DENIED`，**不再走 syncTypes.resourceTypeCodes——该维度已退役且保存含此字段拒绝**；API 类型禁止声明 SYNC）；管理面 create/batch-create/update/move/remove 对 SYNC 类型拒绝（20055，remove 守卫覆盖级联删除全集含跨类型后代）；类型下有有效资源行时声明不可变更（20056）。**事实链路四类型 USER/ORG/MENU/ROLE 种子声明 SYNC+access-service（内部来源，仅 is_system 可声明豁免）**——外部同步对四类型一律拒、管理面资源 CRUD 20055，原类型保留清单与行级投影防线（rejectIfLocalResource/rejectIfForeignResource/isOwnResource）已收编删除。`resource_entity.maintain_source/owner_service_code` 为 service-config 通道行归属标记（读取面封闭）；`resource_entity.sync_key` 列已删除（写-only 死列）。
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

## 常用命令（开发阶段预估）

```bash
# 全量构建（含 install，确保 SNAPSHOT 依赖刷新到本地仓库）
mvn clean install -DskipTests

# 仅编译（不刷新本地仓库，依赖方可能拿到旧 SNAPSHOT）
mvn clean compile

# 运行测试
mvn test

# 本地启动（需先启动 Nacos + Redis + PostgreSQL）
mvn spring-boot:run -pl <module>

# Docker Compose 启动基础设施
docker compose -f docker-compose.yml up -d nacos redis postgresql
```

> **⚠️ SNAPSHOT 依赖陷阱**：本项目使用多模块 SNAPSHOT 依赖（如 `perm-common` → `perm-client-spring-boot-starter` → `example-service`）。
> `mvn compile` 不会将上游模块 install 到本地仓库，依赖方编译时可能拿到**上次 install 的旧版本**。
> 当上游模块（`perm-sdk/*`、`common`、`perm-entity`）有 API 变更时，**必须**执行 `mvn install -pl <上游模块> -DskipTests` 或全量 `mvn clean install -DskipTests` 后再编译下游模块。

## 文档治理

文档分层职责 / 写入口清单 / 关键词扫描 / 测试适用性覆盖见 `docs/design/project-rules.md` §文档治理（仓库级权威）。phase plan/README 任务行只保留标题/状态/直接依赖/链接，详细范围写进任务卡。
