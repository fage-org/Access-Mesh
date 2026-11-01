---
doc_type: task
id: T-PERM-114
title: 查询预算退役范围与日志口径定稿
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#query-observation-boundary
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
depends_on: []
acceptance:
  - "形成每个 EngineLimits 字段与全部调用点的保留/退役映射，明确 exclusiveScope 的输出探测读取去向。"
  - "定稿 executionId、输入/展开规模、累计读取/闭包/候选/输出/证据数量、耗时与终态字段；按已确认 DEBUG/WARN 规则输出，无逐行/敏感载荷日志。"
  - "明确 BUDGET_EXCEEDED 的退役/保留分类，不把剩余上下文/快照错误混同普通超载日志。"
  - "目标规则回写后才启动 T-PERM-115；不把规划检查冒充性能实测。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §一.2、§三.12、§五测试；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

原报告发现授权 List 返回后才检查行数与 deadline；核实时未证明生产 OOM。当前已选择撤除内部计算规模和时间硬拒绝，改用运行日志观察真实负载。

## 范围

逐点核对 EngineLimits/RunState/读取/闭包/候选/投影/审计/快照装配的计数与异常分类；定稿日志字段、输出频率/阈值与异常终态、旧配置迁移、被保留边界的归属，列出实施清单。

## 当前口径

目标以服务架构查询观测边界为准。已确认撤除内部计算数量与引擎时间硬拒绝，保留上下文/快照及其他独立边界。正常执行一条 DEBUG 摘要，任一原规模/时间阈值触发时同次执行仅汇总一条 WARN；原阈值只作观测，不拒绝请求。最终实施清单及保留配置的组织须与该目标一致，出现新产品取舍再报告。

权威回写位置：[docs/design/access-service-architecture.md#query-observation-boundary](../../docs/design/access-service-architecture.md#query-observation-boundary)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)、[docs/design/access-service-api-contract.md](../../docs/design/access-service-api-contract.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 形成每个 EngineLimits 字段与全部调用点的保留/退役映射，明确 exclusiveScope 的输出探测读取去向。
- [ ] 定稿 executionId、输入/展开规模、累计读取/闭包/候选/输出/证据数量、耗时与终态字段；按已确认 DEBUG/WARN 规则输出，无逐行/敏感载荷日志。
- [ ] 明确 BUDGET_EXCEEDED 的退役/保留分类，不把剩余上下文/快照错误混同普通超载日志。
- [ ] 目标规则回写后才启动 T-PERM-115；不把规划检查冒充性能实测。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

