---
doc_type: task
id: T-ORG-003
title: "组织与岗位成员候选门禁统一"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: org-user
design_refs:
  - docs/design/iam-task-closure.md#member-candidates
  - docs/design/org-user-permission-contract.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "只有MANAGE_MEMBER或ASSIGN_POSITION_USER并有必要可见范围的管理员能选人和分配，不需要ORG:UPDATE。"
  - "同一管理员不能修改组织结构；无成员动作权、越租户、不可见默认树用户仍拒绝/过滤。"
  - "候选与提交门禁共用既有动作码解析，保留目标存在性、默认身份池及已绑定排除。"
  - "API组合测试为证据（真权限PgIT组合链：有限管理员候选→挂载→排除已绑定→改结构拒→无成员动作权拒→越租户拒，旧实现下主链403实证红）；浏览器分配链让渡 T-ACCESS-055 组合验收承接（2026-09-22 用户拍板，对应其 acceptance③）；相关权威文档冲突消除。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-22
---

# T-ORG-003 组织与岗位成员候选门禁统一

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F005；基线与静态/动态证据强度见该记录。实施与验证见完成记录。

## 范围

- UserAppServiceImpl.memberCandidates与UserOrgWriteAppServiceImpl.assign的目标类型解析和动作码复用。
- PositionTab及普通组织成员操作的选择/提交链路，校准总册旧UPDATE规则。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#member-candidates)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。验收④「浏览器分配链」按 2026-09-22 用户拍板让渡 T-ACCESS-055（registry 同日行）——本卡证据=真权限 API 组合链 + 前端权限口径静态核对，浏览器分配链为明确未验收项（T-ACCESS-055 acceptance③ 承接）。

## 完成记录（2026-09-22）

