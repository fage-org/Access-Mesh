# 实施基线（2026-10-03）

- HEAD：`71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`；起始工作树只有本计划的设计、任务和索引文档改动，无源码改动。
- [机器快照](test-inventory-2026-10-03.json)中全部 326 个测试文件逐一重算 SHA-256，均匹配；原静态扫描无需重做。统计含声明与词法计数，不等于参数/嵌套展开执行数。
- 环境：Windows，Azul Java 21.0.12.1，Maven 3.9.16，Docker Server 29.7.2，pnpm 11.5.0。容器可用；首批是本会话第一次运行，但不能据此声称物理冷启动（ItInfra 可复用容器）。
- 首批命令：`mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,AccessServiceSchemaH2Test,PermissionCenterIntegrationTest`。2026-10-03 22:01 完成，wall time 30.554s；PG schema 14、H2 schema 16、独立连接烟测 1 个 testcase，全部零 failure/error/skip。完整日志在 `.tmp/testing-simplification/schema-before.log`，报告副本在同目录 `before-reports`。计数逐类解析实际 testcase，未以最后一条 execution 汇总代替全部执行。
- 其他候选当前实跑基线为 **not run**；每批删除前运行原证据并保留对应报告，最后 T-ACCESS-076 做完整后端与前端回归。旧报告只作成本线索，不能作为本次通过证据。

## 候选与映射边界

候选路径沿[全仓评估](whole-suite-assessment.md)与各任务范围，未获得广泛删测许可。每批只记录实际改变的 case/assert、接替位置/退出依据、实跑与错误反例；未改候选保留，不创建永久总账。

| 批次 | 原证据与最小映射起点 | 当前准入 |
|---|---|---|
| 072 | Schema H2 的方法/内嵌位值映射到原样 PG；typed/code、typed_bit、非空类型负例分别命中目标约束；任务唯一键合法插入移出预期异常块 | PG-only 已确认，先保证负例可信再迁证据；独立烟测仍需核对实际配置与消费者 |
| 073 | AutoGrantMigrationPgIT/ApiAuthorizationRetirementPgIT 的升级专属断言退出；ManifestMigrationPublishPgIT 当前发布/no-seed 另行保留 | 两条支持分别退出，删除须关闭所有活跃引用；T-ACCESS-066/Q-015 的迁移 COMMENT 要求随资产退出同步收窄 |
| 071 | OAuth2 客户端安排、QueryStages 同结构拒绝、common TTL 数据、Gateway exchange/PG 请求安排 | 同结构才抽取，身份与核心断言保持就地；逐候选确定真正重复面 |
| 074 | noSelectUsesLimit、userRoleProjectionValidityWindow、batchInQueriesGuardEmptyCollections 文本窗口转 statement 边界 | PG 行为仍独立保留，结构检查不实现 SQL 解析器 |
| 075 | 三条 E2E 的启动/请求/清理重复 | 拓扑、独立生命周期与业务断言保持；随 reactor 验证 |
| 077/078 | 重连 complete/error/stop 时序；AdminXmlPaginationPgIT 真实 Mapper 单类试点 | 原默认间隔不变；上下文试点按实际证据决定保留，不强推全仓 |
| FE063 | grant-plan/grant-keys/defer 等能力内安排与同结构变体 | 消费者语义、双端 golden 与碰撞反例保持 |
| 070/076 | 原测试规则与附件技能；独立样题、真实任务试用与最终回归 | 规则就近消歧、双副本，真实任务效果不得用样题冒充 |

## 支持范围

2026-10-03 用户分别确认退出自动授权旧库迁移、API 授权旧库迁移和配套回滚；当前设计 §3、自动授权设计 §10、契约 §25.1 已记录，T-ACCESS-073 关闭资产引用。当前 manifest/no-seed、当前 schema 和脏数据拒启保持。

同时确认 H2 schema 转 PG-only：非容器轨不再验证 DDL，DDL 变更必须定向真实 PG 实跑；Docker 不可用的 skip 不等于通过。T-ACCESS-072 删除前迁完有效主张。以上不授权实际部署数据删除或重建。

## 本地核验

代码轨：起始源码无 diff，全部快照哈希匹配；首批报告确实发现并执行目标类，零 skip。支持退出不改变当前在线鉴权语义。文档轨：支持决定分别记录，未混同两条升级链；直接依赖可独立推进，后续卡不以旧报告验收。无新增存疑取舍，无需额外机制。
