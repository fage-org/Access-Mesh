---
doc_type: task
id: T-API-005
title: 可选字段显式清空协议逐域定案
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md §2（通用协议：可选字段清空 §2.7）
  - docs/design/project-rules.md §7.6
depends_on: []
blocks: []
acceptance:
  - "四字段族逐域拍板清空语义（decision-question-protocol 举例上报）：condition.description、menu.path/icon 等、OAuth2 client 的 grantTypes/redirectUris/scopes/audiences 等、system-config description（现状 null 跳过且 update(entity) 忽略 null、内存组装响应与重查不一致）"
  - "OAuth2 客户端字段逐项核对安全后果（清空 redirectUris=锁死授权、清空 grantTypes=禁用流程类）后定各字段清空安全语义"
  - "system-config 契约 §17.2 未定义清空语义的现状确认——不能预定为空串协议（T-API-004 六字段「空串拒 400」口径是否延伸由本卡定）"
  - "拍板结论落契约 §2.7 逐字段族表（可清空/拒绝/有后果警示）；实施（DTO extraClear 类通道、真实 NULL 写入、表单、契约）另立新任务——本卡只定案不实施"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-API-005 可选字段显式清空协议逐域定案

## 背景

承接 [Q-043](../pending-problems.md#q-043)（含外部报告 B-11）：可选字段缺显式清空通道——condition/menu 族 null 跳过无清空通道；OAuth2 client 族清空通道与各字段清空安全语义待明确；system-config hook 将空描述发 null、upsertSystemConfig 以 update(entity) 回写（MyBatis-Flex 默认忽略 null），清空提交可能成功却保留旧值。T-API-004 已交付范围（六字段+perm 轨 extra）不扩撤；Q-018 同型矩阵登记维持关联。

## 范围

定案卡：四族清空语义逐域拍板+契约 §2.7 修订；实施通道（DTO/NULL 写入/表单）另立新号。

## 当前口径

参考既有先例：T-API-004 的「冲突全端点拒绝 / type extraClear 拒清 / 空串拒 400」六字段口径与 extraClear 通道形态；逐域按此模式定，不盲推空串协议。

## 非目标 / 遗留

- 实施与前端表单改造（定案后另立）。
