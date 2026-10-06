---
doc_type: ops
title: 生产部署基线
status: adopted
domain: cross-service
last_reviewed: 2026-10-06
---

# 生产部署基线（Deployment Baseline）

> 仓库内 `docker compose --profile app` 是**本地预览形态**（端口回环绑定、数据库与 Redis 密码必填）。本文档定义生产或预发环境的部署基线；内部安全规范见 `.claude/rules/security-standards.md`。

## 1. 拓扑

```text
客户端 → TLS 终止（LB/nginx） → 前端静态资源（nginx 同源反代 /api） → Gateway(8080)
                                                            ├→ access-service(9100) → PostgreSQL + Redis
                                                            └→ example-service(9300)（按需）
Nacos(8848)：服务注册/配置（三服务共同依赖）
```

- 前端与 API **同源**是既定形态：前端 axios 全相对路径 `/api/**`，由 nginx `location /api/` 反代 Gateway（仓库形态见 `frontend/nginx.conf`）；明文 http 且对外端口与网关观测一致的同源形态下 CORS 应显式禁用（`GATEWAY_CORS_ALLOWED_ORIGINS=` 置空），不是配置跨域白名单。
- **TLS 终结 / 对外非默认端口形态下 CORS 同源短路不成立，不得置空**（T-GW-010 claude 外评 P2，2026-09-23）：浏览器 Origin（如 `https://<域名>`）与网关观测 URI（明文 http；`X-Forwarded-Proto` 按 T-GW-008 清洗、framework 转发策略被启动护栏拒绝，网关无法感知外层 https）scheme/端口不等，携带 Origin 的请求全部走白名单——置空（禁用）会使全部非 GET 请求 403、登录不可用；**必须把对外 Origin（如 `https://<域名>`）列入 `GATEWAY_CORS_ALLOWED_ORIGINS`**。
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
- `DB_PASSWORD`/`REDIS_PASSWORD` 在仅基建与全栈两档均必填且非空，Compose 插值校验缺失即拒绝启动；本机直接启动 Java 服务同样须注入。PG 初始化显式使用 `--auth-host=scram-sha-256`（含容器内 TCP 回环连接），Redis 不再提供公开默认值。旧 PG 数据卷不会因新增 `POSTGRES_PASSWORD` 自动修改账号密码或 `pg_hba.conf`：先备份，在维护窗口设置数据库密码并将 host 规则改为 `scram-sha-256`、重载验证；可重建环境按 §8 新建。不得用改 `.env` 冒充存量认证已收紧。
- `.env` 经 Compose 转为容器环境变量，具备 Docker 管理权限者可通过 `docker inspect` 读取明文；限制 Docker 权限与 `.env` 文件权限，不把 `docker compose config` 原文上传。需要避免环境变量暴露时，以只读 secret 文件挂载，并用 Spring Boot 原生 `configtree:/run/secrets/` 导入属性文件（例如 `spring.datasource.password`、`spring.data.redis.password`）；同时移除对应环境覆盖项。PG 官方镜像支持 `POSTGRES_PASSWORD_FILE`，Redis 可挂载受限 `redis.conf`。仓库默认仍为环境注入，未实现统一 `_FILE` 转换器。
- `NACOS_SERVER_ADDR` 在 Compose 三服务同源消费，默认 `nacos:8848`；外部 Nacos 可直接覆盖。内置 Nacos 的 `/home/nacos/data` 挂持久卷。容器使用 `restart: unless-stopped`，依赖按健康检查放行。

### 服务凭证接入与内部密钥轮换

外部业务查询/同步统一使用服务凭证，SDK 不再注入内部密钥。先启动 access-service 与 Gateway，管理员注册服务并调用 `/api/access/service-credential/create` 签发；把返回的 credentialId/secret 配到业务服务的 `PERM_CREDENTIAL_ID`/`PERM_CREDENTIAL_SECRET`，同时显式设置 `PERM_ALLOW_INSECURE`。example 使用 `example.permission.tenant-credentials` 映射与 `example.permission.allow-insecure`，通过 Nacos 或 Spring Boot 的 `SPRING_APPLICATION_JSON` 注入（Compose 从 `.env` 的 `EXAMPLE_PERMISSION_CONFIG_JSON` 传入）。每个映射项必须对应该租户真实签发的凭证；无映射的请求拒绝，不回落 SDK 固定凭证。配置更新后重启服务生效，本次不引入热刷新或自动轮换机制。

