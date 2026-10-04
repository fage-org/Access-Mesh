# 清单定案后续实施验收

范围：T-PERM-105、T-ADMIN-035、T-API-006、T-ACCESS-079、T-ACCESS-080。实施基线 `3368d21143d995a567e9b639c74bb148406220ab`，日期 2026-10-04。当前权威在任务 design_refs；本证据只记录验证事实。

## 代码轨

按生产 diff、直接调用方、DTO 校验、真实持久化和认证链分别核对。P0–P2 未发现未处理缺陷。P3：OAuth2 用户检查 helper 沿用了客户端 Javadoc；ExportJobRunner/compose 留有已删除发头机制描述，已事实性修正。

实证通过项：闲置列无生产/测试消费者；PG 原样 DDL 与自动授权推导/撤销/explain 通过；Clear 沿 UpdateEntity 显式 NULL 且响应重查一致；用户状态查询无正向缓存、故障不降级；服务凭证租户不受自报头影响；Gateway 平台查询与签名用户链保留；同步既有类型/来源/保留键守卫未改变；示例在发起远程查询前拒绝其他租户。

存疑待决项已定案：无仓外旧同步调用，端点/SDK 同批切换；example 固定单租户并拒绝不匹配。没有新增未决项。过度设计可裁剪项：无；复用既有 DTO Clear、领域查询、凭证验证与查询引擎，删除旧注入器/租户 ThreadLocal，无通用 patch 框架或多租户凭证路由。

## 文档轨

独立核对任务 design_refs、契约 §2.7/6.1/19/24/25.7、schema、工程规范 §7.6、服务认证、SDK 架构、示例及接入/部署指南。发现旧双身份接入说明、示例启动步骤与字段清空表述仍沿原实现，已回写现行协议；历史 superseded/归档引用保留用于追溯。关键边界：凭证不开放管理写；OAuth2 配置清空不撤销已有令牌；真实部署密钥轮换尚未执行，仅交付操作步骤。

实证通过项：前端只修改已有条件/配置表单，不新增菜单/OAuth2 页面；字段删除采用重建 schema、不新增迁移；SDK 与服务端端点镜像一致；前端构建、类型与 lint 通过，完整 Vitest 43 文件/492 用例通过。未处理存疑项：无。最终归档链接与完整回归结果见下节。

## 验证记录

全部以下命令于 2026-10-04 执行；计数为展开案例，零 skip，不能以编译成功代替行为通过。

| 命令 | 结果 |
|---|---|
| `mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,AutoGrant*Test,AutoGrant*PgIT,PermissionGrantPlanDomainServiceImplTest` | passed：156，0 失败/错误/跳过 |
| `mvn test -pl access-service -Dtest=ExplicitClearFieldsPgIT,ClearFieldProtocolValidationTest,OAuth2*Test,RequestContextInterceptorTest,ConditionAppServiceImplTest,SystemConfigAppServiceImplTest,MenuWriteAppServiceTest` | passed：198，0 失败/错误/跳过 |
| `mvn test -pl common,perm-sdk/perm-common,perm-sdk/perm-client-spring-boot-starter,gateway,access-service -am -Dtest=M2mCredentialEndpointsTest,FeignCredentialInterceptorTest,PermClientAutoConfigurationTest,M2mCredentialFilterTest,ServiceAuthArbiterTest,RequestContextInterceptorTest,SecurityMatrixIT,SyncEndpointAuthIT -Dsurefire.failIfNoSpecifiedTests=false -DskipTestcontainers=true` | passed：119，所选单测 0 失败/错误/跳过；容器轨未运行 |
| `mvn test -pl example-service,access-service -am -Dtest=BusinessPermCheckerTest,ExampleServiceApplicationTest,CustomResourceTypeSlicePgIT,ServiceCredentialPgIT,ResourceSortOrderRetiredTest -Dsurefire.failIfNoSpecifiedTests=false` | passed：33，0 失败/错误/跳过 |
| `pnpm build`、`pnpm typecheck`、`pnpm lint`（frontend） | passed |
| `pnpm test`（frontend） | passed：492，0 失败 |

反例实跑：OAuth2 旧实现 7 项失败（跨租户、失效用户、查询故障）；清空旧实现检出响应/持久化和新建 Clear 错误；DTO 空白/线格式旧实现 9 项失败；前端旧请求组装 2 项失败；M2M 旧清单 10 项失败；example 旧跨租户查询形态 1 项失败。环境/编译失败不计作反例证明。

契约快照隔离验证：`mvn test -pl access-service -Dtest=PermCommonReqContractTest,ClearFieldProtocolValidationTest -DskipTestcontainers=true` passed，21 项，0 失败/错误/跳过。ResourceUpdateReq.extra 的新增空白约束已同步快照。

最终全量：`mvn test -T 1C` **BUILD SUCCESS**。按完整 Maven 日志执行汇总为 **2521** 项，0 失败、0 错误、0 跳过；其中 access-service 容器轨 477 项、E2E 29 项，heavy 实际执行。另 `mvn clean install -DskipTests` passed，用于清理旧编译产物并刷新全部 SNAPSHOT，不作为行为通过证据。

文档/结构检查：当前变更 Markdown 文件链接无悬空；Compose `config --services` 解析成功；`git diff --check` 通过；退役生产符号搜索无活消费者。工程规范中的轮次词只命中治理规则词表，不是过程记录。完整回归后仅订正 DTO/测试注释并恢复 lint 自动改动的无关组织树格式，未改变已验证的执行逻辑。

## 退役测试处置

| 退役证据 | 依据与接替 |
|---|---|
| FeignInternalSyncInterceptorTest | 旧外部共享密钥注入能力已退出支持；凭证注入/精确端点由 FeignCredentialInterceptorTest、装配由 PermClientAutoConfigurationTest、旧服务通道拒绝由 RequestContextInterceptorTest 接替 |
| FeignTenantHeaderInterceptorTest 与租户 ThreadLocal 生命周期案例 | example 固定单租户已确认；新 BusinessPermCheckerTest 在远程调用前拒绝错误租户，删除无消费者的发头与上下文组件 |

真实环境边界：未重建用户本地数据库、未轮换任何真实密钥、未部署；当前 schema 初始化和业务凭证接线由独立测试数据库验证。
