# AccessMesh

基于 Spring Cloud 微服务架构的通用访问控制平台。定位：**开源通用 IAM**——通用多租户访问控制平台（2026-08-28 定案）；下列能力按「当前可用 / 已规划 / 仅演进方向」三档口径表述，口径定义见 [docs/design/README.md](docs/design/README.md)。

## 特性

- **多租户数据隔离**：tenant_id 行级隔离底座已实现（租户上下文 + MyBatis-Flex TenantFactory）；租户开通/运营能力未交付（首期固定单租户试运行）
- **树形角色模型**：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE 五种角色类型已建模；首期功能角色仅 BASIC_ROLE（PERSONAL/GROUP_ROLE 未交付）
- **RBAC 权限引擎**：资源-操作-角色三位一体，支持条件权限和范围权限
- **网关级鉴权**：Spring Cloud Gateway + Sa-Token，接口级白名单模式（快照本地匹配 + 30 秒撤权边界）
- **OAuth2 认证**：授权码+PKCE / 密码 / 客户端凭证多种模式
- **SDK**：perm-client（Feign 远程查询 SDK，业务服务按需使用）、perm-gateway（Gateway 鉴权插件，已使用）。数据权限参考实现（原 perm-data 设想）属演进方向，未提供模块
- **接入示例**：example-service 单受保护接口已交付（`POST /api/example/demo/hello`，经 Gateway 鉴权 + 身份回显）；报表数据范围、动态 SQL 数据权限**未交付**（暂缓，等 PM 重申——T-PERM-035/036）

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
  ├── access-service (9100)    能力包单体：管理面（用户/组织/菜单/认证/字典等）+ 权限面（权限事实 + 鉴权引擎）
  │                             （17 顶层包 = 12 能力包 + sync/engine/projection/bootstrap/infrastructure）
  └── example-service (9300)    对接演示（单受保护接口 /api/example/demo/hello 已随 T-API-001 交付，经 Gateway 鉴权）
```

`admin-service` 与 `permission-center` 已归并为 `access-service`（单库 `access_db`），详见 [架构设计](docs/design/architecture.md) 与 [归并后目标架构](docs/design/access-service-architecture.md)。

## 项目状态

**v0.1.0 预览版**——核心产品链路（空库 bootstrap → 登录 → 建角色授权 → 网关级鉴权生效 → 撤权 30 秒内回收）已由跨服务 E2E 测试钉死并通过；全部管理页面已切换真实接口。版本变更与已知限制见 [CHANGELOG](CHANGELOG.md)；开发历史与任务口径见 [docs/README.md](docs/README.md)。

**已知限制（预览版边界）**：单租户试运行（租户开通/运营未交付）；功能角色仅 BASIC_ROLE（PERSONAL/GROUP_ROLE 未交付）；动态数据权限与自动授权暂缓；DDL 无迁移框架（变更=销毁重建）；bootstrap 与文件存储单实例。

## 文档

| 文档 | 内容 |
|------|------|
| [快速开始](docs/quickstart.md) | 从 clone 到全栈跑起来、登录、体验授权闭环 |
| [生产部署基线](docs/ops/deployment.md) | 拓扑、密钥、TLS、XFF 代理前提、数据与升级 |
| [CHANGELOG](CHANGELOG.md) | 版本变更记录 |
| [文档索引](docs/README.md) | 设计文档入口、权威来源、阅读顺序 |
| [架构设计](docs/design/architecture.md) | 微服务整体架构、服务职责、模块划分 |
| [access-service API 契约总册](docs/design/access-service-api-contract.md) | 全部 API 路径、请求体、响应体、错误原因（单命名空间 `/api/access/**`） |
| [引擎子系统概念模型](docs/design/engine/overview.md) | 权限模型、角色模型、范围权限模型 |
| [扩展指南](docs/design/extension-guide.md) | 业务服务接入、自定义资源类型、前端二开 |

## 快速开始

两步跑起全栈预览（前置：JDK 21 + Maven + Docker；详见 [快速开始文档](docs/quickstart.md)）：

```bash
mvn package -DskipTests
cp .env.example .env        # 填四个必填密钥（模板内有说明）
docker compose --profile app up -d --build
```

就绪后：管理前端 http://127.0.0.1/ （首管理员 `admin` + 你设置的密码）；开发模式手工启动、example 接口 403→授权→200 演练、常见问题见 [docs/quickstart.md](docs/quickstart.md)。默认 `docker compose up -d` 仅启动基础设施（PG/Redis/Nacos）。

## License

MIT License — 详见 [LICENSE](LICENSE)（前端派生自 pure-admin-thin，上游声明保留于 [frontend/LICENSE](frontend/LICENSE)）
