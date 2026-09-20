---
doc_type: task
id: T-PERM-074
title: "同步失败与版本记账事务一致性"
status: proposed
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#sync
  - docs/design/access-service-api-contract.md
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/ops/runbook-full-sync.md
depends_on:
  []
blocks: []
acceptance:
  - "缺依赖BIND V1失败后补依赖，同版本V1重发真实落关系；仅成功应用之后同版本才判幂等/STALE。"
  - "互斥/归属拒绝、资源DISABLE目标缺失等适用失败不遗留已消费版本或部分事实。"
  - "旧版本/时间相同sequence不同/并发版本竞争及full-sync缺失项删除保持原契约，PgIT核对账本与事实。"
  - "事务失败注入同时回滚事实与版本；存量恢复只针对可确认键，契约/runbook不再矛盾。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-074 同步失败与版本记账事务一致性

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F003；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- UserRoleSyncAppServiceImpl和ResourceEntitySyncAppServiceImpl的失败分支与SyncMetadata提交顺序；类推主体/角色同步。
- 单条与full-sync逐项/整批边界、必要锁、版本竞争及存量受影响版本恢复。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#sync)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：启动先核full-sync实际事务承诺，选择最小item边界；若现有边界无法表达部分失败，比较预验证与小范围独立事务后定案。不得默认新建savepoint框架或补偿队列。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