本批确认无仓外旧调用方（2026-10-04），端点/SDK/示例同批切换。部署验收依次确认：有效凭证查询/同步成功、伪造租户头不改变身份、旧共享密钥加自报服务头拒绝、业务服务不再持有内部密钥。随后生成新的平台内部密钥，只更新 Gateway/access-service 等内部消费者并协调重启，再检查用户访问及 Gateway 准入链路、确认旧密钥失效。不要把签发服务凭证等同于已完成内部密钥轮换；仓库测试不代表真实环境已执行轮换。

## 3. TLS 与安全头（强制）

- 所有对外入口（前端、Gateway）**仅 HTTPS**：TLS 在最外层 LB/nginx 终止；HTTP→HTTPS 重定向；HSTS 开启。
- 会话令牌仅经 `Authorization: Bearer` 头传递（Cookie 通道双端关闭，无 CSRF 面）；TLS 下无需额外 CSRF 令牌。
- 安全响应头（X-Content-Type-Options、X-Frame-Options、CSP 等）在 TLS 终止层配置；Gateway/服务侧不重复注入。

## 4. X-Forwarded-For 与多层代理（部署前提）

- Gateway 会**清洗**外部传入的 `X-Forwarded-For`/`X-Real-IP`，并以自身观测的 **remoteAddr（直连对端 socket 地址）** 重建 XFF 下发下游（单值可信来源）；IP 条件权限（白名单/黑名单/快照本地重评）同样只消费该直连地址——**不读任何 XFF 头**。
- **多层 LB/CDN 部署的真实 IP 边界**：Gateway 恒观测到代理出口 IP——外层代理即使正确重建 XFF，也会被 Gateway 清洗丢弃，**真实客户端 IP 条件在此形态下不可用**。可行处置（对齐 security-standards §7）：① IP 白/黑名单按代理出口网段粗约配置；② 真实 IP 精细管控上移至最外层 WAF/LB；③ 未来需要网关级真实 IP 时另立 trusted-proxies 机制（现无）。外层伪造 XFF 不构成越权风险（Gateway 无条件清洗）。
- Gateway 管理端口（8081）默认仅绑定回环；Prometheus 抓取需经 `GATEWAY_MANAGEMENT_ADDRESS` 显式放开并配网络访问控制。
- access-service 管理端口（9101）同款形态（T-PERM-094）：默认仅绑定回环，主端口 9100 无任何 `/actuator/**`；远程抓取经 `ACCESS_MANAGEMENT_ADDRESS` 显式放开并配网络访问控制。

## 5. 时间语义

- 全链路 UTC 墙钟：JVM 时区由 common 启动强制 UTC（代码级，无需部署侧 `-Duser.timezone`/`TZ` 约定）；PG 使用 TIMESTAMPTZ；API 返回 ISO-8601 无偏移字符串（语义=UTC）。详见 [归并后目标架构 §16](../design/access-service-architecture.md)。

## 6. 数据与升级

- 唯一权威 DDL：`docs/design/schema/access-service.sql`；**当前无 migration 框架**。DDL 及 bootstrap 固定图加行可能令旧库启动 fail-fast；可重建环境先按 §8 备份、演练恢复，再按 [重建手册](../design/access-service-rebuild-runbook.md) 单独重建业务库。备份用于恢复旧版本，不能把旧全库 dump 灌入新 DDL 当作升级。持有不可丢数据的环境须停在升级前，不自动执行销毁。避免 `docker compose down -v` 连带删除 Redis/Nacos 卷。
- 空库首启顺序：DDL → access-service（bootstrap 建首管理员与固定图）→ gateway → 注册服务/签发凭证 → 配置业务服务并启动。
- 文件存储（ADMIN_FILE）落在 access-service 本机 `${user.home}/accessmesh-files`——**单实例限制**，多实例/容器化需挂载持久卷并保持单写者。

### 固定图种子来源切换（T-PERM-106）

新库固定图授权来源为 BOOTSTRAP_SEED。旧库的 MANUAL 种子与运营授权无法可靠追溯区分，不自动补标，启动明确拒绝未标记固定图。按 §8 备份并验证旧版本可恢复，再按重建手册新建库；这是既定开发期重建路径，不是保数据迁移方案。转授出的普通 MANUAL 行不因来源收紧而锁定。

### OAuth2 凭据代际切换（T-ACCESS-082）

