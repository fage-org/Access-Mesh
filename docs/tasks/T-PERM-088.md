---
doc_type: task
id: T-PERM-088
title: （R2-T09）根审计、TRACE 与故障证据
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.3/§3.4/§4.1/§6.1
depends_on:
  - T-PERM-083
  - T-PERM-086
blocks: []
acceptance:
  - "ConflictEvidence 按 execution+内部 item+stage+ruleRef 聚合；根 execute 统一受控提交一次（纯计算与父项不发日志；共享父被多项引用=一条父证据关联多项）"
  - "A01~A04：未触发规则不进证据（A-C 未触发不列）；重复 key/同规则各维计数按定义去重；一次受控提交无重复通知；后续装载故障保留技术失败、证据标 EXECUTION_ERROR_AFTER_CONFIRMED_STAGE 且不覆盖主异常"
  - "X01：DB/规则装载故障统一包装 QueryExecutionException（不当普通 DENY/空清单/半批成功；结构错误与未实现区域不包装——2026-09-26 用户拍板）；X02：预算/deadline 超限不返回半份 FACTS 或未经完整评估的 ALLOW（EngineLimits 配置本体按计划 A.9 归 T-PERM-093，超限走同一整体失败边界）"
  - "A05：TRACE 复用真实阶段覆盖（ResultDetails.ExecutionTrace），不补跑 scopeAll 短路的实例阶段、不用另一时刻重评条件；敏感字段门禁暂缓（2026-09-26 用户拍板「暂不考虑敏感字段问题，先记录」——登记 pending-problems Q-045，089+ 接线前外部契约不得透传 trace）；指标以枚举端口 QueryEngineMetrics 交付（参数仅枚举/布尔=低基数结构性锁定，Micrometer 绑定随 089+/094 接线——2026-09-26 用户拍板）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-088 （R2-T09）根审计、TRACE 与故障证据

## 背景

设计 §6.1 审计提交责任与 §3.4 原因/异常边界（报告临时编号 R2-T09）。准入事件独立标注 OPERATION_ADMISSION、不写「业务实例互斥已通过」——本卡落定的 ConflictEvidence 结构供 T-ACCESS-057 复用。

## 范围

- 证据模型与根级受控提交（非阻塞，失败记技术日志/指标）；新内部原因不未经版本化扩散到普通 SDK（外部错误映射在适配层保持）。
- A05 TRACE 输出：`ResultDetails.ExecutionTrace` 复用 087 已验证的真实阶段覆盖，不补跑 scopeAll 短路的实例阶段。TRACE 敏感字段门禁经 2026-09-26 用户拍板暂缓实施（Q-045）；本卡交付输出本体。
- 三类公开异常边界对齐 permission-coding-standards（技术故障≠SecurityException；运行态 RuntimeException 统一包装 `QueryExecutionException`）。
- 指标端口 `QueryEngineMetrics`（枚举维度）；角色对证据沿旧「租户×用户×命中对」1h JVM 去重、PERM 规则证据不去重（2026-09-26 用户拍板沿旧口径）。

## 非目标 / 遗留

- 强持久/outbox 审计为独立写模型，不在本计划（§11 审计行）。
- TRACE 敏感字段门禁（引擎侧双入口或应用层显式鉴权）暂缓——Q-045；T-PERM-089+ 适配层接线时收敛，接线前外部 DTO 无 trace 直通字段。
- EngineLimits（预算/deadline 配置本体）归 T-PERM-093（计划 A.9 书面结论面）；Micrometer 绑定归 089+/094 Bean 化接线。

## 当前口径

- 根 execute 在 finally 一次受控提交（先于 RunState 释放，幂等闸）；每条证据一行 CONFLICT_DETECTED（targetType=permission_conflict_rule，单行单规则结构化摘要——512 截断由审计服务统一兜底，旧单条多规则拼接截断形态不进入新核心，旧路径接受边界维持至 T-PERM-092）。
- 主体解析角色对证据 stage=null、evaluationItemId=subject、affectedRootItemKeys=全部根项 key；NO_ROLE 全删早退仍提交；共享父=一条 parent#N 证据关联全部受影响根项。
- 提交失败仅技术日志+指标（证据类别计数），聚合期/提交期异常均不从 finally 外抛覆盖主异常；不宣称持久必达或跨请求 exactly-once。
- TRACE 块内容：主体解析后角色集/角色互斥删除对/各阶段 raw与retained 快照（含 permissionId/roleId）/真实命中互斥规则（MutexRuleRef）/共享父证据（内部 ID+受影响根项+命中权限 ID）——全部为已完成计算快照，零新增 I/O。
- 指标事件：itemStage(选择类型,阶段,COMPLETED|SKIPPED_*)、executionCompleted(SUCCESS|TECHNICAL_FAILURE)、evidenceSubmissionFailed(证据类别)；空请求零审计零指标（C01 审计半边）。

