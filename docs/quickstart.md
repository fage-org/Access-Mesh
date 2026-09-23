# 快速开始（Quickstart）

> 本文档面向第一次接触 AccessMesh 的使用者：从 clone 到跑起全栈、登录管理台、体验一次完整的「接口授权生效」闭环。开发者深入文档入口见 [docs/README.md](README.md)。

## 系统要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Docker / Docker Compose | Docker 24+（含 compose v2） | 基础设施 + 全栈预览 |
| JDK | 21+ | 构建服务 jar（全栈档需要） |
| Maven | 3.9+ | 构建服务 jar |
| Node.js + pnpm | Node ≥22.13（或 20.19）、pnpm ≥9 | 仅开发模式改前端时需要（全栈档前端在容器内构建） |

> 全栈预览档总共两条命令，其余都是可选项。

## 路径 A：全栈一键预览（推荐第一次使用）

```bash
# 1. 构建三个服务 jar（跳过测试，约 1~3 分钟）
mvn package -DskipTests

# 2. 准备密钥（复制模板并填值，四项均为必填）
cp .env.example .env

# 3. 一键全栈（前端 + gateway + access-service + example-service + PG/Redis/Nacos）
docker compose --profile app up -d --build
```

`.env` 必填项：`ACCESS_BOOTSTRAP_ADMIN_PASSWORD`（首管理员密码）、`JWT_SECRET_KEY`（≥32 字符）、`ACCESSMESH_SIGNATURE_SECRET`（gateway/access-service/example-service 三处同值）、`PERM_INTERNAL_SECRET`（仅 gateway 与 access-service 同值）——分发范围详见模板注释。

首次启动时 PostgreSQL 空数据卷自动执行唯一权威 DDL 建库建表；access-service 幂等 bootstrap 创建首管理员 `admin`（密码=你填的值，重复启动不重置）。

就绪后访问：

| 入口 | 地址 |
|------|------|
| 管理前端 | http://127.0.0.1/ |
| Gateway（API 直调试） | http://127.0.0.1:8080 |
| Nacos 控制台 | http://127.0.0.1:8848/nacos |

> 默认 `docker compose up -d` 只起基础设施（PG/Redis/Nacos）不启应用——日常开发用这个；`--profile app` 才是全栈。所有端口只绑定 127.0.0.1（本地预览边界，见 [部署基线](ops/deployment.md)）。
> 前端镜像构建约 3~8 分钟（容器内 pnpm install + vite build），JVM 服务镜像秒级（拷 jar）。

**登录**：浏览器打开 http://127.0.0.1/，用户名 `admin` + 你在 `.env` 填的密码（验证码看图输入）。

**体验授权闭环**（example 演示接口 403 → 授权 → 200）：

1. 登录管理台，进入「服务与接口」页（侧栏菜单），登记 example-service 服务并全量声明其接口——一步创建 API 资源与 Gateway 映射；
2. 在「角色管理」页点目标角色行进入权限入口（按钮文案按是否持 ROLE:MANAGE 为「权限授予」/「查看权限」；该页不进侧栏菜单，入口是角色管理页的操作按钮），给该角色授予此 API 资源的 `API:ACCESS` 操作（空库首启只有 admin 与固定图管理角色——可先在「组织与用户」页新建用户并绑定目标角色，或直接授给 admin 自身持有的管理角色后复用 admin 会话体验）；
3. 用持有该角色的用户会话调 `POST http://127.0.0.1:8080/api/example/demo/hello`（body `{"name":"accessmesh"}`）——授权前 401/403，授权后 30 秒内变 200（网关快照撤权边界），响应回显 Gateway 注入的用户与租户身份。令牌来源：登录接口 `POST /api/access/auth/login` 响应的 `accessToken`（`Authorization: Bearer <token>` 头），或浏览器登录后从 DevTools 取本地会话令牌。

完整五步接入指引（含服务身份头、SDK 现状）见 [扩展指南 §2](design/extension-guide.md)。

## 路径 B：开发模式（改代码热迭代）

基础设施用 compose，应用跑在本机便于调试：

