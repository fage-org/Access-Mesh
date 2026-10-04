# Mapper 上下文试点（2026-10-03）

结论：采用 AdminXmlPaginationPgIT 单类最小上下文。保留原 8 个案例及所有夹具/断言，不扩展到带领域、缓存、鉴权编排的其他类。

## 装配与等价范围

使用 SpringJUnitConfig + ConfigDataApplicationContextInitializer 加载现有配置，类内 TestConfiguration 仅导入数据源、JdbcTemplate、事务与 MyBatis-Flex 自动配置，以及生产 MybatisFlexTenantConfig/TypeHandlerConfig；MapperScan 覆盖实际消费者。保留 ItInfra、原 class 名/独立数据库、DynamicPropertySource、testcontainers 标签与生产 XML。类后关闭上下文，没有共享上下文或静态业务夹具。

临时只读观察器记录装配后即删除。完整上下文记录位于 `.tmp/testing-simplification/mapper-ab-full.log` 的 MAPPER_PILOT 行；最小上下文见[装配快照](mapper-slice-context.txt)。

| 项目 | 完整上下文 | 最小上下文 |
|---|---|---|
| Bean 数 | 695 | 73 |
| Configuration | FlexConfiguration | 相同 |
| DataSource | HikariDataSource | 相同 |
| map-underscore-to-camel-case | true | true |
| LocalDateTime TypeHandler | TimestamptzLocalDateTimeTypeHandler | 相同 |
| MyBatis Interceptor 列表 | 空 | 相同 |
| mapped statement 名集合 | [固定快照](mapped-statements.txt) | 逐字相同，[哈希对照](mapping-comparison.json) |

最初 `SpringBootTest + TestConfiguration` 实际仍补入主应用，不能作为最小上下文证据；改用显式 SpringJUnitConfig 后才取得上述结果。TestConfiguration 不进入其他整应用测试的组件扫描，避免试点配置串入业务上下文。

## 同机 A/B 与验证

环境沿 T-ACCESS-069：Windows/Java 21.0.12.1/Maven 3.9.16，复用相同 ItInfra 容器配置，每次 fork 仍独立重建本类数据库。A/B 命令均为 `mvn test -pl access-service -Dtest=AdminXmlPaginationPgIT`，临时观察器相同，原测试体与数据不变，均实跑 8 案例、零 failure/error/skip。

| 形态 | suite 时间 | Maven wall time |
|---|---:|---:|
| 完整应用 | 18.274s | 47.583s |
| 最小上下文 | 8.545s | 37.759s |

[机器摘要](mapper-ab.json)；完整日志 `.tmp/testing-simplification/mapper-ab-full.log` / `mapper-ab-slice.log`。两次都含改测试源后的编译，收益仅代表本机单类试点，不等同完整 reactor 加速或容器数量减少。ItInfra 仍按原规则提供 PG/Redis。

- 错误 XML：临时将 OAuth2 分页 OFFSET 参数替换为 0，仅跑 shouldPageOauth2ClientsWithFilterAndCount。第二页重复返回 pgt-a1/a2，原断言要求 pgt-a3/a4，目标断言失败，零 error/skip，见[错误反例](mapper-offset-mutation.json)。XML 与最终测试源码均在 finally 中恢复，无永久探针。
- 同 JVM 邻类：`mvn test -pl access-service -Dtest=AdminXmlPaginationPgIT,QueryMapperPgIT,LocalProjectionBatchSqlIT -Dit.forkCount=1`，8 + 3 + 3 = 14 案例、零失败/错误/跳过，58.595s，23:05 完成；日志 `.tmp/testing-simplification/mapper-neighbors.log`。临时使用已有单 fork 参数强制验证同 JVM，POM 默认双 fork 及线程隔离规则未改。
- 同 JVM 顺序为整应用 QueryMapper/LocalProjection 与轻量 Mapper 共跑，轻量类仍执行完整数据/分页/排序/count；测试通过不是跳过大数据或少跑方法。

## 本地两轨核验

代码轨：原用例与 SQL 不变，数据源/MyBatis/TypeHandler/插件/映射集合一致，错误 OFFSET 仍变红；邻类验证无方言或静态配置串扰；没有通用基类、配置框架或额外依赖。文档轨：只采纳此单类试点，应用装配与安全接线证据保留，测量与隔离范围明确；无 P0–P3 发现，无新增待决取舍。完整默认双 fork 回归由 T-ACCESS-076 承担。