本版授权码、刷新记录及访问 JWT 新增密码代际和链期限，存量缺字段的凭据拒绝，不提供兼容回退。使用维护窗口停止旧 access-service 全部实例，再启动新实例，避免混跑旧签发节点；第三方应用须让用户重新登录授权。种子客户端 refresh TTL 与 DDL 默认值改为 604800 秒，存量客户端配置需通过客户端管理入口核对，本版不自动修改已有客户端行。此处不要求重建 sys_user，也不把用户 updated_at 作为凭据失效依据。

## 7. 监控与告警

- Gateway 暴露 health/info/prometheus/metrics（管理端口）；告警规则示例见 [gateway 设计 §监控](../design/services/gateway.md)。
- access-service 同样暴露 health/info/prometheus/metrics（管理端口 9101）；引擎查询指标 `access.query.stage`（阶段终态计数，scopeAll/无角色短路率）、`access.query.execution`（执行终态+P50/P95/P99，`BUDGET_EXCEEDED` 单列容量信号）、`access.query.evidence.failed`（审计证据提交失败）——指标口径见 [engine/implementation §3.11](../design/engine/implementation.md)。
- example-service 健康检查仅在回环管理端口 9301 的 `/actuator/health`，主端口 9300 不暴露 actuator；Compose 不映射管理端口。
- 三服务日志为 stdout JSON，由容器日志驱动/集中采集负责轮转与保留；仓库不承诺本地日志文件。生产配置轮转容量与保留期，避免 Docker 默认 json-file 无限增长。
- 权限快照链路有 30 秒撤权安全边界（授权 L2 ≤10s + 回源截止 5s + Gateway L1 ≤15s）；缓存失效失败有 `cache.invalidate.failures` 指标兜底观察点。

### 拒绝尝试取证（T-ACCESS-085）

管理员在“操作日志”页输入响应的 requestId 精确检索；Gateway 拒绝事件为 ACCESS/GATEWAY_PERMISSION_DENIED，服务层失败摘要包含失败码。Gateway 发送审计最多等待 500ms，失败仍返回原403并输出关联 requestId 的兜底告警；该机制复用现有异步审计落库，不保证故障期间零丢失。早于可信身份建立的认证失败不能伪归某租户，按调用方响应及安全日志排查，未查到记录不等于从未尝试。

login-log 固定图新增路由，旧图启动缺行时须先备份再重建。bootstrap 本身保持启动日志取证（无登录操作者）；本卡不增加启动审计事务或审计表种子，避免初始化失败留下“成功”行，恢复操作由运维变更记录承载。

## 8. PostgreSQL 备份与恢复

以下命令为 Linux/Bash 运维示例，在仓库根目录执行。备份包含用户、授权与凭据哈希，目录须仅运维账号可读；离机副本加密保存。数据库备份之外，另行备份 Nacos 配置、密钥及 ADMIN_FILE 持久目录。Redis 恢复时清空本平台专用逻辑库，重新登录；不要恢复旧会话、授权缓存和令牌。

### 8.1 定时备份

```bash
set -euo pipefail
umask 077
mkdir -p backups
backup_file="backups/access-db-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d access_db -Fc -f /tmp/access-db.dump'
docker compose cp postgresql:/tmp/access-db.dump "$backup_file"
docker compose exec -T postgresql rm /tmp/access-db.dump
sha256sum "$backup_file" > "$backup_file.sha256"
```

将上述片段保存到运维目录脚本，开头先 `cd /绝对路径/Access-Mesh`；单实例调度示例 `0 2 * * * /bin/bash /运维目录/backup-accessmesh.sh >> /受限目录/backup.log 2>&1`（每天 02:00，按调度主机时区）。失败必须告警；保留期与异地频率按实际 RPO/RTO 配置，不只检查文件存在，定期执行恢复演练。

### 8.2 恢复演练与灾难恢复

先使用独立库 `access_restore`，不能覆盖运行库。停用该库所有应用写入；恢复期间使用与备份匹配的应用版本和密钥。

```bash
sha256sum -c "$backup_file.sha256"
docker compose cp "$backup_file" postgresql:/tmp/access-restore.dump
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" createdb -h 127.0.0.1 -U "$POSTGRES_USER" access_restore'
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -h 127.0.0.1 -U "$POSTGRES_USER" --exit-on-error --single-transaction --no-owner -d access_restore /tmp/access-restore.dump'
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d access_restore -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM sys_user; SELECT count(*) FROM role_resource_permission WHERE delete_flag=0;"'
docker compose exec -T postgresql rm /tmp/access-restore.dump
```

