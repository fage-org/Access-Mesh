---
doc_type: task
id: T-ACCESS-151
title: 审计饱和与事务连接占用验证
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/engine/implementation.md
depends_on: []
acceptance:
  - "记录外层事务资源是否保留、实际提交线程、第二连接等待与恢复结果，区分可达风险和实测故障。"
  - "核对失败告警、去重标记、请求关联与异常隔离；不宣称证据持久必达。"
  - "提交验证证据和方案差异交用户决定；本卡不自动改变审计策略或普通日志可靠性。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §一.3、§三.16、§五测试；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

P2 风险：查询证据经 asyncRecordLog 提交，审计池满后 CallerRuns，REQUIRES_NEW 在外层业务事务尚占连接时再取连接。源码链路成立，尚无本次饱和实测。

## 范围

复用 AsyncConfigTest、QueryAuditAndTraceTest 和 ItInfra，控制有界池/连接池占用，验证事务内触发证据、同步降级与连接等待；定位其他同型提交路径。

## 当前口径

验证须用可释放的闸门和有界等待，不靠裸 sleep 或制造无法清理的永久等待。不把 REQUIRES_NEW 本身或普通 CallerRuns 背压一概认定错误；区分操作日志与尽力查询证据。

权威回写位置：[docs/design/access-service-architecture.md](../../docs/design/access-service-architecture.md)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 记录外层事务资源是否保留、实际提交线程、第二连接等待与恢复结果，区分可达风险和实测故障。
- [ ] 核对失败告警、去重标记、请求关联与异常隔离；不宣称证据持久必达。
- [ ] 提交验证证据和方案差异交用户决定；本卡不自动改变审计策略或普通日志可靠性。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

