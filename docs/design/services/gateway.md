---
doc_type: design
title: Gateway 服务设计
status: adopted
domain: gateway
last_reviewed: 2026-06-27
---

# Gateway 服务设计

本文档是 Gateway 服务的精简设计入口。旧版完整设计已归档到 `../../archive/2026-04-28/gateway-design.full.md`，仅用于追溯。

## 职责边界

- 作为系统唯一流量入口，负责路由、Token 校验、白名单、请求上下文注入和接口级鉴权。
- 不直接读取业务库或权限中心数据库。
- 鉴权采用**快照模式**（T-PERM-001）：调用权限中心 `POST /api/perm/auth/interface-snapshot` 拉取用户全量接口权限快照，本地内存匹配，不再每请求打 RPC。
- 只处理入口安全和路由职责，不承载业务权限管理页面或授权配置。

## 核心链路

1. 接收客户端请求并匹配白名单。
2. 解析 Sa-Token / OAuth2 Token，得到主体信息。
3. 清洗客户端伪造的安全 Header，再注入可信 `X-Tenant-Id`、`X-Request-Id`、`traceId`、主体标识等上下文。
4. **快照鉴权**（T-PERM-001）：按 `(tenantId, subjectTypeCode, userId, serviceCode)` 查本地快照缓存——命中则本地匹配；未命中回源拉取 `interface-snapshot` 快照后缓存再匹配。
5. 允许时转发到目标服务，拒绝时返回统一 403 错误响应。

> **平台超管跨租户（2026-06-20 审计 S-017）**：v3.5 **不支持**平台超级管理员跨租户操作。超管必须分别登录每个租户实例，`X-Tenant-Id` 始终对应当前登录租户。不支持双 Header（`X-Tenant-Id` + `X-Target-Tenant-Id`）跨租户切换；如未来需支持，作为 v3.5.1+ platform-admin 增量设计。`TenantManager.ignore()`（见 project-rules.md）仅用于内部测试/迁移场景，**非超管跨租户能力**，禁止用于生产跨租户访问。

## 快照模式鉴权（T-PERM-001）

### 缓存模型

| 项 | 旧（check-interface 单值） | 新（快照模式） |
|---|---|---|
| 缓存 key | `(tenantId,subjectTypeCode,userId,serviceCode,httpMethod,path)` | `(tenantId,subjectTypeCode,userId,serviceCode)` |
| 缓存值 | `Boolean`（仅 true 入缓存） | `InterfaceSnapshotResp`（含 `allowedApis[]`） |
| 鉴权方式 | 每请求打 RPC（未命中时） | 本地内存匹配，O(1) |
| TTL | 10s | 30s 兜底（主靠 Redis pub/sub 主动广播，T-PERM-006 已落地） |
| 未命中处理 | 调 check-interface | 回源拉 interface-snapshot 快照后缓存再匹配 |

### 本地匹配规则（`InterfaceSnapshotMatcher`）

对快照 `allowedApis` 遍历做 **OR 合并 + 三态判定**（ALLOW / FALLBACK / DENY）：

1. `scopeMode=ALL` 且 `serviceCode` 匹配 → 该条目纳入候选（覆盖该服务全部接口）。
2. `scopeMode=INSTANCE` 且 `httpMethod` 相等（条目为 null 视为通配）且 `pathPattern` 按 **Ant 风格**匹配请求路径 → 该条目纳入候选。
3. 候选条目按 `hasCondition` 分支处理：
   - `hasCondition=false`（无条件授权）→ 立即 `ALLOW`（"任一无条件授权放行"原则）。
   - `hasCondition=true` 且 `conditionRules` 内联（`gateway_evaluable=true`）→ 用请求 `clientIp` + 本进程时钟本地评估：通过则 `ALLOW`，不通过继续遍历。
   - `hasCondition=true` 但 `conditionRules` 未下发（`gateway_evaluable=false` 或防御过滤拒绝）→ 标记需要 `FALLBACK`，继续遍历（后续仍可能有无条件条目兜底）。
4. 遍历结束：未命中 `ALLOW` 时，有 `FALLBACK` 标记 → 调 `/api/perm/auth/check-interface` 同步回退实时鉴权（context 仅承载 `clientIp`）；否则 `DENY`。

> **条件权限混合评估（T-PERM-017，2026-06-24）**：废止"`hasCondition` 直接放行"。可下发条件（`IP_WHITELIST` / `IP_BLACKLIST` / `DATE_RANGE` / `TIME_RANGE` 四类）由权限中心 `SnapshotAssembler` 内联 `conditionRules` JSON 进 `ApiPermissionEntry`，Gateway 用 `ConditionEvalUtils`（已迁入 `perm-common`）本地重评。跨进程时钟一致性由 NTP 同步保证（亚秒漂移 < 业务粒度小时级），不通过 context 传递 `timestamp`。未来扩展类型（如 `ORG_SCOPE` / `DATA_OWNER`）默认 `gateway_evaluable=false`，由 fallback 通路回到 permission-center 评估。`PermissionFilter` 提取 `clientIp` 顺序：`X-Forwarded-For` 首段 → `X-Real-IP` → 远端地址。

> **P1-② 多授权折叠修复**：`SnapshotAssembler` 实例级条目按 `(resourceEntityId, conditionId)` 组合展开；同一资源含条件+无条件多条授权各产出独立 `ApiPermissionEntry`，避免折叠后被错误统一处理。配合 Matcher OR 合并语义，保证"任一无条件条目存在即放行"。

### 主动失效（T-PERM-006）

Gateway 启动后订阅 Redis topic `perm:invalidate`。permission-center 写路径在事务提交后发布 `PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes)` JSON，Gateway 收到后清理本地 `interfaceSnapshotCache`：

