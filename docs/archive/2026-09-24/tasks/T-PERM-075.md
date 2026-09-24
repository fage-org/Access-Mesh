---
doc_type: task
id: T-PERM-075
title: "互斥角色有效期与判定入口一致性"
status: done
plan: docs/archive/2026-09-24/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#mutex
  - docs/design/engine/overview.md
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks:
  - T-ACCESS-055
acceptance:
  - "U002在实现前形成可执行决定并回写替代的registry条目；未来重叠有效期、禁用后绑定再启用均有具体预期。"
  - "同一主体/时刻下check、batch、管理门禁、scope、接口快照及相关菜单消费的互斥语义一致。"
  - "写守卫仍检查原始候选；间接持有、存量双持和规则启停有适用反例，无N+1或过度缓存时间态。"
  - "用可控时间/同步机制覆盖边界并验证撤销失效，不新增到点调度维持正确性。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-22
---

# T-PERM-075 互斥角色有效期与判定入口一致性

## 背景

承接[评审证据](../../../archive/2026-09-20/comprehensive-review.md)的 F004、D002；基线与静态/动态证据强度见该记录。实施与验证见完成记录。

## 范围

- 角色新增持有/改期/启停、冲突规则变更和SubjectDomainService/PermQueryEngine/接口快照的共同有效角色语义。
- 保留原始持有候选供写守卫；统一运行时过滤与失效，不把过滤职责移动成层级循环。

## 当前口径

已实施（2026-09-22）。U002 两项拍板与共同判定语义取代关系登记 [decision-registry](../../../design/decision-registry.md) 2026-09-22 行；实施口径见 [IAM 闭环方案 §2.4](../../../design/iam-task-closure.md#mutex) 与 [implementation §2.4](../../../design/engine/implementation.md)。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

## 完成记录（2026-09-22）

- **U002 两项拍板**（AskUserQuestion）：①未来有效期重叠=写时拒绝（区间交闭区间口径，null=无限期、首尾相接同刻算重叠，真正不相交放行；已过期不计；运行时保留双删兜底并发窄竞态）；②禁用通道=绑定写时堵死（候选纳入禁用持有与禁用新增目标；启用动作保持全局性不查）。
- **写守卫**：`SubjectDomainService.batchResolveRawHoldings/resolveRawHoldings`（`RawHolding`=roleId+窗口；未过期原始行 ∪ 组展开含禁用子树并继承绑定行窗口；不缓存 DB 新鲜读）；管理面 assign/batch-assign 与 sync/full-sync BIND 守卫统一消费（full-sync 批内一次预载消 N+1，appliedThisBatch 窗口补偿）；改期「幂等改期不触发」随窗口重叠判定消解；20063 收敛同口径。
- **共同判定语义**：`PermissionConflictDomainService.resolveJudgementRoleIds`（=resolveEffectiveRoles+filterRoleMutex）统一引擎 query/queryBatch 解析分支、getDenied\* 两便捷入口、菜单/权限串 buildEffectiveView、接口快照（快照专有过滤消除）；显式 roleIds 分支不过滤；EFFECTIVE_ROLES 缓存语义不变（规则变更沿 ROLE_MUTEX_RULE 10s TTL 收敛，空规则集也缓存防穿透）。取代 2026-09-09「ROLE_MUTEX 不归引擎」定案（registry superseded 表）。
- **回归锁**：单测（管理面未来窗口/禁用目标/持有禁用对端/已过期不触发、sync BIND 未来窗口/改期重查/过期不触发/批内互斥、20063 禁用计入、区间交边界（不相交放行/首尾相接算重叠）、空规则缓存、引擎 verify 统一入口、full-sync N+1 次数锁）+ RoleMutexGuardPgIT 端到端四用例（未来重叠拒绝/不相交放行、禁用通道双向、判定入口一致+撤销失效+规则软删恢复、禁用再启用双删）。
- **双轨评审处置**：代码轨四处陈旧 javadoc 直修、三件死代码删除（`selectEnabledRoleIds` 接口方法/`PermQueryEngine.subjectDomainService` 字段/`PermQuery.useRoleCache` 死开关）；文档轨 implementation 签名行/AGENTS 旧口径两处/overview/skill 双副本/detect 行/registry 章节号与粗体/两册 last_reviewed 同步；「当天算重叠」统一「同刻」。
- **claude 外评处置**（2026-09-22，P0-P2=0/P3×3 全核实全采纳，处置见 registry 同日外评行）：①full-sync 稳态重放规则查询批内一次预载（`loadRoleMutexRulesFresh`+三参重载，消树锁内逐 item 直查）+次数锁；②倒置窗口（from>to）判定前剔除（运行时恒假=空窗，`neverEffective` 单点三面共用）+放行回归锁；③规则文档 permission-coding-standards.md §2/§11 重写为判定面（resolveJudgementRoleIds）/写守卫（batchResolveRawHoldings）双入口。存量观察 3 条留观（管理面已过期持有静默跳过/多互斥对双删顺序/GROUP_ROLE 端规则恒不命中）。
