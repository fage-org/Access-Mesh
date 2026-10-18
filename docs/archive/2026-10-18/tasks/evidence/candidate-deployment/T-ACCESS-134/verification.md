# T-ACCESS-134 重启持久化与备份恢复验收

## 结论与边界

2026-10-18，沿既有隔离栈完成相同镜像的应用重启、容器重建、数据库与文件成套备份，以及另一套独立资源的同版本恢复。正常新登录后的允许、拒绝、跨租户文件拒绝及文件内容核验通过；本地双轨结论见下方。

没有改动产品源码、构建新镜像、清空源 Redis、覆盖源数据库或删除数据卷。源环境与恢复环境均保留，全部应用及基础设施健康。完整回归仍按计划由 T-ACCESS-135 承载，本次未运行 Maven/Vitest 全量，也不把单次实测表述为生产 RPO/RTO、跨版本升级或自动灾备通过。

本次发现并修正了操作顺序：同时重启应用时 nginx 缓存的 Gateway 地址失效，全部容器 healthy 仍出现登录 API 502。保留[失败证据](restart-ingress-before-fix.json)，在后端就绪后重启前端恢复，按此顺序完成应用重建和恢复启动。部署手册已明确前端刷新及真实 API 入口探测；没有更改 nginx 解析机制。

## 版本、拓扑与材料

候选源码仍为 `44e178a1bc953f3488819376f3bbe2fd24081a49` 加 T-ACCESS-131 登记的前端公开环境默认值补丁。应用镜像与 T-ACCESS-133 完全一致；[persistence-comparison.json](persistence-comparison.json)核对 restart 容器 ID 保持、recreate 应用 ID 改变、基础设施 ID 保持，以及全部镜像 ID 不变。

| 项 | 源环境 | 恢复环境 |
|---|---|---|
| Compose project | am-candidate-20261018-44e178a | am-restore-20261018-44e178a |
| 前端入口 | http://127.0.0.1:18080 | http://127.0.0.1:18090 |
| Gateway 回环端口 | 18081 | 18091 |
| PostgreSQL 容器/库 | 项目前缀-pg / access_db | 项目前缀-pg / access_restore |
| Redis、Nacos、文件卷、网络 | 原有专用资源 | 全新专用资源，与源无共享挂载或网络 |
| 业务/数据库宿主端口 | 未发布 | 未发布 |
| 应用密钥/租户服务凭证 | 私密 candidate.env | 匹配原应用及恢复数据；PG/Redis 密码另生成 |

[compose.restore.yml](compose.restore.yml)覆盖固定容器名、发布端口、全部卷名、网络名、CORS/网页 Origin 及 JDBC URL；PostgreSQL 使用 POSTGRES_DB=postgres，去掉自动执行 schema 的挂载，再显式 createdb access_restore。预检确认目标不存在，未自动 DROP 或复用任何碰撞资源。[restore-preflight.json](restore-preflight.json)保存脱敏检查结果。

[actual-database-connections.json](actual-database-connections.json)核对真实 JDBC 会话：源 access 容器 `172.19.0.5` 连接 access_db；恢复 access 容器 `172.20.0.5` 连接 access_restore，两个 PostgreSQL 实例仅有各自应用的连接。[isolated-service-discovery.json](isolated-service-discovery.json)确认两套 Nacos 只登记各自网络中的 access/Gateway/example 健康实例。最终容器与挂载见 [source-final-runtime.json](source-final-runtime.json)、[restore-final-runtime.json](restore-final-runtime.json)。其他既有容器的 ID、镜像、启动时间与状态未改变，见[检查结果](unrelated-containers-check.json)。

备份实体保留于受限本地目录：`.tmp/candidate-deployment-acceptance/T-ACCESS-134/backup-20261018T123757Z/`，包含 `access-db.dump`、`files.tgz`、`nacosdata.tgz`、匹配的 `candidate.env` 和 `restore.env`。当前用户与 SYSTEM 专用 ACL，所有材料均被 Git 忽略，未上传/提交原始备份或制作离机副本。[backup-manifest.json](backup-manifest.json)仅记录字节数、SHA-256、位置及时间；原密钥、用户密码哈希、服务 secret 和 token 不进入证据。容器内临时 dump 已在复制和恢复成功后删除，保留上述完整备份。

