---
doc_type: task
id: T-PERM-088
title: （R2-T09）根审计、TRACE 与故障证据
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.4/§6.1
depends_on:
  - T-PERM-083
  - T-PERM-086
blocks: []
acceptance:
  - "ConflictEvidence 按 execution+内部 item+stage+ruleRef 聚合；根 execute 统一受控提交一次（纯计算与父项不发日志；共享父被多项引用=一条父证据关联多项）"
  - "A01~A04：未触发规则不进证据（A-C 未触发不列）；重复 key/同规则各维计数按定义去重；一次受控提交无重复通知；后续装载故障保留技术失败、证据标 EXECUTION_ERROR_AFTER_CONFIRMED_STAGE 且不覆盖主异常"
  - "X01：DB/规则装载故障为技术异常（不当普通 DENY/空清单/半批成功）；X02：预算/deadline 超限不返回半份 FACTS 或未经完整评估的 ALLOW；TRACE 不用另一时刻重评条件、不为完整过程补跑短路阶段；敏感角色/授权 ID、IP 规则只向经门禁的诊断开放（§6.1——承接 T-PERM-087 非目标移交）；指标低基数（不使用 resourceCode/permissionId/itemKey 标签）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-26
---

# T-PERM-088 （R2-T09）根审计、TRACE 与故障证据

## 背景

设计 §6.1 审计提交责任与 §3.4 原因/异常边界（报告临时编号 R2-T09）。准入事件独立标注 OPERATION_ADMISSION、不写「业务实例互斥已通过」——本卡落定的证据结构供 T-ACCESS-057 复用。

## 范围

- 证据模型与根级受控提交（非阻塞，失败记技术日志/指标）；新内部原因不未经版本化扩散到普通 SDK（外部错误映射在适配层保持）。
- A05 TRACE 输出在本卡接入，复用 087 已验证的真实阶段覆盖，不补跑 scopeAll 短路的实例阶段；TRACE 输出与敏感字段门禁一并交付（设计 §3.3，2026-09-26 用户确认）。
- 三类公开异常边界对齐 permission-coding-standards（技术故障≠SecurityException）。

## 非目标 / 遗留

- 强持久/outbox 审计为独立写模型，不在本计划（§11 审计行）。
