---
doc_type: task
id: T-PERM-047
title: 操作定义缓存失效接线——OPERATION_PERMISSIONS_BY_TYPE 写路径 evict（create/update/deleteOperation）
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§5
  - .claude/skills/dual-layer-cache-framework/SKILL.md
depends_on: []
blocks: []
acceptance:
  - "OPERATION_PERMISSIONS_BY_TYPE（L1 60m/L2 120m，普通缓存+跨实例 L1 失效广播）在 createOperation/updateOperation/deleteOperations 写路径后无任何 evict——引擎（PermQueryEngine 位掩码判定）最长 1-2 小时按旧位值/已删操作判定，位值变更后已授权角色语义静默翻转（T-PERM-028 双轨评审 P2 登记，2026-08-29 用户决策立独立任务；旧 id 版同样缺失，非 T-PERM-028 回归）"
  - "按 dual-layer-cache 框架 evictAfterCommit 模式接线：三处写路径事务提交后失效；键粒度需定夺（引擎按 type 维度缓存 identifier，写操作影响该租户该类型集合——逐 type 失效 vs 整租户失效），含 TypeDefinition 联动预置路径的失效"
  - "注意失效广播目录语义：L1_L2 目录失效经 RTopic 广播各实例清本地 L1（框架已内置，写路径只需 evictAfterCommit）；补缓存失效触发表到 implementation.md §5.2"
  - "回归：位值更新后引擎判定即时生效的单测/PgIT（旧实现下失败——TTL 兜底窗口内旧值判定）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-29
---

# T-PERM-047 操作定义缓存失效接线——OPERATION_PERMISSIONS_BY_TYPE 写路径 evict

> 状态：proposed（T-PERM-028 双轨评审登记，2026-08-29 用户决策立独立任务）
> 依赖：无硬依赖（缓存框架 evictAfterCommit 机制现成，键粒度设计是主要工作）

## 背景

PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE 目录注释承诺「普通缓存 + 跨实例 L1 失效广播」，但 operation-permission 的三个写路径（create/update/delete）从未接线任何 evict——承诺的失效机制只对快照链路目录实现过。T-PERM-028 把位值更新做成管理页主流程后暴露面变大（此前该页无真实后端）。

## 风险定性

位值变更是极低频管理操作，TTL（L1 60m/L2 120m）兜底；窗口内语义翻转属惰性不一致，非越权非损坏。因此登记独立任务而非热修。
