---
doc_type: task
id: T-PERM-115
title: 内部计算预算改为执行观测日志
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#query-observation-boundary
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
depends_on:
  - T-PERM-114
acceptance:
  - "旧内部阈值被跨越时不再因这些阈值拒绝，执行结果与无预算基线语义一致。"
  - "上下文/快照边界、DTO 校验、Gateway 截止、继承/互斥/条件/关系核验保持有效。"
  - "正常/失败/大规模日志符合定稿频率且无敏感载荷；保留观测不能改变主查询结果。"
  - "复用 QueryBudgetPgIT、引擎行为及观测测试，改为证明观测与独立边界；只在需证明 SQL/事务时跑 ItInfra 定向组。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §一.2、§三.12、§五测试；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

承接已确认的预算简化方向及 T-PERM-114 的实施口径；不再按原报告建议增加授权 SQL 返回上限。

## 范围

撤除已确认的内部规模/时间拒绝路径，接入定稿后的执行摘要日志；清理旧配置、异常包装、输出探测与相关断言，保留独立输入/传输保护及现有阶段指标。

## 当前口径

仅按 T-PERM-114 已定稿规则实施。超过旧阈值继续完整求值并如实观测；数据库/权限读取故障仍整体技术失败，不能静默返回半份事实。不得引入新预算框架、SQL 硬限、全局超时或绕过引擎的快路径。

权威回写位置：[docs/design/access-service-architecture.md#query-observation-boundary](../../docs/design/access-service-architecture.md#query-observation-boundary)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 旧内部阈值被跨越时不再因这些阈值拒绝，执行结果与无预算基线语义一致。
- [ ] 上下文/快照边界、DTO 校验、Gateway 截止、继承/互斥/条件/关系核验保持有效。
- [ ] 正常/失败/大规模日志符合定稿频率且无敏感载荷；保留观测不能改变主查询结果。
- [ ] 复用 QueryBudgetPgIT、引擎行为及观测测试，改为证明观测与独立边界；只在需证明 SQL/事务时跑 ItInfra 定向组。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

