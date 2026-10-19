# 持久化权限演示接入

元数据与资源发布入口由 T-ACCESS-139 提供。动态策略、CSV、行列取数及定时任务按[计划](../../docs/plans/example-permission-demo-plan.md)后续交付；本步骤不会创建业务记录或授予普通读者权限。

## 准备与执行

使用[独立业务库](../../docs/ops/example-database.md)和当前权威 access-service schema 的测试环境，先按[首次接入](first-access.md)准备已启用的 example-service 服务配置、持有 bootstrap-admin 角色的租户管理员原生登录令牌和同租户服务凭证。默认类型所有者及首授根为 bootstrap-admin。将该凭证配置到 example 的 `example.permission.tenant-credentials`，保持 Gateway 与 example 签名配置一致。脚本不会把密码、令牌或凭证写入仓库。

通过环境变量提供 `ADMIN_TOKEN`、`EXAMPLE_CREDENTIAL_ID`、`EXAMPLE_CREDENTIAL_SECRET`。在仓库根运行：

```powershell
pwsh -File tools/init-example-permission-demo.ps1 -BaseUrl https://your-gateway.example
# 仅可信隔离网络的本地测试可显式追加 -AllowInsecure 并使用 http 地址。
```

`-FieldType` / `-ScopeType` 可选择自定义类型名；二者按用途分离。`-ReportCode` 默认 sales-report。脚本核对服务凭证属于管理员当前租户，建立缺失的类型与操作、独立 EXAMPLE_DEMO 分类域和显式 SUB_PERM 规则，保留类型所有者的现役首授基座，再发布[完整接口声明](permission-demo-interfaces.json)并登记报表和字段。

重复执行复用原报表与字段身份，仅登记缺少的固定目录字段。遇到不兼容的类型所有权、已有域配置、未知路由、停用路由或不同准入要求会停止；已完成步骤保留，不回滚为清库操作。FULL 声明保留受支持的旧 hello/report 路由及其已有资源编码，不发布未来尚未实现的接口。

管理入口用管理员 Bearer，接口声明与后台资源发布用服务凭证；服务凭证不能调用类型/授权等用户管理写接口。不要将普通读者设为管理员来绕过最终鉴权。

## 发布状态

`POST /api/example/demo-publication/page` 可按 `{pageNum,pageSize,status?}` 查询最新源版本，删除字段后的失败事件也保留。`get` / `retry` 使用 `{resourceTypeCode,resourceCode,codeType}`。状态为 PENDING / PUBLISHED / FAILED，原因是受控分类，不返回原始异常或凭证。

单实例发送器默认每秒处理一个到期事件。临时失败按 10/30 秒退避，周期内至多三次；服务重启继续消费数据库中的事件。修正明确失败的原因后，管理员可对最新 FAILED 事件调用 retry，开始新的有界尝试周期；载荷和源版本不变。人工重试 PENDING/PUBLISHED 不会制造重复发送。

发布确认要求首次应用成功或相同版本、相同内容的严格重放确认；不能把 STALE 或 HTTP 200 当作成功。这个确认不提供跨系统即时撤权或资源永久存在的承诺，后续数据操作仍检查当前本地版本及实际权限。

本阶段没有跨版本数据库升级承诺。已部署旧 schema 的环境须先按自己的备份/升级流程准备当前 schema；服务不会隐式执行 DDL。临时 E2E 环境使用原样 schema 初始化。