核对备份时记录的用户/有效授权数量、关键租户与业务行，随后用隔离的 Redis 与旧版本应用连接恢复库，验证登录、授权允许和拒绝各一条。灾难恢复时只有环境负责人确认目标后才切换数据源；清空平台专用 Redis DB、启动一台 access-service 验证，再启动其余实例与 Gateway。独立库创建失败（同名存在）须先调查，不自动 DROP。

## 9. 忘记 bootstrap 密码的离线恢复

`ACCESS_BOOTSTRAP_ADMIN_PASSWORD` 只在空库首启创建账号，改它并重启不会重置存量密码。恢复前执行 §8 备份并停止 Gateway 与全部 access-service 实例，防止并发写和旧会话使用。

1. 用 Java 21 和项目已有 Sa-Token 库在可信交互终端生成 BCrypt 哈希（明文交互输入、不放命令参数；Windows 将 `$HOME` 路径换为本机 Maven 仓库）：

   ```bash
   java --class-path "$HOME/.m2/repository/cn/dev33/sa-token-core/1.38.0/sa-token-core-1.38.0.jar" tools/ops/PasswordHash.java
   ```

2. 在 `psql` 交互会话中先核对 `SELECT id, tenant_id, username, status FROM sys_user WHERE tenant_id=1 AND username='admin' AND delete_flag=0;`。记录唯一目标 ID，禁止同时改其他租户或借重置自动启用停用账号。设置 psql 变量后更新（下面的值按刚查到的 ID 和生成哈希填入）：

   ```sql
   \prompt '目标用户 ID: ' recovery_user_id
   \prompt 'BCrypt 哈希: ' recovery_hash
   BEGIN;
   UPDATE sys_user
      SET password = :'recovery_hash', force_reset_pwd = true, updated_at = now()
    WHERE tenant_id = 1 AND id = :'recovery_user_id'::bigint
      AND username = 'admin' AND delete_flag = 0;
   -- 必须且只能 UPDATE 1；否则 ROLLBACK 并调查。
   COMMIT;
   ```

3. 清空**本平台独占**的 Redis 逻辑库（本仓默认 DB 0）：`docker compose exec -T redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -n 0 FLUSHDB'`。共享逻辑库不得执行此命令，须先隔离或精确清理平台会话/令牌/缓存键；本仓部署前提是独占 DB。
4. 启动一台 access-service 与 Gateway，用新密码登录并完成强制改密，确认旧平台会话和旧 OAuth2 凭据被拒，再恢复其他实例。记录操作者、目标 ID、时间与验证结果，不记录明文密码或哈希。

## 10. 授权墓碑恢复

误删固定图授权时，先按 §8 备份、停应用写入，再人工核对 `role_resource_permission` 的租户、角色、资源类型/实例、操作位和墓碑 ID。这只是恢复已存在的同身份授权，不为新版本固定图补行，也不改变其他授权属性。

```sql
\prompt '租户 ID: ' recovery_tenant_id
\prompt '墓碑行 ID: ' recovery_grant_id
BEGIN;
SELECT * FROM role_resource_permission
 WHERE tenant_id=:'recovery_tenant_id'::bigint AND id=:'recovery_grant_id'::bigint
   AND delete_flag=id FOR UPDATE;
UPDATE role_resource_permission
   SET delete_flag=0, deleted_at=NULL, deleted_by=NULL, updated_at=now()
 WHERE tenant_id=:'recovery_tenant_id'::bigint AND id=:'recovery_grant_id'::bigint
   AND delete_flag=id;
-- 必须 UPDATE 1；若相同有效授权已存在，唯一约束报错，ROLLBACK 后人工核对，禁止删新行绕过。
COMMIT;
```

直改库不会广播缓存失效。恢复后按 §9 清理平台专用 Redis、重启全部服务，再检查恢复权限允许、无关权限仍拒绝；SQL 操作及验证结果留运维审计。种子行锁定只限制管理 API，不代替离线灾难恢复。

## 11. 密钥轮换

本仓不支持 JWT 多签名键并行验证或双 HMAC 密钥过渡，使用维护窗口协调切换，禁止部分实例长期混用新旧值。

