---
doc_type: task
id: T-ACCESS-077
title: 网关重连测试可控调度与等待成本优化
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#whole-suite
depends_on:
  - T-ACCESS-069
blocks: []
acceptance:
  - "complete/error/stop 信号与到期前后均可确定驱动，不以固定多秒等待证明负向行为"
  - "定向验证与相关相邻类通过，调度全局状态/订阅清理可核验"
  - "新旧在相同环境测量，分别报告等待消除、实际 wall time 与局限，不宣称 reactor 等额加速"
  - "现役缓存安全边界和生产默认行为不变；设计全仓建议回写最终方法和未替代证据"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-077 网关重连测试可控调度与等待成本优化

## 背景

[全仓评估证据](evidence/T-ACCESS-069/whole-suite-assessment.md)发现明确成本候选。按[实施方案](../../../design/testing-simplification.md#whole-suite)完成确定性调度改造；运行与收益边界见完成记录。

## 范围

1. 核对 PermInvalidationSubscriberTest 的 complete/error/stop 分支与生产 scheduleReconnect，先记录定向基线，再用可控调度表达到期前后与停止后的行为。
2. 优先复用当前版本已有测试能力；如确需 reactor-test 或内部调度注入点，先形成具体差异并核对范围，默认生产 5 秒重连及外部配置不变。
3. 与 T-ACCESS-067 的会话/网关到期测试分工，避免重复重写 AuthTokenFilterTest。SnapshotSafetyBoundaryTest 保留真实跨层边界；先核对当前快照 expiresAt/回源截止/缓存时间源，未经等价证明不整体虚拟化。
4. 用不重连、不 clearAll、stop 后仍重连等目标错误检查保留断言；按现行规则清理调度器/订阅，不污染相邻类。

## 当前口径

按[证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证要求](../../../design/testing-simplification.md#validation)执行。现役[测试规范](../../../../.claude/rules/testing-standards.md)继续约束实现；技能安装不是本卡启动前置。开始前重新核对 HEAD 和当前定向报告，旧报告仅用于候选排序。

## 验收对照

- [x] complete/error/stop 信号与到期前后均可确定驱动，不以固定多秒等待证明负向行为
- [x] 定向验证与相关相邻类通过，调度全局状态/订阅清理可核验
- [x] 新旧在相同环境测量，分别报告等待消除、实际 wall time 与局限，不宣称 reactor 等额加速
- [x] 现役缓存安全边界和生产默认行为不变；设计全仓建议回写最终方法和未替代证据

## 非目标 / 遗留

不改重连策略或安全预算，不降低超时断言；不默认新增测试依赖，不取代 T-ACCESS-067。


## 完成记录

2026-10-03：重连改用测试虚拟调度，生产 5 秒行为不变。目标 5 案例与相邻共跑 20 案例均零失败/错误/跳过；测量、调度清理与错误变体见[证据](evidence/T-ACCESS-077/reconnect-evidence.md)。
