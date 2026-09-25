---
doc_type: task
id: T-PERM-084
title: （R2-T05）QueryReadSupport 与读来源分桶
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §4.2/§5.2/§5.4
depends_on:
  - T-PERM-082
blocks: []
acceptance:
  - "I02：20 辅助类型默认一次多类型 Mapper 调用（batchLoadOperationsByResourceTypes 逐类型 SQL 直查收敛；辅助操作解析移到输出开关判断之后）——PQ-02 修复面；I03：最小输出成功路径无装配专用操作解析/描述读取"
  - "三态记忆（UNLOADED／LOADED_EMPTY／LOADED_VALUE）落地；I04：已读空类型/缺失操作请求内不重复回源（Map.get()==null 不再兼任「未读」与「不存在」）"
  - "I05：缓存回填保留读前令牌/剩余 TTL 不重置；I06：缓存掩码目录与新鲜定义分桶、互不覆盖（缓存掩码不得覆盖 freshDefinitionIndex）"
  - "RolePermEntry 作为缓存载荷边界例外保留（读边界转 GrantFact），新执行器不消费旧 PermResult（§5.4）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-PERM-084 （R2-T05）QueryReadSupport 与读来源分桶

## 背景

设计 §5.2 读取矩阵与 §4.2 装载批/判定集合分离（报告临时编号 R2-T05）。现状缺陷：resolveOperationIdsForAncillary 逐类型循环且在 includeOperations 开关前调用；batchLoadOperationsByResourceTypes 逐类型 SQL 直查绕过缓存目录。

## 范围

- QueryReadSupport 部件（有租户/空集守卫的批量 DB/缓存访问与同源复用）；空 entityIds/bitMasks 守卫不得退化为无界 SQL。
- 读来源分桶与三态记忆；转授无目标类型授权时仍装载目标操作定义（区分 INVALID_OPERATION 与 NO_PERMISSION）。

## 非目标 / 遗留

- 不改各缓存条目 mode/TTL（§5.2 保留既有边界）；RolePermEntry 载荷版本化备选不在本卡（§5.4 备选）。
