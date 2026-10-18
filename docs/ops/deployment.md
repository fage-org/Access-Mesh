---
doc_type: ops
title: 生产部署基线
status: adopted
domain: cross-service
last_reviewed: 2026-10-18
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

同机独立候选部署不能仅指定另一个 Compose project name：仓库编排包含固定容器名、宿主端口和文件卷名。使用覆盖配置时须检查合并后的全部资源和服务发现地址，避免列表合并保留原端口；数据库、Redis、Nacos 与文件卷均使用新资源。回环非默认端口的正式包实测、脱敏配置及复现命令见 [T-ACCESS-131](../tasks/evidence/candidate-deployment/verification.md)。此记录仅覆盖实际预览拓扑，不替代前述 TLS 终结和多代理部署验证。

## 2. 密钥与敏感配置（强制）

| 密钥 | 作用 | 约束 |
|------|------|------|
| `JWT_SECRET_KEY` | Sa-Token JWT 签名 | ≥32 字符；缺失 access-service 启动失败 |
| `ACCESSMESH_SIGNATURE_SECRET` | 用户身份头 HMAC 签名 | gateway 与下游服务**同值**；不一致时经 Gateway 的请求一律拒绝（信封 30003） |
| `PERM_INTERNAL_SECRET` | Gateway→access-service 内部密钥 | 与 access-service 同值 |
| `ACCESS_PLATFORM_ADMIN_PASSWORD` | 平台初始管理员密码 | 仅空账号表创建；重启不覆盖。租户从运营界面开通 |

- 全部经环境变量或 Nacos 加密配置注入，**禁止**写入代码/仓库/明文 compose 文件（模板 `.env.example` 只是占位，`.env` 已被 git 忽略）。
- `DB_PASSWORD`/`REDIS_PASSWORD` 在仅基建与全栈两档均必填且非空，Compose 插值校验缺失即拒绝启动；本机直接启动 Java 服务同样须注入。PG 初始化显式使用 `--auth-host=scram-sha-256`（含容器内 TCP 回环连接），Redis 不再提供公开默认值。旧 PG 数据卷不会因新增 `POSTGRES_PASSWORD` 自动修改账号密码或 `pg_hba.conf`：先备份，在维护窗口设置数据库密码并将 host 规则改为 `scram-sha-256`、重载验证；可重建环境按 §8 新建。不得用改 `.env` 冒充存量认证已收紧。
- `.env` 经 Compose 转为容器环境变量，具备 Docker 管理权限者可通过 `docker inspect` 读取明文；限制 Docker 权限与 `.env` 文件权限，不把 `docker compose config` 原文上传。需要避免环境变量暴露时，以只读 secret 文件挂载，并用 Spring Boot 原生 `configtree:/run/secrets/` 导入属性文件（例如 `spring.datasource.password`、`spring.data.redis.password`）；同时移除对应环境覆盖项。PG 官方镜像支持 `POSTGRES_PASSWORD_FILE`，Redis 可挂载受限 `redis.conf`。仓库默认仍为环境注入，未实现统一 `_FILE` 转换器。
- `NACOS_SERVER_ADDR` 在 Compose 三服务同源消费，默认 `nacos:8848`；外部 Nacos 可直接覆盖。内置 Nacos 的 `/home/nacos/data` 挂持久卷。容器使用 `restart: unless-stopped`，依赖按健康检查放行。

### 服务凭证接入与内部密钥轮换

外部业务查询/同步统一使用服务凭证，SDK 不再注入内部密钥。先启动 access-service 与 Gateway，管理员注册服务并调用 `/api/access/service-credential/create` 签发；把返回的 credentialId/secret 配到业务服务的 `PERM_CREDENTIAL_ID`/`PERM_CREDENTIAL_SECRET`，同时显式设置 `PERM_ALLOW_INSECURE`。example 使用 `example.permission.tenant-credentials` 映射与 `example.permission.allow-insecure`，通过 Nacos 或 Spring Boot 的 `SPRING_APPLICATION_JSON` 注入（Compose 从 `.env` 的 `EXAMPLE_PERMISSION_CONFIG_JSON` 传入）。每个映射项必须对应该租户真实签发的凭证；无映射的请求拒绝，不回落 SDK 固定凭证。配置更新后重启服务生效，本次不引入热刷新或自动轮换机制。

