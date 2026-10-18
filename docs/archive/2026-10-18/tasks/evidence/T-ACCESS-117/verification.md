# 登录审计事务边界验收

任务：T-ACCESS-117。实施基线：`7b2e12a81`。日期：2026-10-18。

## 范围与已确认边界

Q-067 已解除暂缓。登录由现有 AuthAppServiceImpl 使用 TransactionTemplate 包裹原认证流程，事务完成后同步调用原 LoginLogDomainService 的独立事务。用户锁读、BCrypt 验密、状态/租户门禁、会话及标记创建仍在一个登录事务中。

成功记录已形成但事务向调用方抛出完成异常时，记录 status=0 与固定原因“登录事务未正常完成”；认证拒绝保留原失败原因。审计写入/提交失败不替换认证结果或原异常。不修改事务管理器，不识别其内部未抛出的故障，不增加已创建会话的跨资源补偿。

## 行为验证

| 状态 | 命令 | 结果 |
|---|---|---|
| failed，预期反例 | `mvn test -pl access-service '-Dtest=AuthLoginLockTest#failedLoginAuditsOutsideTheLoginTransactionButStillLocksInsideIt,LoginSessionPgIT#loginShouldEstablishSessionAgainstRealDatabaseAndRedis+wrongPasswordStillPersistsAuditWithOnlyOneConnection'` | 旧实现单元/上下文断言 1 失败、0 错误/跳过：审计调用时外层事务仍活跃；该 execution 失败后 PG 未执行 |
| failed，预期反例 | `mvn test -pl access-service '-Dtest=LoginSessionPgIT#loginShouldEstablishSessionAgainstRealDatabaseAndRedis+wrongPasswordStillPersistsAuditWithOnlyOneConnection'` | 旧实现真实 PG 2 失败、0 错误/跳过：最大连接数 1 时成功/失败审计均未落库，日志显示嵌套借连接超时 |
| passed | `mvn test -pl access-service '-Dtest=AuthLoginLockTest,LoginTransactionBoundaryTest,LoginUnknownAccountCostTest,LoginLogDomainServiceImplTest,OAuth2LoginLogTest,LoginSessionPgIT,UserLoginRowLockPgIT,LoginLockTemporaryPgIT'` | 单元/上下文 28 项、真实 PG/Redis 7 项通过，0 失败/错误/跳过 |
| passed | `mvn test -pl access-service -DskipTestcontainers=true '-Dtest=LoginTransactionBoundaryTest'` | 补充会话标记创建时事务仍活跃的断言后，4 项通过；该命令明确跳过容器 execution |
| passed | `mvn test -T 1C` | 2974 项，0 失败/错误/跳过，全部 reactor 模块 SUCCESS，包含 E2E/heavy；耗时 12:38 min (Wall Clock) |

PG 使用已有 ItInfra，LoginSessionPgIT 的测试池上限改为 1（生产配置未变）。既有成功登录用例增加成功审计及验证码重放失败审计落库断言；错误密码用例证明回滚后失败日志仍在。锁窗口用例在真实登录的验密后、会话创建前通过闭锁暂停，使用独立 JDBC 连接尝试修改同一用户密码，断言 PostgreSQL `55P03` 行锁等待超时；独立连接避免把连接池等待误判为行锁。释放后登录成功、密码更新可执行。原 UserLoginRowLockPgIT 继续验证重置方先写入时，随后锁读得到新密码。

非容器边界测试验证会话标记仍在登录事务内创建、审计在事务结束后执行、审计失败不覆盖成功或认证拒绝、事务完成异常按固定失败记账、已有调用方事务在认证前被拒绝。故障注入仅验证应用处理实际传播异常的行为，不把测试事务管理器当作真实数据库证明。

初次绿色验证命令曾因新增测试误用 BizException.getCode 而编译失败，修正为 getErrorCode 后重跑；这次编译失败不计作反例或执行通过。完整日志保存在 `.tmp/T-ACCESS-117/`。完整回归前确认 9100 无开发服务占用，冻结变更源码并保存哈希；运行期间未改源码，[源码清单](source-sha256.json)前后核对一致。

## 本地代码轨

主代理独立只读核对生产改动、全部终态日志分支、调用方与测试证据：

- 原认证顺序保持，用户锁读、密码判断和 Sa-Token 会话/标记创建均在 callback 中，事务边界不落到 DomainService。
- finally 位于 TransactionTemplate 调用外；没有在仍持连接的业务方法 finally、事务完成回调或异步执行器中写审计。
- 日志快照为单次请求私有对象，只保存既有不可变 LoginLogEntry，既有终态分支复用同一落库入口；无密码、令牌或共享 ThreadLocal。
- 登录入口复用项目已有的调用方事务拒绝模式；Bearer Controller 与 BrowserAuthAppService 的现有调用均不持外层事务。
- 审计异常仍隔离；已准备成功但事务未正常返回时降为固定失败，原异常继续传播。未知租户保持不虚构日志归属。
- 全部 LoginLogDomainService 生产消费者已查：原生登录改为事务外调用；OAuth2 兑换/刷新沿原独立路径，授权码签发事务未被改动；平台身份日志不受影响。

无未处置 P0–P3。存疑待用户决策项：无；事务异常记账已按用户选择落地。

**过度设计检查**：未引入新 Bean、线程池、队列、事件总线、独立数据源或通用审计框架；只使用项目既有 TransactionTemplate 和审计领域服务。请求内单槽快照用于跨异常退出保留审计数据，不需要并发容器或统一事件模型。测试新增的私有事务管理器只用于故障注入，不进入生产。

## 本地文档轨

代码轨结论形成后，独立核对架构审计分级、登录与密码重置契约、容量说明、任务/计划/问题状态：

- 架构与契约明确非事务外层、登录锁窗及事务外同步审计，不再把整个 login 方法描述为声明式事务。
- 保留单连接池测试与生产容量规划的区别，不承诺并发请求永不等待；同步修正容量文档中的旧 Hikari 版本指代。
- 技术故障规则限定于事务管理器实际传播的异常；保留审计失败告警及未解析租户边界，不扩展补偿或强一致承诺。
- Q-067 经本卡验收后收敛；顶栏/文件计划保持 proposed，T-PERM-036 继续暂缓。归档时同步各入口及稳定问题锚点。

无未处置 P0–P3；相对链接、设计锚点和任务状态同步检查随归档执行。

## 验证边界

未运行外部 AI 评审，未做生产压测或部署；没有修改连接池配置、前端、DDL、OAuth2/平台认证和事务管理器。本次修复消除已确认的登录嵌套连接占用，不把一般数据库故障或全部审计可靠性问题声明为已解决。
