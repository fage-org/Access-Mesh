# T-ACCESS-137 持久化基础验证

日期：2026-10-19。实现基于 `2f4004c80` 后的工作树；任务已完成，定向验证与收口全量结果分别记录如下。

## 实现边界

- 原未消费 BI schema 替换为 example 独立业务 DDL，包含固定数据集、报表/字段、范围元数据、策略版本、导入批次/记录、发布事实及任务/执行状态。初始化无 IAM 表或隐式业务样本，不提供清库/升级框架。
- 字段 resourceCode 由主键生成，主键/资源身份不因软删同名重建而复用。有效业务键使用租户维度唯一约束；报表/字段 Mapper 的条件插入和读取校验同租户父对象。
- PostgreSQL/MyBatis-Flex、UTC TypeHandler、显式租户 Mapper 和启动基础表检查已接入。新业务 API、发布器、策略 SQL 执行器及任务调度仍由后续任务完成；原 report 探针继续使用内存数据。
- 共享 `PgTestInfra` / `TestForkSlot` 只作为 access/example 测试源码编译。access 的 ItInfra 门面保留 Redis/租户模板职责，example 不加载这些能力；未新增生产或发布型测试支持依赖。
- 原应用启动测试迁入 PG 轨，保留身份签名、信封、未知路由和管理端口断言；直接 JDBC 回滚案例由实际 Mapper 事务回滚及重试提交案例接替，不用连接级回滚冒充 Spring/Flex 事务。

## 定向验证

| 命令 / 验证 | 结果 | 范围 |
|---|---|---|
| `mvn test -pl access-service -am -Dtest=AccessServiceSchemaPostgresTest -Dsurefire.failIfNoSpecifiedTests=false` | 抽取前 25 pass；抽取后 25 pass；0 fail / 0 skip | 原 access DDL/模板入口仍可运行 |
| `mvn test -pl example-service -am -Dtest=ExampleSchemaPgIT -Dsurefire.failIfNoSpecifiedTests=false` | 旧 DDL 7 error（缺少目标表/列），新 DDL 7 pass / 0 skip | 原样 PG schema；后续精简了被实际 Mapper 事务测试接替的直接 JDBC 回滚案例 |
| `mvn test -pl example-service -am -Dtest=MetadataPersistencePgIT,ExampleSchemaPgIT -Dsurefire.failIfNoSpecifiedTests=false` | 新增事务/空值约束负例先 2 fail；补齐后通过 | 空 SQL 映射不能冒充预期回滚；CHECK 显式拒绝缺失 task_version/result_row_count |
| `mvn clean test -pl e2e -am -Dtest=ExampleProtectedApiE2EIT,ExampleBusinessFinalCheckE2EIT,MetadataPersistencePgIT,ExampleSchemaPgIT,ExampleServiceApplicationTest,AccessServiceSchemaPostgresTest -Dsurefire.failIfNoSpecifiedTests=false` | **65 pass / 0 fail / 0 skip**，4:24 min | access schema 25；example Mapper 4、schema 6、启动 6；现有 E2E 24 |
| `mvn package -pl example-service -am -DskipTests` | passed，26.743s | 生成可运行 jar；不是测试通过证据 |
| jar 内容检查 | passed | 含 PG 驱动；不含 testsupport、Testcontainers 或 access-service 依赖 |

中间一次工具脚本语法错误使 Mapper 文件未写入并导致 testCompile 失败，已修正；该失败不计为 RED 行为证据。另一次 JDBC 夹具被误认为参与 Flex 事务导致重复数据/会话缓存干扰，已独立清理夹具，真正回滚使用受管 Mapper 事务并从数据库独立核对。

## 编译环境问题与处置

