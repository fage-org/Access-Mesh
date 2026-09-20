---
doc_type: task
id: T-PERM-077
title: "操作继承掩码缺省值契约对齐"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#operation-default
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/extension-guide.md
depends_on:
  []
blocks: []
acceptance:
  - "自定义类型新增EXPORT位16，省略inheritMask与显式0均成功且结果一致，非法掩码仍拒绝。"
  - "PgIT实际INSERT不再写非法NULL，同事务授权根补种并可首次转授，失败不留半成品。"
  - "采用唯一入口缺省归一化，不扩大为全仓ORM默认策略改造。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-077 操作继承掩码缺省值契约对齐

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F013；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- OperationCreateReq到OperationAppServiceImpl及Mapper的inheritMask缺省处理、调用方与授权根联动。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#operation-default)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
