---
doc_type: task
id: T-ACCESS-068
title: 服务凭证覆盖运行时查询端点阶段二规划
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: cross-service
design_refs:
  - docs/design/service-authentication.md §3.5（分期与退役判据）
depends_on: []
blocks: []
acceptance:
  - "产出阶段二规划定案：auth/check、batch-check、query-resources、query-scopes 四端点（现要求 X-Internal-Secret 与服务/租户头）逐端点凭证化路径、服务可查询的主体/资源范围、租户派生与调用能力定义"
  - "两套身份并存的过渡窗口、全局共享密钥失陷风险的收敛判据与退役时间线写入 §3.5 分期表"
  - "T-ACCESS-053 的阶段边界维持（本卡不推翻其维持现状拍板，只规划后续阶段）；SDK/网关接线影响面盘点随规划产出"
  - "实施不在本卡（阶段二立项目另拆新号）；规划按 decision-question-protocol 举例上报用户确认后，service-authentication §3.5 更新为定稿口径"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ACCESS-068 服务凭证覆盖运行时查询端点阶段二规划

## 背景

承接 [Q-040](../pending-problems.md#q-040)：per-service 凭证（T-PERM-070）覆盖同步/manifest 通道，但 auth/check、batch-check、query-resources、query-scopes 仍要求 X-Internal-Secret 与服务/租户头——凭证不能直接替代该查询身份。接入方维护两套配置与失效语义，查询面仍承担全局共享密钥失陷风险。新准入端点接线（T-ACCESS-059）不代表本项自动收敛。

## 范围

定案/规划卡：阶段二范围定义与分期修订；不含实施。

## 当前口径

阶段一（T-PERM-070）已交付同步/manifest 双通道；阶段二按端点逐个规划，先定义服务可查询的主体/资源范围与租户派生。

## 非目标 / 遗留

- 阶段二实施（另立新号）。