```bash
# 1. 基础设施（仅 PG/Redis/Nacos，与 dev 配置零参数对接）
docker compose up -d

# 2. access-service（首启注入密钥与首管理员密码；幂等，重复启动 no-op）
ACCESS_BOOTSTRAP_ENABLED=true ACCESS_BOOTSTRAP_ADMIN_PASSWORD=<密码> \
  JWT_SECRET_KEY=<密钥> ACCESSMESH_SIGNATURE_SECRET=<签名密钥> PERM_INTERNAL_SECRET=<内部密钥> \
  mvn spring-boot:run -pl access-service

# 3. Gateway（另开终端）
ACCESSMESH_SIGNATURE_SECRET=<与上同值> PERM_INTERNAL_SECRET=<与上同值> \
  mvn spring-boot:run -pl gateway

# 4. example-service（可选演示服务，另开终端）
ACCESSMESH_SIGNATURE_SECRET=<与上同值> mvn spring-boot:run -pl example-service

# 5. 前端（frontend/ 目录；preinstall 强制 pnpm，禁 npm/yarn）
cd frontend && pnpm install && pnpm dev
```

> 前端 dev 默认端口 8848 与 Nacos 控制台同端口——本机同起 Nacos 时用 `VITE_PORT=8890 pnpm dev` 覆盖。前端经 vite 代理把 `/api` 同路径转发到 Gateway 8080；代理转发会携带页面 Origin 到 Gateway（`changeOrigin` 只改写 Host 头），`localhost`/`127.0.0.1` × 8848/8890 四个页面形态均在 Gateway 默认 CORS 白名单内（T-GW-010）——换其他端口/域名时须同步设置 Gateway 的 `GATEWAY_CORS_ALLOWED_ORIGINS`（**设值=整体替换默认四条**，需保留其他形态时全列出），否则请求被 Gateway 以 CORS 拒绝（403 且无 JSON 信封）。

## 常见问题

| 现象 | 归因 | 处置 |
|------|------|------|
| access-service 容器反复重启 | `.env` 密钥缺失（服务 fail-fast 并打印缺失项） | `docker compose logs access-service` 看启动失败原因并补齐 `.env` |
| 经 Gateway 的 example 请求全部返回信封 30003 | `ACCESSMESH_SIGNATURE_SECRET` 在 gateway/example-service 两处不同值（身份签名校验失败） | 两处改同值后重启 |
| 403，响应体无 JSON 信封，Console 报 CORS 错误 | 请求**已到 Gateway**，但页面 Origin 不在 CORS 白名单（不要归因为「未走 Gateway」） | 用四个默认形态之一访问（`localhost`/`127.0.0.1` × 8848/8890）；自定义端口/域名时设 `GATEWAY_CORS_ALLOWED_ORIGINS` 放行该 Origin（设值=整体替换默认四条，需保留其他形态时全列出） |
| 404，响应体为非 JSON 短文本（无 `code` 字段） | 路径或路由不对（`/api` 前缀被改写、`VITE_PROXY_TARGET` 指错目标） | 核对请求路径（应为 `/api/<服务命名空间>/**`）与代理目标（Gateway 8080） |
| 400 提示租户缺失 | 请求绕过了 Gateway 直连 access-service（租户头由 Gateway 注入） | 前端必须经 vite 代理（dev）或 nginx（http://127.0.0.1/）/Gateway 8080 访问，不要直连 9100 |
| 401/403，响应体是 JSON 信封（有 `code` 字段） | 已过 Gateway 且 CORS 放行，是会话/权限问题 | 401=令牌缺失/过期重新登录；403=无权限（核对角色授权与接口声明） |
| DDL 变更后想重建库 | 开发期销毁重建（无迁移框架，`down -v` 会清掉数据卷，勿对有数据的库使用） | `docker compose --profile app down -v` 后重来；细节见 [rebuild runbook](design/access-service-rebuild-runbook.md) |

## 下一步

- [架构总览](design/architecture.md) / [引擎概念模型](design/engine/overview.md)
- [API 契约总册](design/access-service-api-contract.md)（单命名空间 `/api/access/**`）
- [业务服务接入扩展指南](design/extension-guide.md)
- [生产部署基线](ops/deployment.md)
