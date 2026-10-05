---
doc_type: task
id: T-PERM-107
title: 权限拒绝解释字段修正（conditionEvaluated 真值与互斥可区分）
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md（§3 查询管线/拒绝原因）
  - docs/design/access-service-api-contract.md（auth check 响应字段）
depends_on: []
blocks: []
acceptance:
  - "deny 路径 conditionEvaluated 传真实评估事实——与 allow 侧同源派生（有效事实中存在挂条件行才为 true；CONDITION_NOT_MET_OR_CONFLICT 不恒置 true：纯互斥清空且无挂条件行时为 false），旧实现下失败的回归用例"
  - "「条件不满足」与「被互斥清空」在 check 线格式可区分（reason 拆分或附加互斥标识），拒绝原因可区分用例"
  - "用例覆盖三场景：条件失败拒绝 / 纯无条件互斥拒绝 / 有条件互斥拒绝；batch-check 各项语义与单点一致"
  - "不开放外部 TRACE（Q-045 已定边界不越线）；引擎内部 ExecutionTrace/ConflictEvidence 结构复用，不新建证据通道"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-PERM-107 权限拒绝解释字段修正

## 背景

check 响应的 conditionEvaluated 在拒绝路径写死 false（`AuthCheckResp.java:59`）——即使拒绝原因恰是「条件参与了评估但未通过」，字段也与名义相反，系统性误导消费方；batch-check 各项无此字段。「条件不满足」与「被权限互斥清空」共用 CONDITION_NOT_MET_OR_CONFLICT（`DecisionResult.java:55-68`、`QueryExecutionEngine.java:447-452`），互斥命中只旁路写 CONFLICT_DETECTED 操作日志，check 线格式不可区分——互斥是产品差异化能力，排障面却不可见。

## 范围

deny 工厂签名加评估事实标志；拒绝原因可区分。排障能力新形态重做不在本卡（登记待重启，Q-060）。

## 当前口径

G2：deny 工厂签名加评估事实标志，CONDITION_NOT_MET_OR_CONFLICT 分支传真实值。G3：reason 拆分或附加互斥标识；引擎内部已有 ExecutionTrace/ConflictEvidence 结构，复用输出，不新增外部 TRACE 通道。

## 验收对照

- [ ] conditionEvaluated 真值（与 allow 侧同源派生，不恒置 true；旧实现下失败的红跑实证）
- [ ] 拒绝原因可区分用例
- [ ] 三场景用例（条件失败/纯无条件互斥/有条件互斥）+batch-check 语义一致
- [ ] 不越 Q-045 边界

## 非目标 / 遗留

- 排障页面/诊断端点新形态（Q-060）；effective-permission-codes 实例粒度、授权时间线（随重做设计）。
