# 租户保底管理员连续性验收

日期：2026-10-17。基础提交：`14eafea32`；本任务工作树，后端修改。

## 行为证据

| 验证 | 命令 | 结果 |
|---|---|---|
| 缺陷复现 | `mvn test -pl access-service -Dtest=TenantAdminContinuityPgIT -DfailIfNoTests=false` | 旧实现下 3 项失败、0 错误：最后成员撤回与到期设置未拒绝；交接后初始化仍要求原绑定。均为行为断言失败。 |
| 完整回归 | `mvn test -T 1C` | 2888 项，0 失败/错误/跳过，BUILD SUCCESS，17:36 min；包含容器、heavy 和 E2E。 |
| 实现收尾定向验证 | `mvn test -pl access-service -Dtest=TenantAdminContinuityPgIT,AccessBootstrapPgIT,AbstractRoleSyncAppServiceTest,FullSyncResponseContractTest,AccessServiceArchitectureTest,QueryBoundaryArchitectureTest -DfailIfNoTests=false` | 80 项，0 失败/错误/跳过，BUILD SUCCESS。 |
| 最终 PG 验证 | `mvn test -pl access-service -Dtest=TenantAdminContinuityPgIT,AccessBootstrapPgIT -DfailIfNoTests=false` | 新增行为组 14 项通过；初始化组 20 项因该 fork 的 Docker 探测失败被跳过，未计作通过。 |
| 初始化组隔离复验 | `mvn test -pl access-service -Dtest=AccessBootstrapPgIT -DfailIfNoTests=false` | 20 项，0 失败/错误/跳过，BUILD SUCCESS；补齐上行被跳过的验证。 |

完整回归后的实现收尾包括 SQL 参数分批、类型解析复用现有入口、删除已无调用方的旧固定绑定查询，以及初始化诊断文案对齐现任成员；最终工作树由上表定向验证覆盖这些变更。未重复运行与这些收尾无关的完整回归；未修改前端，未运行前端测试。不存在将跳过的容器组或编译成功当作行为通过的情况。

本地完整日志保存在 `.tmp/T-ACCESS-106/`；本文件内联命令、日期与计数作为持久验收记录，不以临时日志路径作为唯一证据。

## 代码轨

实证通过项：

- 最后永久成员撤回、改为有限期、未来生效、唯一永久账号启停/删除以及内置角色停用的拒绝与数据库回滚。
- B1：接班人待强制改密且登录失败临时锁定时仍可交接；认证限制本身不解除。
- C1：临时管理员保留既有管理权限，但不能移除最后永久成员；操作者、目标、角色与有效期在既有审计机制中可定位，不记录凭据。
- PostgreSQL 真实并发：首个退出事务未提交时，第二个请求在咨询锁上等待；提交后重读并拒绝最后成员退出。测试通过数据库锁等待状态协调，不使用固定 sleep 余量。
- 账号、主体批量加载复用 `SqlBatches`；跨批场景验证全部资格候选被保留。
- 同步单条拒绝先于版本消费；full-sync item 拒绝与缺失删除拒绝不推进被拒项目的版本/删除标记，保留既有部分应用语义。
- 交接后原账号可删除；初始化不重建原账号、不补回其绑定，仍拒绝结构损坏与无合格保底成员。
- 现有 QueryGate 操作者门禁保留；能力包 Mapper 边界、BootstrapSeedWriter 使用范围与引擎退役规则通过架构测试。

未解决 P0/P1/P2：无。未解决事实性 P3：无。没有需要用户另行决定的新增抽象或裁剪项；零消费旧查询随调用方替换删除。

## 文档轨

实证通过项：A/A1/B1/C1 归位租户生命周期；契约登记 10014 与同步 reason；架构登记事务锁顺序、固定评估时刻和交接后的初始化语义；初始化直绑条件与原生账号边界保留。Q-075 内置服务登记的独立启停边界、平台凭据恢复权限与审批路线图没有混入本任务。

未解决 P0/P1/P2/P3：无。存疑待决策项：无。任务归档、Q-ID 锚点及当前文档链接已核对。
