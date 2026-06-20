---
doc_type: task
id: T-PERM-001
title: Gateway 缓存改快照模式（user → InterfaceSnapshot）
status: review
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/services/gateway.md
depends_on: []
blocks: [T-PERM-002, T-PERM-006, T-GW-003]
acceptance:
  - "[x] Gateway 缓存 key 从 (user,service,method,path)→bool 改为 (tenant,subjectType,userId,serviceCode)→InterfaceSnapshotResp"
  - "[x] 鉴权时本地内存匹配(InterfaceSnapshotMatcher，Ant通配+scopeAll)，不再每条路径打 RPC"
  - "[x] 接入现成接口 POST /api/perm/auth/interface-snapshot（PermissionClient.interfaceSnapshot）"
  - "[x] 缓存失效采用短 TTL（30s）兜底；Redis pub/sub 主动广播为 T-PERM-006 协同范围"
  - "[x] InterfaceSnapshotResp/Req 迁入 perm-common 供 Gateway 共享"
  - "[x] fail-close 过渡期保留（stale-allow 为 T-GW-003）"
  - "[x] gateway compile + permission-center 135 tests 0 failures"
  - "[x] design/services/gateway.md + v3.5 §7.2 回写"
design_writeback:
  required: true
  status: done
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

## 决策记录（用户确认 2026-06-20）

| # | 议题 | 决策 |
|---|---|---|
| 1 | 本地快照路径匹配 | **支持 Ant 通配匹配**——pathPattern 含通配(如 `/api/user/**`)按 Ant 风格匹配；精确路径 equals。scopeAll=true 覆盖该 serviceCode 全部接口 |
| 2 | 令牌统一 sha256(permissions) | **本任务不做**，保留 `buildInterfacePermissionVersion`（私有 roleIds 指纹）与 `buildPermissionVersionKey`（domain service 占位）现状。⏳ **待办**：v3.5 §5.1 要求令牌改 sha256(permissions)，统一两套生成逻辑——单独跟踪，勿遗漏 |
| 3 | Fail-mode 过渡期 | **保持 fail-close 硬编码**——permission-center 不可达返回 503。stale-allow 是 T-GW-003 独立任务范围 |
| 4 | 缓存未命中处理 | **回源拉取快照后本地匹配**——本地快照缺失/过期时同步调 interface-snapshot 拉取并缓存，再本地匹配 |

## 范围边界

- **本任务做**：Gateway 缓存结构改 InterfaceSnapshot、PermissionClient 增 interfaceSnapshot 调用、PermissionFilter 改本地匹配、TTL 统一 30-60s、CacheConfig/CacheCatalog 一致性
- **本任务不做**：令牌算法升级(#2 待办)、stale-allow(T-GW-003)、Redis pub/sub 广播订阅(T-PERM-006)、fail-mode 配置化(T-GW-001)


## 验收

见 frontmatter `acceptance`。全部满足 + 回写 `design/services/gateway.md` 与 v3.5 §7.2 后方可 `done`。

## 设计回写

- `docs/design/services/gateway.md`：补"快照模式缓存"段落
- `docs/design/permission-center-v3.5-design.md §7.2`：核对快照模式表述与实现一致

## 阻塞下游

- T-PERM-002（AOP afterCommit 影响范围收集，需知道缓存对象形态）
- T-PERM-006（Redis 广播订阅器，需在快照上 evict）
- T-GW-003（stale-allow 依赖 InterfaceSnapshot 缓存对象）
