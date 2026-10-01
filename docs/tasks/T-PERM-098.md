---
doc_type: task
id: T-PERM-098
title: 操作位写入与准入消费一致性边界定案与收口
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §12（resource 能力：操作定义）+ §25（操作准入协议）
  - docs/design/engine/implementation.md §3.3（执行管线/prepareAdmissionClauses）
depends_on: []
blocks: []
acceptance:
  - "拍板写入侧与准入消费的一致性边界（候选：写侧单比特约束 / 准入侧坏位行宽容并明确报错口径），拍板按 decision-question-protocol 举例上报用户"
  - "核对 T-PERM-077「不新增数值校验」原取舍：若推翻须在契约 §12 修订并留取舍变更依据；inheritMask 负数表示维持不是错误"
  - "按拍板实施：直接 API 向内置类型写非幂位后，与其有效位相交的准入要求不再出现未定义行为（20071 触发条件与快照构建失败面有契约口径）；自定义类型补种链路（AUTHORITY_ROOT 单比特 CHECK 拦截）行为不变"
  - "回归锁覆盖：碰撞位写入的准入快照构建路径（旧实现下失败/未定义）"
  - "契约 §12/§25 与 implementation §3.3 口径同步"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-PERM-098 操作位写入与准入消费一致性边界定案与收口

## 背景

承接 [Q-050](../pending-problems.md#q-050)：`OperationAppServiceImpl.createOperation/updateOperation` 未限制 `binaryBit` 为单比特，operation_permission 表也无对应 CHECK；内置类型跳过补种、可追加不重复的非幂位。而 `QueryExecutionEngine.prepareAdmissionClauses` 拒绝覆盖坏位行——直接 API 写入位 3 后，与其有效位相交的准入要求可能触发 20071、相关快照构建失败。前端已有幂位校验，缺口在直接 API 面。该问题是 T-PERM-077 之后新增严格准入消费引入的影响。

## 范围

定案写入约束与准入消费的一致性边界 → 核对并按需修订 T-PERM-077 原取舍 → 实施与回归锁 → 契约/实现口径同步。属「定案+实施」混合卡：定案后若实施面超预期（如需 DDL CHECK），超出面另立新号。

## 当前口径

现状：内置类型可追加非幂位、自定义类型被补种 CHECK 天然拦截；准入侧对坏位行 fail（20071）。任务内决策点：一致性边界收在写侧（源头拒）还是准入侧（消费宽容/报错口径明确），两案的安全与存量影响随拍板记录。

## 非目标 / 遗留

- inheritMask 负数表示语义（非错误，维持）。
- 前端幂位校验（已有）。
