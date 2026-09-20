---
doc_type: task
id: T-ADMIN-029
title: "公告状态与受众生命周期闭合"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#notice
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
depends_on:
  []
blocks: []
acceptance:
  - "草稿对接收者不可见，发布后仅目标租户/受众可见，撤回后不可读/标已读；全员公告按契约处理。"
  - "单用户/多用户/空受众和非法目标ID表示有明确语义，真实JSONB写读通过。"
  - "U007落定DTO迁移；历史错写状态不能仅按值全量翻转，恢复或无存量结论有依据。"
  - "正常业务API路径可验收，不以权限门禁403冒充公告行为验证。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ADMIN-029 公告状态与受众生命周期闭合

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F010；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- NoticeAppServiceImpl/Controller/Mapper的草稿、发布、撤回、我的公告和已读链；目标用户typed IDs及JSONB。
- 只读盘点存量状态与受众形态，必要时给定点迁移；不扩公告UI。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#notice)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U007：typed IDs与旧逗号字段是否窄兼容由消费者盘点决定；若决定收窄交付而退役API，需先改写范围/验收并处理数据，不能直接把任务标完成。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