## 验证事实

| 验收面 | 实测结果 | 证据 |
|---|---|---|
| 重启幂等 | 原数据卷保留，restart 应用 ID 不变；新登录前整库数据指纹与基线相同，平台/租户身份、密码及授权未被初始化覆盖 | baseline、after-restart 数据及运行快照；persistence-comparison.json |
| 重建幂等 | 应用容器 ID 变更、镜像 ID 不变；稳定业务事实与基线相同，基础设施/卷未重建 | before/after-recreate 快照；persistence-comparison.json |
| 重启/重建业务行为 | A/admin、A/qareader、B/admin 均通过新验证码密码登录；report-1 允许，qareader report-2 为 200/30004，B 自己的 report-1 允许 | restart/recreate-login.json 系列及 requests.jsonl |
| 文件持久化 | 两个保留文件实际下载字节、物理路径及 SHA-256 相同；document 委派不扩展到 default，B 不能读取 A 的文件 | 各阶段 file-downloads.json、requests.jsonl、physical-file-hashes.json |
| 成套备份一致性 | 停止前端、Gateway、example、access 及 Nacos 后，源库其他客户端连接为零，再 dump 与 tar；恢复源服务前备份完成 | backup-frozen-database.json、backup-manifest.json、source-stop-write-window.json |
| 离线恢复完整性 | 应用启动前全部 40 张表的行数/整行 SHA-256、39 个序列状态与冻结源库完全一致 | [offline-restore-comparison.json](offline-restore-comparison.json)、restored-before-app-database.json |
| 恢复后初始化 | 恢复应用启动后稳定业务表与基线一致，无身份/密码/业务授权重种或重置 | restored-after-app-database.json、persistence-comparison.json |
| 独立 Redis | 新实例所有数据库为空；未导入源会话或授权缓存，源 Bearer 请求恢复入口返回 HTTP 401/code=401 | [restore-redis-before-app.json](restore-redis-before-app.json)、restore-requests.jsonl |
| 恢复后新登录及授权 | 三个原账号各自重新验证码登录成功；hello、report-1 允许，qareader report-2 200/30004；授权行仍为 id=133 | restore 的 login.json 系列、[restore-requests.jsonl](restore-requests.jsonl) |
| 文件范围与删除事实 | 普通用户仅 document 可读；default 详情 403/403；B 查 A 文件 200/10501；已删除 file id=2 仍 200/10501 且卷中不存在 | requests.jsonl、physical-file-hashes.json |
| 内存业务数据边界 | T-ACCESS-133 动态创建的 report-101 在恢复后仍有权限登记，但内存对象不存在，返回 200/30001；不宣称该数据可持久化 | restore-requests.jsonl |

固定样本：file id=1 `验收文档-保留.txt`（document，59 bytes），SHA-256 `7e92d23fd0f2eea2b24cb033a9e775f0dc334f482c71cbe44b14b775da8c21eb`；file id=3 `其他分类-隔离.txt`（default，60 bytes），SHA-256 `6c1e6fe48df87e55e9f04af9816e3cc2ca1c59cce07a5fddefe3cd3b36becd91`。归档目录、原物理卷、恢复物理卷和实际下载分别核对，[backup-file-manifest.json](backup-file-manifest.json)保留相对路径。

稳定业务对比排除了运行期审计与调度表，这些会因正常登录、拒绝审计或调度发生变化，完整排除表见命令说明与对比口径；离线备份/恢复比较没有排除表。仅导出摘要，数据库中的密码/凭证摘要本身不公开。没有为了核验而修改数据库事实。

源 Nacos 无配置项，见 [source-nacos-config-inventory.json](source-nacos-config-inventory.json)。本次保留冷备 Nacos 卷，但恢复端从空配置注册中心启动，由 env 提供实际配置；不声称非空 Nacos 配置恢复已演练。身份、授权、服务登记、凭证摘要、文件元数据属于 PG 恢复范围；文件内容通过文件卷恢复，example 内存数据与 Redis 会话不属于恢复范围。

