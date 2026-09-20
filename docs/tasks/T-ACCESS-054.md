---
doc_type: task
id: T-ACCESS-054
title: "外围任务能力与缓存过渡机制取舍"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#trimming
  - docs/design/access-service-architecture.md
depends_on:
  []
blocks: []
acceptance:
  - "U010明确现役任务/计划内Reconciler是否消费底座，保留/缩小/删除均有可追溯理由及影响面。"
  - "U011有部署证据才删除legacy；无证据明确保留及后续删除条件，不能假设旧实例为零。"
  - "若执行裁剪，调用面/配置/文档/测试同步；保留时不引入新层；需要更大改造则独立登记不伪报完成。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ACCESS-054 外围任务能力与缓存过渡机制取舍

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 R002、R003；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 通用任务管理真实消费者与依赖盘点；ORG_VISIBILITY_LEGACY旧部署兼容前置核验。
- 按最终决定保留、缩小当前交付面或定点裁剪；不连带删除正确性基础设施。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#trimming)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U010/U011：任务消费者与旧部署事实未知，实施时核对。推荐无部署证据保留compat，无消费者优先收窄交付面而非删除租约/fencing。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