- **核实**：F005 属实——`UserAppServiceImpl.memberCandidates` 固定 `ORG:UPDATE@targetOrgId` 且门禁先于目标存在性校验；`assignUserToOrgs` 自 v1.4 起即按 `OrgOperationCodeMapper.resolveForUserOrg` 分组分桶判权（普通组织 MANAGE_MEMBER、岗位 ASSIGN_POSITION_USER）——候选与提交两门禁分叉，仅持成员动作权的有限管理员在选择器一步即 403（DDL 两操作位 inherit_mask=2 只继承 VIEW，无 UPDATE 覆盖；bootstrap 首管理员全码掩盖）。前端核对：PositionTab/UserDetailPanel 权限口径本就按成员码门控（`ORG:ASSIGN_POSITION_USER`/`ORG:MANAGE_MEMBER`），选择/提交链路无需改动，仅两处陈旧注释（user-manage.ts getMemberCandidates JSDoc、PositionTab.vue 载入注释）仍写旧门禁——事实性修正。
- **实现**：memberCandidates 门禁收敛唯一入口——先验目标组织存在（`ORG_NOT_FOUND`，与 setPrimaryOrg 同序）再 `resolveForUserOrg(targetOrg.getOrgType(), OperationCode.UPDATE)` 判权；可见范围裁剪（`getOperatorVisibleDefaultTreeOrgIds` ORG:VIEW 裁剪）与已绑定排除零改动。`org/update` 结构编辑门禁不动（成员动作权仍不能改组织结构）。
- **Q-025 随卡收敛**（registry 2026-09-21 绑定兑现）：`UserOrgAppServiceImpl.setPrimaryOrg` 默认树范围解析换绑 `OrgTreeConfigDomainService.resolveDefaultTreeOrgIds` 共享入口并删除私有副本（多配置 flatMap 与 get(0) 行为差随 uk_tree_config_default 唯一性消解；退化根由共享入口空返回统一折算 `ORG_TREE_CONFIG_NOT_FOUND`，正常形态等价）；顺带清同文件无调用方死 helper `isPositionOrg` 与未用 import；removeUserFromOrg Javadoc 旧 `ORG:UPDATE` 门禁表述同步校准。
- **回归锁**：`UserAppServiceMemberCandidatesGateTest` 门禁解析四锁（岗位→ASSIGN_POSITION_USER、普通/null→MANAGE_MEMBER、never 裸 UPDATE、目标不存在 ORG_NOT_FOUND 且不触门禁）——旧实现下 4/4 红实证；`MemberCandidatesGatePgIT` 真权限组合链（bootstrap 真链 + jdbc 直插授权行装配有限管理员 VIEW@默认根+ASSIGN_POSITION_USER@岗位，真实登录会话）：候选放行含默认树可见用户→挂载成功（sys_user_org+POSITION 容器投影落库）→已绑定成员排除→org/update 改结构 403→仅 VIEW 无成员动作权 403→越租户 10101——旧实现下主链 403 实证红（F005 症状复现）。
- **用户拍板（当轮 registry 登记）**：验收④「浏览器分配链」让渡 T-ACCESS-055 组合验收承接；本卡以真权限 API 组合链 + 前端静态核对为证据，验收④同步改写并明确未验收项。
- **夹具注记**：bootstrap 固定图业务门禁全 `canGrant=false`（转授链仅 API:ACCESS）、ORG 预置类型无 AUTHORITY_ROOT——apply-grant-plan 对 ORG 操作位无转授起点（20040），PgIT 授权行因此 jdbc 直插（授权消费面测试不依赖转授链）；「首管理员能否经正规入口委派 ORG 权限」属 T-ACCESS-052 U004 首次委派射程。
- **设计回写**：iam-task-closure §3.1 转已实施+实施口径；契约门禁总表（member-candidates 行+user-org assign/remove/set-primary 三行）与 §7.2/§7.5/§8.8/§8.9/§8.10 旧 `ORG:UPDATE` 句全面校准为按 orgType 解析成员码/ORG:VIEW 可见性口径（§7.2 验收句补存在性先于判权、候选范围措辞修准为 ORG:VIEW 机制；§7.5 user/delete 默认树二次校验措辞同批修准）；CHANGELOG [Unreleased] Fixed；registry 2026-09-22 收口行；pending-problems Q-025 关闭入已收敛索引；plan/看板状态随收口同步。
- **双轨评审处置（代码轨 P0-P2=0、P3×2；文档轨 P1×1+P3×2）**：文档轨 P1——pending-problems 已收敛索引表 Q-025 行误插表头与分隔行之间断表（GFM 插行坑）→ 移正（评审实证，直修）；文档轨 P3-1——收敛行「T-ORG-003 done」超前任务终态 → 措辞改「2026-09-22 随 T-ORG-003 收敛」（随 P1 同批）；文档轨 P3-2——校准面列举漏 §7.5 user/delete → 任务卡/设计稿/registry 三处补全；代码轨 P3-1——PgIT 主链①仅有下界断言（目标用户被可见性错裁仍可绿）→ 补 contains(targetUserId, limitedAdminId) 正向包含断言；代码轨 P3-2——单测 orgType=null 场景复用岗位语义常量 → 独立 NULL_TYPE_ORG 常量。类推发现登记 Q-034（可见性裁剪固定 VIEW 不按 VIEW_POSITION 精化——同 Q-032 精化码族，过严/过宽非越权，随 Q-032/T-ACCESS-055 拍板）。
- **收口验证**：全量回归（收口形态 `-T 1C` 含 E2E/heavy）全模块 SUCCESS、0 失败 0 错误（13:00 墙钟）；处置批次复跑双测试类全绿（单测 4 + PgIT 组合链）。
- **新发现登记**：Q-032（`UserWriteAppServiceImpl.createUser` orgId 分支裸 `ORG:UPDATE` 与成员码族分叉——契约与实现一致无漂移、非 F005 射程，改动会移动 user/create 权限面，收敛时机待拍板）；Q-033（契约总册 org CRUD 门禁行/正文未带岗位精化码——结构编辑族 doc-only 漂移，org-user-permission-contract v1.3/v1.4 为权威）；Q-034（双轨评审类推——可见性裁剪对岗位节点不按 VIEW_POSITION 精化，同 Q-032 族）。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记（Q-032），禁止把未知结果写为完成。
