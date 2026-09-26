---
doc_type: task
id: T-PERM-083
title: （R2-T04）角色互斥 S/H/D 确定化与纯互斥计算
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.1/§6.1
depends_on:
  - T-PERM-081
blocks: []
acceptance:
  - "filterRoleMutex 换 S/H/D 全命中确定化：R01（{A,B,C,D}+规则 A-B/B-C → 仅 D，规则顺序任意同果）在旧实现下红、新实现绿——终结 registry 2026-09-22 留观②「双删顺序不确定」；R02（只持 A/C/D 无 B → 全保留，无图连通传递删除）为恒绿语义锚（修复前后不变，T-PERM-081 翻转契约口径）"
  - "I01：空互斥规则时互斥专用操作目录装载零调用（computeMutexContext 补空规则短路）"
  - "单条路径 PERM_MUTEX 返回真实 triggeredRuleIds（notifyPermConflict 不再按冲突端点反推规则）；引擎消费不立即通知的纯角色判定能力，双删/互斥通知每租户×用户×规则对一次无重复"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-083 （R2-T04）角色互斥 S/H/D 确定化与纯互斥计算

## 背景

S/H/D 为 2026-09-25 用户拍板定案（registry 同日行）：对原始有效角色集 S 一次算全部命中对 H，端点并集 D 一次删净——顺序无关。删多为预期收紧（持 {A,B,C} 且规则 A-B/B-C：现行保留一端 vs S/H/D 全删），属灰度差异登记项，非回归。报告临时编号 R2-T04。

## 范围

- `PermissionConflictDomainServiceImpl.filterRoleMutex` 算法替换 + S/H/D 单元回归锁（含真实规则顺序倒置用例）。
- PERM_MUTEX：空规则短路、真实 triggeredRuleIds 直返、纯计算与通知解耦（通知归根执行统一提交，§6.1）。
- 角色规则缓存仅存角色对时证据用 RolePairRef，不虚构 ruleId（§5.1）。

## 非目标 / 遗留

- 不改写守卫面（batchResolveRawHoldings/findAssignMutexConflicts 语义不动）；EFFECTIVE_ROLES 缓存仍存过滤前集合。

## 实现记录（2026-09-26）

**两项用户拍板（2026-09-26，决策提问；registry 同日行）**：①验收第 3 条「引擎消费不立即通知的纯角色判定能力」落点=**域服务内解耦、引擎调用点零改动**——`PermissionConflictDomainService` 新增公开纯计算（`computeRoleMutex`→`RoleMutexComputation`〔保留集+命中对 `RolePairRef`〕、`computePermMutex`→复用 `BatchPermMutexEvaluator.PermMutexComputation`〔过滤条目+真实 triggeredRuleIds〕），旧入口 `filterRoleMutex`/`filterPermMutex` 保持签名内部复用同一计算叠加去重通知；纯方法本卡零生产消费者（单测锁证纯度），新核心 T-PERM-085/088 消费（沿 T-PERM-082 契约先行先例）。②**收口全量回归合并 T-PERM-095 基线取证一次跑**（两卡构成 §9.3 回退基线），本卡收口=单测轨道+互斥容器定向。

**事实消解（未另行提问，设计/约束直接定形）**：RolePairRef=角色对证据，缓存载荷不扩 ruleId——`uk_conflict_rule_role` 唯一约束（tenant+first+second）保证角色对↔规则一一对应，配对证据无损（设计 §5.1「缓存仅存角色对、有真实 ruleId 才附带」）。

**实现**（`PermissionConflictDomainServiceImpl`，引擎 `PermQueryEngine` 零改动）：

- `filterRoleMutex` → `computeRoleMutex` S/H-D：对原始集一次算全部命中对 H（两端都在 S）、端点并集 D 一次删净；规则装载抽 `loadMutexPairs`（缓存 JSON 优先/miss 回源回填，行为不变）；链式命中对各通知一条（1h 去重不变）。
- `computePermMutexInternal`（原 `computeMutexContext` 重构）：I01 空规则短路（规则空→直接返回副本，操作目录零装载）；AND 两端在场一次收集 triggeredRuleIds/triggeredRules/conflictingOpIds；`PermMutexContext` 记录删除。
- `notifyPermConflict` 明细由真实命中规则构造（旧按冲突端点 OR 反推终结——单端恰落冲突集的未触发规则不再误入明细），审计 summary 补 detail 段（对齐批量路径 shape）。

**测试**：

- 红跑取证 2026-09-26（`mvn test -pl access-service -DskipTestcontainers=true -Dtest='PermissionConflictDomainServiceImplTest'`，旧实现下）：5/5 新行为锁红——链式 [400,300]、倒序 [400,100]（顺序依赖同框可视）、链式通知 1 条（应 2）、I01 仍装载、明细反推；R02 语义锚绿（设计如此）。
- 转绿：单测类 24/24；容器定向 42/42（MutexSemanticsCharacterization 5——R01 两断言+顺序无关断言按翻转契约收敛 `containsExactlyInAnyOrder("D")`/两集相等、BatchAuthCheck 11、QuerySemanticsBaseline 17、RoleMutexGuard 9）；单测轨道 1460 项 0 失败（BUILD SUCCESS）。
- 纯度锁：`computeRoleMutex`/`computePermMutex` 各一条 verifyNoInteractions(auditDomainService)（契约先行，无法对旧实现红跑——API 此前不存在）。

**顺手修正**：`shouldCacheEmptyRuleSetToPreventPenetration` 工作树既有漂移（stub 误为 `ConflictType.PERM_MUTEX`，HEAD 正确形态 `ROLE_MUTEX`；会话起点 git 快照 clean 但工作树实有该单行未提交改动，来源不明）红跑暴露 PotentialStubbingProblem，恢复 HEAD 形态后旧实现下即绿——非本卡行为面。

**文档核查**：`engine/implementation.md` 互斥叙述均为调用面口径（filterRoleMutex 原语/resolveJudgementRoleIds 统一入口/双删日志），无顺序遍历算法内述，S/H-D 落地后不失实——沿 T-PERM-080 附录 A.8 口径不动，留计划完结 §3 族整体重写；设计回写=本稿 §5.1/§6.1 两处就地实施注（沿 T-PERM-082 迁移期注先例）。

**双轨评审处置（2026-09-26）**：代码轨 P0-P2=0、P3×3；文档轨 P1×1+P2×3+P3×2——逐条亲核全成立直修：registry 补 2026-09-26 两项拍板行＋拍板段指针（P1）；验收句 R01 红/R02 恒绿语义锚拆分修正；`BatchPermMutexEvaluatorImpl` Javadoc 死链 `PermMutexContext` 与「复用」失实句改口径；R01 测试方法名随翻转更名（OrderIndependent）；设计 `last_reviewed`/计划 `last_updated` bump；接口 Javadoc 补返回集合不可变注。存疑两项（plan 进度行测试计数写法、S/H-D 与 S/H-D 拼写统一）按评审倾向维持现状；过度设计可裁剪项两轨均零。
