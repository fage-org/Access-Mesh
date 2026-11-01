---
doc_type: task
id: T-ACCESS-158
title: 工程验证与发布支持范围核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/ops/deployment.md
  - docs/ops/engineering-baseline.md
  - docs/design/access-service-architecture.md
  - docs/design/testing-simplification.md
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

来源：原报告（外评快照按定规不入库） §五测试／数据库／构建部署、§七保留资产；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

E2E、两种 SDK 宿主探针与真实 PG 各有独有接线信号；子进程测试类路径有显式禁用 SDK 配置耦合。无自动旧库升级、文件单实例、Redis 单主、nginx 路由更新顺序均有现行边界。

## 范围

核对公开发布说明、恢复步骤、SNAPSHOT 安装与 CI/DTO 门禁；仅有真实类路径污染证据时评估少量打包产物验收；核对租户/软删/有效唯一与关系复合外键证据。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐保留现有验证资产并准确表达支持范围；备选在证据充分后补少量真实包发布验收。不要用测试全绿替代部署承诺，也不为消除限制建设复杂部署平台。

待用户逐项确认：是否调整打包验收和测试拓扑、是否新增升级/多实例存储/故障切换支持；外部 AI 评审仅用户另行显式触发。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/ops/deployment.md](../../docs/ops/deployment.md)、[docs/ops/engineering-baseline.md](../../docs/ops/engineering-baseline.md)、[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/testing-simplification.md](../../docs/design/testing-simplification.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

