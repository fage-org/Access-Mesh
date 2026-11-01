---
doc_type: task
id: T-ACCESS-155
title: 租户运营结果与运行时门禁表达核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/tenant-lifecycle.md
  - docs/ops/tenant-operations.md
  - docs/design/access-service-api-contract.md
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

来源：原报告（外评快照按定规不入库） §三.2、§四独立平台运营；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

数据库租户状态与 Redis 门禁发布可能不同时就绪；当前已有区分数据库与运行时状态的设计，单主 Redis 边界明确。

## 范围

核对启停、恢复、运营返回及页面对数据库已提交但门禁未就绪的表达；核对 Redis 进程身份、旧会话代次和真实恢复步骤的既有证据。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐仅补被证实缺失的返回/提示/运维步骤；备选维持当前实现并明确支持边界。单主 Redis 与当前恢复协议继续作为现有前提，不擅自扩到自动故障切换一致性。

待用户逐项确认：具体哪些运营入口存在表达缺口；是否需要产品状态字段、页面重试或恢复操作的改动。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/tenant-lifecycle.md](../../docs/design/tenant-lifecycle.md)、[docs/ops/tenant-operations.md](../../docs/ops/tenant-operations.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

