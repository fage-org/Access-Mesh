# RuoYi-Cloud-Plus

基于 Spring Cloud 微服务架构的通用权限中心，支持 SaaS 多租户模式。

## 特性

- **多租户 SaaS**：所有数据租户隔离，支持租户级配置
- **树形角色模型**：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE 五种角色类型
- **RBAC 权限引擎**：资源-操作-角色三位一体，支持条件权限、数据权限
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

```
Gateway (8080)
  ├── admin-service (9100)      — 用户/组织/菜单/认证/字典/通知/文件/审计/调度
  ├── permission-center (9200)  — 核心权限管理与鉴权引擎
  └── example-service (9300)    — 对接演示 + SDK 参考实现
```

## 项目状态

**设计完成，待编码实现。** 设计文档见 `plan/` 目录。

## 文档

| 文档 | 内容 |
|------|------|
| [架构设计](plan/ARCHITECTURE_DESIGN.md) | 微服务整体架构、服务职责、模块划分 |
| [权限中心设计](plan/DESIGN.md) | 18 张表的权限模型、鉴权流程、授权流程 |
| [网关设计](plan/GATEWAY_DESIGN.md) | 过滤器链、路由配置、安全设计 |
| [管理服务设计](plan/ADMIN_SERVICE_DESIGN.md) | 11 模块设计（认证、用户、组织等） |
| [开发规范](plan/PROJECT_RULES.md) | 接口、异常、日志、事务、安全等规范 |

## 快速开始（开发中）

```bash
# 1. 启动基础设施
docker compose up -d nacos redis postgresql

# 2. 初始化数据库
psql -h localhost -U postgres -f plan/permission_center_schema.sql
psql -h localhost -U postgres -f plan/admin_service_schema.sql
psql -h localhost -U postgres -f plan/example_service_schema.sql

# 3. 启动服务（后续步骤，待编码）
mvn spring-boot:run
```

## License

MIT License — 详见 [LICENSE](LICENSE)