本批确认无仓外旧调用方（2026-10-04），端点/SDK/示例同批切换。部署验收依次确认：有效凭证查询/同步成功、伪造租户头不改变身份、旧共享密钥加自报服务头拒绝、业务服务不再持有内部密钥。随后生成新的平台内部密钥，只更新 Gateway/access-service 等内部消费者并协调重启，再检查用户访问及 Gateway 准入链路、确认旧密钥失效。T-GW-014 起 Gateway 仅向路由目的为 access-service 的请求注入该头——业务服务不再经用户流量收到内部密钥，密钥轮换只需 Gateway/access-service 等内部消费者协调，无需与业务服务联动。不要把签发服务凭证等同于已完成内部密钥轮换；仓库测试不代表真实环境已执行轮换。

## 3. TLS 与安全头（强制）

- 所有对外入口（前端、Gateway）**仅 HTTPS**：TLS 在最外层 LB/nginx 终止；HTTP→HTTPS 重定向；HSTS 开启。
- 自带网页登录使用 HttpOnly Cookie，并同时校核 Origin/Referer 和会话绑定的 CSRF 标记；原生工具登录仍返回 Bearer。TLS 不替代 CSRF 防护。
- gateway 与 access-service 同值配置 `ACCESS_BROWSER_ALLOWED_ORIGINS=https://<对外域名>`（或 `accessmesh.browser-session.allowed-origins`），多个 Origin 用逗号分隔；必须是纯 Origin，不带路径，不允许通配符。TLS 终止层不改变这一公开 Origin，不能由未信任的转发头推断。默认仅含本地回环开发/预览地址，生产不配置实际域名会拒绝网页登录。Cookie 在 HTTPS 下具有 Secure，HTTP 仅允许回环预览并使用独立名称。
- 安全响应头（X-Content-Type-Options、X-Frame-Options、CSP 等）在 TLS 终止层配置；Gateway/服务侧不重复注入。

## 4. X-Forwarded-For 与多层代理（部署前提）

- Gateway 会**清洗**外部传入的 `X-Forwarded-For`/`X-Real-IP`，并以自身观测的 **remoteAddr（直连对端 socket 地址）** 重建 XFF 下发下游（单值可信来源）；IP 条件权限（白名单/黑名单/快照本地重评）同样只消费该直连地址——**不读任何 XFF 头**。
- **多层 LB/CDN 部署的真实 IP 边界**：Gateway 恒观测到代理出口 IP——外层代理即使正确重建 XFF，也会被 Gateway 清洗丢弃，**真实客户端 IP 条件在此形态下不可用**。可行处置（对齐 security-standards §7）：① IP 白/黑名单按代理出口网段粗约配置；② 真实 IP 精细管控上移至最外层 WAF/LB；③ 未来需要网关级真实 IP 时另立 trusted-proxies 机制（现无）。外层伪造 XFF 不构成越权风险（Gateway 无条件清洗）。
- Gateway 管理端口（8081）默认仅绑定回环；Prometheus 抓取需经 `GATEWAY_MANAGEMENT_ADDRESS` 显式放开并配网络访问控制。
- access-service 管理端口（9101）同款形态（T-PERM-094）：默认仅绑定回环，主端口 9100 无任何 `/actuator/**`；远程抓取经 `ACCESS_MANAGEMENT_ADDRESS` 显式放开并配网络访问控制。

## 5. 时间语义

- 全链路 UTC 墙钟：JVM 时区由 common 启动强制 UTC（代码级，无需部署侧 `-Duser.timezone`/`TZ` 约定）；PG 使用 TIMESTAMPTZ；API 返回 ISO-8601 无偏移字符串（语义=UTC）。详见 [归并后目标架构 §16](../design/access-service-architecture.md)。

## 6. 数据与升级

浏览器 HttpOnly 切换（T-ACCESS-109）：协调部署 gateway、access-service 和前端，避免新页面访问旧认证入口实现。新版网页清除旧版脚本可读认证存储，租户与平台分别重新登录一次；没有自动会话迁移，也不批量吊销工具的 Bearer 会话。原登录接口保留。尚未加载新版的旧标签页不会仅因发布而在服务器端强制下线，仍受原过期与门禁约束。同一浏览器已建立新 Cookie 后，旧页面的 Bearer 请求会因 T1 混用规则被拒绝，应刷新到新版；这不等于批量吊销旧 Bearer 令牌。

