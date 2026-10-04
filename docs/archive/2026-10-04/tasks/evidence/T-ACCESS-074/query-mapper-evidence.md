# QueryMapper 契约证据（2026-10-03）

## 映射与边界

QueryMapperXmlContractTest 的原测试方法均保留，检查对象限定为实际 XML statement，诊断包含文件与 statement ID。

| 原检查 | 当前证据 |
|---|---|
| queryXmlsContainOnlySelects | DOM 映射节点，不从注释误提取标签 |
| everySelectCarriesTenantId | 每个 statement 的实际文本，空白归一后匹配显式参数绑定 |
| noSelectUsesLimit | 每个 statement 的完整文本，无 1200 字符窗口、无相邻语句借用 |
| projectionsUseExplicitColumns | 完整 statement、大小写/空白归一，含别名星号保护 |
| userRoleProjectionValidityWindow | 指定 statement 的有效期与 LEFT JOIN 条件，无 1600 字符截断 |
| batchInQueriesGuardEmptyCollections | 各 foreach 所属 mapped statement 的真实 MyBatis BoundSql，空/null 集合必须拒绝查询，非空集合必须绑定 IN 参数；不借同文件其他语句守卫 |

检索既有真实 PG 测试发现，QueryReadSupportPgIT 属引擎装载，LocalProjectionBatchSqlIT 属投影写 SQL；都不直接覆盖这些 QueryMapper 的完整边界。新增 QueryMapperPgIT 使用原 ItInfra + Spring/MyBatis/真实 PG，未用手工查询替代 mapper：

- roleWindowIncludesExactBoundsAndNull_butExcludesForeignDeletedAndOutsideRows：精确起止等值、NULL 窗口、未来/过期、异租户、软删。
- leftJoinsPreserveRelations_whenTargetsAreMissingForeignOrDeleted：target 和 relation 分别覆盖本租户停用角色、异租户、软删、缺失；关系行保留，不能泄漏角色字段。
- batchCollectionsAndOrganizationQueriesRespectTenantAndDeletion：组织简要/用户组织集合的非空正向、空/null 拒绝，菜单/默认树/递归子树的租户与软删隔离，递归入口不能借外租户根节点。

每个案例使用不同租户，原样 schema、生产 XML 和 TypeHandler 保持。未引入 SQL 解析平台或新依赖；MyBatis 解析器本就是项目依赖。同步修正能力结构设计中仍声称分页 LIMIT 的旧描述，现行组合查询已非分页。

## 实跑

- 原结构测试：`mvn test -pl access-service -Dtest=QueryMapperXmlContractTest -DskipTestcontainers=true`，6 testcase 通过（容器 execution 明确不运行），5.807s，日志 `.tmp/testing-simplification/query-xml-before.log`。
- 修改后结构/PG：`mvn test -pl access-service -Dtest=QueryMapperXmlContractTest,QueryMapperPgIT`，6 + 3 testcase，零 failure/error/skip；首次 48.856s，恢复 XML 后 30.111s。
- 最终配置核对：`mvn test -pl access-service,gateway -Dtest=QueryMapperXmlContractTest,QueryMapperPgIT,PermInvalidationSubscriberTest`。QueryMapper 仍为 6 + 3 testcase、零失败/错误/跳过；access 模块 44.536s，22:41 完成。该次同时采集另一任务的 Gateway 基线，不将 reactor 总时间当作本任务耗时。完整日志 `.tmp/testing-simplification/query-final-reconnect-before.log`。

## 有效错误变体

[机器结果](query-mutations.json)：

- 长 XML 注释含 `LIMIT SELECT *`、换行分隔有效期运算符：全部结构测试通过。
- 将角色组织查询的空集合条件替换为注释：结构断言定位 `UserRoleQueryMapper.xml#selectOrgBriefsByIds`；PG 空集合实际返回 root/child，目标空集合断言失败。
- 移除 valid_to 过滤：结构窗口断言失败；PG 过期角色被返回，目标集合断言失败。
- 移除菜单 tenant 条件：结构断言定位 `UserMenuQueryMapper.xml#selectMenus`；PG 返回 foreign 菜单，目标租户断言失败。

每个错误变体分别运行结构和 PG 方法，均零 error/skip。初次把两层放一条命令时，单测失败使后续容器 execution 未运行；那份旧报告未计入有效结果，已改独立命令并核对每次方法发现数。临时生产 XML 在 finally 中逐字恢复，与基线 HEAD 字节相同。

## 本地两轨核验

代码轨：每条映射语句独立定位，动态集合用真实 MyBatis 展开，PG 同时证明结果边界；无 mock/文本互相替代、无生产 SQL 改动。文档轨：当前非分页语义、结构/行为分工、任务依赖与支持范围一致。无 P0–P3 发现，无新增待决取舍。

本批增加真实 PG 缺口证据，不以减少执行数或上下文数宣称提速。全面回归由 T-ACCESS-076 承担。
