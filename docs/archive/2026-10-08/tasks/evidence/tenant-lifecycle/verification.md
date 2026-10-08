# Q-063 验收记录

日期：2026-10-08。结论：八项任务完成，Q-063 验收覆盖。代码轨与文档轨见同目录 [代码评审](code-review.md)、[文档评审](docs-review.md)。

## 执行结果

| 验证 | 结果与范围 |
|---|---|
| `mvn test -T 1C` | **passed**，2026-10-07 23:57:41 完成，耗时 16:50；382 个类报告合计 **2770 案例、0 失败、0 错误、0 跳过**。含全部容器轨、heavy 及 e2e 模块 |
| E2E 模块 | 上述全量内 **32 案例通过**：三条产品旅程 9+12+9，以及进程辅助 2 案例；所有租户通过运营开通入口建立 |
| 全量后的最小收尾验证 | `mvn test -pl access-service -Dtest=TenantOpeningPgIT,TenantNativeSessionTest,RequestContextInterceptorTest` **passed**：64 个单元案例 + 7 个 PG/HTTP 案例，0 跳过。覆盖新拒绝响应的追踪 ID 修正；未重复无关 heavy 轨 |
| `pnpm --dir frontend lint:check` | **passed**，ESLint、Prettier、Stylelint |
| `pnpm --dir frontend typecheck` | **passed**，TypeScript 与 Vue 类型检查 |
| `pnpm --dir frontend test` | **passed**，45 文件、**523 案例**；包含首次改密后 OAuth2 本地续接与外站拒绝 |
| `pnpm --dir frontend build` | **passed**，生产构建完成 |
| `pnpm --dir frontend contracts:check` | **passed**，43 组已登记 DTO 字段对账 |
| `git diff --check` | **passed** |
| 浏览器检查 | 平台／租户登录入口与实际布局核对；发现并修正平台路由误入租户布局，临时服务与标签已关闭 |

全量期间源码保持冻结，输出完整落盘后解析。最初全量暴露旧测试夹具未登记租户、未满足首次改密前提，以及网关配置测试缺少响应式 Redis 依赖；先隔离复现，再在测试侧补齐前提，相关 7+89 案例通过后执行上表完整回归。原生会话进程标记是用户确认后的行为增强，公共模块先 install 刷新 SNAPSHOT，再编译消费方。

## 验收覆盖

| 风险／行为 | 证据载体 |
|---|---|
| 独立平台身份、同号不同域、强制改密、凭据代次 | PlatformOperatorSessionTest、PlatformAuthenticationPgIT |
| 平台账号审计事务与并发最后启用管理员保护 | PlatformAccountsPgIT（含临时锁定不计入保护） |
| 原样 DDL 不种租户、参数化种子与编码约束 | AccessServiceSchemaPostgresTest |
| 开通完整模板、双租户隔离、失败回滚 | TenantOpeningPgIT、AccessBootstrapPgIT、DualTenantSameCodeIsolationPgIT |
| 凭据恢复不恢复停用／角色，删除原管理员拒绝并审计 | TenantOpeningPgIT |
| Lua 进程核验、旧发布令牌、长整型精度及畸形状态 | TenantGateStateTest、TenantGateRedisIT |
| 迟到恢复不能覆盖停用；失败停用回滚后的保守门禁与修复 | TenantOpeningPgIT 的确定性提交／发布屏障案例 |
| Redis 重启后恢复出的旧原生 token 不复活 | TenantNativeSessionTest（旧行为反例先失败）、TenantGateFilterTest、TenantGateRedisIT 的实际进程读取 |
| 强制改密必须不同密码、后端业务拒绝、完成后重新登录 | UserAppServiceResetPasswordGateTest、TenantOpeningPgIT、PlatformAuthenticationPgIT |
| 排队任务在执行前因停用被拒 | JobAppServiceImplTest（旧行为反例先失败） |
| 热权限快照、原生／OAuth2／M2M 启停恢复与身份隔离 | ExampleProtectedApiE2EIT.tenantLifecycleBlocksAllCredentialChannels |
| 平台与租户存储／HTTP 401／路由隔离、旧草稿不归租户 1 | 前端 auth、http、router、user store 测试及浏览器布局核对 |

## 交付边界

首次部署只初始化平台账号，租户全部从运营台开通。部署步骤与恢复规程见现行 `docs/ops/tenant-operations.md`；本次没有实际销毁部署数据库、发布服务或远端推送。Redis 首期为单主；客户内部最后管理员治理仍属 Q-059，未由平台身份体系替代。