- 唯一权威 DDL：`docs/design/schema/access-service.sql`；**当前无 migration 框架**。DDL 及 bootstrap 固定图加行可能令旧库启动 fail-fast；可重建环境先按 §8 备份、演练恢复，再按 [重建手册](../design/access-service-rebuild-runbook.md) 单独重建业务库。备份用于恢复旧版本，不能把旧全库 dump 灌入新 DDL 当作升级。持有不可丢数据的环境须停在升级前，不自动执行销毁。避免 `docker compose down -v` 连带删除 Redis/Nacos 卷。
- 空库首启顺序：DDL → access-service（bootstrap 建首管理员与固定图）→ gateway → 注册服务/签发凭证 → 配置业务服务并启动。
- 文件存储（ADMIN_FILE）落在 access-service 本机 `${user.home}/accessmesh-files`——**单实例限制**，多实例需保持单写者（共享存储为非目标）。容器化部署须挂载持久卷：全栈预览档已固定 `FILE_STORAGE_PATH=/var/lib/accessmesh/files` 并挂命名卷 `accessmesh-files`（T-ACCESS-099）——常规 `--profile app up -d --build` 重建容器后既有文件仍可下载；备份见 §8（含该卷），`down -v` 会连带删除该卷。

### 标准文件能力（T-ACCESS-116 / T-FE-078）

文件下载错误边界修复（T-ACCESS-118）须一并发布 access-service 与前端：前端保留原 MIME 并用 attachment 附件头识别真实文件，服务端只在完整读取成功后设置该头。仅发布新前端、仍使用旧服务实现时，读文件失败可能残留附件头，不能据此完成本版协议验收。

- 本版新建租户自动登记五条 `/api/access/file/*` 准入路由及 `/system/file` 菜单，内置管理员获得本租户全部分类 CREATE/VIEW/DELETE 并可转授；普通用户默认无权。管理员可在现有授权页仅委派 `document` 的 VIEW，授权粒度为分类而非上传者。
- 对已初始化库不自动补路由或权限：缺固定图仍按现行校验拒绝；先备份数据库与文件卷、验证旧版本可恢复，再由环境负责人按重建手册决定处置。不得把“新版本启动”当作旧库已升级，也不得自行清理文件卷。
- 上线验收使用正常开通的租户经 Gateway 上传、查询、下载和删除，再检验分类委派与跨租户拒绝。原业务默认大小上限 10 MiB；multipart/nginx 的 100MB 仅为请求信封上限。文件卷和业务数据库必须成套备份。
- Gateway 与 access-service 使用现有 `sa-token-redis-jackson` 时，正式运行包必须包含其 optional Java Time 依赖 `jackson-datatype-jsr310`（Jackson 2，版本由统一 BOM 管理）。Boot 4 的 Jackson 3 不能提供这个类。两个服务已显式补齐 runtime 依赖；发布前须启动正式 JAR 验证，不能只凭测试类路径启动成功判断打包完整。不更换现有会话序列化器或迁移 Redis 数据。

### 固定图种子来源切换（T-PERM-106）

新库固定图授权来源为 BOOTSTRAP_SEED。旧库的 MANUAL 种子与运营授权无法可靠追溯区分，不自动补标。防护形态（2026-10-06 bootstrap 校验方案 A 拍板）：①**写路径全形态守卫**——种子行撤/改/挂子权限拒 **20074**、固定图墓碑身份的孪生行撤/改/挂子权限拒 **20075**（不依赖 bootstrap 开关，任意实例生效）；②**启动检查**（随 bootstrap 开关装配）——未标记且无种子墓碑的真旧库维持拒启；「种子墓碑+同键 MANUAL 孪生」并存态 WARN 放行不补回（孪生行已由 20075 置为只读）。真旧库按 §8 备份并验证旧版本可恢复，再按重建手册新建库；这是既定开发期重建路径，不是保数据迁移方案。转授出的普通 MANUAL 行不因来源收紧而锁定。

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

