---
doc_type: task
id: T-PERM-064
title: 角色互斥守卫通道补全——sync/full-sync BIND 逐条守卫 + 互斥规则拒 ORG/POSITION 对
status: done
plan:
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#56-高级能力（conflict-rule 契约要点：规则角色对类型值域）
  - docs/design/permission-center/api-contract.md#6222-同步接口通用响应与错误分类（ROLE_MUTEX_CONFLICT reason）
  - docs/design/permission-center/implementation.md#24-permissionconflictdomainservice-冲突规则
  - AGENTS.md#权限中心实现提醒（守卫通道口径更新）
depends_on: []
acceptance:
  - "user-role/sync、full-sync 的 BIND 分支挂授予守卫：upsert 将新增「启用且有效期覆盖当前时刻」的持有时，授予后有效角色集命中 ROLE_MUTEX 对 → 该 item 按通道语义返回 NON_RETRYABLE + reason=ROLE_MUTEX_CONFLICT（非管理面 20062 整批语义），零写库；UNBIND 不适用；旧实现下该用例必红"
  - "既有行有效期改写不新增当前有效持有的 upsert（如已有效行仅改期、或改为 future 生效）不做冲突检查（幂等更新不产生新违规）"
  - "conflict-rule/create、update 的 ROLE_MUTEX 分支拒绝 ORG/POSITION 角色对（VALIDATION_FAILED，结构角色由本地投影通道维护、UI 选择器本不提供）；角色行缺失维持既有惰性规则语义（存量观察，不随本任务收口存在性校验）"
  - "api-contract §5.6/§6.2.2.2、implementation §2.4、AGENTS.md 守卫通道口径回写；registry 当轮登记"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-12
---

## 背景

claude 外评 T-PERM-063（e7c972338 处置轮）P2-1 经代码级核实成立：`user-role/sync`、`full-sync` 接受 BASIC_ROLE/PERSONAL/自定义角色类型（`rejectReservedRoleType` 只拒 ORG/POSITION）且 BIND 分支无互斥守卫，是管理面 assign/batch-assign 两守卫入口之外的绕过通道——外部服务同步可造出双持，用户快照双删全 403，外部服务与管理员均收不到拒绝信号。用户拍板（2026-09-12）：守卫补全（sync 挂守卫 + 规则面拒 ORG/POSITION 对，投影通道结构性闭合）。

## 范围

- `UserRoleSyncAppServiceImpl.doSyncOneInternal` BIND 分支（sync 与 full-sync 共用单点）：upsert 前授予守卫，冲突按逐条错误信封拒绝。
- `ConflictRuleAppServiceImpl` create/update 的 ROLE_MUTEX 分支：ORG/POSITION 角色对拒绝。
- 契约/规范口径回写。

## 当前口径

- **sync 守卫语义=逐条 NON_RETRYABLE**：sync 是逐 item 错误信封协议（OWNERSHIP_CONFLICT/DEPENDENCY_MISSING 同款），不套管理面 20062 整批 BizException 形态；reason 码 `ROLE_MUTEX_CONFLICT`。
- **检查时机=「新增当前有效持有」**：新建行且「启用 + 有效期覆盖当前时刻」、或既有行由非当前有效改为当前有效（重激活）时检查 postState（现有效 ∪ 本目标）；已有效行的幂等改期不检查（违规已存在，拒更新不消除既有状态）、改为 future 生效不检查（不新增当前持有）——谓词与管理面守卫同源（镜像 selectValidByUserIdsWithValidity）。
- **规则类型值域=非结构角色**：ORG/POSITION 角色对拒绝立规——本地投影通道（组织/岗位成员关系投影）写的只有 ORG/POSITION 目标行，规则面拒绝后该通道结构性造不出违规；UI 选择器本就只提供 BASIC_ROLE/GROUP_ROLE。
- 守卫规则读取复用 `findAssignMutexConflicts` DB 直查（新规则即刻生效）。

## 验收对照

见 frontmatter `acceptance`；sync 守卫用例在旧实现下必红（旧代码直接 upsert 返回 applied）。

## 完成记录

- **提交链**：e7c972338（claude 外评处置轮，P2-1 拍板守卫补全）→ 4c0ea3d1c（本任务实施+回写+立项）。
- **来源**：claude 外评 T-PERM-063（deepseek-flash[1M]，headless plan 禁子代理）P2-1 经代码级核实成立——`rejectReservedRoleType` 只拒 ORG/POSITION，sync 通道接受 BASIC_ROLE/PERSONAL/自定义类型写入且 BIND 无守卫；用户拍板守卫补全（弃「登记不适用」与「只堵 sync」两案，registry 2026-09-12 行）。
- **回归证据**：sync 守卫四用例（冲突逐条拒/禁用放行/future 生效跳过/幂等改期跳过——旧实现下冲突用例必红）+ 结构角色对三用例 + `RoleMutexGuardPgIT` 第 5 用例（ORG 对拒绝/BASIC 对放行，真实 PG）全绿；收口全量 `mvn clean test -T 1C`（2026-09-12）BUILD SUCCESS——3324 项测试 0 失败 0 错误，E2E 模块 SUCCESS。
- **回写**：api-contract §5.6 要点（①′sync 面守卫 + 结构角色对值域）+ §6.2.2.3（ROLE_MUTEX_CONFLICT item reason）、implementation §2.4、AGENTS.md（全部用户-角色持有写入口必须挂守卫）、registry 当轮拍板行。

## 非目标 / 遗留

- 不改 sync 通道其余语义（归属校验/applyVersion/错误分类不动）。
- 不收口「规则对象 id 存在性校验」存量观察（createConflictRule 不校验角色/操作 id 存在性，维持惰性规则语义）。
- UNBIND/remove 不适用互斥守卫（回收不产生双持）。
