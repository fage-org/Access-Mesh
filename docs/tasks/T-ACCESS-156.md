---
doc_type: task
id: T-ACCESS-156
title: example 最小接入与完整样例分工核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/services/example-service.md
  - docs/design/example-permission-demo.md
  - docs/design/example-service-api-contract.md
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

来源：原报告（外评快照按定规不入库） §二 example、§五 e2e、§七样例外壳；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

内存 report 探针与持久化演示分别有首次接入及完整行为证据，文档已有入口说明；内存任务不承诺持久恢复或长期清理。

## 范围

走查 first-access 与 permission-demo 的真实使用旅程、重复外壳和独有验收，校核内存 Map 任务与真实 SQL 行列权限的教学定位。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐明确最小接入/完整演示导航和支持说明，保留独有路由与测试。备选合并入口但保留证据；删除探针或改变示例承诺须先列替代链路。

待用户逐项确认：是否仍有具体学习障碍、需修改哪些入口；是否合并外壳及其兼容成本，不能以已有持久版本直接裁掉内存样例。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/services/example-service.md](../../docs/design/services/example-service.md)、[docs/design/example-permission-demo.md](../../docs/design/example-permission-demo.md)、[docs/design/example-service-api-contract.md](../../docs/design/example-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

