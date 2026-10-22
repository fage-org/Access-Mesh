> 本文保留分项验证时点的命令和结果；整体终态与最终完整回归见 [T-ACCESS-150](../T-ACCESS-150/verification.md)。早期 target/task-logs 已随根 clean 清理，不能作为当前可访问文件；最终日志在本地 .git/task-logs。

# T-ACCESS-147 验证证据

范围：资源候选/关系详情、类型候选、树形归属显示数据、SDK 发布/管理 DTO 接线与身份边界。运行时判定和页面组件接入由下游任务负责。

| 日期 | 命令 | 结果 |
|---|---|---|
| 2026-10-22 | `mvn install -pl perm-sdk/perm-client-spring-boot-starter -am -DskipTests` | 构建并刷新公共 DTO 与 client starter；跳过测试不计为行为证明 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT,ResourceManageAppServiceImplTest,PermissionGrantAppServiceImplTest` | 单测 55、PG 17，零失败/错误/跳过 |
| 2026-10-22 | `mvn test -pl perm-sdk/perm-client-spring-boot-starter -Dtest=PermissionFeignClientContractTest` | 8 项通过；路径与 POST/JSON 封闭清单、显式凭证头及归属身份/整类引用序列化 |
| 2026-10-22 | `mvn test -pl access-service -Dtest=ResourceAssociationDomainPgIT,HttpApiPathSnapshotTest,PermCommonReqContractTest,PermissionGrantAppServiceImplTest,AccessServiceArchitectureTest,QueryBoundaryArchitectureTest` | 单测/架构/契约 34、PG 18，零失败/错误/跳过 |
| 2026-10-22 | `pnpm -C frontend contracts:check` | 49 组字段对账通过 |
| 2026-10-22 | `pnpm -C frontend typecheck` | TypeScript 与 Vue 类型检查通过 |
| 2026-10-22 | `mvn test -pl access-service '-Dtest=ResourceAssociationDomainPgIT#shouldFilterCandidateVisibilityBeforePaginationAndDistinguishMissingReference'` | 1 项 PG 通过，零跳过；区分缺引用、无可见权限、空目录及分页超限 |

当时完整日志写入 target/task-logs；源码移动后已执行 access-service clean 构建，未依赖旧目录遗留 class。字段对账已显式登记新响应及资源树节点，不假设未登记字段受保护。

## 代码轨

- 实证通过：A/B 同名专属字段按归属隔离；引用同一通用类型的两主资源均能取得完整有效目录，解除某主引用不影响他方目录。
- 实证通过：可见集合在计数和分页 SQL 前生效；来源身份、空目录、缺引用与参数超限分开处理；读取故障没有全目录回退。
- 实证通过：类型候选复用原 SUB_PERM 解析并组合关系适用性；父 ALL 保留类型上下文，不被错误要求存在某个具体引用。
- 实证通过：资源同步请求与版本值对象迁入 perm-common 单源；SDK 资源发布支持显式租户凭证选择，不新增自报租户信任头。管理只读入口不进入 M2M 白名单。
- 机制核对：新增只读聚合位于 resource 能力包，跨包通过领域服务；复用分页、目录权限、类型解析、bootstrap 路由登记和原 Feign 客户端，没有新查询引擎或发布队列。
- 存疑待用户决策：无。

## 文档轨

- API 字段、错误、SDK 单源位置、服务认证与接入步骤已回写；前端 API/DTO 已就绪，组件未接入的边界明确。
- 当前权限保存与运行时尚未消费关系，不能以候选过滤替代 T-ACCESS-148 的验收。
- 全量 E2E/heavy 回归仍由 T-ACCESS-150 执行，本卡未宣称完整计划完成。
