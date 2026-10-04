# Schema 精简证据（2026-10-03）

## 接替映射

原文件固定于 `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236`，前置运行见 [069 基线](../T-ACCESS-069/implementation-baseline.md)。H2 有效主张全部进入原样 PG，未改 schema 语义。

| 原 H2 case/assert | AccessServiceSchemaPostgresTest 接替 |
|---|---|
| shouldHaveCanonicalTables / shouldNotHaveSysSyncTaskTable | 同名现存断言 |
| shouldHaveAllSeedRows | 同名现存断言，表及种子计数不变 |
| shouldHaveNamespacedSeedKeys | 同名新增断言，逐 key 允许前缀 |
| shouldHaveAllRuntimeRequiredOperations / shouldNotHaveRetiredTypeCodesOrValues | 同名现存断言，逐操作及退役码/值集合核对 |
| shouldHaveAuthoritativeTypeValues | 同名迁入，全部 type_key/type_code/type_value 对保留 |
| shouldHaveCrudOperationsForEveryStaticResourceType | shouldHaveExactCrudPerType 保留数量；shouldHaveAuthoritativeOperationBits 独立保留 MENU/ORG/USER 位与掩码 |
| shouldHaveOwnerServiceCodeColumns | 同名现存断言 |
| shouldHaveSystemConfigMergedColumns / shouldHaveOperationLogMergedColumns | 同名迁入，字段存在/退役列缺失/target_id 字符类型与 256 长度保持 |
| shouldEnforceSystemConfigUniqueKey | 同名，SQLSTATE=23505 + uk_system_config |
| shouldEnforceDomainConfigUniqueKey | 同名迁入，合法首行在 assertThrows 外；23505 + uk_domain_config |
| shouldEnforceRoleResourcePermissionCheck | 同名，合法 scopeAll 对照 + 23514 + 指定 CHECK |
| shouldEnforceConditionCanGrantCheck | 同名迁入，先成功写 condition_id=30/can_grant=false，再 UPDATE 为 true 命中指定 CHECK |
| shouldHaveTaskExecutionTable | 同名保留表与 execution_key/lease_owner/lease_until/attempt_count；合法首行在 assertThrows 外，23505 + uk_task_execution |

PG 原有 JSONB String 绑定、复杂索引和表达式索引实际 EXPLAIN 保留。操作唯一负例每次使用保存点恢复，重复 code 改用不同 bit，分别锁定 typed/code、typed_bit、resource_type 必填 CHECK；不再接受前一个异常造成的 25P02。

## 独立连接烟测接替

PermissionCenterIntegrationTest 仅验证 `JdbcTemplate select 1`、`StringRedisTemplate set/get` 与连接工厂非空。AccessBootstrapPgIT 同样使用 `@SpringBootTest`、test profile、`ItInfra.register(..., class)`：同一 PG 驱动/凭证/池配置与 Redis host/port/password/database 通道；类库与 Redis 索引仍按类隔离。

bootstrap 的附加属性只禁用 Nacos/调度/启动 runner、明确驼峰映射与日志级别，不改变 PG/Redis 连接装配。其真实 initializer 表操作和 bootstrapAdminCanLoginWithRealCaptcha（真实验证码写 Redis 后读取并登录）已实际运行；JdbcTemplate 与 StringRedisTemplate 均未 Mock。Spy 的故障注入属于另有用例，不替换此处连接行为。连接工厂非空被实际读写蕴含，因此删除烟测，不搬非空断言。

## 执行与错误反例

- 原始运行：PG 14 + H2 16 + 烟测 1，零 failure/error/skip，30.554s；仅作为本批起点。
- 迁入后：`mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,AccessBootstrapPgIT`，21 + 18 testcase，零 failure/error/skip，53.211s。
- 删除 H2/烟测、同步 DDL 注释后同命令：21 + 18 testcase，零 failure/error/skip，56.116s（22:14）；日志 `.tmp/testing-simplification/schema-final.log`。不同选择器不作 wall time 收益比较。
- 错误变体：完成原样 DDL 初始化后，临时追加 DROP INDEX / DROP CONSTRAINT，分别验证操作 code、bit、非空类型，以及 domain/system/task 唯一键、scopeAll/conditionCanGrant CHECK。[结果](constraint-mutations.json)记录 8 个目标断言均因未抛出预期异常变红，零 error/skip。实际命令为 `mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest#<目标方法>`，其他约束组以 `+` 连接方法。无新增永久变异机制。
- 初次在种子执行前直接移除索引导致 ON CONFLICT 初始化错误，已剔除，未作为有效反例。改在初始化后删除约束后才获得上述有效失败。临时 DDL 在 finally 中逐字恢复，最终 schema 仅有指向 PG 测试的注释改动。

本批测试文件从 3 减为 1，源码 911→490 行（净减 421，含新增 helper/断言）；PG schema 展开由 14 增为 21。未改 bootstrap 测试，也未宣称容器个数或全仓耗时按文件数同比下降。完整后端/前端收口属于 T-ACCESS-076。

## 本地两轨核验

代码轨：逐项 H2→PG 映射完整；事务恢复在 finally，合法准备在预期异常外，具体 SQLSTATE 与约束名防止其他失败假绿；原 PG 独有信号保留，连接由现存业务路径证明。无生产行为改动，无新框架或共享上下文。

文档轨：PG-only 已明确确认，日常无 Docker/非容器轨反馈范围写入规则与设计；PG Javadoc 和 schema 注释无 H2 兜底残留。搜索被删类名只剩任务/基线中的历史比较，不存在可执行消费者。无 P0–P3 发现，无额外待决取舍。
