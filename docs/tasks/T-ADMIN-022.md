---
doc_type: task
id: T-ADMIN-022
title: 登录锁定临时化与账号状态语义统一
status: proposed
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
  status: pending
last_updated: 2026-08-23
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
