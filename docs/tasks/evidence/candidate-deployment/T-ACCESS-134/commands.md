# T-ACCESS-134 脱敏复现命令

PowerShell 7，仓库根执行。以下为本次已使用的资源与备份名称；仅用于定位与同版本演练，不能对已恢复的库直接重跑 create/restore，更不能自动 DROP。备份与 env 实体只在受限 `.tmp` 目录，不提交仓库。

```powershell
$sourceArgs = @('--project-name','am-candidate-20261018-44e178a',
  '--project-directory','.tmp/candidate-deployment-acceptance/source',
  '--env-file','.tmp/candidate-deployment-acceptance/candidate.env',
  '-f','.tmp/candidate-deployment-acceptance/source/docker-compose.yml',
  '-f','docs/tasks/evidence/candidate-deployment/compose.candidate.yml','--profile','app')
$restoreArgs = @('--project-name','am-restore-20261018-44e178a',
  '--project-directory','.tmp/candidate-deployment-acceptance/source',
  '--env-file','.tmp/candidate-deployment-acceptance/T-ACCESS-134/restore.env',
  '-f','.tmp/candidate-deployment-acceptance/source/docker-compose.yml',
  '-f','docs/tasks/evidence/candidate-deployment/T-ACCESS-134/compose.restore.yml','--profile','app')
```

## 重启与重建

首次同时 restart 四个应用返回全部 healthy，但 nginx 仍访问 Gateway 旧地址，502 证据单独保留。修正顺序是后端就绪后刷新前端；下例在操作前暂停入口。每次后端操作完成后，用 `docker ps --filter label=com.docker.compose.project=am-candidate-20261018-44e178a` 检查健康状态，按实际状态轮询、设置超时，不固定等待后宣称成功。

```powershell
docker compose @sourceArgs stop frontend
docker compose @sourceArgs restart access-service gateway example-service
# 后端健康后启动前端，刷新 nginx 上游解析
docker compose @sourceArgs up -d --no-build --no-deps frontend

# 保留原数据卷的重建步骤（独立场景，不紧接上面盲目重复）
docker compose @sourceArgs stop frontend
docker compose @sourceArgs up -d --force-recreate --no-build --no-deps access-service gateway example-service
# 后端健康后
docker compose @sourceArgs up -d --force-recreate --no-build --no-deps frontend
Invoke-RestMethod 'http://127.0.0.1:18080/api/access/auth/captcha' `
  -Method Post -ContentType application/json -Body '{}'
```

健康不替代真实 API 检查。正常验证码与密码登录后，用本目录 restart/recreate 的请求日志逐项验证，下载需检查附件头与字节 SHA-256；不从浏览器提取 Cookie，不沿用旧会话冒充登录验证。

## 停写、成套备份与恢复源服务

本次备份目录创建前先限制其父目录 ACL 为当前用户和 SYSTEM。新的备份应使用新时间戳目录、dump 临时名和辅助容器名。

```powershell
$backupDir = 'D:/codespace/Access-Mesh/.tmp/candidate-deployment-acceptance/T-ACCESS-134/backup-20261018T123757Z'
docker compose @sourceArgs stop frontend gateway example-service access-service nacos
docker exec am-candidate-20261018-44e178a-pg psql -U postgres -d access_db -Atc `
  "select count(*) from pg_stat_activity where datname='access_db' and pid<>pg_backend_pid()"
# 本次结果为 0；再做 dump 与文件快照
docker exec am-candidate-20261018-44e178a-pg sh -ec `
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d access_db -Fc -f /tmp/accessmesh-20261018T123757Z.dump'
docker cp am-candidate-20261018-44e178a-pg:/tmp/accessmesh-20261018T123757Z.dump "$backupDir/access-db.dump"
docker run --rm --pull never --name am-restore-20261018-44e178a-backup-files --network none `
  --mount type=volume,source=am-candidate-20261018-44e178a_files,target=/data,readonly `
  --mount "type=bind,source=$backupDir,target=/backup" postgres:16-alpine tar czf /backup/files.tgz -C /data .
docker run --rm --pull never --name am-restore-20261018-44e178a-backup-nacosdata --network none `
  --mount type=volume,source=am-candidate-20261018-44e178a_nacosdata,target=/data,readonly `
  --mount "type=bind,source=$backupDir,target=/backup" postgres:16-alpine tar czf /backup/nacosdata.tgz -C /data .
