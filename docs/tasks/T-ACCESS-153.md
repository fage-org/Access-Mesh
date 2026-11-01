---
doc_type: task
id: T-ACCESS-153
title: 现役架构规则与交付状态订正
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/engine/overview.md
  - docs/design/engine/implementation.md
  - docs/design/architecture.md
  - docs/design/project-rules.md
  - docs/design/services/gateway.md
  - docs/ops/deployment.md
  - docs/design/services/example-service.md
  - docs/design/resource-association.md
depends_on: []
acceptance:
  - "原报告 §六各项均有文件/章节级处置，涉及工程规范的同型表述一并核对。"
  - "现役文档明确区分已采纳目标、代码现状、交付事实和历史追溯；旧类名仅在历史语境出现。"
  - "链接/锚点/语义及过程词扫描通过；无生产代码、可执行 DDL 或未经批准的新安全策略变更。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §五数据库与部署、§六、§七现役文档；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

已核实：概览仍含同路径多资源 OR、退役 SnapshotAssembler 和有效角色 L1；无外键绝对规则与资源关系 DDL 冲突；安全头/限流和 example/关系交付状态有漂移。

## 范围

按具体主题修正原权威文件及 .claude/rules/security-standards.md 的冲突表述；以现行准入协议、schema、部署边界和交付证据核对，不按文档新旧简单取舍。

## 当前口径

保留当前完整路由匹配/歧义拒绝、有效角色 L2_ONLY、资源关系复合外键、TLS 终止层安全头及首期限流边界。未消解来源冲突先逐项交用户，不静默新增限流或删除外键。T-PERM-113/115 的新目标与未交付现状须同时准确表达。

权威回写位置：[docs/design/engine/overview.md](../../docs/design/engine/overview.md)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/architecture.md](../../docs/design/architecture.md)、[docs/design/project-rules.md](../../docs/design/project-rules.md)、[docs/design/services/gateway.md](../../docs/design/services/gateway.md)、[docs/ops/deployment.md](../../docs/ops/deployment.md)、[docs/design/services/example-service.md](../../docs/design/services/example-service.md)、[docs/design/resource-association.md](../../docs/design/resource-association.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 原报告 §六各项均有文件/章节级处置，涉及工程规范的同型表述一并核对。
- [ ] 现役文档明确区分已采纳目标、代码现状、交付事实和历史追溯；旧类名仅在历史语境出现。
- [ ] 链接/锚点/语义及过程词扫描通过；无生产代码、可执行 DDL 或未经批准的新安全策略变更。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

