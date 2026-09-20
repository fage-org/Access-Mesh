---
doc_type: task
id: T-PERM-075
title: "互斥角色有效期与判定入口一致性"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#mutex
  - docs/design/engine/overview.md
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "U002在实现前形成可执行决定并回写替代的registry条目；未来重叠有效期、禁用后绑定再启用均有具体预期。"
  - "同一主体/时刻下check、batch、管理门禁、scope、接口快照及相关菜单消费的互斥语义一致。"
  - "写守卫仍检查原始候选；间接持有、存量双持和规则启停有适用反例，无N+1或过度缓存时间态。"
  - "用可控时间/同步机制覆盖边界并验证撤销失效，不新增到点调度维持正确性。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-075 互斥角色有效期与判定入口一致性

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F004、D002；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 角色新增持有/改期/启停、冲突规则变更和SubjectDomainService/PermQueryEngine/接口快照的共同有效角色语义。
- 保留原始持有候选供写守卫；统一运行时过滤与失效，不把过滤职责移动成层级循环。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#mutex)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U002：未来有效期重叠在写时拒绝还是允许保存后运行时双删？推荐写时阻止可确定冲突、所有读入口共同兜底；需明确无限期、边界相等、存量双持处理。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
