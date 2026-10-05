---
doc_type: task
id: T-PERM-106
title: 管理员种子行锁死与转授前提声明
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§11 授权计划 removes/转授语义）
  - docs/design/dependency-auto-grant.md（来源与授权根先例）
depends_on: []
blocks: []
acceptance:
  - "GrantSource 新增种子来源值，bootstrap 种子行打标进入 assertMutable 只读边界；撤/改 bootstrap-admin 种子行被拒（旧实现下失败的回归锁）"
  - "转授出去的 MANUAL 行照常可撤（合法回收不受影响）用例"
  - "启动对账给存量种子行补标记，重启不 fail-fast（或按重建口径显式注明）"
  - "契约 §11 removes 只读边界+转授扩散前提（每次转授须对目标角色持 ROLE:MANAGE）语义段同步"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-PERM-106 管理员种子行锁死与转授前提声明

## 背景

撤权轨（apply-grant-plan removes）零委托校验：delegationKeys 仅由 creates/updates 构建（`PermissionGrantPlanDomainServiceImpl.java:340,355-356,495`），assertMutable 只挡 AUTO_DEP/AUTHORITY_ROOT（`:978-990`）——持 ROLE:MANAGE 者可无条件拆任何 MANUAL 行含 bootstrap-admin 管理位种子行，拆完即管理面死锁（无自动恢复；人工规程在 `docs/design/access-service-rebuild-runbook.md:104`）。另：四条 canGrant=true 种子（T-ACCESS-052 最小集）转授不衰减、链深无上限，其中 SERVICE:MANAGE=全租户凭证签发权。

## 范围

GrantSource 行级来源标记扩展+只读边界接入+契约语义声明。成员分配守卫（能力守卫）与最后管理员保护不在本卡（延后，Q-059）；转授衰减/链深不实施（Q-059 路线图同族，契约声明扩散前提）。

## 当前口径

GrantSource 加第四值（如 BOOTSTRAP_SEED），bootstrap 种子写入时打标；assertMutable 照 AUTO_DEP/AUTHORITY_ROOT 先例（T-PERM-062）纳入——种子行不可经 apply-grant-plan 改/拆，转授 MANUAL 行照常可撤；启动对账给存量种子行补标记。零角色名特判（保护跟行来源元数据走）。I3 默认树边界覆盖范围在契约标注；I5 审批为路线图登记。I2 维持最小集（2026-10-05 拍板），契约声明扩散前提与风险边界。

## 验收对照

- [ ] 种子行锁死回归锁（旧实现下失败）
- [ ] MANUAL 行可撤用例
- [ ] 存量补标记重启语义
- [ ] 契约 §11 语义段同步（removes 边界+转授前提）

## 非目标 / 遗留

- 能力守卫（绑入持 ROLE:MANAGE 行的角色要求操作者持同等能力+强审计）与最后管理员保护：Q-059 延后。
- 转授衰减/链深上限、审批机制：治理路线图，不实施。
