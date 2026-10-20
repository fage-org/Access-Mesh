# 持久化权限演示接入

元数据与资源发布入口由 T-ACCESS-139 提供。动态策略、行列查询与备注修改由 T-ACCESS-140 实现，CSV 导入由 T-ACCESS-138 实现，定时任务按[计划](../../docs/plans/example-permission-demo-plan.md)后续交付；本步骤不会创建业务记录或授予普通读者权限。

## 准备与执行

使用[独立业务库](../../docs/ops/example-database.md)和当前权威 access-service schema 的测试环境，先按[首次接入](first-access.md)准备已启用的 example-service 服务配置、持有 bootstrap-admin 角色的租户管理员原生登录令牌和同租户服务凭证。默认类型所有者及首授根为 bootstrap-admin。将该凭证配置到 example 的 `example.permission.tenant-credentials`，保持 Gateway 与 example 签名配置一致。脚本不会把密码、令牌或凭证写入仓库。

通过环境变量提供 `ADMIN_TOKEN`、`EXAMPLE_CREDENTIAL_ID`、`EXAMPLE_CREDENTIAL_SECRET`。在仓库根运行：

```powershell
pwsh -File tools/init-example-permission-demo.ps1 -BaseUrl https://your-gateway.example
# 仅可信隔离网络的本地测试可显式追加 -AllowInsecure 并使用 http 地址。
```

`-FieldType` / `-ScopeType` 可选择自定义类型名；二者按用途分离。`-ReportCode` 默认 sales-report。脚本核对服务凭证属于管理员当前租户，建立缺失的类型与操作、独立 EXAMPLE_DEMO 分类域和显式 SUB_PERM 规则，保留类型所有者的现役首授基座，再发布[完整接口声明](permission-demo-interfaces.json)并登记报表、字段及本人/团队/部门/地区范围，等待发布后保存初始策略。

重复执行复用原报表、字段与范围身份，仅登记缺少的固定目录字段；已有策略内容相同则不创建新版本，不同则停止而不覆盖。既有停用/失败的范围不会被脚本自动恢复。遇到不兼容的类型所有权、已有域配置、未知路由、停用路由或不同准入要求会停止；已完成步骤保留，不回滚为清库操作。FULL 声明保留受支持的旧 hello/report 路由及其已有资源编码，不发布未来尚未实现的接口。

管理入口用管理员 Bearer，接口声明与后台资源发布用服务凭证；服务凭证不能调用类型/授权等用户管理写接口。不要将普通读者设为管理员来绕过最终鉴权。

## 发布状态

`POST /api/example/demo-publication/page` 可按 `{pageNum,pageSize,status?}` 查询最新源版本，删除字段后的失败事件也保留。`get` / `retry` 使用 `{resourceTypeCode,resourceCode,codeType}`。状态为 PENDING / PUBLISHED / FAILED，原因是受控分类，不返回原始异常或凭证。

单实例发送器默认每秒处理一个到期事件。临时失败按 10/30 秒退避，周期内至多三次；服务重启继续消费数据库中的事件。修正明确失败的原因后，管理员可对最新 FAILED 事件调用 retry，开始新的有界尝试周期；载荷和源版本不变。人工重试 PENDING/PUBLISHED 不会制造重复发送。

发布确认要求首次应用成功或相同版本、相同内容的严格重放确认；不能把 STALE 或 HTTP 200 当作成功。这个确认不提供跨系统即时撤权或资源永久存在的承诺，后续数据操作仍检查当前本地版本及实际权限。

本阶段没有跨版本数据库升级承诺。已部署旧 schema 的环境须先按自己的备份/升级流程准备当前 schema；服务不会隐式执行 DDL。临时 E2E 环境使用原样 schema 初始化。

## 策略与记录接口

`demo-policy/get` 使用 `{datasetCode:"sales-records"}`；修改返回的 configuration 后，以当前 version 作为 expectedVersion 调用 save。每次有效保存生成新版本；非法配置、未发布引用或版本竞争保留旧版。策略由独立 MANAGE 权限保护，不因能读取报表而开放修改。

`demo-data/fields` 使用 `{reportCode}` 返回当前可读字段 ID 和筛选/排序标志。`page` 使用 `{reportCode,pageNum,pageSize,fieldIds?,filters?,sort?}`，未选列时默认当前可读列；无权字段不能用于筛选或排序。`get` / `batch-get` 使用已知 recordCode / recordCodes 定位，编号无读权时不返回编号；批量结果用请求下标关联。

`update` / `batch-update` 只修改 remark，显式 `remarkClear:true` 才清空；需要 DATA_EDIT 及对应行、备注字段编辑权，读权不能替代写权。混入无权或不存在的目标会整批回滚。没有导入业务数据时，初始化后的 page 正常返回 EMPTY_ROWS；不要用 IAM 资源登记冒充实际记录导入。

## CSV 导入与撤销

准备 UTF-8 CSV（可带 BOM），表头为 `recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount,remark`；ownerUserId 填真实用户 ID。remark 可空或省略该表头，其余列必填。先完成报表字段登记、资源发布、策略与授权；导入者需要独立数据维护权、报表 DATA_EDIT、行编辑 ALL 和所有提交列的编辑权。INSTANCE 即使覆盖当前全部记录也不能导入。

