# T-ACCESS-146 验证证据

范围：资源关系存储、类型用途守卫、single/FULL 原子发布、来源身份、删除与事务串行边界。完整查询/授权/页面闭环不属于本卡交付。

| 日期 | 命令 | 结果与证明范围 |
|---|---|---|
| 2026-10-21 | `mvn install -pl perm-sdk/perm-common -am -DskipTests` | 成功刷新上游 SNAPSHOT；此命令不计为测试通过 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT,ResourcePublicationPgIT,ResourceTypeOwnershipGuardTest,ResourcePublicationNormalizerTest,AccessServiceSchemaPostgresTest` | 单测 34、PG 60，零失败/错误/跳过；其中原样 DDL 29 项、既有发布协议 PG 19 项 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT,AccessServiceArchitectureTest,QueryBoundaryArchitectureTest` | 架构 13、关系 PG 12，零失败/错误/跳过；随后增加的领域场景由下列命令覆盖 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT,TypeDefinitionAppServiceImplTest,ResourceTypeOwnershipGuardTest` | 单测 88、关系 PG 14，零失败/错误/跳过；包括真实提交闸门下引用与用途修改、字段发布与主资源删除 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT` | PG 15，零失败/错误/跳过；补齐专属树跨归属拒绝与同归属允许，最终领域用例实际经过生产同步入口 |

完整本地日志保存在 `target/task-logs/T-ACCESS-146-*.log`，长期结论以本文件的命令和结果及对应提交为据。SQL、声明与发布行为先由缺失能力触发失败，再验证实现；容器不可用导致的跳过未计入上表通过证据。

## 代码轨

- 实证通过：原样 PG 约束保证专属基数、整类引用去重、目标二选一、租户身份与来源身份防复用；业务模式及软删有效性由锁内新鲜读守卫，未声称 CHECK 可以跨表核模式。
- 实证通过：原子写入失败回滚资源、关系及同步元数据；依赖缺失不消费版本；同码重建后已发布与从未到达的旧字段均被来源身份拒绝。
- 实证通过：FULL 保持逐项失败与缺失清理范围，失败项原事实不被修改；删除主资源回收专属实例及其发布确认，保留通用实例和他方引用。
- 实证通过：采用既有 RESOURCE_ENTITY 锁与数据库约束，真实事务并发验证；FULL 关系预校验批量装载，应用循环不逐项读关系。没有新引擎、缓存、队列或锁框架。
- 实现收敛：单条与批量发布统一走预校验/应用路径，原型单条关系写 helper 已移除；回滚、幂等、并发与负向证据转由生产同步入口执行。管理面专属资源仍按 SYNC 只读，树边限制由合法同步写者验证。
- 存疑待用户决策：无。授权清理、准入及运行时失效的交接继续由 T-ACCESS-148 承接。

## 文档轨

- 已核对：通用整类引用、部分实例授权、解绑精确清理、ALL、类型删除及用途限制与最新用户决定一致。
- 已核对：来源身份协议、唯一 schema、错误索引及写侧已实现状态对应代码；候选/SDK 与授权运行时仍明确未交付。
- 已核对：服务停用沿现役门禁，不隐式删除业务事实；租户没有删除端点，本卡未增加清理平台；系统种子不默认启用关联模式。
- 未发现阻断项。计划整体回归（E2E/heavy）留到 T-ACCESS-150，当前未执行完整回归。