## 实测时间

均为本机同版本、当前样本体量的一次观测。用户侧时间按 Asia/Shanghai；JSON 中备份/恢复时点明确带 UTC 偏移。

| 指标 | 结果 | 起止口径 |
|---|---|---|
| 首次并行 restart | 49.845 秒 | 命令至所有容器 healthy 并采样；此时 API 仍 502，不能当作入口恢复时间 |
| 顺序重建应用 | 93.331 秒 | 重建后端、就绪后重建前端、全部健康及数据采样；随后新登录验证通过 |
| 成套备份 | 15.989 秒 | 停写命令开始至 dump/文件/Nacos/配置备份完成；其中 pg_dump+复制 0.538 秒 |
| 源环境停写操作窗口 | 98.107 秒 | 20:37:58 至 20:39:36，包含停止、备份、重新启动及实际 API 入口成功 |
| 数据库恢复 | 2.313 秒 | pg_restore 命令执行 |
| 文件解包恢复 | 0.814 秒 | 独立文件卷解包 |
| 恢复执行至 API 入口可用 | 68.913 秒 | 20:39:20 至 20:40:29；基础设施已预先就绪，包含建库、恢复、核对、应用启动及实际入口探测；新账号登录验收随后另做 |

原始计时见 restart-timing.json、recreate-timing.json、backup-manifest.json、source-stop-write-window.json、restore-timing.json。容器健康等待按状态有界轮询，未用固定 sleep 当作成功依据。

## 命令、收口与保留责任

完整脱敏步骤见 [commands.md](commands.md)，公开部署规范已补顺序、同版本恢复、独立资源及 JDBC 实证检查。临时辅助程序位于 `.tmp/candidate-deployment-acceptance/T-ACCESS-134/`，用于本次真实环境验证，不新增常驻框架。Git 仅保留文档、Compose 覆盖配置和脱敏证据。

两套栈、数据库/文件卷和受限备份留给 T-ACCESS-135 追溯与收口；后续清理须明确对象，不自动删除。源入口 18080 与恢复入口 18090 可分别访问。未修改原有暂缓项，不启动跨版本迁移、多实例文件存储或自动灾备。

## 本地双轨评审

主代理按代码轨、文档轨顺序对同一候选及本次文档/证据只读核对，无子代理或外部 AI 评审。

**代码轨**：无剩余 P0–P3。实证通过项：`frontend/nginx.conf:20` 的启动时上游解析与 502 原始记录相符，刷新前端后真实登录通过；`compose.restore.yml:23` 显式指定恢复库，实际 JDBC 会话确认目标；独立 PG/Redis/Nacos/文件卷及网络与容器实态一致，源数据未被恢复覆盖；备份脚本先停写再 dump/tar，恢复脚本核对摘要、拒绝同名库、使用单事务 pg_restore；全部表与序列离线对比独立复核通过，稳定业务事实与物理文件/实际下载一致。应用镜像未变化，无新增生产机制。存疑待用户决策项：无；过度设计可裁剪项：无。

**文档轨**：无剩余 P0–P3。独立按任务 acceptance、部署 §8、空库重建边界、单实例文件设计及文档治理核对。实证通过项：来源版本与资源命名可追溯，操作命令明确停写、独立目标、库名及前端顺序；健康探测失败未包装为成功，时间口径注明基础设施已先就绪和新登录另验；同版本恢复未扩展到升级、内存数据或生产承诺；JSON/JSONL 可解析，本次修改及新增 Markdown 本地链接均可定位，PowerShell 示例通过语法解析，过程词、精确敏感值扫描与 `git diff --check` 通过。备份文件 ACL 实查仅当前用户与 SYSTEM。存疑待用户决策项：无。

2026-10-18 执行记录：run.py 的 snapshot/restart/recreate/captcha/login、verify.py 的 restart/recreate/restore、backup.py、restore.py、final-collect.py；对应 35 条有明确 HTTP/信封断言的业务请求、9 次新登录、各阶段下载哈希及离线数据核对，命令与原始文件均可定位。这是部署验收记录，不是 Maven 测试计数。
