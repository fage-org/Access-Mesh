---
doc_type: task
id: T-PERM-099
title: 删除类型所有者角色引用守卫
status: done
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §13（type 能力：类型所有权与授权根）
  - docs/design/dependency-auto-grant.md §3.4（自动授权结果）
depends_on: []
blocks: []
acceptance:
  - "拍板删除语义（拒绝并提示先迁移 / 警告放行），按 decision-question-protocol 举例上报用户后落地"
  - "deleteRoles 对被 type_definition.extra.grantOriginRole 引用的角色按拍板处置：拒绝时错误码入契约 §13 错误族；警告放行时响应含后果提示且契约写明恢复路径（updateType 迁移所有者+重建授权根）"
  - "deleteRoles 列表入口（单条即单元素列表）同批覆盖守卫；回归锁以「删除所有者角色→类型首授/转授资格检查无人通过」场景实证旧实现无守卫"
  - "契约 §13 同步守卫口径与恢复路径"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-PERM-099 删除类型所有者角色引用守卫

## 背景

承接 [Q-023](../pending-problems.md#q-023)：删除类型所有者角色会回收其 AUTHORITY_ROOT 而类型保留，无引用守卫或提示——之后无人能通过该类型首授/转授资格检查，授权入口锁死；管理员删除时不知后果。可用 updateType 迁移所有者并重建授权根恢复，但无提示等于隐性陷阱。

## 范围

`RoleManageAppServiceImpl.deleteRoles` 补引用守卫（单条+批量）；守卫语义拍板后落契约 §13；错误码/提示按拍板形态入册。

## 当前口径

2026-10-03 用户三项拍板（AskUserQuestion 举例上报）：**硬守卫拒绝**（对齐引用面守卫先例 20056/20051）+ **整批拒绝**（任一命中整批不动，对齐 rejectIfLocalRole/T-PERM-056）+ **覆盖缺省引用**（无指针 is_system 预置类型按运行时缺省解析口径同源命中 bootstrap-admin，守卫无显式绕过通道）。双轨评审类推扫描命中 sync 通道绕过（角色 full-sync 漂移校准/单条 DELETE 同样软删并回收授权根，BASIC_ROLE 可进 sync scope），用户拍板**本卡扩面收口**（单条 DELETE=20073 抛出且先于版本推进；full-sync 校准=整单 NON_RETRYABLE，两段式校准防「元数据标 DELETED 但角色未删」漂移）。实现终态：

- `GrantOriginDomainService` 新增两面：`findOwnerPointerReferences`（经 `TypeDefinitionDomainService.selectByTenantAndTypeKey` 反查，Q-009 收敛读；显式指针匹配+无指针缺省命中同源；坏指针结构行跳过——jsonb 列挡非法 JSON，现实悬挂=合法 JSON 坏结构；修复通道=updateType 覆盖）与 `resolveGrantOriginReferenceDetail`（按角色 id 集合的守卫共用编排：装载→类型反解→判定→明细串）。
- `deleteRoles` 守卫置于级联展开后、任何软删/回收写前（`rejectIfGrantOriginOwner`）；判定集=直接目标∪级联子孙的最终删除集；命中抛新增 `ROLE_GRANT_ORIGIN_CONFLICT(20073)`，message 携带命中角色业务键与引用类型列表+迁移指引。
- `AbstractRoleSyncAppServiceImpl` 两落点：单条 DELETE 守卫在 applyVersion 前（拒绝不消耗同步版本，对齐环路判定与 GROUP_ROLE throw 先例）；full-sync 差异校准改两段式（先收集→守卫→markStatus/softDelete），命中整单 `fullSyncRejected(NON_RETRYABLE)`。
- 契约回写：§10.3 `remove` 要点补守卫句；§13.1 末新增「类型所有者与角色删除」段（守卫口径+恢复/迁移路径+坏指针边界+并发语义 best-effort 对齐 T-PERM-056 先例）；§19.4 补 sync 通道守卫条目。
- 回归锁：单测新增 8 用例（反查判定 4 + deleteRoles 守卫 2 + sync 两落点 2）；PgIT `RoleGrantOriginGuardPgIT` 5 用例（显式/缺省/级联/恢复闭环/坏指针不拦）；红跑双证——HEAD（stash 实现）下 PgIT 3 拒绝面用例红、sync 守卫禁用（临时补丁）下 2 用例红，放行面用例两态均绿（锁行为不变）。
- 前端零改动（`handleDelete` catch 透传 `error.message`，20073 指引与命中明细直达管理员）。

## 非目标 / 遗留

- 授权根重建的自动化（维持手工恢复路径）。