以下命令为 Linux/Bash 运维示例，在仓库根目录执行。备份包含用户、授权与凭据哈希，目录须仅运维账号可读；离机副本加密保存。数据库备份之外，另行备份 Nacos 配置、密钥及 ADMIN_FILE 持久目录（Compose 预览档=命名卷，物理卷名已钉死为 `accessmesh-files`——默认卷名带项目前缀且随目录名/COMPOSE_PROJECT_NAME 漂移，钉死后备份命令可移植；备份形态如 `docker run --rm -v accessmesh-files:/data -v <备份目录>:/backup alpine tar czf /backup/accessmesh-files-$(date +%Y%m%d%H%M%S).tgz -C /data .`）。Redis 恢复时清空本平台专用逻辑库，重新登录；不要恢复旧会话、授权缓存和令牌。

**一次性文件迁移（升级到钉名卷形态，codex sol r2 P2-5 + r3 P2-3 双源补全）**：当前形态固定挂载命名卷 `accessmesh-files`（容器内 `/var/lib/accessmesh/files`）。直接 `up -d` 不会把旧来源内容带进钉名卷——PG 元数据仍在而文件内容全失（下载报物理文件不存在）。已上传过文件的环境按旧来源二选一迁移，共同次序为「先备份留底 → 内容落到钉名卷 → 切换新栈 → 抽样核验既有文件可下载且内容一致 → 清理旧来源」：

- **来源一：无卷形态（T-ACCESS-099 之前，2026-10-14 前的更早部署）**——无文件卷，内容在旧 access-service 容器可写层（容器内 `${user.home}/accessmesh-files`，root 运行时典型为 `/root/accessmesh-files`，以旧部署实际 FILE_STORAGE_PATH 为准；`docker exec accessmesh-access-service sh -c 'ls "${user.home:-/root}/accessmesh-files"'` 可确认）。旧容器一旦删除可写层**不可恢复**——必须先导出再重建：①停止写入（停旧容器即可）；②`docker cp accessmesh-access-service:<上述容器内路径> ./accessmesh-files-backup` 导出留底；③按新版本重建服务（钉名卷自动创建）；④`docker run --rm -v "$(pwd)/accessmesh-files-backup:/src" -v accessmesh-files:/dst alpine sh -c "cp -a /src/. /dst/"`。
- **来源二：项目前缀卷形态（T-ACCESS-099 已挂卷、钉名之前的部署）**——物理卷名为默认 `<项目名>_accessmesh-files`，Docker 不会改名/迁移旧卷：①停止写入；②对旧物理卷做一次上述 tar 备份留底；③`docker run --rm -v <旧物理卷名>:/src -v accessmesh-files:/dst alpine sh -c "cp -a /src/. /dst/"`（旧卷名用 `docker volume ls` 确认，典型为 `access-mesh_accessmesh-files`）；④核验后删除旧卷。

全新环境无旧来源，直接按钉名卷启动即可。

### 8.1 定时备份

```bash
set -euo pipefail
umask 077
mkdir -p backups
backup_file="backups/access-db-$(date -u +%Y%m%dT%H%M%SZ).dump"
# 容器内临时文件名并入主机侧唯一时间戳+容器 shell PID：定时与人工备份共用固定路径交错时，
# 后写者截断同名文件会使先写者的拷贝得到部分文件且 sha256 与其自身自洽（无法靠校验和识别）
# ——2026-10-06 逐任务评审修正
tmp_dump="/tmp/access-db-$(basename "$backup_file")-$$.dump"
docker compose exec -T postgresql sh -ec "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_dump -h 127.0.0.1 -U \"\$POSTGRES_USER\" -d access_db -Fc -f '$tmp_dump'"
docker compose cp "postgresql:$tmp_dump" "$backup_file"
docker compose exec -T postgresql rm "$tmp_dump"
sha256sum "$backup_file" > "$backup_file.sha256"
```

将上述片段保存到运维目录脚本，开头先 `cd /绝对路径/Access-Mesh`；单实例调度示例 `0 2 * * * /bin/bash /运维目录/backup-accessmesh.sh >> /受限目录/backup.log 2>&1`（每天 02:00，按调度主机时区）。失败必须告警；保留期与异地频率按实际 RPO/RTO 配置，不只检查文件存在，定期执行恢复演练。

### 8.2 恢复演练与灾难恢复

先使用独立库 `access_restore`，不能覆盖运行库。停用该库所有应用写入；恢复期间使用与备份匹配的应用版本和密钥。

