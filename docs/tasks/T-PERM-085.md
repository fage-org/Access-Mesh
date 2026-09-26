---
doc_type: task
id: T-PERM-085
title: （R2-T06）TYPE_GRANT/INSTANCE 单一阶段主体
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.3/§3.4/§5.4/§6.1
  - docs/design/r2-unified-query-and-admission.md §4.1~§4.4
depends_on:
  - T-PERM-083
  - T-PERM-084
  - T-PERM-095
blocks: []
acceptance:
  - "唯一 execute 生命周期（§4.1 职责表）与 TYPE_GRANT/INSTANCE 两阶段落地；装载批与判定集合分离——SQL 合批不扩大 item+stage 候选；I08：SQL 分块跨同一 item 时候选合齐后再计算互斥（块边界不改变结果）；新核心不调用旧完整核心"
  - "D01~D14 阶段用例全绿：独立 item 双允许/共同集合按项拒绝、同目标互斥两端必拒（不见第一条授权即返回）、条件剔除互斥一端、类型级门禁不认实例/子 scopeAll、scopeAll 短路（最小输出不解析实例）、DISALLOW 不回退、scopeAll 评估清空后实例仍可命中、原因优先级（CONDITION_NOT_MET_OR_CONFLICT 优先于 DEPENDENT/NO_PERMISSION）、SELF 不消费他项闭包、跨类型同位值不泄漏、物理合批超集切回原配对"
  - "getDenied* 跨 item 冲突语义并入本阶段验收（PQ-01 修复面：每目标独立评估）"
  - "接入 T-PERM-084 读取部件，并在真实 execute 判定链验证相关读取不变量（I03 最小输出、I04 缺失记忆、I05 令牌、I06 来源隔离），不以部件测试替代整体执行验证"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-085 （R2-T06）TYPE_GRANT/INSTANCE 单一阶段主体

## 背景

设计 §4.3/§4.4 阶段算法与候选公式 C(item)（报告临时编号 R2-T06）。充分决策允许阶段短路、不允许集合内不完整互斥。

## 范围

- TYPE_GRANT 阶段（类型覆盖掩码汇总装载→逐项切回候选→条件→PERM_MUTEX→DECISION 短路/FACTS 保留）与 INSTANCE 阶段（闭包×1、按 clause 精确候选、item 内合并去物理重复、跨 SQL 块候选合齐再算互斥）。
- 阶段结果去重键含真实候选/type-mask 配对/绑定与父结果/评估策略（不只按资源类型）。
- 最小 FACTS 输出包含不可变 StageFacts、按档位保留的 raw/kept 与命中 ID；TYPE_GRANT/INSTANCE 独立评估，FACTS 不因前阶段成功而漏收后阶段。

## 非目标 / 遗留

- 父要求与 GRANT_LIST 在 T-PERM-086；消费者迁移在 T-PERM-089+。
- 描述/有效操作/展示在 T-PERM-087，TRACE 与根审计受控提交在 T-PERM-088；当前仅保留请求内真实互斥证据，不调用旧通知入口。

## 当前口径

- FACTS 在本卡提供最小可消费输出：不可变 StageFacts、按 OutputSpec 保留的 raw/kept 与命中 ID；类型回退命中后仍收集实例阶段。描述、有效操作、展示与 TRACE 留后续任务，未实现输出明确抛未实现异常，不返回伪完整结果。范围依据见设计 §3.3（2026-09-26 确认）。
- I05 在普通 execute 验证条件缓存的首次 miss 令牌、跨阶段增量读取和热缓存复用；ROLE_SNAPSHOT 属 GRANT_LIST 来源，其 execute 验证由 T-PERM-086 承接（设计 §5.4）。
- 条件缓存令牌复用落在共享的 PermissionConditionDomainServiceImpl.BatchConditionEvaluatorImpl：同一评估器首次 miss 时 beginRead，后续增量 miss 沿用该令牌。现役旧 PermQueryEngine.queryBatch 同样受影响，其 scopeAll 条件预载与后续实例条件预载共享评估器，回填预算从首次 miss 起累计，不在实例阶段重新起算；本卡未修改旧引擎执行体或迁移其消费者。
- TYPE_LEVEL 仅有 depend_on 子授权时返回 NO_PERMISSION；TARGET_SET 父上下文排除仍返回 DEPENDENT_NOT_IN_PARENT_CONTEXT，见设计 §3.4（2026-09-26 确认）。运行态保留 User 角色互斥计算结果的迭代顺序，遵守设计 §6.1 的不可变与保序边界。

## 验收对照

- D01～D04：独立目标双允许、共同集合/同目标互斥拒绝、条件先于互斥；QueryStagesTest 与 QueryExecutionPgIT 共用真实读取/领域计算能力，getDenied 两轨以既有正确性基线对照。
- D05～D09：类型级排除实例与 dependOn、scopeAll 最小输出短路、DISALLOW、条件清空后实例回退与原因优先级。
- D10～D14：SELF 与祖先闭包隔离、同目标/祖先候选同场互斥、跨类型位空间与 clause 配对隔离、未知对象逐项拒绝。
- I03/I04/I06：最小输出不读描述/额外操作，缺失目标与操作复用，同次执行新鲜互斥定义与缓存掩码来源隔离。
- I05/I07/I08：固定服务端时刻；跨阶段缓存令牌递减/耗尽；Mapper 写入后新 execute 读取本事务新事实；SQL 分块合齐后互斥，容器轨检查实际 prepare 次数。

## 完成记录

- 2026-09-26：新 execute 已接通 TYPE_GRANT/INSTANCE 与最小事实投影，复用纯角色互斥、批量条件和权限互斥能力；候选按 item+stage 隔离，阶段结果和读取记忆随 RunState 释放。
- 定向验证：`mvn test -pl access-service -Dtest=QueryStagesTest,QueryExecutionPgIT,QueryExecutionEngineTest`，40 项单测＋10 项真实 PG/Redis 通过；覆盖旧单条/批量 TYPE_LEVEL 原因对照与角色顺序传递。新增及调整的原因/保序反例修复前有 3 处失败，修复后全通过；初始阶段反例在旧骨架上因未实现而失败，接线后通过。
- 收口全量：`mvn test -T 1C`，2026-09-26 15:57 完成，2170 项、零失败/错误/跳过；access-service 单测 1513、容器 379（含 heavy）、跨服务 E2E 16，11 模块全部成功。
- 代码轨与文档轨本地核对完成，无待决策或未解决缺陷；设计引用、读取来源边界、后续任务责任与最小事实输出已回写。任务所属计划仍活跃，本卡保留原位。
