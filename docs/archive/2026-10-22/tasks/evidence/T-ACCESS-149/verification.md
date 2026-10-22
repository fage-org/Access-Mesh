# T-ACCESS-149 实施验证

日期：2026-10-22。实现基线：`bcba8eb40` 及本任务后续通用目录退役改动。联合结果见 [T-ACCESS-150](../T-ACCESS-150/verification.md)。

## 行为证据

- 报表保存整类引用；更新省略保持、空数组清空，引用与版本及冻结事件同事务持久化。字段事件携带所属报表业务键和不可复用 reportId；重试不按名称改挂归属。发布复用 perm-client SDK，远端调用保持在本地事务外。
- 通用目录的 Controller、DTO、AppService、DomainService、实体、Mapper、DDL 和发布分支已退役；执行计划不再保存 scopeVersions。实际 HTTP 映射、接口清单和原样 DDL 测试断言旧入口及表不存在。
- 策略可在 IAM 实例准备前保存；保留管理门禁、结构和用途校验、版本 CAS。DemoPolicyPgIT 经 AppService 保存未知实例映射成功，并确认没有远程目录调用。运行时 INSTANCE 返回项缺少业务映射仍报配置错误，ALL 保持全数据集语义。
- 准备脚本通过 IAM 管理 API 建通用目录，业务服务发布报表引用和专属字段；拒绝覆盖不兼容的类型声明。独立测试环境以真实三服务连续执行脚本两次成功，证明准备及重复执行路径。

## 测试资产调整

| 原证据 | 当前证据及理由 |
|---|---|
| 本地范围目录发布/版本用例 | 随目录及发布功能删除；IAM 实例维护与权限变化由资源领域 PG 和 E2E 验证 |
| 字段/范围用途碰撞、历史占用、并发写入 | 迁至报表 referencedTypeCodes 与策略类型占用；仍用真实数据库和事务竞争验证 |
| 策略依赖本地目录存在/启用/已发布 | 按已确认语义替换为可提前保存映射；保留管理门禁、CAS、事务回滚、精度和类型用途测试 |
| 数据执行 scopeVersions 断言 | 随副本移除；读计划不能用于写入、报表/字段目录/策略版本变化拒绝等现役安全断言保留 |

## 执行

- `mvn test -pl example-service -Dtest=DemoPolicyPgIT,DemoDataPgIT,MetadataPersistencePgIT,ExampleSchemaPgIT,ExampleServiceApplicationTest,DemoInterfaceManifestTest`：通过，覆盖策略、SQL、冻结发布、原样 DDL 和真实 HTTP 路由。
- `mvn test -pl access-service,example-service -DskipTestcontainers=true`：example 91 项，access 1991 项，0 failure/error/skip；此命令按开关不执行容器轨。
- `mvn clean install -DskipTests`：所有模块构建并安装成功，刷新上游 SNAPSHOT；不作为测试通过证据。
- 前期 MetadataPersistencePgIT 的 owner 断言在旧发布请求上失败；补充归属后通过。当前元数据 PG 资产为 14 项，原样 DDL 6 项、策略 PG 6 项；最终执行统计归联合验收。

完整日志保存在本地 `.git/task-logs/`；根 clean 前的 `target/task-logs` 已清理，不再作为可访问证据。测试源码和本记录保留行为及命令，完整回归见联合验收。

## 本地评审

代码轨：主代理只读核对类型历史占用、参数化 SQL、冻结身份、CAS、SDK 凭证、事务外发送、实际执行权限及退役调用方；未发现未处理的确定性缺陷，没有新增目录副本、求值器或发布框架。

文档轨：独立核对 example 契约、设计、唯一 DDL、接口清单、任务和脚本。通用目录由 IAM 管理、业务映射可以提前保存、INSTANCE 缺映射拒绝、ALL 不缩窄的表述一致。待用户决策项：无。最终状态和链接由 T-ACCESS-150 一并收口。