## 验收对照

- A01：QueryAuditAndTraceTest 未触发规则（91=11v21）不进证据，仅 rule=90 一行。
- A02：重复 key（key-a/key-b）×scope/instance 双阶段=4 行各维分列，ops=[11, 12] 保序。
- A03：角色对+根项阶段+共享父 3 行一次提交；QueryStagesTest 共享父用例锁「affected=[first, second]」单行。
- A04：INSTANCE 装载故障=QueryExecutionException（cause 保留），TYPE_GRANT 已确认证据标 EXECUTION_ERROR_AFTER_CONFIRMED_STAGE。
- A05：QueryStagesTest trace 用例——TRACE 只列实际执行阶段、实例 SQL 零调用；QueryAuditAndTraceTest TRACE 块零条件重评/零描述读取。
- X01/X02：规则装载故障=QueryExecutionException 非 DENY；投影期故障不返回半份 FACTS。
- 去重沿旧：同用户同对第二次 execute 无角色对行、PERM 规则行如实重报；提交失败不影响主结果并计 PERM_RULE 指标。
- 低基数：QueryEngineMetrics 反射锁——参数仅枚举/布尔，无高基数标识通道。

## 完成记录

- 2026-09-26：ConflictEvidence＋根级受控提交（`QueryAuditCollector`：finally 一次提交、幂等闸、聚合/提交期异常不外抛）、`QueryExecutionException` 技术故障包装（X01/X02）、`ResultDetails.ExecutionTrace` TRACE 输出（A05，零新增 I/O）、`QueryEngineMetrics` 枚举端口（低基数结构性锁定）已实现；`BatchPermMutexEvaluator` 增 `describeRules`（请求级已装载规则零额外 I/O），`CandidateEvaluator.Evaluated` 携带规则引用；新执行器仍不注册 Bean、无生产消费者，`ConflictEvidence`/`QueryExecutionException` 未进任何 SDK。
- 四项用户拍板（本日决策提问）：①X01 统一包装 `QueryExecutionException`（cause 保留；结构错误/未实现区域不包装）；②角色对证据沿旧「租户×用户×命中对」1h JVM 去重、PERM 规则证据不去重；③TRACE 敏感字段门禁暂缓，登记 [pending-problems](../pending-problems.md) Q-045（089+ 接线前外部契约不得透传 trace）；④指标走枚举端口（Micrometer 绑定随 089+/094）。
- 反例验证：新增 13 项行为锁（QueryAuditAndTraceTest）在实现前均为「无证据提交/裸异常/无 TRACE」形态失败；087 的四条行为锁按新契约翻转（技术异常裸传→QueryExecutionException 包装、trace 拒绝→TRACE 落地、三处 verifyNoInteractions(audit)→受控提交行数断言、共享父证据 affected 全键单行）。
- 512 截断复核（§6.1 交办）：新核心单行单规则结构化摘要（远低于列上限），旧单条多规则拼接截断形态不进入新核心；旧路径接受边界维持至 T-PERM-092 退出，无范围外新影响。
- 单测轨全量：access-service 1567 项 0 失败（`-DskipTestcontainers=true`）；定向容器组 Mutex 6＋BatchAuthCheck 11＋QueryExecutionPgIT 16 全绿（真实 PG/Redis）。
- 双轨评审：代码轨 P0-P2=0、P3×2（RunState/QueryExecutionEngine 过时注释——事实性直修）、过度设计可裁剪=0；实证通过项含 finally 顺序（证据提交先于释放）、C01 空请求零审计零指标、NO_ROLE 全删早退仍提交角色对证据、共享父单证据多关联、去重窗口提交失败回滚、TRACE 零重评零补跑（verifyNoInteractions 锁）、SDK 零扩散（perm-sdk/gateway/example 无新类型引用）、并发面（RunState 单线程＋Caffeine 线程安全）。文档轨一致（project-rules 分层/异常三分/操作日志通道、任务卡/计划/看板/Q-045/设计实施注互指可解析）。
- 收口回归：2026-09-27 00:34，`mvn test -T 1C`（含 E2E/heavy），11 模块 BUILD SUCCESS；合计 2230 项，零失败/错误/跳过（access-service 单测 1567、容器组 385 含 heavy、E2E 16）。完整输出落盘 `/tmp/t088-full.log` 后聚合核对，耗时 16:16。
