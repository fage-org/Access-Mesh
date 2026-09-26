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
  - "filterRoleMutex 换 S/H/D 全命中确定化：R01（{A,B,C,D}+规则 A-B/B-C → 仅 D，规则顺序任意同果）在旧实现下红、新实现绿——终结 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-22 留观②「双删顺序不确定」；R02（只持 A/C/D 无 B → 全保留，无图连通传递删除）为恒绿语义锚（修复前后不变，T-PERM-081 翻转契约口径）"
  - "I01：空互斥规则时互斥专用操作目录装载零调用（computeMutexContext 补空规则短路；实施时该方法重构为 computePermMutexInternal）"
  - "单条路径 PERM_MUTEX 返回真实 triggeredRuleIds（notifyPermConflict 不再按冲突端点反推规则）；引擎消费不立即通知的纯角色判定能力，双删/互斥通知每租户×用户×角色对一次无重复（角色双删按角色对 1h 去重、批量路径按 (组,ruleId) 聚合；单条 PERM_MUTEX 通知为存量逐次形态——claude 外评 2026-09-26 处置限定口径）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-083 （R2-T04）角色互斥 S/H/D 确定化与纯互斥计算

## 背景

S/H/D 为 2026-09-25 用户拍板定案（[历史定案原文](../archive/2026-09-26/decision-registry-before.md) 同日行）：对原始有效角色集 S 一次算全部命中对 H，端点并集 D 一次删净——顺序无关。删多为预期收紧（持 {A,B,C} 且规则 A-B/B-C：现行保留一端 vs S/H/D 全删），属灰度差异登记项，非回归。报告临时编号 R2-T04。

## 范围

- `PermissionConflictDomainServiceImpl.filterRoleMutex` 算法替换 + S/H/D 单元回归锁（含真实规则顺序倒置用例）。
- PERM_MUTEX：空规则短路、真实 triggeredRuleIds 直返、纯计算与通知解耦（通知归根执行统一提交，§6.1）。
- 角色规则缓存仅存角色对时证据用 RolePairRef，不虚构 ruleId（§5.1）。

## 非目标 / 遗留

- 不改写守卫面（batchResolveRawHoldings/findAssignMutexConflicts 语义不动）；EFFECTIVE_ROLES 缓存仍存过滤前集合。

## 实现记录（2026-09-26）

**两项用户拍板（2026-09-26，决策提问；[历史定案原文](../archive/2026-09-26/decision-registry-before.md) 同日行）**：①验收第 3 条「引擎消费不立即通知的纯角色判定能力」落点=**域服务内解耦、引擎调用点零改动**——`PermissionConflictDomainService` 新增公开纯计算（`computeRoleMutex`→`RoleMutexComputation`〔保留集+命中对 `RolePairRef`〕、`computePermMutex`→复用 `BatchPermMutexEvaluator.PermMutexComputation`〔过滤条目+真实 triggeredRuleIds〕），旧入口 `filterRoleMutex`/`filterPermMutex` 保持签名内部复用同一计算叠加去重通知；纯方法本卡零生产消费者（单测锁证纯度），新核心 T-PERM-085/088 消费（沿 T-PERM-082 契约先行先例）。②**收口全量回归合并 T-PERM-095 基线取证一次跑**（两卡构成 §9.3 回退基线），本卡收口=单测轨道+互斥容器定向。

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

**双轨评审处置（2026-09-26）**：代码轨 P0-P2=0、P3×3；文档轨 P1×1+P2×3+P3×2——逐条亲核全成立直修：[历史定案原文](../archive/2026-09-26/decision-registry-before.md) 补 2026-09-26 两项拍板行＋拍板段指针（P1）；验收句 R01 红/R02 恒绿语义锚拆分修正；`BatchPermMutexEvaluatorImpl` Javadoc 死链 `PermMutexContext` 与「复用」失实句改口径；R01 测试方法名随翻转更名（OrderIndependent）；设计 `last_reviewed`/计划 `last_updated` bump；接口 Javadoc 补返回集合不可变注。存疑两项（plan 进度行测试计数写法、S/H-D 与 S/H-D 拼写统一）按评审倾向维持现状；过度设计可裁剪项两轨均零。

**claude 外评处置（2026-09-26，通道=claude headless plan、模型=本机默认 deepseek-flash[1m]；P0-P2=0、P3×2、可裁剪=0）**：两条 P3 逐条亲核全成立、全采纳直修——①`computeRoleMutex` 返回 `Set.copyOf` 换 `Collections.unmodifiableSet(new LinkedHashSet(...))`：SetN 迭代起点按 JVM 级随机盐旋转，而下游 `loadRolePermEntriesWithCache` 按角色迭代序拼接 allEntries 进响应数组/分页切片，跨重启顺序漂移；LinkedHashSet 恢复旧 HashSet 的跨运行确定序，并补返回集合不可变行为锁（`keptRoleIdsShouldBeImmutable`）；②同轮术语清扫三处——验收句 `computeMutexContext` 补现名括注、「通知无重复」限定口径（角色双删按角色对 1h 去重、批量按 (组,ruleId) 聚合、单条 PERM_MUTEX 为存量逐次形态）、interface+impl「内部复用 computePermMutex」字面失实改「共用同一计算体（computePermMutexInternal）」。处置后定向单测 25/25 绿。存量观察五条（PQ-01 归 095、单条通知无去重、未规范化历史行双键、summary 512 截断、implementation.md 口径）登记不修。

**codex sol 复评处置（2026-09-26，通道=codex exec、模型=gpt-6-sol×xhigh〔点名分档：封顶 xhigh、不注入上下文〕、`--disable multi_agent`、read-only 实核 git status 干净；P0-P2=0、P3×2、可裁剪=0）**：复核上轮 claude 处置无次生缝隙（unmodifiableSet 包装局部 LinkedHashSet 无内部后续变异、六个角色集消费方无写入面；`keptRoleIdsShouldBeImmutable` 锁不可变契约——顺序修复因 SetN 跨 JVM 盐随机化无可确定性红锚，不设不稳定锁）。两条 P3 逐条亲核：①**活跃契约两处口径失实——成立直修**：`BatchPermMutexEvaluator` 接口 Javadoc 单条「detail 用任一端点命中 OR 过滤」旧句（上轮漏扫的接口文件）改「AND 两端在场真实命中（T-PERM-083 起与批量同口径）」；设计 §6.1 实施注「每『租户×用户×命中对/规则』一次去重」改三形态精确口径（角色对 1h／批量 (组,ruleId)／单条逐次）。②**单条 PERM_MUTEX 审计 summary 无界拼规则明细被 512 静默截断——成立，用户拍板维持现状登记**（与全仓审计 truncate 行为一致；无界拼接为本卡新引入形态、批量路径每行单规则有界不受影响；T-PERM-088 ConflictEvidence 按规则分条+受控提交统一收编）。处置后定向单测 25/25 绿。存量观察：角色通知 getIfPresent→put 非原子（并发同对可重记，T-PERM-063 多实例独立记账口径内）；PQ-01 归 095。
