完成两轮只读评审，发现 **1 项 P2**。未修改文件或 Git 状态，未运行构建、测试，未使用子代理；开始和结束工作区均干净，HEAD 均为 `c754c414eed29cf08d94d6294cf2c2f3da932573`。

### [P2] 取锁等待被中断时，可能遗留持续续期且无人释放的重置锁

**位置：**

- [PasswordResetLockSupport.java:40](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/PasswordResetLockSupport.java:40)
- 受影响调用方：[UserAppServiceImpl.resetPassword:381](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/user/service/impl/UserAppServiceImpl.java:381)、[TenantAppServiceImpl.resetAdminPassword:204](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/tenant/service/impl/TenantAppServiceImpl.java:204)、[PlatformAccountAppServiceImpl.resetPassword:120](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/auth/service/impl/PlatformAccountAppServiceImpl.java:120)。

**问题：** `tryLock()` 正常返回成功后才注册释放回调。但 Redisson 4.8.0 的同步等待被中断，不等于底层异步取锁被撤销：调用方可以先抛异常退出，底层随后成功获取锁并启动 watchdog。此时没有注册 `afterCompletion`，也不会进入第 51 行的清理分支。

**触发条件：** 取锁命令已发出、结果尚未返回时，执行线程被中断；底层命令随后成功，Redisson 客户端继续存活。这里限定的是线程中断，不把普通浏览器断连或 HTTP 超时直接等同于线程中断。

**影响：** 当前事务失败且未修改密码，但该账号的锁可能一直续期；其他线程或实例后续重置持续收到 `10015`。租户用户会同时失去租户内重置和平台首管理员恢复两条通路，不能依靠普通锁 TTL 自动恢复。

**证据：**

1. 实际依赖版本为 Redisson **4.8.0**，已对照本地 JAR 字节码及官方同版本源码。
2. `CommandAsyncService.get(RFuture)` 捕获中断后取消返回 Future，并抛出 `RedisException`。[官方源码](https://github.com/redisson/redisson/blob/redisson-4.8.0/redisson/src/main/java/org/redisson/command/CommandAsyncService.java)
3. `ServiceManager.execute` 创建独立结果 Future；取消它不会取消内部操作，也没有在迟到成功时补偿解锁。[官方源码](https://raw.githubusercontent.com/redisson/redisson/redisson-4.8.0/redisson/src/main/java/org/redisson/connection/ServiceManager.java)
4. 内部 `tryAcquireOnceAsync` 成功时仍启动续期；续期检查 Redis 所有权字段，不检查原调用线程是否仍在处理事务。[取锁源码](https://raw.githubusercontent.com/redisson/redisson/redisson-4.8.0/redisson/src/main/java/org/redisson/RedissonLock.java)、[续期源码](https://raw.githubusercontent.com/redisson/redisson/redisson-4.8.0/redisson/src/main/java/org/redisson/renewal/LockTask.java)
5. 当前测试覆盖普通失败、手工事务回调及真实提交/回滚，没有覆盖“同步等待中断、异步取锁迟到成功”。本结论来自源码控制流核对，本轮未执行故障注入。

已按[架构 §18](/D:/codespace/Access-Mesh/docs/design/access-service-architecture.md:750)复核：已接受的是续期丢失和提交后交付窗口；本项是失败请求遗留持续续期锁，不属于上述边界。

**建议：** 在 `PasswordResetLockSupport` 一处统一处理取锁结果与中断，使上述三个调用方共同受益。保留非等待竞争语义，但确保异步获取最终成功时必有释放责任：可以采用不会因线程中断而丢弃获取结果的等待方式，或显式处理放弃后的迟到成功并按原 owner 解锁。仅扩大同步 `try/catch` 后立即尝试解锁不足以覆盖迟到成功。补充确定性中断场景，验证失败后另一客户端能够重新获取该键。

### 专项核查结果

| 项目 | 第二轮反查结果 |
|---|---|
| 1. 全部入口与身份链 | 三个重置入口均接入公共组件；原登录、权限、默认树及平台身份检查保留。平台自助改密另有旧密码验证和管理锁，未混同为管理员重置。 |
| 2. 键隔离与锁顺序 | 两条租户恢复路径同键；不同租户、不同账号、平台身份域隔离。首管理员绑定仅在创建事务中写入。Redis 锁先于用户行锁、租户行锁或平台管理咨询锁。 |
| 3. 异常、事务与重入 | 无事务/同步时拒绝；占用返回 10015；Redis 异常不降级写入。注册回调失败有清理。正常提交、回滚及未知结果进入完成回调；中断例外见上述 P2。未发现现役嵌套重置调用链。 |
| 4. watchdog 与释放 | 普通异步解锁失败会取消续期，不能据此报告永久锁；正常释放位于数据库提交之后。取锁中断发生在释放责任建立之前，是独立缺口。[Redisson 依据](https://raw.githubusercontent.com/redisson/redisson/redisson-4.8.0/redisson/src/main/java/org/redisson/RedissonBaseLock.java)、[Spring 依据](https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.9/spring-tx/src/main/java/org/springframework/transaction/support/AbstractPlatformTransactionManager.java) |
| 5. 测试真实性 | 区分了手工回调、入口 mock、真实 PG/双 Redis 客户端及 MockMvc 接线。`PlatformAccountsPgIT` 使用真实新锁组件；其审计回滚用例针对状态变更，不能扩大为重置审计失败的专项证据。现有测试未覆盖上述中断窗口。 |
| 6. 副作用与前端 | 冲突在凭据写入和会话吊销前终止；平台恢复不恢复账号状态或角色。10015 可经响应解包显示，原 pending/busy 保留。未发现新增配置或抽象过重。 |
| 7. 反向扫描与其余改动 | 已反查密码写入点、全部生产调用方及失败出口。记住我残留裁剪、iframe 查询参数修复、异常日志脱敏及 Q-056 校准，未发现其他明确缺陷。结束 Git 状态与开始一致。 |

提交后再次重置使前次密码失效，按[契约 §7.7](/D:/codespace/Access-Mesh/docs/design/access-service-api-contract.md:793)明确接受，不列为缺陷。

既有报告核对：后端 **3000 项，失败/错误/跳过均为 0**；前端 **639 项通过**。这些是已有报告结果，不是本轮重新运行。

### 过度设计可裁剪项

无。

### 存量观察

- [TreeWriteLockSupport.java:88](/D:/codespace/Access-Mesh/access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/TreeWriteLockSupport.java:88)：也采用同步获取成功后才注册释放回调的结构，存在同类取锁中断窗口，属既有实现，未计入本提交缺陷数量。

### 总结

**P0：0；P1：0；P2：1；P3：0。**

建议修复公共锁组件的中断清理缺口后再收敛本次评审。