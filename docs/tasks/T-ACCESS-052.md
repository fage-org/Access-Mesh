---
doc_type: task
id: T-ACCESS-052
title: "实例委派的目录菜单与管理任务闭环"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#delegated-directory
  - docs/design/access-service-api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/engine/implementation.md
  - docs/design/frontend/login.md
  - docs/design/frontend/service-interface-mapping.md
  - docs/design/frontend/role-manage.md
  - docs/design/access-service-architecture.md
depends_on:
  - T-ORG-003
blocks: []
acceptance:
  - "U003/U004定案：目录可见语义和合法首授来源有具体可执行方案，引用并处理现有canGrant/菜单定案关系。"
  - "首管理员经产品UI/API创建有限角色并赋权；service-a负责人只见并管理a，b不泄露/不可操作。"
  - "部门成员管理员职责内任务完成且不能改结构；无实例/条件失效/撤权时菜单、目录、深链和后端结果一致。"
  - "类型级VIEW保留全量语义；先按实例权限过滤，再统计total、排序和分页，不逐行N次查询；必要跨服务/浏览器验收通过。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-ACCESS-052 实例委派的目录菜单与管理任务闭环

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 D001；关联F005；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- 服务负责人和部门管理员的首授→可见目录→菜单/路由→合法操作→越界拒绝→撤权链，类推同模式角色/类型/资源页。
- 复用统一引擎和领域查询保证权限过滤先于分页；不额外建立全局能力目录。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#delegated-directory)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U003：管理权与查看权是否依既有操作覆盖；U004：内置首授是否足够。推荐先复用合法委派来源，仅在证据证明必要时窄改种子，不能用SQL夹具或全局canGrant=true代替产品可用性。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