```bash
sha256sum -c "$backup_file.sha256"
# 容器内临时文件名并入备份文件名+shell PID（与备份段同款唯一化：交错恢复/备份共用固定
# 路径会产出校验和自洽的部分文件——2026-10-06 逐任务评审修正）
tmp_restore="/tmp/access-restore-$(basename "$backup_file")-$$.dump"
docker compose cp "$backup_file" "postgresql:$tmp_restore"
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" createdb -h 127.0.0.1 -U "$POSTGRES_USER" access_restore'
docker compose exec -T postgresql sh -ec "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_restore -h 127.0.0.1 -U \"\$POSTGRES_USER\" --exit-on-error --single-transaction --no-owner -d access_restore '$tmp_restore'"
docker compose exec -T postgresql sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d access_restore -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM sys_user; SELECT count(*) FROM role_resource_permission WHERE delete_flag=0;"'
docker compose exec -T postgresql rm "$tmp_restore"
```

核对备份时记录的用户/有效授权数量、关键租户与业务行，随后用隔离的 Redis 与旧版本应用连接**恢复库**验证登录、授权允许和拒绝各一条。注意：`application.yml` 的 JDBC URL 库名硬编码 `access_db`（仅 HOST/PORT 为占位符），起验证实例必须显式覆盖库名，否则应用会静默连回运行库、三查通过即产出「备份已验证」假结论（2026-10-06 逐任务评审修正）——验证实例以 `SPRING_DATASOURCE_URL=jdbc:postgresql://postgresql:5432/access_restore` 环境变量覆盖（compose `run`/临时服务定义注入，勿改动运行实例）。灾难恢复时只有环境负责人确认目标后才切换数据源；清空平台专用 Redis DB、启动一台 access-service 验证，再启动其余实例与 Gateway。独立库创建失败（同名存在）须先调查，不自动 DROP。

## 9. 平台与租户管理员恢复

平台账号初始化、恢复、租户开通、门禁故障和备份恢复规则见[租户运营手册](tenant-operations.md)。环境变量只在平台账号表为空时创建初始账号，不能通过修改环境变量重启接管已存在账号。

租户首管理员凭据可由平台运营界面受审计重置，但不恢复状态和角色，已删除则拒绝。客户内部管理员仍按租户内流程管理用户；所有管理员失联或授权墓碑异常需部署方维护窗口核对，不能假定存在固定租户 1。

生成 BCrypt 哈希可使用已有交互工具（明文不放命令参数）。以下 JAR 版本与根 pom 的 `sa-token.version` 一致；先完成当前版本依赖解析，不依赖历史 Maven 缓存：

```bash
java --class-path "$HOME/.m2/repository/cn/dev33/sa-token-core/1.46.0/sa-token-core-1.46.0.jar" tools/ops/PasswordHash.java
```

工具校验与服务写入口同口径（T-PERM-110）：密码须 8–32 位、含字母和数字，且 UTF-8 字节长度不超过 72（约 24 个汉字）——BCrypt 只参与前 72 字节，超限密码尾部差异会被静默忽略，先拒后哈希。

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

<a id="service-credential-digest-upgrade"></a>
**服务凭证 SHA-256 切换（T-ACCESS-114）**：当前无正式部署，不保留 BCrypt 验密兼容。旧开发凭证在新版统一拒绝 403/20065，不能通过启用旧记录或修改过期时间恢复；没有自动转换或查询内迁移。字段宽度和表结构未变，仅摘要格式改变。

开发环境先暂停业务接入方调用，将全部 access-service 实例协调切换到新版；使用原有管理员登录及服务凭证管理入口重新签发，更新 SDK 配置和 example-service 的各租户 `example.permission.tenant-credentials`（Compose 为 `EXAMPLE_PERMISSION_CONFIG_JSON`），按现有方式重启消费方。核验新凭证经原查询协议成功、旧凭证 20065，再删除旧凭证记录。不得将数据库摘要填作 secret，也不把旧 BCrypt 串再哈希当作转换；新版凭证不能在旧版服务端验密，不做新旧实例混合运行。无需为本次算法切换清空业务库、Redis 或用户会话；若叠加其他 schema/固定图变更，仍按其独立升级规则执行。

