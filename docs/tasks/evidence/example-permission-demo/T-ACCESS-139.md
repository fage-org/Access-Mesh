# T-ACCESS-139 资源登记与可靠发布验证

日期：2026-10-19。实现基于 `b2dff58a1` 后的工作树。任务已完成，定向与完整回归证据分别记录；不将本阶段验收视为整个权限演示计划交付。

## 实现与范围

- 元数据 Controller → AppService → DomainService → Mapper；可信签名身份与实际目标最终检查，远程调用在本地事务外。报表/字段/范围写入共用数据集行锁，类型用途按租户隔离并保留历史身份，字段批量写入与冻结事件同事务。
- 单实例发送器从数据库恢复事件，冻结时间与版本重放，按当前源版本回写就绪状态；有限重试、最新事件查询与人工重试。旧事件成功不能把新版标为已发布。
- 资源纯增量同步保存已应用事件摘要，精确匹配才返回 SYNC_VERSION_REPLAYED；同版本改内容拒绝，更旧版本仍 STALE，历史缺摘要不猜测成功。FULL/发布代次的原有协议保留。
- 初始化脚本校核服务凭证所属租户、已有类型所有权和完整路由，保留旧示例路径及已有资源编码；独立 EXAMPLE_DEMO 分类与显式 SUB_PERM 配置，复用现役首授根。不创建业务记录或普通读者授权，不改实际部署库。

## 定向行为证据