Copy-Item .tmp/candidate-deployment-acceptance/candidate.env "$backupDir/candidate.env"
Copy-Item .tmp/candidate-deployment-acceptance/T-ACCESS-134/restore.env "$backupDir/restore.env"
Get-ChildItem -LiteralPath $backupDir -File | Get-FileHash -Algorithm SHA256

docker compose @sourceArgs up -d --no-build access-service gateway example-service
docker compose @sourceArgs up -d --no-build --no-deps frontend
```

源 Nacos 配置查询结果为零项，冷备 Nacos 数据留底但不向恢复端复制注册状态；源 Redis 不备份、不清空。dump 原文件与容器内原始 SHA-256 一致。数据库与文件都在应用停写后完成，启动源应用前备份材料已齐全。

## 独立恢复

先检查 `am-restore-20261018-44e178a` 的容器、卷、网络及 18090/18091 端口均未占用。私密 restore.env 保持匹配应用密钥与租户凭证映射，PG/Redis 密码单独生成。检查合并后的 Compose（原文含密钥，仅私密保存）：PG 移除自动 schema 挂载，POSTGRES_DB=postgres；应用明确连接 access_restore，其他资源名称及网络全部独立。

```powershell
docker compose @restoreArgs up -d --no-build postgresql redis nacos
# 待基础设施健康，确认 access_restore 不存在后才创建
docker exec am-restore-20261018-44e178a-pg psql -U postgres -d postgres -Atc `
  "select datname from pg_database where datname='access_restore'"
docker exec am-restore-20261018-44e178a-pg sh -ec `
  'PGPASSWORD="$POSTGRES_PASSWORD" createdb -h 127.0.0.1 -U "$POSTGRES_USER" access_restore'
docker cp "$backupDir/access-db.dump" am-restore-20261018-44e178a-pg:/tmp/backup-20261018T123757Z-restore.dump
docker exec am-restore-20261018-44e178a-pg sh -ec `
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -h 127.0.0.1 -U "$POSTGRES_USER" --exit-on-error --single-transaction --no-owner -d access_restore /tmp/backup-20261018T123757Z-restore.dump'
docker volume create --label com.docker.compose.project=am-restore-20261018-44e178a `
  --label com.docker.compose.volume=accessmesh-files am-restore-20261018-44e178a_files
docker run --rm --pull never --name am-restore-20261018-44e178a-restore-files --network none `
  --mount type=volume,source=am-restore-20261018-44e178a_files,target=/data `
  --mount "type=bind,source=$backupDir,target=/backup,readonly" postgres:16-alpine tar xzf /backup/files.tgz -C /data
docker exec am-restore-20261018-44e178a-redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO keyspace'
# 确认空 Redis、恢复数据与序列逐表匹配后启动应用
docker compose @restoreArgs up -d --no-build access-service gateway example-service
docker compose @restoreArgs up -d --no-build --no-deps frontend
```

检查真实 JDBC 目标，不只看环境变量：

```powershell
docker exec am-restore-20261018-44e178a-pg psql -U postgres -d access_restore -Atc `
  "select datname,client_addr,application_name from pg_stat_activity where client_addr is not null"
docker inspect am-restore-20261018-44e178a-access --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

首次恢复入口 `http://127.0.0.1:18090` 验证源会话拒绝，再取新验证码正常登录。原生 POST 形状和返回值见 `restore-requests.jsonl`，下载文件的字节数和 SHA-256 见 `restore-file-downloads.json`。原始密码、token、服务 secret 不属于复现日志。

离线表指纹使用每张 public 表的 `row_to_json(t)::text` 排序串联后在 PostgreSQL 内计算 SHA-256，只导出行数与摘要；序列读取 pg_sequences。备份/恢复启动前全部对照，运行期仅排除审计/调度表后比较稳定业务事实（排除表名与核对表名在 persistence-comparison.json）。数据库中的密码/凭据摘要本身不导出到证据。

本次执行辅助脚本位于受限 `.tmp/candidate-deployment-acceptance/T-ACCESS-134/`：run.py、verify.py、prepare-restore.py、backup.py、restore.py、final-collect.py。备份、两套环境及数据保留；仅已复制并完成恢复核验的容器内 `/tmp/*dump` 副本已删除。后续清理须明确对象，不自动 down -v 或删除备份。
