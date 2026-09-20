---
doc_type: task
id: T-ORG-002
title: "默认身份目录删除与恢复边界闭合"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: org-user
design_refs:
  - docs/design/iam-task-closure.md#directory
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/org-user-permission-contract.md
  - docs/design/access-service-api-contract.md
  - docs/design/access-service-architecture.md
depends_on:
  []
blocks: []
acceptance:
  - "默认根无子节点时删除仍明确拒绝，拒绝后管理事实、投影、配置和成员关系不变，重启正常。"
  - "删默认树叶子会消灭最后归属时按U001结论处理；直接移除与级联删除对同一业务结果一致。"
  - "普通可删组织成功路径、另一租户同ID/同码拒绝、事务故障注入及定点恢复副本验证有证据。"
  - "共享逻辑覆盖所有本任务实际影响入口，权威设计/错误码契约与可行动提示同步。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ORG-002 默认身份目录删除与恢复边界闭合

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F001；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- OrgWriteAppServiceImpl、UserOrgWriteAppServiceImpl、OrgTreeConfigAppServiceImpl 的默认树不变量及实际投影调用面；批量检查、树锁、事务和失效复用。
- 梳理默认根墓碑、成员最后归属丢失的存量只读诊断与定点恢复说明；不自动重建有数据租户。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#directory)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U001：删除部门自动迁用户到根还是先拒绝并要求迁移？推荐后者；研发部是U唯一归属的反例及迁移影响见方案§2.1。树配置改默认目录的入口一并核实，未决不以恢复脚本替代。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
