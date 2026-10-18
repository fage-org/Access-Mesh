# 公共锁外评与类型身份写入核实

## 外评对象与结果

用户指定 `gpt-6.1-sol`，按 `max` 调用，关闭 multi_agent，read-only，两轮核查。范围为 `a4214fc31..3e7ec01e3`，原始[提示词](codex-prompt.md)、[报告](codex-report.md)及[调用记录](codex-run.json)保留。外评退出码0，开始/结束工作区均干净，无构建或测试；P0～P3均0，另有类型缓存写入存量观察。该结论针对送评增量，不代表全项目没有并发问题，也不是后续修复的外部复评。

公共 helper 的事务结束释放、异常/中断清理与重入计数，以及树写/密码重置门面的身份和事务边界，未发现迁移回归。联合锁图核对未确认实际 Redis↔PG 等待环。三个重置入口保持非等待拒绝和用户接受的提交后交付窗口。

## 确认与处置

存量观察经本机真实 PG/Redis 验证成立，按同根因缺陷计一项（P2）：类型删除提交且同码重建后，先前的旧读取仍可迟到回填 TYPE_VALUE；同步把旧类型值持久化为孤儿，缓存到期不能修复。该交错发生在删除完成后，区别于 user_type 已接受的“有效性检查后、写入前发生删除”窗口。用户已授权修复确认问题，因此无需新增产品决策。

| 写入口 | 处置 |
|---|---|
| ResourceEntitySync single/FULL | 持 RESOURCE_ENTITY 后使用共享数据库类型解析 |
| AbstractRoleSync single/FULL | 持 ABSTRACT_ROLE、PG106 后使用共享数据库类型解析 |
| AbstractUserSync single/FULL | 持 PG106 后使用共享数据库类型解析 |
| UserManage create | 持 PG106 后使用共享数据库类型解析 |
| ResourceManage create/batch-create | 已消费所有权守卫返回的当前类型行，无需修改 |
| Operation create/update | 已在资源锁内直接读取类型行，无需修改 |
| RoleManage create、bootstrap、原生投影、ServiceSync API 固定图 | 类型被枚举或系统类型约束限制，不存在本次同码删除重建前提 |
| 列表、详情、反解与对既有对象的解析 | 不新增类型事实，保留既有读取缓存；不把整个 TypeResolutionService 改为隐式按事务切换缓存 |

新增 `TypeResolutionService.resolveTypeValueFromDatabase` 复用已有 `selectByTypeKeyAndCode`（flushCache=true、有效行/租户过滤），不读写 Redis 缓存、不取得新锁。原缓存 miss 复用同一数据库解析，CacheReadToken 起点和剩余 TTL 逻辑不变。FULL 在批量预载前只解析一次，无新增循环单查；没有新增注解、自动装配、配置或 TTL。

## 验证证据

全部日志位于 `.tmp/concurrency-lock-review-sol-20261018/`，未删除原失败报告。2026-10-18：

- `mvn test -pl access-service -Dtest=BackendWriteConcurrencyPgIT`，`type-stale-red.log`：11项，4失败，0错误/跳过；资源/角色的single/FULL均实际落库旧类型值。
- 同命令，`type-stale-family-red.log`：14项，7失败，0错误/跳过；增加主体single/FULL和管理创建，均实际落库旧值，既有7项通过。
- `mvn test -pl access-service -Dtest=BackendWriteConcurrencyPgIT,TypeResolutionServiceImplTest,ResourceEntitySyncAppServiceTest,AbstractRoleSyncAppServiceTest,AbstractUserSyncAppServiceTest,FullSyncN1GuardTest,FullSyncResponseContractTest,FullSyncStaleVersionTest,UserManageAppServiceImplTest`，`type-stale-green.log`：106项单测、14项容器测试全部通过，0失败/错误/跳过。

反例使用真实 Mapper 读、类型删除事务、Redis CacheReadToken 回填及实际同步/创建入口，按读→删除提交→同码重建→旧回填确定性展开交错，不靠 sleep。类型重建由 SQL 夹具准备，权限查询和主体/角色同步白名单按原测试上下文模拟；资源所有权门禁、业务事务、版本 CAS 和落库结果真实。先断言旧缓存确实存在，再核对最终新记录只引用当前类型，防止缓存过期造成伪绿。不宣称覆盖全部并发交错。

完整 `mvn test -T 1C` 于2026-10-18 18:19:29 +08完成：3025项全部通过，0失败/错误/跳过，包含35项E2E与heavy；wall clock 19分30秒。逐模块统计、重叠定向运行、日志哈希与哨兵见[机器可读结果](results.json)。统计按本次运行的XML testcase汇总并与Maven汇总对账，排除两份旧报告；运行前后2459个输入文件的新增/删除/变更均0。前端未修改，不重跑前端。没有部署或推送。

## 本地代码轨

代码轨只读检查当前差异及调用方：共享解析方法参数顺序、有效行SQL、缓存旁路、先锁后读、FULL预载/版本CAS位置、null失败分支、单测夹具与真实反例均核对通过。没有改公共锁机械逻辑或新增锁，因而未引入新的取锁边；7个持久化入口统一使用领域层方法。实证通过项为上述120项定向测试及7项红/绿反例。存疑待用户决策项：无；过度设计可裁剪项：无；未处置P0～P3：0；完整回归及输入一致性检查已通过。

## 本地文档轨

在代码轨后独立核对当前契约、公共锁设计、任务范围与接受边界。主体类型删除“用户写入口无锁可复用”的理由已过期：改为明确类型删除尚未接入共同主体锁，不把本次新鲜读取修复误写成类型删除强互斥。原接受窗口仍保留，原暂缓事项不变。外评0条增量缺陷与主代理核实的存量缺陷分别记录，测试命令/范围未混用。存疑待用户决策项与过度设计可裁剪项：无；完整回归结果已对账，任务与证据归档后链接检查通过。
