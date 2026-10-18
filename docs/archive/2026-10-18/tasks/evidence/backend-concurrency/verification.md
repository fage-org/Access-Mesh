# 后端并发修复验收记录

实施任务：T-ACCESS-122～128。基线：a4214fc31（仅排查/文档提交；生产基线为8733a51c2）。用户继续指令后实施；原始 gpt-6.1-sol max 报告保留在 T-ACCESS-121，未追加外部评审。

## 处置范围

- common.lock.RedisTransactionLocks 提取原 helper，树/重置两个门面委托；key、10015、watchdog 与 afterCompletion 边界保持，依赖仍 optional。
- Q-083：同族旧实体回写改为请求触达列；有效行锁/共同主体事务锁保护组合验证、投影与删除互斥。类型 extra 和互斥字段为锁内校验后的派生字段，保持原业务语义。有效行已锁定并持有至提交，不依赖重复检查影响行数来替代互斥。
- Q-084：类型 Mapper 显式清会话缓存；操作 create/update 使用锁内数据库类型身份。只读操作查询仍可复用原缓存。
- Q-085：岗位结构读取移入 SYS_ORG 锁内，assign/remove 不再使用旧 parent。
- Q-086/Q-087：原咨询键106集中到 AbstractUserMapper XML，由 SubjectDomainService 统一调用；原生/管理/同步主体与新增关系共享，版本CAS保留。创建带组织先取树锁，保持联合锁序。
- Q-088：字典类型删除与创建/改绑共用父行锁，批量固定顺序。创建额外修复 schema 默认值缺失：sort_order=0/is_default=false。
- Q-089：删除 keyGeneration 时同步提升 globalEpoch，防旧 liveKeys 清理复活旧 token；新 token 可正常提交。

## 反例与证据边界

- Gateway 旧实现：InvalidationMarkerTest 两个清理变体稳定失败；修复后与既有失效竞态共10项通过。
- 类型一级缓存与岗位parent：真实PG旧实现5项失败（类型3、岗位分配/移除2）；修复后对应真实业务/Mapper断言通过。
- OAuth2 旧实现：真实事务竞争后 NEW_HASH 被还原为 OLD_HASH、delete_flag 从id还原0，两项失败；修复后通过。
- 主体删除/绑定旧实现留下1条孤儿关系；FULL v2在single v1之后撞uk_abstract_user。修复后实际AppService/Mapper事务通过，版本2内容生效。
- 字典旧实现：改绑与父删除两方向均留下孤儿；独立创建默认值用例在原生产路径触发NOT NULL。修复后通过并钉住两方向既有业务错误码。
- 操作更新的旧类型缓存模拟反例：缓存值4、数据库身份5，旧写路径报 Operation not found；修复后选择数据库身份，不消费缓存类型值。

最初 OAuth2 持锁夹具误把 JdbcTemplate 当作 Flex 同事务连接，不能算有效反例；已改显式独立 JDBC 事务，通过pg_stat_activity确认竞争者实际等待后提交，最终红/绿证据使用该版本。字典创建原有默认值缺陷曾阻断创建竞态，所以生命周期反例改用原本可达的“改绑”；随后默认值缺陷单独验证并修复，不把不可达创建路径算成原有已复现交错。

新增PgIT为隔离并发不变量而模拟操作者权限/同步白名单，SQL、事务、版本CAS和目标写入仍真实执行；完整E2E承担真实认证授权接线。未穷举全部可能交错，不宣称全项目已无并发问题。

## 本地代码轨

代码轨先行，只读核对最终实现和全部同族写入口。实证通过项：原线程获取/异常释放；事务后释放；三个重置入口的共同/隔离键；主体锁SQL单源与锁序；锁前读取移除；有效行查询租户/删除条件与参数顺序；请求触达列/显式NULL；版本CAS及任务fencing保留；Gateway清理后旧token拒绝、新token可用；无新增N+1、自动装配或必选Redis依赖。

同类扫描仍保留的普通实体写入（组织树配置、角色同步、成员同步、资源同步）已有共同串行边界，本次没有无证据地替换。存疑待用户决策项：无。过度设计可裁剪项：无。未处置P0/P1/P2/P3：0；完整回归已通过，冻结输入一致。

## 本地文档轨

在代码轨后独立核对当前规范、公共方案、任务/计划/Q映射、范围与接受边界。发现converted条目残留设想正文、当前索引仍称草案、连续性回写不在引用章节内，均已修正。实现目标与验收完成分开，原始排查证据不回写成已修。存疑待用户决策项：无；当前文档差异无未处置P0～P3。完整回归后已补结果与归档，最终链接核对见下方。

## 验证记录

执行日志整文件位于 `.tmp/concurrency-sol-20261018/`。上游 common 已通过 `mvn install -pl common -am -DskipTests` 刷新，行为验证不以install成功替代。

近期定向 `mvn test -pl access-service -Dtest=BizDomainAppServiceImplTest,JobAppServiceImplTest,OperationAppServiceImplTest,UserWriteAppServiceCreateStatusTest,BackendWriteConcurrencyPgIT,NoticeLifecyclePgIT,TenantAdminContinuityPgIT,UserRoleReassignmentPgIT`：83项单测、36项容器测试通过；更早单测轨失败已定位为旧夹具、旧全实体断言与测试桥接错误，按当前请求字段语义修正，未删除业务验证。

当前任务与设计链接、过程词检查已通过。完整 `mvn test -T 1C`：2026-10-18 17:10:07 +08 完成，21分22秒，3018项全部通过，0失败/错误/跳过；reactor全部SUCCESS，包含E2E 35项与heavy。前端未修改，不重跑前端验证。测试环境部署未执行。


## 最终核对

[机器可读结果](results.json)保留模块统计、E2E/heavy哨兵、完整日志SHA-256、源输入清单摘要与reactor结果；[定向日志摘要](targeted-results.json)保留反例和修复后的独立执行统计，重叠用例不累加为唯一测试数。完整输出仍在 `.tmp/concurrency-sol-20261018/full-backend.log`。

统计按本轮XML testcase元素并与Maven执行汇总对账：JUnit嵌套套件的根tests属性可能为0，不能直接相加根属性；排除运行前遗留的AccessBootstrapRunnerTest/TxDiagScratchPgIT旧报告，未把旧测试计入此次通过数。完整回归前后2453个跟踪及未忽略输入文件哈希一致，新增/删除/变化均为0，清单SHA-256为20ccbdca738a503874353b60a8ec5cf37131dd85d439d3b55e81e7dc435df7f6。

heavy实际完成434.2秒。等待期间只读线程/数据库诊断确认执行在markResourcesDeleted，PG的sync_metadata清理查询活跃且阻塞者为0；没有为耗时跳过或中止用例。执行前9100无监听。回归结束及输入对比完成后仅更新验收/归档文档并清理文档末尾空行，生产与测试代码未变。

最终文档轨：问题状态、任务/计划终态、设计引用和归档链接检查通过，终态卡已全部迁出活跃目录；生产/测试及构建输入在回归后仍与冻结摘要一致。旧路径与diff空白检查随提交完成；原Q-060/Q-062/Q-065/Q-068及T-PERM-036继续暂缓。无新增待决事项，无追加外部评审或部署。
