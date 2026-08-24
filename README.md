# AccessMesh

基于 Spring Cloud 微服务架构的通用访问控制平台。

## 特性

- **多租户数据隔离**：tenant_id 行级隔离底座已实现（租户上下文 + MyBatis-Flex TenantFactory）；租户开通/运营能力未交付（首期固定单租户试运行）
- **树形角色模型**：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE 五种角色类型已建模；首期功能角色仅 BASIC_ROLE（PERSONAL/GROUP_ROLE 未交付）
- **RBAC 权限引擎**：资源-操作-角色三位一体，支持条件权限和范围权限
- **网关级鉴权**：Spring Cloud Gateway + Sa-Token，接口级白名单模式（快照本地匹配 + 30 秒撤权边界）
- **OAuth2 认证**：授权码+PKCE / 密码 / 客户端凭证多种模式
- **SDK**：perm-client（Feign 远程查询 SDK，业务服务按需使用）、perm-gateway（Gateway 鉴权插件，已使用）；perm-data 未实现（规划中）

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
  ├── access-service (9100)    admin 域：用户/组织/菜单/认证/字典/通知/文件/审计/调度
  │                             permission 域：核心权限管理与鉴权引擎
  └── example-service (9300)    对接演示（启动骨架；单受保护接口随 T-API-001 交付）
```

`admin-service` 与 `permission-center` 已归并为 `access-service`（单库 `access_db`），详见 [架构设计](docs/design/architecture.md) 与 [归并后目标架构](docs/design/access-service-architecture.md)。

## 项目状态

**核心垂直切片完成（里程碑 A，T-ACCESS-021 验收通过）**：从空库 bootstrap 到网关级授权生效的完整产品链路已由跨服务 E2E 测试钉死并通过（[BasicRoleGrantVerticalSliceE2EIT](gateway/src/test/java/cn/ac/fage/accessmesh/gateway/e2e/BasicRoleGrantVerticalSliceE2EIT.java)，固定 8 步：空库首管理员真实登录 → 创建用户/空权限 BASIC_ROLE 并分配 → 真实创建 API 映射 → 403 → 授予 API:ACCESS → 30 秒内 200 → 双服务子进程重启后仍 200 + 权限服务不可用 fail-closed 503 → 撤权 30 秒内恢复 403），授权页 GUI 授予场景亦经真实浏览器操作验收；E2E 过程中修复 4 处真实缺陷（API 映射缺省 matchOrder、用户创建 status 两侧同源、授权页 capability 门控源错误、operation-permission/list 缺 includeGlobalFallback 后端实现）。验证证据见[任务卡 T-ACCESS-021](docs/tasks/T-ACCESS-021.md)。

**未交付清单**（里程碑 B 加固与后续，见 [product-vertical-slice 计划](docs/plans/product-vertical-slice-plan.md)）：登录锁定临时化、GROUP_ROLE 写入口删除、文件服务安全加固、Gateway CORS 环境化、时间语义 UTC 统一、操作日志收敛、退役 API 删除（T-ADMIN-022~T-ADMIN-024/T-GW-007/T-ACCESS-024/025）；example-service 受保护接口接入（T-API-001）；验证证据收口（T-ACCESS-026）。租户开通/运营能力未交付（首期固定单租户）；PERSONAL/GROUP_ROLE 角色生命周期未交付（首期功能角色仅 BASIC_ROLE）；其余管理页面仍为 mock 联调（前端 Phase 3 逐页切换）。设计文档入口见 [docs/README.md](docs/README.md)。

## 文档

| 文档 | 内容 |
|------|------|
| [文档索引](docs/README.md) | 设计文档入口、权威来源、阅读顺序 |
| [架构设计](docs/design/architecture.md) | 微服务整体架构、服务职责、模块划分 |
| [权限中心概念模型](docs/design/permission-center/overview.md) | 权限模型、角色模型、范围权限模型 |
| [权限中心 API 契约](docs/design/permission-center/api-contract.md) | 对外 API 路径、请求体、响应体、错误原因 |
| [权限中心核心流程](docs/design/permission-center/core-flows.md) | 核心权限管理场景与调用链路 |
| [开发规范](docs/design/project-rules.md) | 接口、异常、日志、事务、安全等规范 |

## 快速开始（开发中）

- **基础设施**：一键编排根目录 `docker-compose.yml`（PostgreSQL 16 / Redis 7（固定开发密码 `accessmesh-dev`，与各服务 `REDIS_PASSWORD` 默认值一致）/ Nacos standalone，开发期 trust 认证，与各服务默认配置零参数对接）：

  ```bash
  docker compose up -d
  ```

- **数据库**：PostgreSQL 容器**首次启动（空数据卷）自动执行**唯一权威 DDL `docs/design/schema/access-service.sql`（建库 `access_db` + 租户 1 类型种子）；重复 `up` 不会重复执行。DDL 变更后的重建（DROP SCHEMA + 手动 psql）见 [rebuild runbook](docs/design/access-service-rebuild-runbook.md)。example 库用 `docs/design/schema/example-service.sql`（不在 compose 初始化范围，需单独执行）。
- **首管理员**：access-service 内置幂等 bootstrap（`access.bootstrap.enabled`，默认关闭；仅单实例启用）。启用后空库自动创建 `admin` 首管理员 + 管理用功能角色并按 bootstrap 管理 API 清单最小授权；密码经环境变量注入、BCrypt 哈希落库，重复启动 no-op 不重置密码（三个密钥环境变量为服务启动必填，缺一 fail-fast）：

  ```bash
  ACCESS_BOOTSTRAP_ENABLED=true ACCESS_BOOTSTRAP_ADMIN_PASSWORD=<你的密码> \
    JWT_SECRET_KEY=<密钥> ACCESSMESH_SIGNATURE_SECRET=<签名密钥> PERM_INTERNAL_SECRET=<内部密钥> \
    mvn spring-boot:run -pl access-service
  ```

- **启动顺序**：基础设施 → access-service (9100) → Gateway (8080) → 前端（`frontend/`，开发模式 `npm run dev`）。
- **前端**：真实登录链路已通（T-FE-041）——`pnpm dev` 默认走真实 `/auth` 链路（vite 代理 `/auth`、`/admin`、`/perm`、`/example` → Gateway 8080，目标可经 `VITE_PROXY_TARGET` 覆盖）；纯 mock 联调可置 `VITE_MOCK_LOGIN=true`（.env.development）。注意默认端口 8848 与 Nacos 控制台端口相同，本机同时起 Nacos 容器时以 `VITE_PORT=8890` 等覆盖启动。默认导航仅显示登录/主页/授权页（其余管理页路由保留隐藏，Phase 3 逐页开放）。
- **当前限制**：E2E 目标用户与普通角色不在 bootstrap 范围（随 T-ACCESS-021 授权 E2E 创建）；管理页面（组织与用户、角色管理等）仍为 mock 联调。

## License

MIT License — 详见 [LICENSE](LICENSE)
