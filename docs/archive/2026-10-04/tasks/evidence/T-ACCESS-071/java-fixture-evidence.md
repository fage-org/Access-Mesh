# Java 表达与夹具精简证据（2026-10-03）

## 实际处置映射

| 原 case/安排 | 处置与保留信号 |
|---|---|
| OAuth2ClientTtlTest.refreshTokenGrant_usesClientCustomTtl / clientWithoutTtl_fallsBackToDefault86400 | 参数行 3600→3600、null→86400；expiresIn 与实际 JWT 剩余 TTL 都保留。授权码签发路径仍独立；展开仍 3 案例 |
| OAuth2AuthCodeClientBindingTest 同租户/跨租户 B 兑换 A | 请求方 tenant=1/2 两个参数行，授权码仍属 A/tenant=1；反向兑换、合法 scope/audience/tenant 绑定与失败审计保持独立；展开仍 5 案例 |
| RetiredRoleApiContractTest 的 6 个退役路径 | MethodSource 保留各自原 JSON 请求与路径；每行真实 standalone MockMvc 返回 404 且 handler=null。存活 /user-role/view 对照保留；展开仍 7 案例 |
| RetiredGrantApiContractTest 的 5 个退役路径 | 同上，原 save/revoke/children/add-child/remove-child 请求均保留；存活 apply-grant-plan/list 与服务委托对照保留；展开仍 6 案例 |
| Gateway 权限测试的 response/attributes/exchange 副本 | PermissionFilterTestSupport.routedExchange；PermissionFilterTest、PermissionFilterMetricsTest、GatewayInvalidationRaceTest、SnapshotSafetyBoundaryTest 消费。每次独立 headers/attributes/mock；路由、请求 remoteAddr 与 tenant/user/subjectType 显式传入 |
| PG/安全测试的 HMAC 副本 | GatewayTestSignatures.hmac(secret,userId,tenantId,timestamp)；UTF-8、HmacSHA256、userId\|tenantId\|timestamp、小写十六进制保持；不调用生产签名器充当 oracle，不封装身份头/Bearer 或被测动作 |

HMAC 消费者：SecurityMatrixIT、DualTenantSameCodeIsolationPgIT、FileServiceSecurityPgIT、LoginLockTemporaryPgIT、OrgTreeIncludePositionsPgIT、CustomResourceTypeSlicePgIT、ConditionChangeEffectPgIT、NoticeLifecyclePgIT、MemberCandidatesGatePgIT、PositionDisablePruneObservationPgIT、DelegatedDirectoryClosurePgIT。各自 secret 与 user/tenant 仍在调用处，真实签名头与 Bearer 自服务路径没有合并。

## 明确保留的候选

- OAuth2CodeExchangeNegativeTest：redirect、缺 verifier、错误 verifier 后烧码、成功后重放、未知码与正向 PKCE 安排不同；现有 codeData/stubClientAndCode 足够，保留独立失败原因和消费序列。
- OAuth2 客户端工厂保持类内：TTL 类与跨租户关联类的 tenant/audience/TTL 初值不同，不把它们塞进多参数万能工厂。
- QueryStagesTest 已有参数组、可复用 setup 和按查询阶段划分的预算、父要求、条件与禁止下游调用证据；保留 71 展开案例，不为了方法数合并不同拒绝原因。
- CacheEffectiveTtlTest 保留既有可控单调时钟、预算耗尽/上限、读取起点与批量共同预算；没有新增框架。RJsonTest 泛型反序列化与 RWireShapeTest 精确线格式互补，不按小文件配额合并。
- ResourceSortOrderRetiredTest、ServiceConfigSyncOperationCodeRetiredTest、FullSyncResponseContractTest 的字段/操作码/信封契约与路由缺失不同，原样保留。
- Gateway 路由和请求仍就地构造（包括 metrics 的真实 remoteAddr），没有为了再少几行隐藏路由或身份前提。

## 实跑与边界

初始定向基线：`mvn test -pl access-service,common,gateway -Dtest=OAuth2ClientTtlTest,OAuth2AuthCodeClientBindingTest,OAuth2CodeExchangeNegativeTest,QueryStagesTest,CacheEffectiveTtlTest,PermInvalidationSubscriberTest,AuthTokenFilterTest -DskipTestcontainers=true` 通过；日志 `.tmp/testing-simplification/java-candidates-before.log`。

夹具/参数批次：`mvn test -pl common,gateway,access-service -Dtest=OAuth2ClientTtlTest,OAuth2AuthCodeClientBindingTest,OAuth2CodeExchangeNegativeTest,QueryStagesTest,CacheEffectiveTtlTest,PermissionFilterTest,PermissionFilterMetricsTest,GatewayInvalidationRaceTest,SnapshotSafetyBoundaryTest,SecurityMatrixIT,DualTenantSameCodeIsolationPgIT,FileServiceSecurityPgIT,LoginLockTemporaryPgIT,OrgTreeIncludePositionsPgIT,CustomResourceTypeSlicePgIT,ConditionChangeEffectPgIT,NoticeLifecyclePgIT,MemberCandidatesGatePgIT,PositionDisablePruneObservationPgIT,DelegatedDirectoryClosurePgIT`，20 类、168 testcase，零 failure/error/skip（包含 11 个 HMAC 消费类的真实 PG/HTTP）。[逐类摘要](fixture-validation.json)，完整日志 `.tmp/testing-simplification/java-fixtures-after.log`，23:31 完成，wall time 约 152s。

路由/信封批次：`mvn test -pl common,access-service -Dtest=RetiredRoleApiContractTest,RetiredGrantApiContractTest,RJsonTest,RWireShapeTest,ResourceSortOrderRetiredTest,ServiceConfigSyncOperationCodeRetiredTest,FullSyncResponseContractTest -DskipTestcontainers=true`，30 testcase，零 failure/error/skip。[逐类摘要](contract-validation.json)，日志 `java-contracts-after.log`，23:39 完成，56.643s。

本批没有删除行为案例或用 mock 替代真实 PG；参数化不减少展开执行，纯支持函数整理不虚构性能收益。GatewayInvalidationRaceTest 后续由 T-ACCESS-067 同族时序清扫继续验证，最终所有文件版本由 T-ACCESS-076 完整回归覆盖。

## 本地两轨核验

代码轨：参数数据保留原输入，调用/断言未隐藏；新 helper 都有实际消费者、无共享可变状态、无模式开关；所有身份输入和错误/审计/烧码主张仍可区分。文档轨：保留与参数化的范围、跨层证据、真实执行及后续同文件改动责任明确；未改生产功能、ItInfra、并发拓扑或 CI。无 P0–P3 发现，无新增待决项。
