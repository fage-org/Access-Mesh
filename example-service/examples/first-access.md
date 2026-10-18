# example-service 首次接入操作示例

本文给出已改密租户管理员和普通用户的最小接入顺序，覆盖 hello 与报表 VIEW。字段及失败语义以[契约总册 §11–13、§24–25](../../docs/design/access-service-api-contract.md)为准；服务发现、显式 Gateway 路由及 SDK 接线见[扩展指南](../../docs/design/extension-guide.md#2-场景一业务服务接入接口鉴权example-模式)。这里不包含完整报表路由族的批量、子权限及异步导出演示。

## 前置条件

- 租户已开通，管理员和普通用户已完成首次改密；普通用户绑定一个无 EXAMPLE 授权、无管理员或继承覆盖的 BASIC_ROLE。本例角色码 `qa-file-reader` 需替换为实际角色码。
- 管理写入使用租户管理员的用户身份。先请求 `POST /api/access/auth/captcha`（`{}`），再向 `POST /api/access/auth/login` 提交 `tenantCode/username/password/captchaId/captchaCode`；使用返回的 `data.accessToken`。普通用户另行登录取得自己的 token。
- 网页登录使用 HttpOnly Cookie/CSRF，不从浏览器存储提取 Bearer。以下为独立的原生 API 调用；不要记录密码、Authorization 或服务 secret。
- 已部署 Gateway/access-service/Nacos。Gateway 显式路由 `Path=/api/example/**`、`uri=lb://example-service`、`metadata.serviceCode=example-service`，不使用 StripPrefix；sync-v2 不创建转发路由。
- example-service 使用同一注册中心，签名密钥与 Gateway 同源。先准备租户凭证映射，再启动示例；业务端口只在隔离网络内开放。

## 管理员准备服务、类型和资源

以下命令为 PowerShell 7。`$env:ADMIN_TOKEN`、`$env:READER_TOKEN` 由上面的原生登录取得，示例地址可替换；脚本只在内存使用令牌。

```powershell
$base = 'http://127.0.0.1:18080'
$adminHeaders = @{ Authorization = "Bearer $env:ADMIN_TOKEN" }
$readerHeaders = @{ Authorization = "Bearer $env:READER_TOKEN" }
function Post-Access($path, $body, $headers) {
  $result = Invoke-RestMethod "$base$path" -Method Post -ContentType application/json `
    -Headers $headers -Body ($body | ConvertTo-Json -Depth 12) `
    -SkipHttpErrorCheck -StatusCodeVariable httpStatus
  Write-Host "$path HTTP=$httpStatus code=$($result.code)"
  return $result
}
Post-Access '/api/access/service-config/save' @{
  serviceCode='example-service'; name='接入示例'; basePath='/api/example'; status=1
} $adminHeaders
Post-Access '/api/access/type-definition/create' @{
  typeKey='resource_type'; typeCode='EXAMPLE'; name='报表'
} $adminHeaders
foreach ($code in @('report-1','report-2')) {
  Post-Access '/api/access/resource-entity/create' @{
    resourceTypeCode='EXAMPLE'; code=$code; codeType='default'; name=$code; status=1
  } $adminHeaders
}
$issued = Post-Access '/api/access/service-credential/create' @{
  serviceCode='example-service'
} $adminHeaders
if ($issued.code -ne 200) { throw '服务凭证签发失败' }
$serviceHeaders = @{
  'X-Credential-Id'=$issued.data.credentialId
  'X-Credential-Secret'=$issued.data.secret
}
```

每一步须检查信封 `code=200` 后再继续。初次创建 EXAMPLE 自动预置 CREATE/VIEW/UPDATE/DELETE，并向类型所有者（缺省 bootstrap-admin）建立可转授授权根；不需要手写 SQL 或另外创建 VIEW。此例使用 MANAGED 类型，资源走管理 create，不走资源 sync。

报表实际业务类型固定为 `EXAMPLE`，内存种子使用 `report-1`（销售日报）、`report-2`（库存周报）、`report-3` 及对应明细码。只验证 VIEW 时登记前两个即可。hello 没有业务对象，只验证身份；给 hello 配置任意实例候选不能证明报表取数权限。示例的内存业务数据不具备持久化承诺；管理面资源登记也不会自动创建业务报表内容。

## 用服务凭证声明接口并启动

```powershell
$manifest = Get-Content -Raw example-service/examples/first-access-interfaces.json | ConvertFrom-Json
Post-Access '/api/access/service-config/sync-v2' $manifest $serviceHeaders
Post-Access '/api/access/service-config/apis' @{serviceCode='example-service'} $adminHeaders
```

清单为 FULL，只用于尚无其他接口声明的示例服务。重放同一清单可核对幂等结果；已有更多 SERVICE_SYNC 接口时先合并完整清单，避免 FULL 删除未提交的接口。读回应确认完整路径、`maintainSource=SERVICE_SYNC` 与 EXAMPLE 操作要求，不能只看 HTTP 200。

将 `$issued.data` 中只展示一次的凭证私密保存，并按**凭证实际所属 tenantId**写入 `EXAMPLE_PERMISSION_CONFIG_JSON`（结构见[服务认证 §3.5](../../docs/design/service-authentication.md#35-外部凭证化与退役边界)）。本地隔离网络显式声明 `example.permission.allow-insecure=true`。每个租户独立签发、独立映射；无映射的租户必须拒绝，不设置默认凭证。不要把真实 JSON/secret 提交仓库或输出到验收记录。

固定镜像已构建时，更新私密 env 后用原 Compose 参数执行 `up -d --no-build --no-deps example-service`；配置变更须重建容器，`restart` 不会更新容器环境。确认健康检查及 Nacos 同名健康实例后，经 Gateway 调用；无需开放宿主机业务端口。

## 普通用户授权、撤销与恢复

```powershell
Post-Access '/api/example/demo/hello' @{name='first-access'} $readerHeaders
# 未授权：HTTP 403。授权写入必须使用管理员身份。
$grant = @{
  roleTypeCode='BASIC_ROLE'; roleExternalId='qa-file-reader'
  plan=@{creates=@(@{key=@{
    resourceTypeCode='EXAMPLE'; resourceCode='report-1'; codeType='default'
    operationCode='VIEW'; scopeMode='INSTANCE'; canGrant=$false
  }}); updates=@(); removes=@()}
}
Post-Access '/api/access/role-resource-permission/apply-grant-plan' $grant $adminHeaders
Post-Access '/api/example/report/view' @{reportCode='report-1'} $readerHeaders
Post-Access '/api/example/report/view' @{reportCode='report-2'} $readerHeaders
Post-Access '/api/example/report/list' @{pageNum=1;pageSize=20} $readerHeaders
```

期望：hello 与 report-1 允许；report-2 到达业务层后返回 HTTP 200/code=30004；列表只有 report-1。授权生效按 30 秒上界轮询具体结果，记录写入完成时间和首次符合预期的响应，不能固定等待后直接宣称通过。

撤销前通过 `/api/access/role-resource-permission/list`（`roleTypeCode=BASIC_ROLE`、实际 `roleExternalId`、`resourceTypeCode=EXAMPLE`）取得该行 id；向同一 apply-grant-plan 提交 `creates=[]/updates=[]/removes=[该id]`。确认没有其他覆盖授权，30 秒内 hello 回到 HTTP 403。重交 `$grant` 后重新允许，报表 report-2 仍须拒绝。不要删除既有文件等其他类型授权。

## 反例与结果解释

- 租户 B 建立同码 EXAMPLE 资源与接口，但先不给示例配置 B 凭证：B 管理员调用报表应为 200/30004。补齐 B 独立映射、重建示例容器后重验。
- `LOCAL_USER` 的 subjectExternalId 是本地用户数值 id 的字符串，不是 username。凭证查询仅解析其租户：另一租户的主体 id 返回 `allowed=false/USER_NOT_FOUND`；伪造租户头不能改变凭证身份。
- 暂停 A 的服务凭证：`service-credential/update {id,status:0}`；M2M 查询为 403/20067，示例最终检查为 200/30005（依赖拒绝，失败关闭）。B 不受影响；`status:1` 后 A 恢复。管理写接口不能改用服务凭证。
- Gateway 外部伪造 `X-User-Id/X-Tenant-Id/X-User-Signature/X-Signature-Timestamp` 不能覆盖用户会话。隔离网络内直连示例：hello 无身份头返回 200/30002；有身份但缺签名或错误签名返回 200/30003。错误签名探针应带当前秒级 `X-Signature-Timestamp`，避免把缺时间戳拒绝误当成签名比较证据。
- 报表目标已授权但业务对象在当前租户不存在时返回 200/30001；这与 30004 权限拒绝不同。可在 A 创建一条内存报表，在两租户分别登记其同码权限资源：A 读取成功、B 仍不存在，验证业务数据按租户分区。

以上步骤已由代理在固定候选隔离环境走查；不代表独立新人已完成接入。版本、原始响应、授权前提及实际边界见 [T-ACCESS-133 验收证据](../../docs/archive/2026-10-18/tasks/evidence/candidate-deployment/T-ACCESS-133/verification.md)。
