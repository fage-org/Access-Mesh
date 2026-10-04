# T-PERM-101 主体组遍历等价重构

2026-10-04：批量装载与递归遍历合为一套，调用方传是否剪禁用组；删除重复的全子树方法与未使用的 nestedGroupRoleIds 参数。运行时 status≠1 的组及子树仍剪枝；原始持有保留完整子树、绑定有效期窗口、缺失引用跳过及 visited 防环。缓存读写、数据库查询形态、外部接口均保持。

重构前 `mvn test -pl access-service -DskipTestcontainers=true -Dtest=SubjectDomainServiceImplTest,PermissionCheckAppServiceImplTest,PermissionQueryAppServiceImplTest,PermissionViewAppServiceImplTest,QueryAuditAndTraceTest` 49 项通过，SubjectDomainServiceImplTest 其中 10 项；重构后同一批证据随 `mvn test -T 1C` 全部通过。[完整回归](../pending-problems-clearance/batch-2026-10-04.md)。纯等价重构不制造无关 RED。

代码轨：两条调用路径仅以禁用集合是否为空区分，没有新框架或缓存机制；引用/父子两来源的遍历、防环与窗口不变。文档轨：引擎实现 §2.1 回写，Q-028 收敛，简单任务保持看板行形态，无遗留或待决项。
