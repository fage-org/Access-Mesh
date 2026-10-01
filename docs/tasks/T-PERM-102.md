---
doc_type: task
id: T-PERM-102
title: TRACE 诊断输出授权门禁
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/engine/implementation.md §3.5（条件评估、互斥与审计证据——TRACE 输出）
depends_on: []
blocks: []
acceptance:
  - "门禁位置定案（引擎内显式诊断鉴权 / 适配层拦截外部 DTO 直通 trace，按 decision-question-protocol 举例上报用户后拍板），T-PERM-088 暂缓安排（2026-09-26 拍板「先记录」）在本卡兑现；089+ 接线后外部契约不得无门禁透传 trace 的登记约束转为实施"
  - "普通 execute(trace=true) 返回真实角色、权限 ID、互斥命中与父绑定证据的路径经授权门禁：未授权调用方拿不到敏感诊断（回归锁以未授权 trace 请求实证旧实现直通）"
  - "门禁形态与授权判据（操作码/专用诊断权限）落 implementation §3.5；引擎指标与 TRACE 复用真实阶段的既有语义不变"
  - "若门禁落在适配层，引擎内部诊断调用（运维/测试通道）不受影响的边界写明"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-102 TRACE 诊断输出授权门禁

## 背景

承接 [Q-045](../pending-problems.md#q-045)：普通 execute(trace=true) 可返回真实角色、权限 ID、互斥命中和父绑定证据；设计要求敏感诊断授权，门禁交付按 T-PERM-088 安排暂缓（2026-09-26 拍板「暂不考虑敏感字段问题，先记录」）。若适配层把 trace 参数直通外部请求且未授权，调用方可探测他人授权结构及条件归属。早期引擎无生产消费者时无该暴露面；089+ 已接线后本约束需兑现。

## 范围

门禁位置定案+实施+回归锁；TRACE 数据结构不变。

## 当前口径

候选位置两案（引擎内 / 适配层），随实施定；外部契约不透传 trace 的登记约束（088 拍板）为底线。

## 非目标 / 遗留

- TRACE 输出内容裁剪（门禁解决访问面，不裁数据）。
