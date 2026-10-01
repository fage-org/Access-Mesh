---
doc_type: task
id: T-ORG-005
title: 组织/岗位动作码判定入口覆盖定案
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/org-user-permission-contract.md §5（关系动作归属契约）
  - docs/design/access-service-api-contract.md §8（org 能力）
depends_on: []
blocks: []
acceptance:
  - "统一盘点 OrgOperationCodeMapper 的入口覆盖并逐项拍板（decision-question-protocol 举例上报）：① createUser 带 orgId 用 ORG:UPDATE vs 已有用户挂载走成员动作码——创建并挂载一步化是否补 MANAGE_MEMBER 通道（现状=仅持 MANAGE_MEMBER 的管理员不能一步创建并挂载）；② filterVisibleOrgIds 固定 VIEW vs org 树读面按 orgType 分发 VIEW/VIEW_POSITION——岗位裁剪是否精化"
  - "② 受 T-ACCESS-055 已拍板约束：维持岗位裁剪现状并留待专门处理——改为精化码会改变按 VIEW 配权的部门管理员候选池，不能把该安排当未决选项重新选择；本卡定案=在该约束下明确精化与否与影响面"
  - "拍板结论落 org-user-permission-contract §5 与契约 §8 门禁/动作码表；实施若超出单卡范围（如需新端点/新码）另立新号"
  - "影响面结论写明：有限管理员过严的具体场景、VIEW 裁剪下岗位成员进候选池的既有门禁兜底"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ORG-005 组织/岗位动作码判定入口覆盖定案

## 背景

承接 [Q-032](../pending-problems.md#q-032)（合并 Q-034）：两处入口覆盖缺口——创建用户并挂组织用 ORG:UPDATE（已有用户挂载走成员动作码，仅持 MANAGE_MEMBER 的管理员不能一步创建并挂载）；岗位可见性裁剪 filterVisibleOrgIds 固定 VIEW（org 树读面按 orgType 分发 VIEW/VIEW_POSITION），影响成员候选池与 user/delete 默认树可见性校验。有限管理员操作可能过严；VIEW 裁剪可能让岗位成员进候选池但有候选门禁，不直接推导越权。

## 范围

定案卡：盘点+逐项拍板+契约修订；实施视拍板范围（超出另立）。

## 当前口径

T-ACCESS-055 对②的维持现状拍板是既定边界；本卡在其内收敛，不重开已拍选项。

## 非目标 / 遗留

- 候选门禁本身（另有口径）。
