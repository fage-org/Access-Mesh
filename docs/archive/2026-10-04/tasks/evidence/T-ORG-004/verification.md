# T-ORG-004 验证证据

2026-10-04：`mvn test -pl access-service -Dtest=DefaultTreeDirectoryGuardPgIT,OrgTreeConfigAppServiceImplTest,OrgTreeConfigDomainServiceResolveTest,ErrorCodeContractTest`，单测 execution 39 项、容器 execution 11 项，均零失败/错误/跳过。批次完整回归已通过，见[完整证据](../pending-problems-clearance/batch-2026-10-04.md)。

真实 PG 核实了前置创建缺陷：原 create 未设置 `is_default`，MyBatis-Flex 显式插入 NULL，违反当前 DDL 非空约束；这不是重叠守卫的证明。按当前创建非默认配置契约补 `false` 后，仅临时移除新增的重叠守卫与 create 锁，定向执行创建/更新重叠及并发创建案例，3 项均以行为断言失败（零环境错误）；恢复实现后通过。临时变体已恢复，无 RED 标记留存。

守卫复用 OrgDomainService 的批量祖先查询，覆盖同根、候选为后代、候选为祖先、自身配置排除、根不存在；独立根可创建。非默认改根的锁内检查由既有 AppService 测试补齐，默认树身份目录守卫未扩大或弱化。并发测试在首事务插入未提交时提交第二个创建，最终只保留首配置，第二个明确拒绝 11003。

代码轨核对：相同租户 SYS_ORG 锁保护首次配置/祖先读取与写入，组织移动已有同锁与跨树拒绝；无逐根数据库查询、新锁或多根择一策略。文档轨核对：默认/非默认同规、当前无部署与开发重建边界、旧数据未自动清理、创建缺陷与重叠风险分别描述。
