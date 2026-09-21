---
doc_type: task
id: T-ORG-002
title: "默认身份目录删除与恢复边界闭合"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: org-user
design_refs:
  - docs/design/iam-task-closure.md#directory
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "默认根无子节点时删除仍明确拒绝，拒绝后管理事实、投影、配置和成员关系不变，重启正常。"
  - "删默认树叶子会消灭最后归属时按U001结论处理；直接移除与级联删除对同一业务结果一致。"
  - "普通可删组织成功路径、另一租户同ID/同码拒绝、事务故障注入及定点恢复副本验证有证据。"
  - "共享逻辑覆盖所有本任务实际影响入口，权威设计/错误码契约与可行动提示同步。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-21
---

# T-ORG-002 默认身份目录删除与恢复边界闭合

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F001：`OrgWriteAppServiceImpl.deleteOrg` 只查子节点后解绑/删组织——直接成员移除有最后归属保护（11013）而组织删除绕过它；隔离库删默认树后 `org/delete` 根成功、user/page 恒空、根 delete_flag=1、重启 exit1（bootstrap 固定图报默认配置根不存在）。树配置三写入口（javadoc 自认「后续实现必须增加迁移保护」）。

## 实施结果（2026-09-21 收口）

两项启动拍板（AskUserQuestion，registry 同日行）+ 双轨评审拍板（set-default 按拍板文字收紧）：

- **U001 拍板**：删默认树叶子致任一成员失去最后归属 → 整体拒绝 + message 含人数（不自动迁移到根）——与 10103/11013/20062 拒绝+可操作提示先例一致；组织删除与单移成员同码 11013 = 同一业务结果。
- **树配置守卫拍板（最小面）+ 评审拍板 A**：set-default 旧默认树**存在任一用户归属即拒**（扩围树同样拒绝，空租户可切）；update 改默认配置 rootOrgId 失去归属拒/安全扩围放行；delete 默认配置行无条件拒；create 根重叠校验留观（Q-024）。

落地明细：

- 共享守卫核心 `OrgTreeConfigDomainService.findUsersLosingDefaultHome(old, new)`（差集判定、两次批量查询、无逐用户 SQL）+ `resolveDefaultTreeOrgIds`（原 UserOrgWriteAppServiceImpl 内联 helper 下沉；assignUserToOrgs 主归属判定同批换绑）。
- `deleteOrg`：三树锁内、hasChildren 后——默认根无条件拒（11017，无子节点同样拒）；默认树内叶子批量判定失去归属拒（11013 含人数）；非默认树组织照常删除。拒绝先于解绑/投影删除/软删。
- `removeUserFromOrg`：内联过滤换绑共享守卫（成员场景等价；非成员请求由旧实现误拒 11013 修正为幂等放行，PgIT 锁定）。
- `OrgTreeConfigAppServiceImpl`：三入口挂 SYS_ORG 树锁 + 锁内重读配置（评审 P3-1 修复：守卫全部读取在锁内，防并发 set-default/update/deleteConfigs 交错窗口快照过期）；set-default 严格判定借共享守卫空保留集判定存在性；update 改根损失判定；deleteConfigs 默认行拒绝（selectDefaultConfigs 内存判定，零新 mapper 方法）。
- 新错误码：11017 `ORG_DEFAULT_ROOT_DELETE_FORBIDDEN`、11018 `ORG_TREE_CONFIG_DEFAULT_PROTECTED`（message 细分场景与人数）。
- 恢复面：[runbook-default-tree-recovery](../ops/runbook-default-tree-recovery.md)（只读诊断五节 + 定点恢复步骤 + 守卫清单；恢复演练 `DefaultTreeDirectoryGuardPgIT.defaultRootTombstoneRecoveryDrill` 为自动化副本验证）。

回归锁：单测 OrgWriteAppServiceTest +5 / OrgTreeConfigAppServiceImplTest +9（含拍板 A 分歧场景扩围树拒绝锁）/ UserOrgWriteAppServiceTest 新建 4；容器 `DefaultTreeDirectoryGuardPgIT` 新建 8（默认根拒绝零副作用/叶子失去归属解绑前拦截/级联与单移同码对照/非默认树成功路径/跨租户同 ID 拒/守卫通过后投影故障回滚/非成员幂等放行/根墓碑恢复演练）。红跑实证：守卫接线回退后新用例全红（4F+2E / 2F+2E / 1F+2E 三组）、恢复后全绿；既有 FaultInjectionIT 3/3 兼容（removeUserFromOrg/deleteOrg 换绑共享判定行为不变）。

双轨评审（2026-09-21）：代码轨 P2×1+P3×4、文档轨 P1×1+P2×3+P3×6，全处置——P2-1/P2-3（set-default 口径分歧）交用户拍板 A 收紧；P1-1 runbook tree_type='ORG' 修正；P2-2 诊断 SQL 改默认树内无归属语义；P2-1 看板同步；P3 全直修（树配置锁内重读×3、注释措辞+非成员幂等锁、PgIT 根 parent 对齐生产、任务卡口径更新、design_refs 收敛三册、三册 last_reviewed、§8 P1 行标完成）或登记（Q-024 create 重叠、Q-025 读面平行 helper）；不处置留观：单用户移除守卫成本随组织规模放大（拍板已知代价）、契约 §8.6 历史错误码段 10360-10389 存量错位（普遍形态不随本卡）、防御性 null 分支（empty 早退有真实触发，null 防御删除收益趋零）。

## 验收对照

- 默认根无子节点删除明确拒绝且零副作用（11017；PgIT deleteDefaultRootRejectedEvenWithoutChildren + 重启冲突由 bootstrap 检测实证链覆盖）；管理事实/投影/配置/成员关系不变（单测+PgIT verify never 双层）。
- 删默认树叶子失去最后归属按 U001 拒绝；直接移除与级联删除同码 11013（PgIT cascadedDeleteAndDirectRemovalShareSameOutcome 对照用例）。
- 普通可删组织成功路径（PgIT nonDefaultTreeOrgDeleteSucceeds）、另一租户同 ID 拒绝（crossTenantSameIdRejected）、事务故障注入（guardPassedThenProjectionFaultRollsBack + 既有 FaultInjectionIT deleteOrg 用例）、定点恢复副本验证（defaultRootTombstoneRecoveryDrill）。
- 共享逻辑覆盖所有实际影响入口（deleteOrg/removeUserFromOrg/setDefault/update/deleteConfigs 五入口 + 既有 ORG_CROSS_TREE_MOVE 移动守卫核实无需新增）；设计回写三册 + 契约 §8.6/树配置注记 + registry 两行 + runbook。

## 非目标 / 遗留

- Q-024：createOrgTreeConfig 根重叠校验（拍板最小面外，触发面=直连 API 建重叠树）。
- Q-025：UserOrgAppServiceImpl 读面 resolveDefaultTreeOrgIds 私有副本（触发面=异常态多默认配置，可随 T-ORG-003 收敛）。
- 单用户移除走共享守卫的成本随组织规模线性放大（两次批量 IN 查询）——拍板「消灭平行守卫」已知代价，如成热点可在 DomainService 增单用户重载（仍单点实现）。
- 契约 §8.6/§8.9 历史登记「错误码段 10360-10389/10430-10459」与现行 110xx 段错位为普遍存量形态，不随本卡清理。
