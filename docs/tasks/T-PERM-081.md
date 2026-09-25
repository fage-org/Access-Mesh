---
doc_type: task
id: T-PERM-081
title: （R2-T02）PQ-01/06 反例与正常语义基线
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §1.2/§10.2
depends_on:
  - T-PERM-080
blocks:
  - T-PERM-083
  - T-PERM-095
acceptance:
  - "PQ-01 反例（getDenied* 跨 item 整批互斥过拒 vs queryBatch 逐 item 评估）与 PQ-06 反例（角色互斥规则顺序依赖：{A,B,C,D}+规则 A-B/B-C 两种顺序两种结果）在真实规则/数据库夹具下复现并留证"
  - "正常语义基线（check/batch-check/范围四态/快照投影）固定为差分锚，供 T-PERM-089/090 的 X03 等价差分使用"
  - "不全用 passthrough mock——真实规则与真实数据库可复现"
design_writeback:
  required: false
  status: done
last_updated: 2026-09-25
---

# T-PERM-081 （R2-T02）PQ-01/06 反例与正常语义基线

## 背景

设计 §1.2 已核实的缺陷需先有可复现基线，后续修复才有红跑锚点（报告临时编号 R2-T02）。PQ-01 锚点：`PermQueryEngine.computeInstanceDenied` 整批 filterPermMutex 后回映射；PQ-06 锚点：`PermissionConflictDomainServiceImpl.filterRoleMutex` 顺序遍历边删边判、规则无 ORDER BY。

## 范围

- characterization 夹具：真实冲突规则 + 真实授权数据复现 D01~D03、R01~R02 反例与正常语义。
- 差分基线数据集（固定 seed/时钟/规则/读入事实口径，设计 §9.3）。

## 非目标 / 遗留

- 不修实现——PQ-06 修复在 T-PERM-083、PQ-01 最小修复在 T-PERM-095（两者构成 §9.3 最小正确性修复基线）、新核心全面重写在 T-PERM-085。

## 完成记录

**交付（2026-09-25，零生产代码变更，全部真实 PG/Redis 容器轨）**：

- `R2BaselineFixture`：固定事实集共享夹具（类型段 941~953 / 实体·角色·主体·映射·授权·条件·规则·操作位固定 id 段；幂等种子 ON CONFLICT；唯一条件=恒不满足 DATE_RANGE，期望输出与运行时钟无关）——T-PERM-089/090 X03 差分直接复用。
- `MutexSemanticsCharacterizationPgIT`（D01/D02/D03/R01/R02）：
  - **D01（PQ-01 反例，锁当前行为）**：queryBatch 两独立 item 双放行（对照极）vs `getDeniedEntityIds/getDeniedResourceCodes` 现状双拒（整批 filterPermMutex 跨 item 双删）同图对拍；单目标对照锁「任何实现下放行」。红跑取证：正确预期 isEmpty 下实际返回两个实体 id。
  - **D02/D03（正确语义锚，修复前后不变）**：一个目标集合项两端同场按共同集合拒绝；同目标挂两端单点/批量/getDenied/hasPermissionByEntityId 全拒。
  - **R01（PQ-06 反例，锁当前行为）**：两租户两规则处理序实跑——存活集 {C,D} vs {A,D}（断言互不相等=顺序敏感性实跑证据）且均 ≠ 正确答案 {D}。红跑取证：租户 A 正确预期 containsExactly("D") 下实际 ["C","D"]；租户 B {A,D} 由绿跑 isNotEqualTo 反证。**实施发现**：`selectByConflictType` 无 ORDER BY 下 PG 走 `uk_conflict_rule_role` 部分唯一索引扫描，返回序=first_abstract_role_id 索引序而非堆插入序（首轮「两租户两插入序」形态因此归一失败）——终版用「角色创建序定索引序＋规则插入序定堆序」双控对齐，规则行按生产形态 first<second 直插，任一执行计划下处理序稳定。
  - **R02（正确语义锚，修复前后不变）**：只持 {A,C,D} 各规则单端不触发、无传递冲突。
- `QuerySemanticsBaselinePgIT`（四族 X03 差分锚，全经 AppService 契约入口）：check 九用例（实例/闭包/条件/类型级/NO_ROLE/ROLE_MUTEX 双删 NO_ROLE/scopeAll 短路含幽灵编码/条件摘光 scopeAll）、batch-check 下标对齐六形态+互斥整批 NO_ROLE、范围四态（INSTANCE/EMPTY/ALL/DENIED+父拒整表）、快照投影（实例映射精确条目/scopeAll 展开 enabled 映射/空主体与双删空快照）。

**断言翻转契约（T-PERM-083/095 落地时执行，均已在断言 as() 标注）**：D01 两断言→isEmpty；R01 两租户→containsExactly("D") 且互等；D02/D03/R02 不变。T-PERM-095 验收「D01~D03 差分锚转绿」与本契约对齐。

**三项拍板（registry 2026-09-25 同日行）**：①缺陷断言口径=锁当前行为（修复当天变红强制翻转）；②R01 复现=两租户两规则处理序实跑（双控稳定）；③数据集载体=共享 Java 夹具类。

**评审**：双轨本地评审（代码轨 P0~P2=0、文档轨 P2×1+P3×3+代码轨 P3×4）全数逐条核实成立并直修：A.7 补录三件资产并改写「在其上补」失实表述、R01 规则行改生产形态 (B,A)（索引序与结果不变）、D03 合并重复 query、newType 去 identity 返回、insertResourceRow 去无消费 parentId、R02 裁闲置全持用户、进度行时态修正。复跑全绿（容器轨定向，两类全过）。
