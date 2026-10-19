# AccessMesh

基于 Spring Cloud 微服务架构的通用访问控制平台。定位：**开源通用 IAM**——通用多租户访问控制平台（2026-08-28 定案）；下列能力按「当前可用 / 已规划 / 仅演进方向」三档口径表述，口径定义见 [docs/design/README.md](docs/design/README.md)。

当前候选已完成本机隔离部署、租户使用、首次业务接入与同版本备份恢复验收，并通过本地完整回归；[验收结论与复现入口](docs/archive/2026-10-18/tasks/evidence/candidate-deployment/verification.md)列明实际版本及未验证边界。这是预览候选验收，不代表生产认证或正式发布。

## 特性

- **多租户数据隔离**：tenant_id 行级隔离（可信上下文 + Flex 生成 SQL 自动条件 + 自定义 SQL 显式租户约束）；支持独立平台运营账号开通、停用与恢复租户，租户用户以 tenantCode 登录
- **树形角色模型**：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE 五种角色类型已建模；角色管理页维护 BASIC_ROLE，PERSONAL 支持 API 与分配，GROUP_ROLE 写入冻结
- **RBAC 权限引擎**：资源-操作-角色三位一体，支持条件权限和范围权限
- **网关级鉴权**：Spring Cloud Gateway + Sa-Token，接口级白名单模式（快照本地匹配 + 30 秒撤权边界）
- **OAuth2 认证**：授权码 + PKCE / 刷新令牌模式
- **文件管理**：标准租户提供分类上传、查询、下载与删除页面；管理员可向角色转授分类权限，普通用户默认无文件权限（[使用边界](docs/design/frontend/file-management.md)）
- **SDK**：perm-client 提供 Feign 查询与 Servlet Gateway 身份验签；可选 perm-registration 负责依赖发布。Gateway 准入逻辑在 gateway 服务中，空壳 perm-gateway 已退役；SQL 自动改写模块仍未提供
- **接入示例**：example-service 提供问候接口与报表七路由，演示 Gateway 准入、实际对象检查、范围查询、父权限上下文与异步执行时重查；通用 SQL 自动改写仍未交付

## 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 4 + Spring Cloud |
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
  │                             （18 顶层包 = 13 能力包 + sync/engine/projection/bootstrap/infrastructure）
  └── example-service (9300)    对接演示（问候接口与报表路由，经 Gateway 准入并在业务侧做最终检查）
```

`admin-service` 与 `permission-center` 已归并为 `access-service`（单库 `access_db`），详见 [架构设计](docs/design/architecture.md) 与 [归并后目标架构](docs/design/access-service-architecture.md)。

## 项目状态

**v0.1.0 预览版**——核心产品链路（空库 bootstrap → 登录 → 建角色授权 → 网关级鉴权生效 → 撤权 30 秒内回收）已由跨服务 E2E 测试钉死并通过；全部管理页面已切换真实接口。版本变更与已知限制见 [CHANGELOG](CHANGELOG.md)；开发历史与任务口径见 [docs/README.md](docs/README.md)。

**已知限制（预览版边界）**：旧 tenantId 登录已退役，租户登录使用 tenantCode；角色管理页仅维护 BASIC_ROLE（PERSONAL 支持 API 与分配，GROUP_ROLE 写入冻结）；通用 SQL 自动改写未交付（依赖自动授权与报表范围查询已可用）；DDL 无迁移框架（变更=销毁重建）；bootstrap 与文件存储单实例。

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
cp .env.example .env        # 填写数据库/Redis 密码及四个应用密钥（模板内有说明）
docker compose --profile app up -d --build frontend gateway access-service
```

平台就绪后先注册 example-service、签发服务凭证并填入 `.env`，再按[独立业务库说明](docs/ops/example-database.md)初始化 example_db/example_app 后启动 example-service。运营入口 http://127.0.0.1/#/platform/login （平台账号 `admin` + 部署密码）；从运营界面开通租户后，客户在租户登录页填写编码并使用一次交付的随机密码；开发模式手工启动、example 接口 403→授权→200 演练、常见问题见 [docs/quickstart.md](docs/quickstart.md)。默认 `docker compose up -d` 仅启动基础设施（PG/Redis/Nacos）。

持久化报表/字段元数据与可靠资源发布可按[权限演示接入](example-service/examples/permission-demo.md)准备；动态行列 SQL、策略配置与备注修改已实现；CSV 导入与批次撤销已实现；内部定时任务按[计划](docs/plans/example-permission-demo-plan.md)继续交付。

## License

MIT License — 详见 [LICENSE](LICENSE)（前端派生自 pure-admin-thin，上游声明保留于 [frontend/LICENSE](frontend/LICENSE)）

PERSONAL（个人角色）的后端创建、授权 API 与用户详情分配入口可用；角色管理页目前只维护 BASIC_ROLE，暂不提供 PERSONAL 维护页面。GROUP_ROLE 创建与绑定仍按契约冻结。

局部构建推荐 `pwsh -File tools/build.ps1 access-service`，它固定执行上游一起 install 的 reactor 构建；Linux/macOS 可用 `mvn install -pl access-service -am -DskipTests`。时长、分层守护与工作区规约见 [工程验证基线](docs/ops/engineering-baseline.md)。

管理台按不可变 tenantCode 登录；首次部署只创建独立平台账号，租户通过运营界面开通。平台只管理租户生命周期及受审计的首管理员凭据恢复，不代客户管理业务。旧会话须重新登录，缺租户身份的旧草稿不自动归入任何租户。授权页草稿在当前标签页暂存，401 后重新登录并打开同一角色/资源类型时可确认恢复。
