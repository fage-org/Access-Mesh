---
doc_type: task
id: T-PERM-076
title: "资源批量创建复合身份一致性"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#resource-key
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
  - docs/design/schema/access-service.sql
depends_on:
  []
blocks: []
acceptance:
  - "PROJECT/default/X与DOC/default/X、同类型不同codeType均可按契约分别创建；同完整键仍识别重复。"
  - "存量和批内重复共享完整键处理，部分成功不因一个重复项变整批SQL失败。"
  - "PgIT验证真实唯一键/返回ID，所有权和同类型父边约束不放宽；无需新幂等表。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-076 资源批量创建复合身份一致性

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F006；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- ResourceManageAppServiceImpl批量查重、ResourceEntityDomainService/Mapper及完整资源业务键。
- 本批重复项、部分成功和成功响应身份，与单条创建对齐。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#resource-key)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
