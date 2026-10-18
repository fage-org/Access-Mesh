# T-ACCESS-119 核实与验收

## 范围与基线

实施基线 `3ff4d2300`，开始时工作区干净。用户授权测试环境清单校准、Q-070 裁剪、Q-072 最小修复，以及 Q-079/Q-056/Q-081 的定向验证和确认问题修复。其他事项保持暂缓。未调用子代理或外部评审。

Q-081 用户明确选择 Redis 非等待锁：拒绝处理期间的重叠重置，接受提交后的交付窗口。未采用延长锁 TTL、等待后再次重置、凭据领取协议或全局重置冷却。

## 核实结果

| 问题 | 证据 | 处置与边界 |
|---|---|---|
| Q-070 | `frontend/src/store/modules/user.ts` 的两个设置动作无调用方；路由守卫仍读取 `multipleTabsKey` | 删除状态、类型字段、设置动作及期限选择；保留会话标记与独立身份隔离 |
| Q-072 | 旧 URL 拼接对 hash 路由及已有 t 参数失败；三个反例实跑失败 | 局部纯函数使用 searchParams.set，组件调用；保持查询参数、hash 与目标 origin，不启用动态路由 |
| Q-079 | 通用 IllegalArgumentException 处理器的日志捕获实证携带合成敏感标记；旧测试实际失败 | 该技术兜底只记请求 ID 与异常类型，不记 message/cause/堆栈；HTTP 400 与信封不变 |
| Q-056 | `ServiceConfigSyncV2Req.ApiItem.resourceCode` 正则排除竖线，Controller 使用嵌套 @Valid；Bean Validation 实跑验证合法编码与非法编码。映射索引另一路尾段是数字资源 ID | 原现役接口碰撞前提不成立，保留工具格式，不增加元组迁移。未扫描测试环境实际数据库，不承诺离线写入/历史脏数据无碰撞 |
| Q-081 | 用户重置本已有行锁，原缺口为排队后再次重置及响应交付；入口单测、事务回调单测、真实 PG/双 Redis 客户端分别验证接线、释放边界和跨客户端互斥 | 新增 10015 冲突码；租户内重置与平台首管理员恢复共享用户键，平台账号独立键。提交后的后续重置仍会使旧密码失效，已进入契约和交付提示 |

Q-079 扫描覆盖生产源码显式 IllegalArgumentException 构造、常见数字/枚举/URI 解析及异常转换。来源并非全部固定文案：`SyncTypeGuard` 的未知字段、`ResourceTypeOwnershipGuard` 的来源文本进入业务异常，任务反射目标进入任务失败路径；这些不等于技术兜底的真实认证密码泄露。浏览器 Origin 的 URI 异常会转换为固定拒绝，登录请求解析继续使用已脱敏的 HttpMessageNotReadable 通道。本次没有证明真实登录密码/token 已经外泄，也没有把合成异常捕获扩大为全部日志无敏感数据的保证。

Q-056 理论碰撞对为 `(POST, /api/x|y, z)` 与 `(POST, /api/x, y|z)`；末段 `y|z` 不能通过现役请求校验。测试刻意允许 basePath 含竖线，证明结论不依赖“完整路径绝不含竖线”的错误前提。API 类型写入受现有所有权限制，不能用普通资源 CRUD 绕开声明通道。

## 定向验证

执行日期：2026-10-18。完整日志位于 `.tmp/pending-calibration-119/`。

| 状态 | 命令/验证 | 结果 |
|---|---|---|
| failed（预期反例） | `pnpm -C frontend test src/layout/frame-url.spec.ts`，旧拼接逻辑 | 3 项失败；hash 后追加参数不能成为 query，旧 t 也未替换 |
| passed | `pnpm -C frontend test src/layout/frame-url.spec.ts src/utils/auth.spec.ts src/router/index.spec.ts` | 34 项通过 |
| failed（预期反例） | `mvn test -pl common -Dtest=GlobalExceptionHandlerLogMaskingTest`，旧日志行为 | 新增日志脱敏断言失败，原始消息进入 WARN |
| passed | `mvn install -pl perm-sdk/perm-common -am -DskipTests` | common 与 perm-common 上游已 install，避免旧 SNAPSHOT |
| failed（预期反例） | `mvn test -pl access-service -DskipTestcontainers=true -Dtest=UserAppServiceResetPasswordGateTest#overlappingResetIsRejectedBeforeRowLockOrPasswordWrite`，尚未接入重置锁 | 应返回重置冲突却继续进入用户读取，断言失败 |
| passed | `mvn test -pl access-service -DskipTestcontainers=true -Dtest=PasswordResetLockSupportTest,UserAppServiceResetPasswordGateTest,UserResetPasswordByteLimitTest,PlatformAccountAppServiceTest,BusinessFieldValidationTest,PlatformSessionAbsoluteTimeoutTest` | 46 项通过；容器轨按此命令跳过 |
| failed（夹具问题） | 首次 `mvn test -pl access-service -Dtest=UserLoginRowLockPgIT` | 回滚案例的 JDBC 更新未随当前事务回滚；不能作为生产 Mapper 回滚证据 |
| passed | 同一命令，夹具改为生产同款 Mapper/租户上下文 | 3 项通过，无跳过；真实 PG 更新提交/回滚与双 Redis 客户端拒绝、释放、键隔离均验证 |
| failed（装配遗漏，已中止） | 首次 `mvn test -T 1C` | PlatformAccountsPgIT 精简上下文未显式导入新锁组件，6 项上下文启动错误；发现后停止运行，未将该次计作完整回归 |
| passed | `mvn test -pl access-service -Dtest=PlatformAccountsPgIT` | 补齐精简上下文的真实 Redisson 与锁组件后，6 项通过，无跳过；随后重新执行完整回归 |

