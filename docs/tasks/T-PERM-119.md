---
doc_type: task
id: T-PERM-119
title: 授权计划处理阶段与目录装载核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
  - docs/design/dependency-auto-grant.md
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

来源：原报告（外评快照按定规不入库） §三.7；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

PermissionGrantPlanDomainServiceImpl 汇集身份/条件/转授/子权限/级联与审计准备；已有 prepare/apply 等阶段与复用服务，准备时读全租户操作目录。

## 范围

按稳定阶段绘制当前职责和真实复用点；核对窄化目录读取必须包含创建、更新、删除及级联子项的类型与审计解析，不只取 create 的 allKeys。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐保留单事务编排，仅在有具体维护困难时提取具体组件；相关类型范围读取单独评估收益和错误分类影响。备选保持结构并补说明，不引入自由组合策略或工作流引擎。

待用户逐项确认：提取哪些稳定阶段、如何保持验证顺序和事务、是否窄化目录及其收益；没有场景不按行数拆类。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)、[docs/design/dependency-auto-grant.md](../../docs/design/dependency-auto-grant.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

