# 独立依赖发布与可选协调

example-service 的接口鉴权仍由 Gateway 承担。registration starter 默认关闭；只同步资源的应用继续调用资源 sync/full-sync，无需安装它或提交空依赖。角色物化的交付状态以 [任务看板](../../docs/tasks/README.md) 为准。

先注册来源服务，为 MONTHLY_REPORT 声明 SYNC + syncSourceService=example-service，定义 VIEW，并通过独立资源同步登记 sales-monthly 与 sales-template，再发布[示例清单](permission-manifest.json)。代次 42 仅为示例；实际代次由来源的可靠提交顺序分配并绑定完整快照，不使用本机时间、Git revision 或每次重试新取号。

## 显式动态发布

开启 `perm.registration.enabled=true`，显式声明 `perm.allow-insecure=true/false`，即可注入 PermissionRegistrationPublisher。Provider 仅提供依赖，不返回资源目录。

```java
RegistrationTarget target = new RegistrationTarget(tenantId, "example-service", credentialId, credentialSecret);
// generation/revision 和依赖内容来自同一来源快照；capture 只调用 Provider 一次。
PermissionManifestReq snapshot = publisher.capture(generation, revision, dependencyProvider);
SyncResultResp result = publisher.publish(target, snapshot);
// 检查 accepted/retryClass/detail.failedCount 和 itemResults；HTTP 200 不代表全部成功。
// 重试传同一个 snapshot；不要再次调用 Provider 或更换 generation。
```

只改依赖时不必重传资源。明确 `dependencies=[]` 是 tenant + service 的完整空依赖清单；读取失败、分页未完成或 Provider 异常必须停止发布，不能转换为空集合。

## 可选资源前置

资源准备复用应用已有同步程序，回调接收明确的租户与凭证。以下 resourceSource 和 existingResourceSync 是接入方自身接口示意：

```java
var resourceSnapshot = resourceSource.captureFull(); // 含完整资源及源侧代次
var manifestSnapshot = publisher.capture(generation, revision, dependencyProvider);
RegistrationResult result = publisher.prepareAndPublish(target, manifestSnapshot,
        selectedTarget -> List.of(existingResourceSync.publish(selectedTarget, resourceSnapshot)));
```

资源步骤业务失败、部分失败或真正旧发布代次都停止清单发布，manifestResult=null，preparationResults 保留响应。同键同代次同内容的增量重试返回 PUBLICATION_UNCHANGED，可作为已完成前置；PUBLICATION_GENERATION_STALE 不可据此放行。回调异常/null 不转为空快照。

资源成功而依赖失败时，可只重发原 manifestSnapshot；两步不是分布式原子事务。SDK 不回滚资源、不建立后台重试队列。来源已前进时重新采集所需快照，不能给旧清单换新号。

## 多租户

TenantRegistrationProvider.publications() 返回各租户的 TenantPublication(target, manifest)。通过 publisher.capture(provider) 一次固定输入后，逐个 publish(publication.target(), publication.manifest())。每项携带独立凭证与代次，失败重试原项，不回落默认租户或清理其他租户。凭证不写入共享可变拦截器，RegistrationTarget 的文本表示隐藏凭证值。

## 静态文件启动发布

显式开启 Spring profile `permission-manifest`，提供以下环境变量：

| 环境变量 | 含义 |
|---|---|
| PERM_MANIFEST_LOCATION | 不可变 JSON 文件位置，如 file:/opt/example/manifest.json |
| PERM_TENANT_ID | 本次发布的租户 |
| PERM_CREDENTIAL_ID / PERM_CREDENTIAL_SECRET | 该租户的 example-service 现役凭证 |
| PERM_ALLOW_INSECURE | 显式 true/false 信任域声明，沿现役凭证约定 |

文件与 PermissionManifestReq 同构，自身包含 publicationGeneration，SDK 不补默认代次。只有配置文件位置才启动发布；缺文件、坏 JSON、尾随内容、字段缺失、业务部分失败或旧代次均使本次启动发布失败。未开启 profile 时普通 example-service 不调用清单接口。

资源协议切换与旧数据保全分别见 [full-sync 手册](../../docs/ops/runbook-full-sync.md)和[迁移手册](../../docs/ops/runbook-auto-grant-migration.md)。
