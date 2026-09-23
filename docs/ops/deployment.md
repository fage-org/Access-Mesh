---
doc_type: ops
title: 生产部署基线
status: adopted
domain: cross-service
last_reviewed: 2026-09-23   # T-GW-010：§1 补「仓库实测过的接入形态+TLS/外部非默认端口未测」边界行；此前 2026-09-16（release-preview 产出）
---

# 生产部署基线（Deployment Baseline）

> 仓库内 `docker compose --profile app` 是**本地预览形态**（端口回环绑定、PG trust 认证、固定开发密码）。本文档定义把 AccessMesh 部署到生产（或类生产预发）环境时必须满足的基线；内部安全规范全文见 `.claude/rules/security-standards.md`（开发侧），本文档是其部署侧的正式对外落档。

## 1. 拓扑

```text
客户端 → TLS 终止（LB/nginx） → 前端静态资源（nginx 同源反代 /api） → Gateway(8080)
                                                            ├→ access-service(9100) → PostgreSQL + Redis
                                                            └→ example-service(9300)（按需）
Nacos(8848)：服务注册/配置（三服务共同依赖）
```

- 前端与 API **同源**是既定形态：前端 axios 全相对路径 `/api/**`，由 nginx `location /api/` 反代 Gateway（仓库形态见 `frontend/nginx.conf`）；同源部署下 CORS 应显式禁用（`GATEWAY_CORS_ALLOWED_ORIGINS=` 置空），不是配置跨域白名单。
- 仓库实测过的接入形态（T-GW-010，2026-09-23）：开发 vite 代理（`localhost`/`127.0.0.1` × 8848/8890 四个 Origin 经 Gateway 白名单放行）与 compose 全栈档 nginx 同源反代（`http://127.0.0.1/` 登录链路）；**TLS 终结、外部非默认端口（非 80/443 对外域名）未在仓库环境实测**，生产部署时按本基线自行验证 Origin/转发头行为。
- API 外部路径 = 服务路径（单命名空间 `/api/access/**`、`/api/example/**`，Gateway 无 StripPrefix）——LB/nginx 反代**不得改写路径**。
- 信任边界：业务服务（如 example-service）应**仅 Gateway 可达**（网络隔离是根本保障，身份签名校验是纵深防御）。

## 2. 密钥与敏感配置（强制）

| 密钥 | 作用 | 约束 |
|------|------|------|
| `JWT_SECRET_KEY` | Sa-Token JWT 签名 | ≥32 字符；缺失 access-service 启动失败 |
| `ACCESSMESH_SIGNATURE_SECRET` | 用户身份头 HMAC 签名 | gateway 与下游服务**同值**；不一致时经 Gateway 的请求一律拒绝（信封 30003） |
| `PERM_INTERNAL_SECRET` | Gateway→access-service 内部密钥 | 与 access-service 同值 |
| `ACCESS_BOOTSTRAP_ADMIN_PASSWORD` | 首管理员密码 | 仅空库首启生效（幂等 no-op 不重置）；bootstrap **仅单实例启用** |

- 全部经环境变量或 Nacos 加密配置注入，**禁止**写入代码/仓库/明文 compose 文件（模板 `.env.example` 只是占位，`.env` 已被 git 忽略）。
- 生产 PG/Redis 认证自行替换：compose 的 trust 认证与 `accessmesh-dev` 固定密码均为开发边界；`DB_USERNAME`/`DB_PASSWORD`/`REDIS_PASSWORD` 环境变量可覆盖。

## 3. TLS 与安全头（强制）

- 所有对外入口（前端、Gateway）**仅 HTTPS**：TLS 在最外层 LB/nginx 终止；HTTP→HTTPS 重定向；HSTS 开启。
- 会话令牌仅经 `Authorization: Bearer` 头传递（Cookie 通道双端关闭，无 CSRF 面）；TLS 下无需额外 CSRF 令牌。
- 安全响应头（X-Content-Type-Options、X-Frame-Options、CSP 等）在 TLS 终止层配置；Gateway/服务侧不重复注入。

## 4. X-Forwarded-For 与多层代理（部署前提）

- Gateway 会**清洗**外部传入的 `X-Forwarded-For`/`X-Real-IP`，并以自身观测的 **remoteAddr（直连对端 socket 地址）** 重建 XFF 下发下游（单值可信来源）；IP 条件权限（白名单/黑名单/快照本地重评）同样只消费该直连地址——**不读任何 XFF 头**。
- **多层 LB/CDN 部署的真实 IP 边界**：Gateway 恒观测到代理出口 IP——外层代理即使正确重建 XFF，也会被 Gateway 清洗丢弃，**真实客户端 IP 条件在此形态下不可用**。可行处置（对齐 security-standards §7）：① IP 白/黑名单按代理出口网段粗约配置；② 真实 IP 精细管控上移至最外层 WAF/LB；③ 未来需要网关级真实 IP 时另立 trusted-proxies 机制（现无）。外层伪造 XFF 不构成越权风险（Gateway 无条件清洗）。
- Gateway 管理端口（8081）默认仅绑定回环；Prometheus 抓取需经 `GATEWAY_MANAGEMENT_ADDRESS` 显式放开并配网络访问控制。

## 5. 时间语义

- 全链路 UTC 墙钟：JVM 时区由 common 启动强制 UTC（代码级，无需部署侧 `-Duser.timezone`/`TZ` 约定）；PG 使用 TIMESTAMPTZ；API 返回 ISO-8601 无偏移字符串（语义=UTC）。详见 [归并后目标架构 §16](../design/access-service-architecture.md)。

## 6. 数据与升级

- 唯一权威 DDL：`docs/design/schema/access-service.sql`，经 PG `initdb.d` 或 psql 手工执行；**当前无 migration 框架**——DDL 变更 = 销毁重建（`docker compose down -v`），不适用于有存量数据的环境（已知限制，升级路径属后续版本）。
- 空库首启顺序：DDL → access-service（bootstrap 建首管理员与固定图）→ gateway → 业务服务。
- 文件存储（ADMIN_FILE）落在 access-service 本机 `${user.home}/accessmesh-files`——**单实例限制**，多实例/容器化需挂载持久卷并保持单写者。

## 7. 监控与告警

- Gateway 暴露 health/info/prometheus/metrics（管理端口）；告警规则示例见 [gateway 设计 §监控](../design/services/gateway.md)。
- 权限快照链路有 30 秒撤权安全边界（授权 L2 ≤10s + 回源截止 5s + Gateway L1 ≤15s）；缓存失效失败有 `cache.invalidate.failures` 指标兜底观察点。

## 相关文档

- [快速开始](../quickstart.md) / [rebuild runbook](../design/access-service-rebuild-runbook.md)（开发/验收环境重建）
- [Gateway 设计](../design/services/gateway.md) / [架构设计](../design/architecture.md)
