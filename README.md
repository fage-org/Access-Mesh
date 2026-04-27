# AccessMesh

基于 Spring Cloud 微服务架构的通用访问控制平台。

## 特性

- **多租户 SaaS**：所有数据租户隔离，支持租户级配置
- **树形角色模型**：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE 五种角色类型
- **RBAC 权限引擎**：资源-操作-角色三位一体，支持条件权限和范围权限
- **网关级鉴权**：Spring Cloud Gateway + Sa-Token，接口级白名单模式
- **OAuth2 认证**：授权码+PKCE / 密码 / 客户端凭证多种模式
- **SDK 快速集成**：perm-client / perm-data / perm-gateway 三个 Starter

## 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3 + Spring Cloud |
| ORM | MyBatis-Flex |
| 数据库 | PostgreSQL（多租户，软删除） |
| 注册/配置 | Nacos |
| 缓存 | Caffeine (L1) + Redis (L2) |
| 认证 | Sa-Token + OAuth2 |
| 前端 | Vue 3 + Element Plus |

## 服务架构

```text
Gateway (8080)
  ├── admin-service (9100)      用户/组织/菜单/认证/字典/通知/文件/审计/调度
  ├── permission-center (9200)  核心权限管理与鉴权引擎
  └── example-service (9300)    对接演示 + SDK 参考实现
```

## 项目状态

**设计完成，待编码实现。** 设计文档入口见 [plan/README.md](plan/README.md)。

## 文档

| 文档 | 内容 |
|------|------|
| [文档索引](plan/README.md) | 设计文档入口、权威来源、阅读顺序 |
| [架构设计](plan/architecture.md) | 微服务整体架构、服务职责、模块划分 |
| [权限中心概念模型](plan/permission-center/overview.md) | 权限模型、角色模型、范围权限模型 |
| [权限中心 API 契约](plan/permission-center/api-contract.md) | 对外 API 路径、请求体、响应体、错误原因 |
| [权限中心核心流程](plan/permission-center/core-flows.md) | 核心权限管理场景与调用链路 |
| [开发规范](plan/project-rules.md) | 接口、异常、日志、事务、安全等规范 |

## 快速开始（开发中）

```bash
# 1. 启动基础设施
docker compose up -d nacos redis postgresql

# 2. 初始化数据库
psql -h localhost -U postgres -f plan/schema/permission-center.sql
psql -h localhost -U postgres -f plan/schema/admin-service.sql
psql -h localhost -U postgres -f plan/schema/example-service.sql

# 3. 启动服务（后续步骤，待编码）
mvn spring-boot:run
```

## License

MIT License — 详见 [LICENSE](LICENSE)
