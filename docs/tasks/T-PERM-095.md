---
doc_type: task
id: T-PERM-095
title: （R2 基线）getDenied* 跨 item 互斥最小正确性修复——最小修复基线锚点
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §1.2/§9.3/§9.4/§10.2
depends_on:
  - T-PERM-081
blocks: []
acceptance:
  - "computeInstanceDenied 候选按 item（目标闭包集）切分后各自评估条件与 PERM_MUTEX，替代整批一次 filterPermMutex——T-PERM-081 的 PQ-01 红跑用例（D01~D03 差分锚）转绿，正常语义基线全部保持"
  - "与 T-PERM-083（S/H-D）共同构成最小正确性修复基线：PQ-01/PQ-06 两缺陷修复后、不依赖新核心的可回退版本，以 git tag/提交哈希落账并附基线验证证据（命令+日期+测试计数）"
  - "query/queryBatch 主链路既有语义与回归锁不受影响"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-095 （R2 基线）getDenied* 跨 item 互斥最小正确性修复

## 背景

设计 §9.3「先准备保留正常行为的最小正确性修复基线，再实施新内部契约和阶段」与 §9.4 回退目标「退到最小正确性修复基线，而不是恢复 PQ-01／06 的旧 SHA」。PQ-06 修复在 T-PERM-083（现行代码就地换 S/H-D）；PQ-01 此前只随新核心（T-PERM-085/089）解决，基线缺半边——本卡补齐（2026-09-25 外评处置）。

## 范围

- `PermQueryEngine.computeInstanceDenied` 最小修复：候选按目标闭包逐 item 评估（沿 queryBatch 逐 item 语义形态），不动其余管线与装载方式。
- 基线落账：T-PERM-083+本卡两个提交构成基线版本标记，供 T-PERM-094 回退演练引用。

## 非目标 / 遗留

- 不动 query/queryBatch 主链路；不做装载优化（I 系归新核心与 T-PERM-093）；新核心就位后本修复随旧执行体一并由 T-PERM-092 消化。

## 实现记录（2026-09-26）

**用户拍板（决策提问）**：基线落账=本地 annotated tag（`r2-baseline-correctness` 指向本卡修复提交，沿 v0.1.0 本地 tag 先例，仅本地不 push）。

**实现**（`PermQueryEngine.computeInstanceDenied`，两调用点 getDeniedResourceCodes/getDeniedEntityIds）：

- 候选按目标闭包切分、各自 PERM_MUTEX（沿 queryBatch 逐 item 语义形态，设计 §4.4）——独立目标不再合并判定集合；条件评估保持整批一次（`PermissionConditionDomainServiceImpl.evaluate` 纯逐条过滤+conditionId 记忆化、无集合语义，与切分等价）；null 目标/无条目目标拒绝路径与旧实现逐行同形。
- 互斥通道换 `openBatchMutexEvaluator`（请求级共享装载：规则一次、操作目录按 distinct 类型一次；命中按 (组,规则) 聚合后循环尾一次 `notifyHits`，groupKey=`GET_DENIED:{type}:{op}`、hitItemCount=命中目标数）——逐目标调 `filterPermMutex` 会每目标 DB 直查规则+操作目录（N+1），评估器是唯一合规通道。
- `BatchPermMutexEvaluatorImpl.compute` 补 I01 空规则短路（对齐单路径 `computePermMutexInternal`，T-PERM-083 先例）：空规则租户操作目录零装载——通道切换查询数零回归（非空规则租户新旧同=1 规则+1 目录；空规则两侧均=1+0）。

**测试**（红→绿）：

- 红跑取证：I01 评估器锁旧实现红（`ensureOperationIndex` 空规则仍装载操作目录 1 次）；D01 红跑沿 T-PERM-081 取证（2026-09-25，正确预期 isEmpty 下实际返回双目标）。
- D01 两断言按翻转契约转终态锚（`isEmpty`）；D02（query() 共同集合拒绝）/D03（同目标两端必拒）/R01/R02 恒绿锚不动。
- 定向：单测 62（BatchPermMutexEvaluatorTest 5+PermQueryEngineTest 32+PermissionConflictDomainServiceImplTest 嵌套 25）→ 复跑 37+25 绿；容器定向 Mutex 6/BatchAuthCheck 11/QuerySemantics 17/RoleMutexGuard 9 全绿；单测轨 1462 项 0 失败。
- 基线全量取证（2026-09-26，c1e56e3a6）：`mvn test -T 1C`（含 E2E/heavy，无 skip 开关）11 模块 BUILD SUCCESS、全仓 0 失败（access-service 单测 1462+容器 362、E2E 16）；评审追加的 getDenied 审计锁为纯测试增量+注释级主代码改动，经定向 6/6 复验（生产代码行为零变更）。

**基线落账**：annotated tag `r2-baseline-correctness` → c1e56e3a6；基线=13df35589（T-PERM-083 S/H-D）+ c1e56e3a6（本卡 PQ-01）两提交；验证证据=上述全量命令与结果。设计回写：§1.2 PQ-01 行落地注、§9.3 基线注、§9.4 回退句补 tag 名（§10.2 为验收矩阵非执行记录，D01 行预期达成不动）。

**双轨评审处置（2026-09-26）**：代码轨 P0-P2=0、P3×1；文档轨 P0-P2=0、P3×1——逐条亲核全成立：①GET_DENIED 引擎级互斥记账聚合（merge 计数/组键/单次 flush）无回归锁＋PermQueryEngineTest 桩注释半失实 → 镜像 BatchAuthCheckPgIT ⑧ 补 `MutexSemanticsCharacterizationPgIT` getDenied 审计锁（@MockBean AuditDomainService＋captor：times(1)+group+hitItemCount=2+真实规则 detail+未触发规则不出现；旧形态 summary 无 group 段＝红锚）＋注释订正，6/6 绿；②任务卡 last_updated 未 bump → 随收口刷新。存疑按既有口径处置：implementation.md §3.10:525 失实句维持留待（计划 A.8：R2 完结后 §3 族整体重写）；逐目标 O(目标×条目) 扫描维持（PQ-03 已登记修复面 T-PERM-085/093，卡非目标明示；queryBatch `evalBatchItem` 同款形状）。顺手：MutexHit javadoc 补 getDenied 轨语义。过度设计可裁剪项两轨均零。
