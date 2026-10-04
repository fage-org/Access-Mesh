---
doc_type: task
id: T-PERM-097
title: GROUP_ROLE 绑定面入口收紧——四入口拒绑组角色
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §10.4（user-role 管理与同步端点）
  - docs/design/access-service-api-contract.md §19.4（主体、角色、用户角色同步接口）
  - docs/design/access-service-api-contract.md §5.2（角色族 GROUP_ROLE 冻结口径，T-PERM-043）
depends_on: []
blocks: []
acceptance:
  - "管理面 assign/assignRolesBatch 拒绝以 GROUP_ROLE 角色为绑定目标：字符串层+类型值层双保险（对齐角色面 createRole 先例），错误码复用 20022 ROLE_TYPE_MISMATCH 不新增（红跑=旧实现绑上零权限假角色成功）"
  - "user-role sync（single）与 full-sync（scope 级）拒绝 GROUP_ROLE：字符串层、先于服务-类型白名单（对齐角色面 sync/fullSync 先例与 T-PERM-043 拍板顺序；红跑=旧实现经白名单声明通道可走到绑定）"
  - "持有侧/运行时组展开语义零改动（target_type='GROUP_ROLE' 存量行的既有展开与 RoleMutexGuardPgIT 等回归面全绿）"
  - "契约 §10.4/§19.4 落账绑定面拒绝口径，与 §5.2 角色族冻结口径互链；Q-027 以「新增侧场景经入口收紧不可达 + 双事实源技术债（role_inclusion 立项）覆盖」收敛"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-02
---

# T-PERM-097 GROUP_ROLE 绑定面入口收紧——四入口拒绑组角色

## 背景