**服务删除与历史凭证（T-ACCESS-111）**：新版删除服务登记会同事务永久软删除其全部旧凭证；服务停用/恢复仍保留凭证。升级不会扫描或自动清理旧版本删除留下的孤立凭证，也不会在同码新建登记时补清。若要重用旧 serviceCode，先在该租户的服务凭证管理页选择“全部服务”（已删除服务不在筛选候选中），按列表里的服务编码核对并删除全部遗留凭证（包含停用/过期项），确认列表无旧凭证后再登记服务、签发和分发新凭证；“停用”可重新启用，不能代替此处的“删除”。不能从页面处理时，部署方在维护窗口按 tenant_id、service_code 核准目标并执行受审计的软删除，禁止跨租户清理。未先清理则旧钥匙可能随同码登记复活，这是明确保留的人工升级前提。

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

登录审计采用[事务结束后同步独立写入](../design/access-service-architecture.md#login-audit-transaction-boundary)（T-ACCESS-117），消除登录与其审计同时占用连接的等待环；此修复不替代部署容量规划，不调整现有池大小或宣称高并发请求永不等待。

以下为当前配置/库默认值，不是吞吐量承诺；调大线程数不能替代数据库连接预算。多实例总连接数需要按实例数乘以各池上限计算，并留出运维与数据库自身余量。

OAuth2 链签发时间容忍窗 `access.oauth2.clock-skew-tolerance-seconds`（默认 3 秒、配 0 关闭）：多实例部署仍要求节点 NTP 同步（秒级偏差内），容忍窗只吸收残余漂移（慢时钟节点不拒稍早签发的链），不放宽过期判定（2026-10-06 拍板）。

| 资源 | 当前基线 | 调整入口与关注点 |
|---|---|---|
| access-service HikariCP | 未覆盖 HikariCP 默认最大 10 连接 | `spring.datasource.hikari.maximum-pool-size/minimum-idle/connection-timeout`；结合 PG max_connections、事务时长和等待指标设置 |
| 审计异步执行器 | core 4 / max 16 / queue 1000，keep-alive 60s | `spring.task.execution.pool.*`；满时调用者执行，会增加原请求耗时；停机丢弃会告警 |
| 业务作业执行器 | core 2 / max 4 / queue 100，keep-alive 60s | `access.task.executor.*`；满时拒绝并记录失败，租约接管按既有规则重试 |
| 共享调度器 | 2 线程 | `spring.task.scheduling.pool.size`，承载 cron 与扫描；扫描数据库 IO 可能占用线程 |
| 租约续租调度器 | 独立 1 线程 | 与共享扫描池隔离，避免扫描阻塞误触发接管；本次不增加可配置项 |

`user-menu` 每次读取租户菜单，再按当前用户权限派生；目前不增加完整用户菜单结果缓存。菜单数量有界、该接口主要在登录/能力刷新时调用，尚无实测瓶颈；整结果缓存还需覆盖菜单编辑、角色变更、条件时间/IP、用户与租户切换的失效面，不能仅把“每次查询”当作引入缓存的依据。引擎事实缓存仍走现有 CacheCatalogEntry。若以后实测菜单目录读取成为热点，先评估租户菜单元数据目录的 L1+L2（提案，不启用），权限派生仍实时，禁止缓存授权结果替代 QueryGate 判定。

## 相关文档

- [快速开始](../quickstart.md) / [rebuild runbook](../design/access-service-rebuild-runbook.md)（开发/验收环境重建）
- [Gateway 设计](../design/services/gateway.md) / [架构设计](../design/architecture.md)


## 审计日志保留与导出

三类租户日志策略见[API 契约 §16.7](../design/access-service-api-contract.md#contract-section-16-7)。默认不清理，保留天数不设默认值。部署方在 access-service 的部署配置或 Nacos 中明确设置 `access.audit.retention.enabled` 和需要清理类别的 `operation-days` / `change-days` / `login-days`；未设置的类别继续保留。不要把这些键写入租户 `system_config`，该表不参与部署策略读取。

配置在启动时读取，变更后统一更新并重启所有 access-service 实例；旧实例停止前仍可按旧期限执行。清理会直接永久删除在线记录，启用前按部署方需要完成备份。默认每天 UTC 18:00 执行；停用租户暂停，恢复后下一次执行处理已到期历史数据。任务执行、失败和删除计数沿用既有任务日志，部署任务不允许从租户任务接口编辑或手动触发。

本版本按当前权威 DDL 备份/重建升级；`sys_login_log.location` 已退出，不提供原地迁移脚本。代码与测试的实施不会自动修改既有业务库。
