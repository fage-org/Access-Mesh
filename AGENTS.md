# AGENTS.md — RuoYi-Cloud-Plus 项目上下文

## 项目概述

**RuoYi-Cloud-Plus** 是一个基于 Spring Cloud 微服务架构的通用权限中心，支持 SaaS 多租户模式。

- **当前阶段**：设计完成，待编码实现
- **当前分支**：`feat-permission-center`

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

## 架构概览

```
Gateway (8080) ──→ admin-service (9100)      — 用户/组织/菜单/认证
               ──→ permission-center (9200)  — 核心权限引擎
               ──→ example-service (9300)    — 对接演示
```

详见 `plan/ARCHITECTURE_DESIGN.md`。

## 编码规范

**所有规范见 `plan/PROJECT_RULES.md`**，核心要点：

- 所有接口 **POST + JSON Body**，禁止 GET/PUT/DELETE，禁止 RESTful 路径参数
- 路径格式：`/api/{module}/{resource}/{action}`
- 统一响应体：`{ "code": 200, "message": "success", "data": {}, "requestId": "...", "traceId": "..." }`
- 错误码分段：10001-19999(admin) / 20001-29999(perm) / 30001-39999(example) / 90001-99999(全局)
- 分层：Controller → 调度层 Service → 逻辑级 DomainService → Mapper
- 禁止跳层调用，禁止同层横向调用
- 禁止 Lombok，使用 Java 21 Record 替代不可变 DTO
- 日期统一使用 `java.time.LocalDateTime`，禁止 `java.util.Date`
- 实体类不含业务逻辑，审计字段由框架填充

## 项目文档索引

| 文档 | 内容 |
|------|------|
| `plan/DESIGN.md` | 权限中心详细设计（18 表、角色模型、鉴权流程） |
| `plan/ARCHITECTURE_DESIGN.md` | 微服务整体架构（4 服务、基础设施、服务间交互） |
| `plan/GATEWAY_DESIGN.md` | Gateway 网关过滤器链、路由、安全设计 |
| `plan/ADMIN_SERVICE_DESIGN.md` | 管理服务 11 模块设计（认证、用户、组织、菜单等） |
| `plan/EXAMPLE_SERVICE_DESIGN.md` | 演示服务 + SDK Starter 设计 |
| `plan/PERMISSION_CENTER_IMPL_DESIGN.md` | 权限中心实现层设计（类结构、鉴权链路、缓存） |
| `plan/PRODUCT_FEATURES.md` | 权限中心产品功能文档（接口、入参、出参、业务规则） |
| `plan/PROJECT_RULES.md` | 项目开发规范（接口、异常、日志、事务、安全等 16 章） |
| `plan/SERVICE_MODULE_CHECKLIST.md` | 全服务模块讨论清单 |
| `plan/MODULE_DISCUSSION_CHECKLIST.md` | 权限中心模块讨论清单 |
| `plan/problem/UNRESOLVED_ISSUES.md` | 设计问题决议记录 |
| `plan/*_schema.sql` | PostgreSQL 表结构（权限中心 18 表、管理服务 17 表、演示 4 表） |

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