真实 Redis 用例复用 ItInfra，无额外容器拓扑；两个 Redis 客户端访问同一隔离逻辑库。时序由闭锁控制，无新增裸 sleep 余量。事务回调单测只证明回调时机及失败处理，真实提交/回滚由 PG 用例提供，二者不互相冒充。未进行浏览器完整交互验收；本次不改布局和路由启用范围。

## 本地代码轨

主代理只读核对生产 diff、调用链及测试后形成结论：

- 原用户门禁、默认树校验、平台身份核对仍在；非等待锁不作为授权替代。冲突发生在目标数据库行锁和写入前，不修改凭据或吊销会话。
- 租户用户按 tenantId/userId 隔离；平台账号独立身份域。首管理员 ID 只在创建流程 attachAdmin，平台恢复预读目标 ID 不与现役“更换首管理员”入口竞争。
- 所有接入点均为真实事务；平台恢复在全局管理/租户行锁之前获取重置锁，避免排队后再次重置。释放统一挂 afterCompletion，获取失败不降级无锁写入。
- 事务结果已确定时，解锁失败只告警；Redis 故障和续期丢失不被描述成分布式强一致保证。
- Q-070 不删除路由实际消费的标记；Q-072 不恢复动态路由或新增 iframe 功能；Q-079 不改变响应协议。
- 减法检查：未新增通用锁框架、配置开关、持久化表或重复重试机制；局部 URL 函数供组件与行为测试共享。已有 TreeWriteLockSupport 是阻塞树写锁，不把密码重置塞入其树枚举或改变其等待语义。

结论：本范围无未处置的 P0–P3；存疑待用户决策项为零，Q-081 的产品边界已明确。

补充核对：首次完整回归暴露的精简 PG 上下文装配遗漏已修复。该上下文的密码重置仍使用真实锁与原 PostgreSQL/Redis 隔离配置，没有用 mock 或禁测绕过；隔离复跑验证了原有平台账号 SQL、审计回滚及等待后身份复核行为。

## 本地文档轨

代码轨完成后，独立核对契约、服务架构、登录设计、工程规范、任务与清单：

- 10015 同步登记错误码索引与三入口引用；区别“锁被占用”的业务冲突和 Redis 技术故障。
- 事务期间互斥与提交后交付窗口分开描述，未把行锁缺失当根因，也未承诺延长 TTL 可以保证交付。
- Q-056 的撤回仅针对现役正式入口，Q-079 仅确认并修复技术异常兜底输出，不宣称真实凭据泄露或全应用日志安全。
- Q-068 已完成的跨标签同步和 HttpOnly/CSRF 从待办叙事移出；Q-062 扣除已补齐契约；Q-065 不以陈旧行数定重构范围。
- T-PERM-036、权限诊断、完整契约补册、独立强制下线、验证码替代及大型服务拆分均保持暂缓。

结论：本范围无未处置的文档 P0–P3。任务终态、归档链接及完整回归结果在收口时核对。

## 完整回归

2026-10-18 最终完整回归通过，统计及日志哈希见 [results.json](results.json)。

| 状态 | 命令 | 结果 |
|---|---|---|
| passed | `mvn test -T 1C` | 3000 项，0 失败/错误/跳过；全部 reactor SUCCESS，含 E2E 与 heavy，11:52 min (Wall Clock) |
| passed | `pnpm -C frontend test` | 59 文件、639 项通过 |
| passed | `pnpm -C frontend typecheck` | tsc/vue-tsc 通过 |
| passed | `pnpm -C frontend lint:check` | ESLint/Prettier/Stylelint 通过 |
| passed | `pnpm -C frontend contracts:check` | 46 组 DTO 对账通过 |
| passed | `pnpm -C frontend build` | 正式构建通过 |

最终后端运行前确认 9100 无开发服务监听；1643 个源码/构建输入前后哈希一致，无新增输入。完整日志保存在 `.tmp/pending-calibration-119/`，归档 JSON 保留退出码、各关键套件实跑结果、源码清单哈希与日志哈希。收口阶段只更新文档状态和归档，不再改源码。