- `serviceCodes` 非空：按 `tenantId + serviceCode` 清理对应服务下所有用户快照，覆盖 API mapping / 资源 / syncInterfaces / 条件规则影响的快照构建结果。
- `userIds` 非空且 `serviceCodes` 为空：按 `tenantId + userId` 清理该用户所有服务快照，覆盖用户角色关系变化。
- 仅 `roleIds` 非空：Gateway 不读权限库，无法本地反查角色影响用户，按 `tenantId` 级安全清理；广播丢失或订阅断线时仍由 `gateway.cache.l1.ttl-seconds` 兜底。

广播契约定义在 `perm-common` 的 `PermInvalidateEvent`，由 permission-center 发布端与 Gateway 订阅端共享，避免跨模块事件结构漂移。

### 快照失效标记与订阅恢复（T-GW-005 / S-006）

Gateway 本地快照有两种失效方式，在 stale-allow 模式下行为不同：

| 方式 | 触发 | stale-allow 可续命？ | 语义 |
|---|---|---|---|
| 自然过期 | 主缓存 `expireAfterWrite` TTL 到期，条目转入 stale store | ✅ 可以 | 快照陈旧但未被主动撤销 |
| 显式失效 | 收到 `perm:invalidate` Redis 广播 | ❌ 不可以 | 权限中心明确告知权限已变更 |

**核心原则：权限主动撤销 > 服务不可达兜底。**

#### 三层缓存结构

```
interfaceSnapshotCache: Cache<String, InterfaceSnapshotResp>   // L1 主缓存（expireAfterWrite = ttlSeconds，默认 30s）
staleSnapshotCache:   Cache<String, StaleEntry>               // L2 陈旧快照缓存（expireAfterWrite = ttlSeconds + staleGraceSeconds，默认 60s）
invalidatedKeys:      Set<String>                              // 显式失效标记集合
```

`StaleEntry` 包装 `(InterfaceSnapshotResp snapshot, Instant staleUntil)`，`staleUntil` = 快照首次写入主缓存的时刻 + `ttlSeconds` + `staleGraceSeconds`（默认 30+30=60s），续命时检查 `Instant.now().isBefore(entry.staleUntil())`。**不基于 RemovalListener 触发时刻**——Caffeine 过期清理可能延迟触发，基于触发时刻计算会错误延后 stale 窗口。

**关键安全约束**：`perm:invalidate` 事件必须**同时驱逐主缓存和 stale store**，并**标记 invalidatedKeys**。仅驱逐主缓存而遗漏 stale store 会导致 stale-allow 续命使用已撤销权限。

#### 失效标记

显式失效通过独立标记集合 `Set<String> invalidatedKeys` 追踪：

| 触发场景 | 主缓存 | stale store | invalidatedKeys |
|---|---|---|---|
| 回源拉取新快照成功 | `put(key, snapshot)` | `invalidate(key)` | `remove(key)` |
| 主缓存条目过期（RemovalListener cause=EXPIRED） | 自动淘汰 | `put(key, StaleEntry(snapshot, staleUntil))` | — |
| `perm:invalidate` 事件 | `invalidate(key)` | `invalidate(key)` | `add(key)` |
| stale-allow 续命检查 | — | `getIfPresent(key)` → 检查 staleUntil + 检查 !invalidatedKeys | `contains(key)` |
| 订阅重连全量清空 | `invalidateAll()` | `invalidateAll()` | `clear()` |

标记维度与 `InterfaceSnapshotCacheInvalidator.evict()` 驱逐维度一致：`serviceCodes` 非空按服务、`userIds` 非空按用户、仅 `roleIds` 按租户级。标记生命周期：回源成功时清除、定期清理孤立 key（默认 60s 扫描）、重连全量清空时一并清除。

#### Per-key 回源去重

当前 `PermissionFilter` 使用手动 `getIfPresent` + 手动回源，**不存在** per-key 并发去重。重连全量清空后同 key 并发请求会打出多次远端回源。落地时需采用 `ConcurrentHashMap<String, Mono<InterfaceSnapshotResp>>` 作为 in-flight 去重表，同 key 并发请求共享同一 `Mono`。

#### 订阅恢复：重连即全量清空

Gateway 与 Redis 断线重连后执行全量清空（主缓存 + stale store + 失效标记），后续请求按需回源。设计理由：pub/sub 无持久化，断线期间事件不可追回，全量清空确保安全；惊群由 per-key in-flight 去重缓解。

`PermInvalidationSubscriber` 需增加重连检测：Reactive Redis 订阅的 `onError`/`onComplete` 标记断开，重连成功后触发全量清空。

### 配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `gateway.cache.l1.ttl-seconds` | 30 | 快照 TTL 兜底 |
| `gateway.cache.l1.max-size` | 50000 | 本地快照最大条目 |
| `gateway.permission.service-url` | `lb://permission-center` | 权限中心地址 |
| `gateway.permission.interface-snapshot-path` | `/api/perm/auth/interface-snapshot` | 快照拉取端点 |
| `gateway.permission.check-interface-path` | `/api/perm/auth/check-interface` | 保留（单值鉴权，回退用） |

## 与权限中心的约定

- 接口级鉴权契约以 `../permission-center/api-contract.md` 为准（§6.5 check-interface、§6.6 interface-snapshot）。
- CORS、限流、请求体大小、安全响应头按 `../project-rules.md` 和网关配置实现。

## 实现参考

- 整体架构见 `../architecture.md`。
- 权限中心流程见 `../permission-center/core-flows.md`。
- 旧版详细过滤器链和配置样例见归档文档。
