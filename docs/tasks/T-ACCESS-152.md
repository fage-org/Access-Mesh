---
doc_type: task
id: T-ACCESS-152
title: 判定证据提交饱和策略修复
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#query-evidence-submission
  - docs/design/engine/implementation.md
depends_on:
  - T-ACCESS-151
acceptance:
  - "专属执行器的用途、生命周期、饱和/关闭处理与容量依据先归服务架构，再实施；T-ACCESS-151 的验证先完成。"
  - "饱和场景不再造成所选方案声明要消除的连接等待/响应阻塞，失败不覆盖主查询异常。"
  - "普通日志可靠性不被顺带降低，角色对去重及已有 requestId/subject 关联保持。"
  - "复用既有审计与真实事务证据验证，不引入 MQ/补偿平台；本卡依赖验证结论并等待实施授权。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §一.3、§三.16；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

承接 T-ACCESS-151 的风险验证；查询证据的非阻塞目标与共享审计池同步降级存在冲突。

## 范围

实施用户确认的证据提交策略，核对执行器生命周期、饱和/关闭处理、失败指标、角色对去重回退与权限规则证据。

## 当前口径

方案已确认，目标以服务架构对应锚点为准：冲突判定证据使用专属小型有界异步通道，饱和/关闭放弃本次提交并告警、计失败，不在鉴权线程落库，不增加补偿重试。普通操作日志保留原 CallerRuns，登录及权限事实变更审计保持各自语义。具体容量先核对现有负载与配置；新增容量取舍若无依据须再请用户确认。

权威回写位置：[docs/design/access-service-architecture.md#query-evidence-submission](../../docs/design/access-service-architecture.md#query-evidence-submission)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 专属执行器的用途、生命周期、饱和/关闭处理与容量依据先归服务架构，再实施；T-ACCESS-151 的验证先完成。
- [ ] 饱和场景不再造成所选方案声明要消除的连接等待/响应阻塞，失败不覆盖主查询异常。
- [ ] 普通日志可靠性不被顺带降低，角色对去重及已有 requestId/subject 关联保持。
- [ ] 复用既有审计与真实事务证据验证，不引入 MQ/补偿平台；本卡依赖验证结论并等待实施授权。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

