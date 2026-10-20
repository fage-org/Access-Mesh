# example-service 独立业务库

example-service 使用独立的 `example_db` 和 `example_app` 账号。它不读取 access_db 的 IAM 内部表，也不在启动时创建业务样本或执行 DDL。数据源连接及基础表检查失败会阻止服务启动。

本页负责数据库初始化与运行边界；动态行列、导入、快照及内部任务的使用见[演示指南](../../example-service/examples/permission-demo.md)。旧 `/demo/hello`、`/report/**` 仍保留为独立内存探针。完整验收状态见[权限演示计划](../archive/2026-10-20/example-permission-demo-plan.md)。

## 首次初始化

先启动 PostgreSQL。将独立密码填写到根目录忽略文件 `.env` 的 `EXAMPLE_DB_PASSWORD`，并为初始化命令提供同一个环境变量。PowerShell 7 示例（密码不会进入命令文本）：

```powershell
$env:EXAMPLE_DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '输入与 .env 一致的 EXAMPLE_DB_PASSWORD' -AsSecureString)).Password
pwsh -File tools/init-example-db.ps1
```

默认目标容器为当前 Compose 的 `accessmesh-postgresql`，可用 `-Container <明确的容器名>` 指定另一台本地测试容器。脚本仅创建尚不存在的角色/数据库，并在空库里执行原样 [example-service.sql](../design/schema/example-service.sql)；角色无超级用户、建库、建角色、复制或绕过 RLS 权限。已有角色的密码不被重置，已有库的所有者不被改写，非空库拒绝执行，不提供清库或自动升级。

脚本读取容器已有的管理连接配置，独立业务密码只经标准输入交给 [psql 的 `\password`](https://www.postgresql.org/docs/16/app-psql.html)；客户端生成 SCRAM 校验值，不把明文或可逆编码嵌入 SQL、日志或进程命令行。工具采用单行密码输入，不接受换行。原样 DDL 在一个事务中执行，出错时不留下半套表。数据库管理员也可通过既有管理工具建立上述独立角色和空库，然后以业务库所有者执行该 DDL；不要将其应用到 access_db。

## 启动配置

| 环境变量 | 默认/要求 |
|---|---|
| EXAMPLE_DB_HOST | 本机运行默认 127.0.0.1；Compose 使用 postgresql |
| EXAMPLE_DB_PORT | 5432 |
| EXAMPLE_DB_NAME | example_db |
| EXAMPLE_DB_USERNAME | example_app |
| EXAMPLE_DB_PASSWORD | 必填，与初始化时的业务密码一致 |

本机 Maven/Java 进程不会自动读取根 `.env`，应显式注入这些环境变量。Compose 的 example-service 已接入上述固定库与账号。签名密钥及各租户服务凭证继续按[快速开始](../quickstart.md)配置，不与数据库密码混用。

```powershell
# 已完成初始化、配置签名与租户凭证后：
docker compose --profile app up -d --build example-service
```

健康入口仍为容器内回环管理端口 9301 的 `/actuator/health`，业务端口不开放 actuator。服务重启不重建数据库，字段身份、幂等请求号和已写事实由数据库保留；实际业务写入入口按对应任务交付。

## 维护与恢复边界

启用持久化演示后，数据库备份与恢复必须包含 `example_db`，并为恢复目标配置独立账号。可使用既有 PostgreSQL 备份工具，不建设第二套备份平台。仅恢复 access_db 不能恢复 example 的业务事实。

非空库不能重复执行首次初始化脚本；需要调整结构时按本次变更的 DDL 与部署说明执行，禁止通过删除数据卷解决有数据的库升级。2026-10-18 的候选部署验收对应当时的内存探针版本，不构成这个新增业务库的恢复证据。
