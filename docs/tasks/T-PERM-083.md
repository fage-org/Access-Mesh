---
doc_type: task
id: T-PERM-083
title: （R2-T04）角色互斥 S/H/D 确定化与纯互斥计算
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.1/§6.1
depends_on:
  - T-PERM-081
blocks: []
acceptance:
  - "filterRoleMutex 换 S/H/D 全命中确定化：R01（{A,B,C,D}+规则 A-B/B-C → 仅 D，规则顺序任意同果）与 R02（只持 A/C/D 无 B → 全保留，无图连通传递删除）在旧实现下红、新实现绿——终结 registry 2026-09-22 留观②「双删顺序不确定」"
  - "I01：空互斥规则时互斥专用操作目录装载零调用（computeMutexContext 补空规则短路）"
  - "单条路径 PERM_MUTEX 返回真实 triggeredRuleIds（notifyPermConflict 不再按冲突端点反推规则）；引擎消费不立即通知的纯角色判定能力，双删/互斥通知每租户×用户×规则对一次无重复"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
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
