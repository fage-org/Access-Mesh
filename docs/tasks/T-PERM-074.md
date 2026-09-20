---
doc_type: task
id: T-PERM-074
title: "同步失败与版本记账事务一致性"
status: done
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
  status: done
last_updated: 2026-09-21
---

# T-PERM-074 同步失败与版本记账事务一致性

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F003；基线与静态/动态证据强度见该记录。缺陷修复与定向验证已完成，收口以本卡验收与实际回归证据为准。

## 范围

- UserRoleSyncAppServiceImpl和ResourceEntitySyncAppServiceImpl的失败分支与SyncMetadata提交顺序；类推主体/角色同步。
- 单条与full-sync逐项/整批边界、必要锁、版本竞争及存量受影响版本恢复。

## 当前口径

实现边界见[同步一致性方案](../design/iam-task-closure.md#sync)，正式响应与版本规则见[契约总册 §19.3](../design/access-service-api-contract.md#193-同步接口通用响应与错误分类)。本卡修复既有契约偏差，不采纳 IAM 设计稿中其他未决方案。

**实施边界**：已核实单条与 full-sync 均由 AppService 声明事务；full-sync 逐项流程返回 ItemResult 的业务拒绝不修改该项，成功项与缺失集清理共同提交；入口预检拒绝不进入处理，抛出的业务或技术异常整批回滚。沿用此边界，将依赖、归属、互斥和资源 DISABLE 存在性检查置于版本记账前；保留旧版本预判与数据库原子比较。不得仅撤销版本而保留同项事实。

**并发与恢复**：成员单条/full-sync 在读取前复用租户角色树写锁，释放绑定事务完成；资源沿用原资源树写锁。主体/角色同步经类推核查，记账后没有普通错误返回分支：DISABLE 缺失会创建停用事实，DELETE 缺失为成功 no-op，技术异常回滚。存量恢复约束见运维手册 §8，不操作部署环境数据。

## 验收对照

- [x] 缺失主体、目标角色、关系角色逐个补齐后，原版本 BIND 成功且之后同版本为 STALE（SyncFailureAtomicityPgIT）。
- [x] 互斥/人工关系归属拒绝不记账，资源缺失 DISABLE 不记账；互斥解除与资源补齐后原版本可应用。
- [x] 同时间序号竞争最终事实与账本保留较新版本；FULL 部分成功、失败原版重试、缺失解绑及旧事件不复活均核对数据库。
- [x] FULL 在写事实后注入账本回填异常，全部事实与版本回滚；恢复手册限定可确认键，不盲目清账。
- [x] 本地双轨结论均已核实处置；全量含 E2E 回归及补充确定性交错测试通过。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

## 完成记录

2026-09-21 完成。失败检查前移、成员同步持锁读取、资源 DISABLE 不存在拒绝均已实现；契约、流程、实现说明与定点恢复手册已回写。未对部署环境数据执行恢复。

- `mvn test -T 1C`：1790 tests，0 failures / 0 errors / 0 skipped，包含 E2E；完整日志 `.tmp-perm074-full.log`，各模块 Surefire 报告可核对。
- 全量后仅补充确定性交错测试与参数注释；`mvn test -pl access-service -Dtest=SyncFailureAtomicityPgIT`：8 tests，0 failures / 0 errors / 0 skipped，验证单条事务提交前 FULL 等待、提交后读取最新事实。
- 本地代码/文档双轨评审已闭合：请求级错误信封说明、并发测试强度与参数注释均已修正并复核；无待用户决策项。文档残留检查与 `git diff --check` 通过。