E2E 初次启动 access-service 因旧 `PermViewFilter.class` 的空 Code 属性失败；clean 后发现 `SyncResourcesResult` 的无参构造方法也为空。同一源码、同一 JDK/Lombok 在隔离 javac 输出目录生成正常代码；本机正在运行 VS Code Java 1.57.0 / JDT 语言服务，与[上游问题 #4521](https://github.com/redhat-developer/vscode-java/issues/4521)一致。

用户明确同意本项目关闭 Java 自动构建。已在被 Git 忽略的 `.vscode/settings.json` 设置 `java.autobuild.enabled=false`，保留语言服务与其他设置，不提交给团队、不卸载插件。随后 clean 构建恢复正常构造方法，真实 E2E 全部通过；没有为规避该问题修改 access 业务源码或升级依赖。

## 初始化与正式包

在经标签及 POSTGRES_DB 校验的 Testcontainers PostgreSQL 中，确认 example_db/example_app 原先不存在后执行 `pwsh -File tools/init-example-db.ps1 -Container <测试容器>`。初始化成功、表由 example_app 所有，该角色没有 superuser/createdb/createrole/replication/bypassrls 权限。再次初始化非空库被拒绝，原表保留。E2E 的独立 example 账号还检查不能读取 IAM 的 sys_user 表。

正式包 SHA-256：`0F0B3415B50D5F2BD104D3DD3B7A9CF2B6872F2CD04AD517486FD3CAF3C99590`。向该独立库显式写入一条测试报表元数据后，两次启动这个 jar，均健康 UP 且原元数据保留；服务不执行隐式建表/重灌。测试进程通过隐藏窗口启动，结束后仅停止所创建进程；使用专属数据库标记核对后清理测试库/角色，临时密码文件已删除。没有修改用户的实际业务库或根 `.env`。

这证明正式包连接、启动和数据库事实的重启保留，不表示新业务 HTTP 写入接口已经交付。新接口权限、CSV/动态 SQL 与异步结果行为留在 139/140/138/141/143 的验收范围。

## 本地双轨核对

主代理先技术轨、后文档轨独立只读核对；未调用子代理或外评。

技术轨 P2：初始化工具将可逆密码编码放入 SQL；以临时高权限角色触发拒绝，实测服务端错误日志含该编码。已改用 psql 内置 `\password`，由客户端生成 SCRAM 校验值，角色创建/设密在同一事务；不编写自有密码算法。实测含引号、反斜线及 Unicode 的原密码可通过 SCRAM、错误密码拒绝；高权限既有角色和非空库拒绝，错误日志无明文/可逆编码；改用另一输入密码重试也不改变已有角色密码。全部探针限已核对的测试容器，测试角色/库已清理。

技术轨通过项：Mapper 租户与父归属条件、实际事务回滚与重试、字段身份重建、执行状态空值约束、PG 命名空间/fork 锁隔离、原 Redis 门面行为及生产 jar 依赖边界。无剩余技术缺陷或用户待决项。

文档轨通过项：现役内存探针与新增数据库基础分开描述；初始化、环境变量、Compose 前置及备份范围一致；新旧测试证据有接替位置，单测/PG/E2E 轨道及跳过开关同步；任务状态与验收证据一致、设计引用有效。初始化日志声明已随上述 P2 修正，未用客户端不打印掩盖服务端日志泄漏。

减法核对：保留有实际消费者的共享测试源码，未发布额外模块；未新增业务 API、缓存、用户目录、通用文件或迁移平台。无需要用户另行裁剪的无调用方机制。

`mvn test -pl example-service -am -DskipTestcontainers=true -DskipE2E=true`：2026-10-19 passed，41.382s；example 默认轨 47 pass / 0 fail，PG execution 按请求跳过（不记为通过）。

## 完整回归与结论

2026-10-19 执行根 reactor `mvn test -T 1C`，未带 skipE2E、skipHeavyIT 或 skipTestcontainers；**3,035 项，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，17:18 min (Wall Clock)**。按完整日志中的 417 条测试类汇总计数，不重复累加模块汇总。原始日志位于本地忽略目录 `.tmp/T-ACCESS-137/full-regression.log`。

其中 ResourcePublicationHeavyPgIT 的 65,540 条资源清理通过（401.1s）；E2E 为基础角色授权 9 项、example 最终权限检查 12 项、接口保护 12 项及进程支持单测 2 项，全部通过。运行前确认 9100 无开发服务占用，Maven 运行期间未修改源码。

持久化基础、共享测试支持及既有链路回归验收完成。新业务 API 和发布器仍由 T-ACCESS-139 等后续任务交付；本次未运行新的全量依赖安全扫描，不将测试通过表述为漏洞扫描通过。已批准的 Docker 测试依赖例外仍保留原范围与限制。
