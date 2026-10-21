# T-ACCESS-149 分项实施验证

日期：2026-10-22。基线：`ca952ae7a` 后的工作树。仅覆盖报表引用、字段归属和发布 SDK；任务尚未终态。

## 实现与证据

- demo_report 增加 referenced_type_codes，创建缺省空；更新省略保持、空数组清空。引用与报表版本、发布状态及冻结事件同事务持久化。重复、超限、格式错误或字段类型混用在写前拒绝。
- 字段事件保存所属 DEMO_REPORT 的业务键和本地 reportId 作为预期来源身份；fieldId 仍为不可复用字段来源身份。旧事件不在重试时按名称重查归属。
- 发布器复用 PermissionFeignClient 的显式凭证同步重载，删除重复 Feign 接口；请求由冻结 JSON 转成 perm-common 单源 DTO。远端发送保持事务外，重试和迟到回执继续按原版本处理。
- MetadataPersistencePgIT 的旧字段发布用例在补充 owner 断言后明确失败；实现后通过。新增引用用例覆盖创建、缺省更新、清空、CAS 冲突及旧载荷不被重写。
- 准备脚本改为 SYNC/EXCLUSIVE 字段、MANAGED/SHARED 范围与报表整类引用，并以 IAM 管理 API 准备范围实例；不覆盖不兼容的已有声明。纯 PowerShell AST 提取函数、替身 API 验证已有合法声明可重用、不同声明拒绝，修正单元素数组被管道展开的问题；未向真实服务执行该脚本。

## 执行

| 命令 | 结果 |
|---|---|
| `mvn test -pl example-service -Dtest=MetadataPersistencePgIT,ExampleSchemaPgIT` | passed：15 个元数据/发布 PG 用例与 6 个原样 DDL PG 用例，0 failure/error/skip |
| `mvn test -pl example-service -DskipTestcontainers=true` | passed：91 tests，0 failure/error/skip；容器 execution 按开关不运行 |
| `./tools/build.ps1 example-service` | passed：含上游 install，刷新 SNAPSHOT，测试按构建入口跳过 |
| PowerShell Parser + Ensure-DemoType 只读替身检查 | passed：无解析错误，兼容声明通过、不兼容声明拒绝 |

日志保存在本地 `target/task-logs/t149-*.log`。曾使用的 MetadataDtoTest / ExampleSchemaPostgresTest 选择名不存在，不计为验证资产；实际 DDL 证据由上表 ExampleSchemaPgIT 明确执行。没有把该次选择命令作为完整验证依据。

## 代码轨

主代理只读核对 DTO、JSONB SQL、CAS、锁内读取、冻结事件、SDK 凭证调用、重试及脚本。实证通过项：报告/字段身份从持久主键取得，整类引用不接受实例子集，SQL 参数化，引用改变推进报表版本而不伪造字段目录版本，旧载荷不重写，远程调用不进入元数据事务。未发现该已实现分项的未处理确定性 P0–P3 缺陷；未新增第二套发布器、权限求值或目录镜像。

待用户决策：策略保存是否要求远程核验 IAM 实例。现行本地核验保留，未以删除检查隐含采用“允许先保存”方案。旧目录路径不能当作本计划已完成的实现。

## 文档轨

独立核对 example API、业务设计、唯一 DDL、任务及计划状态。报表接口和返回字段已回写；待决策略规则与未退役的旧目录明确分开。初始化脚本目标与现阶段旧策略核验尚不能组成完整新流程，文档已明示未执行、未交付；后续须统一调整目录路径、业务执行版本检查、E2E 夹具及脚本验收。无新增默认业务选择。

## 未执行

通用目录端点/表/发布分支退役、策略目录核验替代、相关执行计划旧 scopeVersions 移除、E2E 夹具及完整联合验收未完成。完整 reactor、E2E/heavy 与真实浏览器验收尚未运行；待决分支明确后继续，不将构建成功当作业务交付。
