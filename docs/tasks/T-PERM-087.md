---
doc_type: task
id: T-PERM-087
title: （R2-T08）投影、展示与范围四态
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.2/§3.3/§6.2/§6.4
depends_on:
  - T-PERM-086
blocks: []
acceptance:
  - "QueryProjector：GrantFact 与 PresentationEntry 分离——展示继承/操作覆盖展开不改 GrantFact.resourceEntityId、不将展示 INHERITED 写成真实 grantSource；hasCondition 与 conditionId 不一致时诊断而非降级为无条件"
  - "范围四态 G02~G05：raw 有覆盖 retained 无→EMPTY；无覆盖/目标 op 未知→DENIED；retained 含 scopeAll→ALL（不展开全量实例）；原有实例全部失效→EMPTY 非空 INSTANCE"
  - "A05：TRACE+scopeAll 短路显示 INSTANCE=SKIPPED 不补查；OutputSpec 不能关闭判定必需计算；extraOperationKeys 不扩大 Selection"
  - "接入 T-PERM-084 读取部件的输出入口，在整体投影链验证 I02/I03/I06；extraOperationKeys 采用 Set<TypeOperation>，无授权时仍可补全目标定义"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-26
---

# T-PERM-087 （R2-T08）投影、展示与范围四态

## 背景

设计 §3.2/§3.3 真值/阶段事实/展示三分与 §6.2 范围四态（报告临时编号 R2-T08）。ScopeCoverageProjector 只消费结果和已装载定义，不再查授权/条件/规则。

## 范围

- rawAfterContext 定义（上下文绑定处理后、条件/互斥前）；Details 用 loadedSections 区分「没请求」与「请求后为空」；内部父 matchedPermissionIds 即使不展示也计算。
- 展示父子展开/有效操作投影与现行 expandByPresentMode/EffectiveOperationEntry 语义对齐（判定后克隆不改判定）。

## 非目标 / 遗留

- TRACE 敏感字段门禁（IP 规则/角色 ID 仅受权诊断）随 T-PERM-088 审计卡收口。