| 密钥 | 执行与影响 | 验收 |
|---|---|---|
| `JWT_SECRET_KEY` | 停入口与 access-service；注入新的至少 32 字符随机密钥到全部 access-service，清理平台专用 Redis 后重启。清理会使平台会话、授权码、刷新链同时失效；仅换 JWT 键不能撤销 Redis 中的旧刷新令牌 | 旧 JWT/刷新令牌拒绝，新登录授权成功 |
| `ACCESSMESH_SIGNATURE_SECRET` | 停 Gateway 入口；同时更新 gateway/access-service/example-service 和其他身份头校验方，先起下游再起 Gateway。用户会话可保留，旧签名请求拒绝 | 新链请求成功、旧签名拒绝，核对三处配置版本 |
| `PERM_INTERNAL_SECRET` | 仅在内部消费者与 access-service 协调切换，步骤见 §2；不分发给业务服务 | Gateway 查询成功、旧内部密钥拒绝 |
| 服务凭证 secret | 在所属租户签发新凭证，替换各消费方并验证，再停用旧凭证；泄露时先立即停旧再恢复服务 | 新凭证成功、旧凭证拒绝，跨租户和不在白名单端点拒绝 |

`X-Credential-Secret` 是可重放的 bearer secret。泄露者在凭证停用/过期前可重新构造该服务有权调用的查询与同步请求，并非只能无害重放同一份 full-sync。TLS、日志不记请求头、限制网络与 Docker 管理权限是部署前提；`allow-insecure=true` 仅声明接受单信任域内明文风险，不提供防重放能力。

## OAuth2 公开客户端版本升级

本版本 sys_oauth2_client 增加 client_type，client_secret 改为可空并增加类型/密钥 CHECK；固定图也包含本计划新增的 API 与菜单。旧开发库按前文备份规程保留数据后重建，不尝试只补一列绕过固定图校验。机密客户端缺省 CONFIDENTIAL，现有密钥方式保持；新 PUBLIC 客户端不配置 secret，必须使用 S256 PKCE。

回调规则按已确定的兼容口径仅收紧根路径：注册 `/` 不再放行 `/任意路径`，非根路径段前缀仍保留。依赖根路径放行的客户端须登记实际 callback 或非根前缀。本次不改为全 URI 精确匹配。

## 12. 容量与菜单读取基线

以下为当前配置/库默认值，不是吞吐量承诺；调大线程数不能替代数据库连接预算。多实例总连接数需要按实例数乘以各池上限计算，并留出运维与数据库自身余量。

| 资源 | 当前基线 | 调整入口与关注点 |
|---|---|---|
| access-service HikariCP | 未覆盖 HikariCP 5.0.1 默认最大 10 连接 | `spring.datasource.hikari.maximum-pool-size/minimum-idle/connection-timeout`；结合 PG max_connections、事务时长和等待指标设置 |
| 审计异步执行器 | core 4 / max 16 / queue 1000，keep-alive 60s | `spring.task.execution.pool.*`；满时调用者执行，会增加原请求耗时；停机丢弃会告警 |
| 业务作业执行器 | core 2 / max 4 / queue 100，keep-alive 60s | `access.task.executor.*`；满时拒绝并记录失败，租约接管按既有规则重试 |
| 共享调度器 | 2 线程 | `spring.task.scheduling.pool.size`，承载 cron 与扫描；扫描数据库 IO 可能占用线程 |
| 租约续租调度器 | 独立 1 线程 | 与共享扫描池隔离，避免扫描阻塞误触发接管；本次不增加可配置项 |

`user-menu` 每次读取租户菜单，再按当前用户权限派生；目前不增加完整用户菜单结果缓存。菜单数量有界、该接口主要在登录/能力刷新时调用，尚无实测瓶颈；整结果缓存还需覆盖菜单编辑、角色变更、条件时间/IP、用户与租户切换的失效面，不能仅把“每次查询”当作引入缓存的依据。引擎事实缓存仍走现有 CacheCatalogEntry。若以后实测菜单目录读取成为热点，先评估租户菜单元数据目录的 L1+L2（提案，不启用），权限派生仍实时，禁止缓存授权结果替代 QueryGate 判定。

## 相关文档

- [快速开始](../quickstart.md) / [rebuild runbook](../design/access-service-rebuild-runbook.md)（开发/验收环境重建）
- [Gateway 设计](../design/services/gateway.md) / [架构设计](../design/architecture.md)
