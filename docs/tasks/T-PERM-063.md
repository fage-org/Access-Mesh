---
doc_type: task
id: T-PERM-063
title: 角色互斥授权时校验——写路径拦截 + 存量立规守卫 + 双删日志与 detect 扩展
status: in-progress
plan:
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#55-授权关系（user-role/assign、batch-assign 错误码注记）
  - docs/design/permission-center/api-contract.md#56-高级能力（conflict-rule 契约要点：存量守卫 + detect 扩展）
  - docs/design/permission-center/core-flows.md#7-鉴权查询引擎评估口径（角色互斥授权时校验落地注记）
  - docs/design/permission-center/implementation.md#3-鉴权查询模块（定案①注记更新）与 #2-公共-domain-service-设计（PermissionConflictDomainService）
depends_on: []
acceptance:
  - "user-role/assign 与 batch-assign 写路径事务内互斥校验：授予后有效角色集（现有效 ∪ 本批新增，仅计启用角色）命中 ROLE_MUTEX 对 → 整批原子拒绝 20062（message 列出冲突用户与角色对），旧实现下该用例失败"
  - "conflict-rule/create、update 在 ROLE_MUTEX 分支存量守卫：存在同时持有两角色的用户 → 拒绝 20063（message 含冲突用户 id 清单，截断上限），旧实现下该用例失败"
  - "互斥规则读取走写路径专用 DB 直查（不经 ROLE_MUTEX_RULE 缓存），新建规则即刻生效于授予校验"
  - "filterRoleMutex 运行时双删补 CONFLICT_DETECTED 操作日志（含用户与角色对），带去重限流（每租户×用户×规则对 1 小时每 JVM 至多一条）"
  - "conflict-rule/detect 支持 ROLE_MUTEX 角色对检测：响应回传同时持有两角色的用户清单"
  - "api-contract §5.5/§5.6、core-flows 评估口径注记、implementation §2/§3 定案注记回写完成"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-12
---

## 背景

T-PERM-057 实施定案一（2026-09-09）将角色互斥（ROLE_MUTEX）从引擎管线剥离：授权写路径（user-role/assign、batch-assign）无任何互斥校验，唯一拦截点是快照构建与权限树的 `filterRoleMutex` 运行时双删（调用方自理）。「角色互斥授权时校验」登记为待补能力另行立项——本任务即该立项。

现状问题（代码级核实）：管理员给用户同时授互斥角色对会静默落库，用户登录后快照构建双删两角色 → 有效角色清空 → 全部 API 403，且双删无任何日志（PERM_MUTEX 有 CONFLICT_DETECTED，ROLE_MUTEX 静默无痕），用户详情页两角色仍显示持有，无页面能解释权限消失原因。用户持有角色的唯一通道是 user_role 直授（GROUP_ROLE 写入口已随 T-PERM-043 删除；组织/岗位在授权页拿的是权限行，不持有角色），校验点覆盖用户-角色写入口即无绕过通道。

## 范围

- `UserManageAppServiceImpl.assignRole` / `assignRolesBatch` 两条写路径事务内互斥校验（整批原子拒绝）。
- `ConflictRuleAppServiceImpl.createConflictRule` / `updateConflictRule` 的 ROLE_MUTEX 分支存量持有守卫。
- `filterRoleMutex` 运行时双删补操作日志（去重限流）。
- `conflict-rule/detect` 扩展 ROLE_MUTEX 角色对检测；前端冲突规则页检测对话框同步支持。
- 新增错误码 20062（授予冲突）/ 20063（存量持有守卫）。

## 当前口径

- **冲突处置 = 整批原子拒绝**：对齐 assignRole 既有「逐项收集 errors、整批抛、无部分成功」风格；校验语义为「授予后状态」检查（现有效角色 ∪ 本批新增目标，仅计启用角色），同批内两个互斥角色同样命中。
- **存量守卫 = 有持有对拒绝立规**：create/update ROLE_MUTEX 规则时存在同时持有两角色的用户则拒绝（20063），管理员先解绑再立规；立规后系统内无违规持有，运行时双删不再是常态兜底。remove 与 PERM_MUTEX 分支不适用。
- **规则读取 = 写路径 DB 直查**：授予校验与存量守卫均直查 `permission_conflict_rule`（不走 ROLE_MUTEX_RULE 10s TTL 缓存），新规则即刻生效；运行时 filterRoleMutex 维持缓存读取不变（快照链路 30s 安全边界设计不动）。
- **并发窄竞态接受**：两笔并发授予分别通过校验后交错提交仍可能形成互斥持有（user_role 无相关约束可焊），由运行时双删兜底（fail-closed 方向，无安全回退），不做锁串行化。
- **双删日志去重口径**：每（租户 × 用户 × 规则对）每 JVM 1 小时至多一条（有界内存去重表），多实例各自独立记账；对齐 PERM_MUTEX 的 CONFLICT_DETECTED 日志形态。

## 验收对照

见 frontmatter `acceptance`；回归锁要求旧实现下失败（新校验断言拒绝，旧代码放行即红）。

## 非目标 / 遗留

- applyGrantPlan（角色→资源权限）不涉及用户-角色持有关系，不做互斥校验（registry 2026-09-09 行将其并列属笼统表述）。
- 不为并发交错提交加锁串行化（见当前口径）。
- ROLE_MUTEX_RULE 缓存失效机制不动（维持快照链路 TTL 设计）。
- 前端仅冲突规则页检测对话框扩展；用户页/授权页零改动（错误 message 透传展示）。
