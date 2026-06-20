---
doc_type: task
id: T-PERM-001
title: Gateway 缓存改快照模式（user → InterfaceSnapshot）
status: proposed
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/services/gateway.md
depends_on: []
blocks: [T-PERM-002, T-PERM-006, T-GW-003]
acceptance:
  - "Gateway 缓存 key 从 (user, path) → bool 改为 user → InterfaceSnapshot"
  - "鉴权时本地内存 O(1) 匹配，不再每条路径打 RPC"
  - "接入现成接口 POST /api/perm/auth/interface-snapshot"
  - "缓存失效采用短 TTL（30-60s）兜底 + Redis pub/sub 主动广播（与 T-PERM-006 协同）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-20
---

# T-PERM-001 Gateway 缓存改快照模式

> 来源：[perm-cache-invalidation-plan](../plans/perm-cache-invalidation-plan.md) 任务 A-1（工作单 A，方案 A'-1）

## 背景

当前 Gateway 缓存 `(user, service, method, path) → Boolean` 单值，且仅靠 10s TTL 失效，**完全没有版本号比对**，permission_version 表实际不通向 Gateway。改快照模式后鉴权降为本地内存匹配，并为 stale-allow（T-GW-003）提供可"过期续命"的快照对象。

## 方案要点

- 缓存 key：`user → InterfaceSnapshot`（用户全部可访问接口集合）
- 鉴权：本地内存 hash 查找，O(1)
- 接入现成接口 `POST /api/perm/auth/interface-snapshot`
- 失效：短 TTL（30-60s）兜底 + Redis pub/sub 主动广播（T-PERM-006 实现广播）

## 验收

见 frontmatter `acceptance`。全部满足 + 回写 `design/services/gateway.md` 与 v3.5 §7.2 后方可 `done`。

## 设计回写

- `docs/design/services/gateway.md`：补"快照模式缓存"段落
- `docs/design/permission-center-v3.5-design.md §7.2`：核对快照模式表述与实现一致

## 阻塞下游

- T-PERM-002（AOP afterCommit 影响范围收集，需知道缓存对象形态）
- T-PERM-006（Redis 广播订阅器，需在快照上 evict）
- T-GW-003（stale-allow 依赖 InterfaceSnapshot 缓存对象）