原卡承接 [Q-027](../../../pending-problems.md#q-027)（新增分组角色互斥写守卫子树展开）。2026-10-02 实施核实发现任务前提与现实断层，经用户拍板改卡（原验收「新增侧展开子树」弃用）：

- Q-027 危害场景（绑定子树含 X 的组 G → 运行时展开后 X/Y 同场双删、原有 Y 静默失效）要求新增行 `user_role.target_type='GROUP_ROLE'`——运行时判定面与写守卫持有侧均按该字段判断组展开（`SubjectDomainServiceImpl` resolveEffectiveRolesBatch / buildRawHoldingsMultiset）。
- 该形态行现行无任何写入方：T-PERM-043（2026-08-25）删除全部专用绑定入口（且其写 abstract_user_id=null 违反 NOT NULL 从未成功写入过一行）、冻结角色面全通道；现行全部写入点（管理面 assign、sync BIND upsert、本地投影 UserRoleProjectionWriter、bootstrap）target_type 写死 `'ROLE'`。测试中 GROUP_ROLE 行均为 SQL 直插。
- 现行唯一活口=管理面/sync 以**存量 GROUP_ROLE 角色行**为绑定目标（resolveRoleId 按类型解析不拒、绑定面 rejectReservedRoleType 只拒 ORG/POSITION、绑定面服务-类型白名单可声明 role:GROUP_ROLE）：产出行 target_type='ROLE'，写守卫与运行时**一致不展开**（两侧行为一致，无「写时放行、运行时双删」分叉），不会发生 Q-027 的 Y 失效；实际后果是零权限假绑定（组角色不能配权限，用户/管理员误以为授权生效）。
- 真缺口即该活口：T-PERM-043 冻结口径漏了绑定面——角色面 create/update/sync/fullSync 全拒 GROUP_ROLE，绑定面四入口未拒。本卡补齐。

## 范围

四入口显式拒绝以 GROUP_ROLE 为绑定目标：管理面 assign（逐 item 类型码）/assignRolesBatch（单类型码）+ user-role sync single（req.roleTypeCode）/full-sync（scope.roleTypeCode；item 与 scope 一致性既有守卫覆盖 item 面）。错误码复用 20022 ROLE_TYPE_MISMATCH；管理面做字符串层+类型值层双保险（对齐角色面 createRole「按值双保险」先例），sync 面字符串层（对齐角色面 sync/fullSync 既有形态）。

## 当前口径

- 分组角色生命周期冻结（T-PERM-043）：绑定面与角色面同口径拒绝；delete/move 仍为存量 GROUP_ROLE 行清理通道。
- 「新增侧展开子树」不再实施：组模型将按 role_inclusion 单事实源另行立项（T-PERM-043「双事实源」技术债），届时组关系表达与展开语义随单事实源统一设计。

## 非目标 / 遗留

- 写守卫新增侧子树展开（Q-027 原设想）——随 role_inclusion 立项处理。
- 持有侧既有组展开语义（存量 target_type='GROUP_ROLE' 行）——零改动。
- 共享遍历参数化（Q-028 → T-PERM-101）、互斥计算语义（T-PERM-083 已定案）——不涉及。

## 完成记录（2026-10-02 收口）

**实施**（四处守卫，错误码复用 20022 ROLE_TYPE_MISMATCH，消息措辞对齐角色面「不支持绑定 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）」）：

- 管理面 `UserManageAppServiceImpl`：新增私有 `rejectGroupRoleBindingType(tenantId, roleTypeCodes)`（字符串层 + `batchResolveTypeValues("role_type")` 值层双保险，对齐 `RoleManageAppServiceImpl#createRole` 先例；批量版而非循环单值解析，守 §10 批量纪律），`assignRole`（M2 校验后、解析前）与 `assignRolesBatch`（domainCode 校验后）两处调用。
- sync 面 `UserRoleSyncAppServiceImpl`：`sync`（single）在 `rejectReservedRelationType` 后、服务-类型白名单前；`fullSync` 在 scope 级（`rejectReservedUserRoleSource` 后）——均字符串层，对齐角色面 sync/fullSync 既有形态；item 面由「item.roleTypeCode 须等于 scope」既有守卫覆盖（scope 级拒绝先于 item 校验，MISMATCH item 到不了绑定）。
- `revoke` 与 `revokeRolesBatch` 不加守卫（存量 GROUP_ROLE 行撤销/清理通道，对齐 delete/move 保留口径）。

**红跑实证**：单测 5 用例（assign 字符串层/assign 值层别名/batch-assign/sync single/fullSync scope）HEAD 上 5/5 红（旧实现不抛 20022：管理面三例成功落库、sync 两例走正常返回），实施后 46/46 绿；PG 真链路 `UserRoleWriteProjectionPgIT#groupRoleBindingShouldBeRejectedOnAssign`（JDBC 直插存量组角色 + 真实解析/门禁/互斥链）旧实现红（assign 成功落库）、新实现绿且 `user_role` 零新行。互斥回归面 `RoleMutexGuardPgIT` 9/9 绿（持有侧组展开语义零改动）。

**回归**：access-service 单测轨道全量绿；收口全量 `mvn test -T 1C`（含 E2E 与 heavy）BUILD SUCCESS 全模块 0 失败。

**回写**：契约 §10.4（assign/batch-assign/revoke 三行）、§10.5（T-PERM-043 收口清单改写——原「`user-role/assign|revoke` 对存量 GROUP_ROLE 行仍可用」废止为 assign 拒 20022/revoke 保留，运行时读模型冻结句独立保留）、§19.4（约束清单补 BIND 目标拒绝）、§2.4 跨字段校验注记与 §5 本地投影通道归属段两处「GROUP_ROLE 属可分配功能角色」残留订正；`UserAssignRoleReq.AssignItem` javadoc 的 domainCode 参数说明同步（GROUP_ROLE 从功能角色列举移除）。外围消费面核实：e2e 零 GROUP_ROLE 引用；前端收口时仅核角色管理页常量（MANAGEABLE_ROLE_TYPES 已收窄 [BASIC_ROLE]），用户详情分配选择器三类型候选漏核——外评发现并处置，见下「外评处置」。

**双轨评审**：代码轨（守卫顺序闭合性、fullSync item=scope 一致性覆盖、UNBIND/差异校准对齐角色面「外部通道全拒、管理面留清理」先例、值层未知码不误抛、mock 默认空 Map 不炸既有用例、批量无缓存解析 vs 循环单值的取舍——逐项实证通过，无 P0-P2）；文档轨（两处「GROUP_ROLE 可分配」残留直修；活文档 grep 清零）。存疑上报：无。过度设计可裁剪项：无（值层双保险对齐角色面既有先例，非新增机制重量）。

**存量影响口径**（对齐角色面 T-PERM-043 先例）：历史上经外部同步产生的 GROUP_ROLE 通道（若存在），收紧后 full-sync 稳态重放整批 20022——外部系统须先行清理该 scope；存量绑定行的撤销走管理面 revoke（不受影响）。

## 外评处置（2026-10-02，claude + codex sol 双通道首轮）

两通道独立核查一致：四入口守卫本体（写入口全集无第五旁路、字符串/值层不变量、守卫顺序与角色面先例同形、UNBIND/差异校准口径、既有回归面）无 P0-P1。处置：

- **P2×1（codex sol 定 P2 / claude 定 P3，同一问题，经用户拍板收窄修法）**：用户详情面板「分配功能角色」选择器仍按 `["BASIC_ROLE","GROUP_ROLE","PERSONAL"]` 装载候选，GROUP_ROLE 提交必收 20022 且前端只弹通用「分配失败」。处置：候选收窄为 `ASSIGNABLE_ROLE_TYPE_CODES=[BASIC_ROLE,PERSONAL]`（`frontend/src/views/system/user/utils/roleAssignCandidates.ts` + 组件接入 + 回归锁 spec，对齐角色管理页 T-PERM-043 收窄先例）；契约 §10.4 迁移落点句同步订正。展示/撤销面（`otherRoles`、revoke 通道）保留 GROUP_ROLE 存量行不动。
- **P3×1（claude）**：`UserRoleBatchAssignReq` javadoc 残留「GROUP_ROLE 属可分配功能角色」——同批孪生 DTO `UserAssignRoleReq` 已订正、batch 侧漏改，同款措辞补齐；`UserRoleBatchRevokeReq` 同句保留（revoke 确实仍接受）。
- 完成记录原「前端……本就传不出」核实结论不实（选择器恰为前端唯一 GROUP_ROLE 写面），已订正为如实口径。
- 排除项（两通道一致，核实采信）：sync UNBIND 同拒与角色面 sync-DELETE 同形（既定口径）；值层双保险理论触发面已被 `uk_type_definition_value` 唯一索引+系统种子封死（防御纵深保留）；permission-center 旧册陈旧句属 superseded 历史锚点。存量观察不处置：Q-057 复合键碰撞（T-ADMIN-030 在办链路）、契约 §10.5/§19.4「BIND 目标」措辞未点明 UNBIND 同拒（实现一致，措辞增强可选）。
