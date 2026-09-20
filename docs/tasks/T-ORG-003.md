---
doc_type: task
id: T-ORG-003
title: "组织与岗位成员候选门禁统一"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: org-user
design_refs:
  - docs/design/iam-task-closure.md#member-candidates
  - docs/design/org-user-permission-contract.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "只有MANAGE_MEMBER或ASSIGN_POSITION_USER并有必要可见范围的管理员能选人和分配，不需要ORG:UPDATE。"
  - "同一管理员不能修改组织结构；无成员动作权、越租户、不可见默认树用户仍拒绝/过滤。"
  - "候选与提交门禁共用既有动作码解析，保留目标存在性、默认身份池及已绑定排除。"
  - "API组合测试和至少一条有限管理员浏览器分配链有证据；相关权威文档冲突消除。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ORG-003 组织与岗位成员候选门禁统一

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F005；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- UserAppServiceImpl.memberCandidates与UserOrgWriteAppServiceImpl.assign的目标类型解析和动作码复用。
- PositionTab及普通组织成员操作的选择/提交链路，校准总册旧UPDATE规则。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#member-candidates)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
