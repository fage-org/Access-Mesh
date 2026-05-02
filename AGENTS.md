# AGENTS.md — AccessMesh 项目上下文

## 项目概述

**AccessMesh** 是基于 Spring Cloud 微服务架构的通用访问控制平台，支持 SaaS 多租户模式。

- **当前阶段**：设计完成，待编码实现
- **当前分支**：`feat-permission-center`
- **文档入口**：`plan/README.md`

## 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3 + Spring Cloud |
| ORM | MyBatis-Flex |
| 数据库 | PostgreSQL（多租户，软删除） |
| 注册/配置 | Nacos |
| 缓存 | Caffeine (L1) + Redis (L2) |
| 消息队列 | RocketMQ |
| 认证 | Sa-Token + OAuth2 |
| JSON | Jackson（禁止 FastJSON / Hutool / Lombok） |
| 日志 | SLF4J + Log4j2 |
| 前端 | Vue 3 + Element Plus |

## 服务架构

```text
Gateway (8080) -> admin-service (9100)      用户/组织/菜单/认证
               -> permission-center (9200)  核心权限引擎
               -> example-service (9300)    对接演示
```

整体架构见 `plan/architecture.md`。

## 权威来源

| 主题 | 权威文档 |
|------|----------|
| 文档入口与阅读顺序 | `plan/README.md` |
| 工程规范 | `plan/project-rules.md` |
| 整体架构 | `plan/architecture.md` |
| 权限中心概念模型 | `plan/permission-center/overview.md` |
| 权限中心 API 契约 | `plan/permission-center/api-contract.md` |
| 权限中心核心流程 | `plan/permission-center/core-flows.md` |
| 权限中心实现设计 | `plan/permission-center/implementation.md` |
| 服务设计 | `plan/services/*.md` |
| 表结构 | `plan/schema/*.sql` |

`plan/archive/` 只用于历史追溯，不作为实现依据。

## 核心编码规范

完整规范见 `plan/project-rules.md`。常用约束：

- 所有接口使用 **POST + JSON Body**，禁止 GET/PUT/DELETE，禁止 RESTful 路径参数。
- 禁止 `@RequestParam`（文件上传除外），所有参数通过 `@RequestBody` + Request DTO。
- 路径格式：`/api/{module}/{resource}/{action}`；权限中心对外接口统一在 `/api/perm/*`。
- 统一响应体：`{ "code": 200, "message": "success", "data": {}, "requestId": "...", "traceId": "..." }`。
- 错误码分段：10001-19999(admin) / 20001-29999(perm) / 30001-39999(example) / 90001-99999(全局)。
- 分层：Controller -> 调度层 Service -> 逻辑级 DomainService -> Mapper。
- **Service 层复用规范**：新增/修改功能必须检查 DomainService 是否有可复用方法，禁止在调度层重新实现领域逻辑。详见 `plan/project-rules.md` §8.4。
- **N+1 查询禁止**：循环内禁止单条数据库查询，必须使用批量查询方法。详见 `plan/project-rules.md` §8.4.8。
- 禁止跳层调用，禁止同层横向调用。
- 禁止 Lombok，使用 Java 21 Record 表达不可变 DTO。
- 日期统一使用 `java.time.LocalDateTime`，禁止 `java.util.Date`。
- 实体类不含业务逻辑，审计字段由框架填充。

## 权限中心实现提醒

- API 路径、请求体、响应体、错误原因以 `plan/permission-center/api-contract.md` 为准。
- 表字段、索引、约束以 `plan/schema/permission-center.sql` 为准。
- 核心场景链路以 `plan/permission-center/core-flows.md` 为准。
- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`；内部表继续使用 `type_value` 数字值。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一；不要按业务域重复分配相同内部值。
- `query-scopes`、`scope_all` 是当前范围权限模型；不要恢复旧的 `query-data-scopes`、`includeDataScope`、`dataScopes`。
- `resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。

## 常用命令（开发阶段预估）

```bash
# 构建
mvn clean compile

# 运行测试
mvn test

# 本地启动（需先启动 Nacos + Redis + PostgreSQL）
mvn spring-boot:run -pl <module>

# Docker Compose 启动基础设施
docker compose -f docker-compose.yml up -d nacos redis postgresql
```
