---
doc_type: task
id: T-ADMIN-022
title: 登录锁定临时化与账号状态语义统一
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/default-org-tree-user-lifecycle.md
depends_on: [T-ORG-001, T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "复用现有 login:fail:{tenant}:{user} 计数键剩余 TTL 作为临时锁定唯一状态源：达到阈值（5）后直接拒绝登录，键过期自动恢复可登录；不新增第二个 Redis 锁键（lockUntil 等），不新增 locked_until 持久化字段"
  - "删除 recordLoginFail 达阈值后的 lockUser() 编排（含 sys_user.status=2 写入与投影禁用链路）；Lua INCR+EXPIRE 原子计数保留不动"
  - "checkAccountLocked 改为 GET 现有计数，替换 increment(key, 0)（后者会为不存在用户创建无 TTL 的零值键）；登录成功删除计数键（现有 clearLoginFail 保留）"
  - "sys_user.status 语义统一为 0/1（管理员手工启停）：DDL 注释（0=停用,1=启用）与实体注释（0=正常,1=禁用）矛盾收口为单一口径并回写契约；状态接口仅接受 0/1"
  - "锁定（临时，自动恢复）与停用（永久，管理员操作）的登录提示可区分（错误码/文案），前端提示适配随本任务（自 T-FE-041 验收移入）"
  - "单测覆盖：计数达阈值 → 拒绝且 status 未被修改 → 键 TTL 过期后可登录 → 登录成功清键；不存在用户登录不产生零值键；真实 PG 用例覆盖 status 字段仅 0/1 写入"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-25
---

# T-ADMIN-022 登录锁定临时化与账号状态语义统一

## 背景

登录失败计数达阈值（5 次）后 recordLoginFail 调用 lockUser() 持久化 `sys_user.status=2` 并禁用权限主体投影，Redis 30 分钟过期后无任何机制恢复数据库状态——临时锁定变成永久停用，只能人工改库救回。且 status 语义三处矛盾：DDL 注释 0=停用/1=启用，实体注释 0=正常/1=禁用，代码写 2（两边契约均未定义）。

已核实的最小修复面：现有计数键经 Lua INCR+EXPIRE 已带 30 分钟 TTL，达到阈值后凭剩余 TTL 拒绝即构成临时锁，无需第二个状态源；`checkAccountLocked` 当前用 `increment(key, 0)` 读取（AuthServiceImpl:439），会为不存在用户创建无 TTL 零值键，需改 GET。本任务在 T-ORG-001 之后实施（同触 AuthService 与用户写链路，避免并发改同一链路）。

## 范围

- AuthServiceImpl 锁定路径：删 lockUser 编排、checkAccountLocked 改 GET。
- status 语义统一（DDL 注释、实体注释、契约文档、前端状态展示口径）。
- 锁定/停用两类提示区分及前端适配。

## 当前口径

- 计数键即锁：键的存在与剩余 TTL 表达锁定状态，不引入第二个 Redis 键或持久化字段。
- 不做自动解锁调度任务（TTL 到期即解锁）。
- 若未来必须持久化锁定，另立明确字段，不复用启停状态。

## 非目标 / 遗留

- 不改失败计数窗口/阈值参数语义（30 分钟/5 次保留）。
- 不做多实例锁定广播优化（Redis 原子计数已满足）。
- smsLogin 失败计数与锁定检查缺口（短信验证码错误不计数、不查锁定）——经用户决策本任务不动；短信通道可控、验证码一次性，风险有限，如需收口另行立项。

## 用户决策（2026-08-25 执行期确认）

| # | 决策点 | 决策 |
|---|--------|------|
| 1 | /user/update 的 status 是否加 0/1 校验（现状可写 2/任意值） | 加校验（与 /user/enable 同款 INVALID_PARAM，文案统一「停用」措辞） |
| 2 | 锁定提示文案口径（实际剩余时间递减，固定文案临近过期失真） | 固定 30 分钟（「登录失败次数过多，账号已临时锁定，请30分钟后重试」） |
| 3 | 锁定拒绝是否补记 sys_login_log（检查在日志记录之前，原无痕迹） | 补记（failReason=「登录失败次数过多，账号临时锁定」，userId 允许回填） |
| 4 | smsLogin 是否接入临时锁定检查 | 本任务不动（登记遗留） |
| 5 | forceResetPwd 提示适配归属冲突（T-FE-041/login.md 声称归本任务，任务卡验收无此项） | 一并做：登录成功后非阻断 warning「当前密码为初始密码，请联系管理员重置」（系统无自助改密通道，阻断式无落地条件） |

## 执行记录（2026-08-25）

**后端（access-service）**：

- `AuthServiceImpl`：
  - `checkAccountLocked` → `isAccountLocked`（返回 boolean）：`increment(key, 0)` 改 `opsForValue().get(key)`（GET 只读不建键；脏值按未锁定处理并 warn）；拒绝处置上移 `login()`——抛 10004 固定文案 + 补记登录日志（userId 回填，user==null 时为 null，与「用户不存在」失败日志同构）。
  - `recordLoginFail`：删除达阈值后的 lockUser 编排（findByUsername + userWriteAppService.lockUser），仅保留 Lua INCR+EXPIRE。
  - `login()`：删除 status==2 检查分支（临时锁定不再落库，该分支不可达）。
  - 删除 userWriteAppService 构造依赖与 import。
- `UserWriteAppService`/`UserWriteAppServiceImpl`：删除 `lockUser`（含 @OperationLog USER_LOCK、batchUpdateStatus(2)、disableAdminUser、change_log、markUsers 编排）；`updateUser` 补 status 0/1 校验（INVALID_PARAM）；`updateStatus` 错误文案措辞「禁用」→「停用」。
- status 注释收口（单一口径 0=停用，1=启用）：`SysUser` 实体、`UserUpdateReq`（两处矛盾注释）、`UserQuery`/`UserPageReq`/`UserResp`/`UserPageItemResp`/`UserUpdateStatusReq`/`UserDomainService`。其它表（Org/Dict/Oauth2Client 等）的 status 注释措辞不在本任务范围。
- 测试：删 `UserWriteAppServiceLockTest`（lockUser 专属）；`PlatformSessionIdleTimeoutTest`/`PlatformSessionAbsoluteTimeoutTest` 的 `increment(key,0)` mock 改 `get`；`OperationLogAspectTest.lockMethod` 注释去 lockUser 引用（切面能力测试本体保留）。

**测试新增**：

- `AuthLoginLockTest`（单测 4 用例）：阈值拒绝（10004+文案+不写库+补记日志+计数 Lua 不再累加）/ 键过期可登录且清键 / 不存在用户不产生零值键（never increment 回归）/ 阈值未达不拦截。
- `LoginLockTemporaryPgIT`（真实 PG+Redis 2 用例）：失败 5 次锁拒不落库（每步断言 DB status=1）→ DEL 键模拟 TTL 过期 → 正确密码恢复 200 → 锁定拒绝补记 sys_login_log；/user/update status=2 拒绝（10008，自我修改豁免直达校验）+ 合法值 1 写回成功。

**前端**：

- 登录页 `login/index.vue`：成功分支消费 `LoginResp.forceResetPwd`——非阻断 warning「当前密码为初始密码，请联系管理员重置」（duration 6s；用户决策 #5）。
- 用户页措辞统一「禁用」→「停用」：`MemberTab.vue`（switch inactive-text/下拉 option/二次确认对话框/操作提示）、`UserDetailPanel.vue`（状态点文案）。
- 锁定/停用登录提示经既有 message 透传天然区分（10004 临时锁定文案 vs 10003 用户已停用），无需前端特判。

**设计回写**：

- `admin-service-api-contract.md`：§4.1.4 /user/update status 字段细化（仅接纳 0/1，违规 INVALID_PARAM）；§4.1.6 /user/enable 措辞统一「停用」+ status 单一口径设计决策块（临时锁定不落库，历史 status=2 已删除）。
- `default-org-tree-user-lifecycle.md` §5.1：`enabled 跟随 sys_user.status` 补单一口径说明。
- `docs/design/frontend/login.md`：交互流程补 10003/10004 区分语义与 forceResetPwd 提示落地；「提示适配归 T-ADMIN-022」改「已随 T-ADMIN-022 落地」。
- `docs/design/schema/access-service.sql`：DDL 注释本已正确（0=停用，1=启用），未改动。

**验证**：

- access-service 单测轨道 698 tests 0 failures（含新增 4）；容器轨道 LoginLockTemporaryPgIT 2 用例真实容器通过；PlatformSession×2 / OperationLogAspect / ErrorCodeContract 定向回归通过。全量双轨验证见提交。
- 前端 typecheck / 210 单测 / lint / 生产构建（vite build，Windows 下 NODE_OPTIONS 内联执行）全绿。

**遗留登记**：smsLogin 无失败计数与锁定检查（用户决策 #4，另行评估）；系统无用户自助改密通道（forceResetPwd 只能提示引导联系管理员，如需自助改密另行立项）。

## 外部评审与修复收口（2026-08-25，codex gpt-5.6-sol xhigh）

评审范围 `1b367fff1..HEAD`（前 4 提交），结论 0 致命 / 1 高 / 5 低，逐条核实全部属实并处置：

| # | 级别 | 评审发现 | 核实与处置 |
|---|------|----------|------------|
| 1 | 高 | `/user/create` 的 `UserCreateReq.status` 无校验，可写 status=2：投影 `isEnabled(2)=false` 停用但登录不拒 → 「认证成功+权限主体停用」事实分裂，违反「仅 0/1」验收 | **修复**：`createUser` 补 0/1 校验（INVALID_PARAM，与 update/enable 同口径）；认证侧 `login`/`smsLogin` 停用检查改 `status != 1` fail-closed（与投影 isEnabled 对齐，任何未定义值不再进入会话）；新增 `UserWriteAppServiceCreateStatusTest` 2 用例（status=2 拒绝且零写入、status=0 投影同步停用）；契约 §4.1.3 status 描述补校验说明 |
| 2 | 低 | 停用与临时锁定重叠时提示优先级错误：锁定检查先于停用检查，被锁定又被管理员停用的账号仍提示「30分钟后重试」误导 | **修复**：停用检查（管理员事实，10003）提前至临时锁定检查（10004）之前；`AuthLoginLockTest` 新增重叠用例固化优先级 |
| 3 | 低 | 容器测试未断言失败计数键带 TTL（永不过期键会使锁定变永久，测试仍会通过） | **修复**：`LoginLockTemporaryPgIT` 补 `getExpire(lockKey) > 0` 断言 |
| 4 | 低 | 残留术语：architecture 文档仍以已删除的 `lockUser` 作匿名租户解析示例；`UserUpdateStatusReq` 类级注释仍写「禁用」 | **修复**：示例更新为通用表述（解析能力保留、历史示例已删）；类注释两处改「停用」 |
| 5 | 低 | 两处格式回退：`login()` Javadoc 丢失类内缩进；登录页内联样式经 lint 属性重排后格式异常 | **修复**：恢复缩进；内联样式整理为 `width: 120px; height: 40px` 并经 lint 复跑确认稳定 |
| 6 | 低 | 前端新增行为（forceResetPwd warning、停用确认）无组件测试 | **用户决策不补**：均为单行 if/文案级逻辑，组件测试需 mock initRouter/router/message/ElMessageBox 成本高断言价值低，按任务卡「测试层声明适用的最小层、不补无价值测试」原则登记豁免 |

修复后验证：单测轨道 702 tests 0 failures（新增 AuthLoginLockTest +2 至 6 用例、UserWriteAppServiceCreateStatusTest 2 用例）；LoginLockTemporaryPgIT 2 用例（含 TTL 断言）真实容器通过；前端 lint 全绿且格式稳定。

## 第二轮外部评审与修复收口（2026-08-25，codex gpt-5.6-sol xhigh 复审）

复审范围 `1b367fff1..HEAD`（含一轮修复提交 6e32ad787），结论 0 致命 / 0 高 / 1 中 / 3 低，逐条核实全部属实并处置：

| # | 级别 | 评审发现 | 核实与处置 |
|---|------|----------|------------|
| 1 | 中 | `forceResetPwd` 端到端语义不闭环：`/user/reset-password` 只更新密码不置 `force_reset_pwd=true`（DDL 语义「管理员重置后」），被重置账号登录不触发本任务新增的 warning；且创建/重置成功弹窗提示「登录后自行修改」与「系统无自助改密通道、联系管理员」决策矛盾 | **修复**：`UserServiceImpl.resetPassword` 补 `setForceResetPwd(true)`（密码不进投影，仅管理事实列）；两处弹窗文案改为「请将密码通知用户妥善保管；用户登录后系统将提示联系管理员修改密码」；契约 §4.1.7 同步动作补 force_reset_pwd 置位说明 |
| 2 | 低 | 一轮修复的回归测试未钉死契约：smsLogin fail-closed 无用例；create status=2 用例未断言错误码 10008（仅断言异常类型与文案） | **修复**：AuthLoginLockTest 新增 smsLogin status=2 → 10003 用例（7 用例）；CreateStatusTest 补 `errorCode == 10008` 断言 |
| 3 | 低 | 术语残留 5 处：`CANNOT_DISABLE_SELF` 运行时文案「不能禁用当前登录用户」、AdminUserController/UserDomainService javadoc、契约 §3 摘要表、前端 user-manage.ts API 注释 | **修复**：全部统一「停用」措辞（枚举名与码值不变，仅文案/注释） |
| 4 | 低 | 计划进度行验证证据停留在一轮评审前（698/4 用例） | **修复**：计划进度补两轮评审收口记录（703 单测/AuthLoginLockTest 7 用例/容器 90） |

二轮修复后验证：单测轨道 703 tests 0 failures；LoginLockTemporaryPgIT/LoginSessionPgIT 定向容器通过；前端 typecheck/210 单测/lint 全绿。
