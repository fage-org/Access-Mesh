---
doc_type: task
id: T-PERM-121
title: 业务域分类与条件互斥解释边界核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md
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

来源：原报告（外评快照按定规不入库） §三.10/11、§四业务域条件互斥；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

业务域仅作管理分类；条件四态、写守卫与运行时互斥承担不同职责。完整诊断已有 Q-060 暂缓载体，不能重复立项或由本任务自动重启。

## 范围

核对域筛选是否被误解为隔离，目录装载是否可请求内批量复用；检查 reason/条件/角色与权限互斥的提示及现有解释覆盖。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐明确分类边界、复用请求内装载并沿用有限条件词表；诊断方向回链现有 Q-060。备选保持实现仅补使用说明；无性能证据不加分布式缓存，不加入脚本/SpEL/SQL 条件执行。

待用户逐项确认：具体解释缺口由现有字段修正还是需要重启 Q-060；任何完整诊断重做须用户单独解除暂缓。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