设置 `EXAMPLE_OPERATOR_TOKEN` 为该用户的原生 Bearer，再运行：

```powershell
pwsh -File tools/import-example-records.ps1 -BaseUrl https://your-gateway.example -CsvPath ./records.csv
# 网络结果不确定时保留脚本先输出的 importKey，并用同一文件、用户和报表重试：
pwsh -File tools/import-example-records.ps1 -BaseUrl https://your-gateway.example -CsvPath ./records.csv -ImportKey <原请求号>
```

原始文件字节摘要在服务器计算。同号换文件或报表拒绝；有效 recordCode 冲突整批回滚。`demo-data/import-status` / `revoke-import` 使用 `{importKey}`，仅原操作者访问。撤销按来源报表和原字段身份重新检查权限，保留请求号及 REVOKED 状态；旧号重放不恢复数据，主动重导使用新号。字段删除后同名重建不能替代旧授权身份。

一次最多 10 MiB、10,000 行；坏行与超限不部分导入，不回显原始单元格内容。已有行列权限查询可在导入后验证真实记录。完整的非管理员场景包继续由 T-PERM-036/T-ACCESS-142 验收。

## 一次性有界快照

以已经具有 DEMO_TASK:TRIGGER/VIEW、报表 EXPORT 与行列读取授权的普通用户 Bearer 调用：

```json
POST /api/example/demo-snapshot/submit
{"triggerKey":"由脚本生成并在本次重试中沿用的 UUID","selection":{"reportCode":"sales-report"}}
```

保存返回的 executionKey，以 `POST /api/example/demo-execution/get` 请求 `{"executionKey":"返回的 UUID"}` 查询状态；SUCCESS 后以相同请求体调用 `/api/example/demo-execution/download` 接收 CSV。`/demo-execution/page` 使用 `{pageNum,pageSize}` 查本人记录。网络重试沿原 triggerKey，主动新执行才换号；同号不能换选择或借用他人身份。

省略 fieldIds 表示执行时的当前可读列；显式 fieldIds、filters 和 sort 使用 `/demo-data/fields` 返回的稳定身份。执行前撤销显式列会拒绝，默认列只保留仍有权的部分。后台不保存提交 IP；仅凭在线 IP 条件获得的权限不保证后台通过。EMPTY 无文件；超行数/字节预算整次 FAILED；成功文件保留 7 天，过期不可下载。完整词表与预算见 [API 契约](../../docs/design/example-service-api-contract.md#tasks)。单实例后台默认启用，测试可用 `example.execution.enabled=false` 停止自动消费而直接驱动服务；不提供多实例保证。

## 导出脚本与本人定时任务

已有普通用户授权后，将其 Bearer 保存在 `EXAMPLE_OPERATOR_TOKEN`，选择文件只含 Selection（示例为 `permission-demo-selection.json`，可添加已授权的稳定 fieldIds、filters、sort）：

```powershell
pwsh tools/export-example-snapshot.ps1 -BaseUrl https://your-gateway.example `
  -SelectionPath example-service/examples/permission-demo-selection.json -OutputPath ./snapshot.csv
```

脚本输出 triggerKey 和 executionKey，轮询后下载 CSV；目标文件须不存在。网络重试可指定原 `-TriggerKey`，不换号创建重复结果；超过等待期限后可以按 executionKey 继续查询。只有在隔离可信 HTTP 测试环境才显式增加 `-AllowInsecure`。

定时任务先 `POST /api/example/demo-task/create`：

```json
{"name":"每分钟权限快照","cron":"0 * * * * *","selection":{"reportCode":"sales-report"}}
```

保存返回的 taskId/version。初始停用；调用 `/demo-task/set-enabled`，请求 `{taskId,expectedVersion,enabled:true}` 启用。cron 统一 UTC、六段且秒字段为 0；需要手动演示时调用 `/demo-task/trigger`，请求 `{taskId,triggerKey}`。从 `/demo-execution/page` 按 `{taskId,pageNum,pageSize}` 发现自动生成的执行键。结束演示后以最新 version 停用任务，避免继续定时生成。

任务仅创建者维护；管理员不因此代管他人任务。修改需完整 name/cron/selection 与 expectedVersion，旧执行保留旧选择；同号触发的重试返回旧执行。任务停止前尚未取数的执行会再次核对启用状态，已完成内容按原归属和期限保留。重启恢复已有 PENDING/RUNNING，重新初始化未来 cron，不补跑停机遗漏时刻。

## 可重复验收入口

`mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false` 使用独立临时 Gateway/access/example/PG/Redis，以真实普通用户完成授权、导入、行列查询、撤权、任务、重启与下载链路。加 `-Dexample.demo.scriptHost=<本机 pwsh 绝对路径>` 同时运行初始化与导出脚本；令牌只注入隔离子进程环境。此命令不使用或清空候选部署环境。

SQL 事务、版本、同键并发、attempt 防旧结果覆盖及精确预算由 `DemoDataPgIT` 验证；执行键和受控状态由 API 留存。完整项目收口命令仍为根 `mvn test -T 1C`，包含 E2E/heavy。
