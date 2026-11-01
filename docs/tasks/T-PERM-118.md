---
doc_type: task
id: T-PERM-118
title: 角色类型支持面与管理提示核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
  - docs/design/frontend/role-manage.md
  - docs/design/frontend/permission-grant.md
depends_on: []
acceptance:
  - "核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。"
  - "形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。"
  - "用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。"
  - "核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §三.6、§四角色、§七 GROUP_ROLE/PERSONAL；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

管理页以 BASIC_ROLE 为主；GROUP_ROLE 写入口受限而有历史展开与清理，PERSONAL 生命周期预留，自定义类型主要经同步。底层可表达不等于产品已开放。

## 范围

核对类型下拉、手工管理/同步/历史读取/清理矩阵及拒绝提示，检查支持说明与实际调用；枚举拟裁剪机制的存量事实与消费者。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐先统一能力说明和提示，暂不扩展产品入口。备选经确认后退出具体历史支持；删除 GROUP_ROLE/PERSONAL 前须有支持范围决定与数据/测试替代方案。

待用户逐项确认：哪些预留和历史语义仍支持、是否裁剪、是否需要数据迁移；不能仅凭写入口隐藏推定可删除读链路。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/frontend/role-manage.md](../../docs/design/frontend/role-manage.md)、[docs/design/frontend/permission-grant.md](../../docs/design/frontend/permission-grant.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

