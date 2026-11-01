---
doc_type: task
id: T-ACCESS-157
title: 投影初始化与公共设施职责核查
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/access-service-capability-structure.md
  - docs/design/project-rules.md
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

来源：原报告（外评快照按定规不入库） §二 common、§三.14/15/18、§七保留机制；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

同库投影、固定图初始化、公共缓存/事务锁/任务租约/attempt fencing 有真实消费者；common 与 infrastructure 职责广，但原报告未证明需要新模块。

## 范围

核对本地投影门面委托、初始化/启动校验/存量升级边界、横向服务凭证生命周期及公共能力真实复用，定位重复表达或跨边界业务决策。

## 当前口径

当前仅纳入核查与方案定稿，未授权按推荐方向修改产品或代码。推荐只按真实职责整理包内委托和入口说明。备选维持结构；不合并管理/主体/资源表，不恢复异步投影，不用不断补 seed 代替升级协议，不新建 seed DSL。

待用户逐项确认：具体职责移动是否降低维护成本及影响范围；存量升级若需支持应另定协议和任务，不能作为本卡默认实现。 核查时须提供真实场景、数据/调用证据、现在未暴露的原因、推荐及备选方案的实际后果。

权威回写位置：[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/access-service-capability-structure.md](../../docs/design/access-service-capability-structure.md)、[docs/design/project-rules.md](../../docs/design/project-rules.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 核对原报告所列建议与当前实现/规范/已有任务，明确成立、已覆盖、已知边界或证据不足，不照单全收。
- [ ] 形成具体场景与可审阅方案；未确认项保持待定，不能记为接受风险或已经决定不改。
- [ ] 用户确认后的长期结论归 design_refs；需要实施时分配未占用实现任务并同步本计划、看板和依赖。
- [ ] 核查阶段仅做必要只读/最小验证；不默认写测试、跑全量或启动大范围重构。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