| 验证 | 结果与边界 |
|---|---|
| 类型用途 PG 负例 | 未加入用途校验时 2 fail，均为应拒绝但写入成功；加入共同守卫后通过。不是编译错误作为 RED |
| `mvn test -pl example-service -am -Dtest=MetadataPersistencePgIT,ExampleServiceApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | 2026-10-19，21 pass / 0 fail / 0 skip；真实事务/身份/HTTP、丢响应重放、迟到响应、STALE 拒绝。日志 `.tmp/T-ACCESS-139/publication.log` |
| 纯增量重放 PG 负例 | 原实现 2 fail：同内容仍 STALE，同版本改内容也被归为 STALE；日志 `.tmp/T-ACCESS-139/sync-replay-red.log` |
| `mvn test -pl access-service -am -Dtest=ResourcePublicationPgIT,ResourceEntitySyncAppServiceTest,ResourcePublicationPolicyTest,ErrorCodeIndexTest,AccessServiceSchemaPostgresTest -Dsurefire.failIfNoSpecifiedTests=false` | 2026-10-19，72 pass / 0 fail / 0 skip；包括原发布协议、权威 schema 与错误码索引。日志 `.tmp/T-ACCESS-139/sync-replay-green.log` |
| `mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false` | 2026-10-19，13 pass / 0 fail / 0 skip；实际三服务链路创建元数据、服务凭证资源发布、正式 SUB_PERM 授权与判定；管理写拒绝服务凭证。日志 `.tmp/T-ACCESS-139/e2e.log` |
| `mvn test -pl e2e -am -Dtest=ExampleBusinessFinalCheckE2EIT,ResourcePublicationPgIT,MetadataPersistencePgIT -Dsurefire.failIfNoSpecifiedTests=false -Dexample.demo.scriptHost=<本机 pwsh 路径>` | 2026-10-19，45 pass / 0 fail / 0 skip；新增删除事件重放、回滚和无摘要记录验证。初始化脚本在隔离 E2E 栈实际执行两次，均成功；第二次复用原身份。日志 `.tmp/T-ACCESS-139/verified-setup.log`、`e2e/target/e2e/permission-demo-setup-0.log` / `-1.log` |

PowerShell 含点的 Maven 属性必须整体引号包裹；最初一次参数拆分导致命令失败，未执行用例，不算 RED。不存在全量依赖安全扫描的新证据。

## 本地双轨核对

代码轨：核对同租户竞争入口、锁后新鲜读取、事务真实回滚、批量字段写入、字段身份重建、报表真实门禁、凭证租户选择、重试预算和源版本条件回写。修正两项 P2：重试期限从失败返回后计算；初始化脚本发送 FULL 前核实凭证属于管理员当前租户。修后定向验证通过。新增机制均有实际 API/调度消费者，未引入 MQ、Redis、发布平台或跨服务任务桥接；无未决定的设计取舍。

文档轨：分别核对契约/schema/错误码索引、类型用途和重放确认的用户决定、任务状态、历史与当前能力边界、脚本前置及初始化范围。已修正仍称 T-ACCESS-136 正在定稿等过期状态；已实现元数据和未实现数据过滤/导入/任务分别表述。人工重试的有界周期、历史缺摘要及空库 schema 边界已写入权威契约。未自动调用外部评审或子代理。

## 收口验证

首次完整 `mvn test -T 1C` 运行 12:59 min，SyncMetadataConcurrencyTest 的局部表缺少资源摘要列而产生 5 error；E2E reactor 因前置模块失败未执行。大规模清理通过（403.8s），接口完整声明与实际 Controller 路由对账通过。原始日志 `.tmp/T-ACCESS-139/full-regression-before.log`。

先隔离运行 SyncMetadataConcurrencyTest，复现同样 5 error（`.tmp/T-ACCESS-139/sync-metadata-isolated-before.log`）。核实后移除通用 upsertIfNewer 中不必要的摘要清理，摘要仅在资源专用路径同事务写入；规范化摘要本身包含版本，进入 FULL/发布代次的范围仍按原协议拒绝无代次请求，不需要让通用主体/角色版本原语依赖资源专用列。未修改并发测试夹具或放宽断言。

`mvn test -pl access-service -am -Dtest=SyncMetadataConcurrencyTest,ResourcePublicationPgIT -Dsurefire.failIfNoSpecifiedTests=false`（显式关闭 Nacos 外部配置/发现）：2026-10-19，24 pass / 0 fail / 0 skip，39.275s；原语并发、严格重放与旧资源发布协议共同通过，日志 `.tmp/T-ACCESS-139/sync-metadata-isolated-after.log`。完整回归待重新确认，任务保持 review。

随后一次完整回归在原并发组已通过后主动中止，日志 `.tmp/T-ACCESS-139/full-regression-interrupted.log`，不记为通过：实体规范核对要求将发布实体改为普通类并补齐范围/发布实体审计字段。调整后 PG、HTTP 与清单定向检查通过；入口权限常量与外部清单已对账。中间一次机械访问器改写造成编译错误，已修正，不计为行为 RED。

`mvn test -pl e2e -am -Dtest=MetadataPersistencePgIT,ExampleBusinessFinalCheckE2EIT -Dsurefire.failIfNoSpecifiedTests=false -Dexample.demo.scriptHost=<本机 pwsh 路径>`：2026-10-19，27 pass / 0 fail / 0 skip，2:25 min；补充范围更新/发布版本、报表停用恢复及脚本连续两次执行。脚本启用状态预检使用已注册的 service-config/list，避免依赖未注册到 Gateway 的 detail 路由；日志 `.tmp/T-ACCESS-139/preflight-final.log`。

最终根 reactor `mvn test -T 1C -Dexample.demo.scriptHost=<本机 pwsh 路径>`：2026-10-19 16:46:51，**3,052 项，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，17:37 min**。按完整日志中的 418 条测试类结果累加，不重复计算模块汇总；未带 skipE2E、skipHeavyIT 或 skipTestcontainers。大规模清理 401.3s，E2E 轨与脚本连续执行均通过，原始日志 `.tmp/T-ACCESS-139/full-regression.log`。运行前确认 9100 无开发服务占用，运行期间没有改动源码。

最终代码轨已核对实体/审计字段与 Mapper 映射、入口常量与完整声明、范围启停与源版本、删除/回滚/重放以及原并发原语；文档轨核对新旧同步调度终态与业务 PUBLISHED 语义分离、脚本的真实 Gateway 路由、任务状态与设计回写。无未处理缺陷或用户待决项。没有重新执行全量依赖漏洞扫描，不把测试通过表述为安全扫描通过。
