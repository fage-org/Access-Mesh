---
doc_type: design
title: Gateway 服务设计
status: adopted
domain: gateway
last_reviewed: 2026-06-28
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

| 项 | 旧（check-interface 单值） | 新（快照模式，T-ACCESS-008） |
|---|---|---|
| 缓存载体 | 裸 Caffeine Bean | 自身唯一 `CacheService`（L1_ONLY，catalog `gw:interface-snapshot`） |
| 缓存 key | `(tenantId,subjectTypeCode,userId,serviceCode,httpMethod,path)` | identifier = `(subjectTypeCode,userId,serviceCode)`；完整键 `{tenantId}:gw:interface-snapshot:{identifier}` |
| 缓存值 | `Boolean`（仅 true 入缓存） | `InterfaceSnapshotResp`（含 `allowedApis[]`） |
| 鉴权方式 | 每请求打 RPC（未命中时） | 本地内存匹配，O(1) |
| TTL | 10s | 15s 兜底（主靠 Redis pub/sub 主动广播；TTL/容量由 catalog 声明，`accessmesh.cache.catalogs."gw:interface-snapshot".*` 运维覆盖，有效 TTL>15s 启动失败） |
| 未命中处理 | 调 check-interface | 5 秒全链路硬截止内回源拉 interface-snapshot 快照后缓存再匹配 |

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

### 主动失效（T-PERM-006 / T-ACCESS-008）

Gateway 启动后订阅 Redis topic `perm:invalidate`。access-service 写路径在事务提交后发布 `PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes)` JSON，Gateway 收到后经统一 `CacheService` 清理本地快照（T-ACCESS-008 用户决策：用户级精确 + 租户级兜底）：

- `userIds` 非空且 `serviceCodes`/`roleIds` 为空：按 `tenantId + userId` 精确清理该用户全部服务快照（本地跟踪索引枚举 identifier，覆盖用户角色关系变化）。
- `serviceCodes` 非空或仅 `roleIds` 非空：按 `tenantId` 租户级 `evictAll` 安全清理——Gateway 无本地反查服务/角色影响用户集合的能力，过度失效方向安全；回源惊群由 per-key in-flight 去重缓解。
- 广播丢失或订阅断线时由快照 TTL（≤15s）兜底。

广播契约定义在 `perm-common` 的 `PermInvalidateEvent`，由 access-service 发布端与 Gateway 订阅端共享，避免跨模块事件结构漂移。

### 快照失效标记与回源防护（T-GW-005 / S-006 / T-ACCESS-008）

> T-ACCESS-008：stale store、stale-allow 续命、`invalidatedKeys` 的 stale 门禁语义随可切换 fail-mode 一并删除；仍有效能力——**失效代际校验、per-key 回源去重、订阅重连全量清空**——保留如下。

**显式失效与代际校验**：`InvalidationMarker` 以租户限定键（`tenantId:identifier`）维护 `keyGeneration` / `globalEpoch`。回源开始前记录 `LoadToken(keyGeneration, globalEpoch)`；完成时只有 token 仍有效才能写入快照缓存，否则丢弃结果并重试一次（重试共享同一截止时刻）或转 fail-closed——禁止旧回源结果复活已撤销权限。

**Per-key 回源去重**：`InterfaceSnapshotLoadRegistry` 使同一快照 key 的并发请求共享同一个 `Mono<InterfaceSnapshotResp>`；回源完成后无论成功/失败都移除 in-flight key。

**跟踪索引**：失效器维护本地 `tenantId:identifier → userId` 跟踪索引（Caffeine），TTL/容量跟随 `gw:interface-snapshot` 的有效配置（经 `accessmesh.cache` 覆盖后的最终值），与主缓存同步过期。仅用于用户级失效枚举；索引缺失（毫秒级定时器偏差）的残留条目与广播丢失同等语义——由快照自身 ≤15s TTL 兜底，在 30s 安全预算内（用户决策 2026-08-21：TTL 兜底，不降级租户级清理）。用户级失效候选同时包含在途回源注册表 key（首次回源尚未登记索引时撤权，在途回源被代际作废重试）。孤立标记按 60s 周期清理。

**订阅重连：重连即全量清空**：与 Redis 断线重连后，先递增 `globalEpoch`（在途回源作废重试），再执行 catalog 级跨租户 `evictAll`（`CacheService.evictAll(catalog)`，不依赖跟踪索引推导租户——索引与主缓存是独立 Caffeine，容量压力下索引会先于主缓存淘汰），后续请求按需回源。pub/sub 无持久化，断线期间事件不可追回，全量清空确保安全；惊群由 per-key in-flight 去重缓解。

### 配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `gateway.permission.snapshot-load-deadline` | `5s` | 快照加载全链路墙钟硬截止（含服务发现/LB、连接、发送、处理、响应读取解码及失效竞争重试）；同一授权请求内所有尝试共享同一截止，超时不写缓存并固定 fail-closed 503。上限 5s，超限启动失败 |
| `gateway.permission.service-url` | `lb://permission-center` | 权限服务地址 |
| `gateway.permission.interface-snapshot-path` | `/api/perm/auth/interface-snapshot` | 快照拉取端点 |
| `gateway.permission.check-interface-path` | `/api/perm/auth/check-interface` | 保留（单值鉴权，回退用） |
| `accessmesh.cache.catalogs."[gw:interface-snapshot]".l1-ttl` | `15s`（catalog 声明） | 快照 TTL 兜底；有效值 >15s 启动失败（`GatewayCacheBoundaryValidator`） |
| `accessmesh.cache.catalogs."[gw:interface-snapshot]".l1-maximum-size` | `50000`（catalog 声明） | 本地快照最大条目 |

> T-ACCESS-008 已删除配置：`gateway.cache.l1.ttl-seconds` / `max-size`（统一到 catalog + `accessmesh.cache` 覆盖）、`gateway.cache.l1.stale-grace-seconds`（stale-allow 删除）、`gateway.permission.fail-mode`（固定 fail-closed，不可切换）。

### 失联兜底模式（T-ACCESS-008 固定 fail-closed）

> T-GW-001/T-GW-002/T-GW-003 历史实现的 `closed`/`open`/`stale-allow` 三模式及 stale store 已于 T-ACCESS-008 删除，权限回源失败**固定 fail-closed**，不可配置、不得绕过授权或使用过期结果。

| 场景 | 行为 |
|---|---|
| access-service 不可达（网络错误、超时、5xx） | 503 `SERVICE_UNAVAILABLE`（fail-closed） |
| 快照加载超过 5 秒全链路硬截止 | 不写缓存 + 503（`reason=deadline_exceeded`） |
| 显式失效（`perm:invalidate` 已到达）后回源失败/在途回源代际失效 | 503（权限主动撤销 > 不可达兜底） |
| 快照匹配 DENY / FALLBACK 且 check-interface 失败 | 403 / 503 |

**核心原则：权限主动撤销 > 服务不可达兜底；任何不确定性一律拒绝。**

### 监控指标

Gateway 通过 Micrometer 暴露 Prometheus 指标。依赖 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，端点 `/actuator/prometheus`。

#### 计数器

| 指标名 | Tag | 含义 |
|---|---|---|
| `gateway.perm.unreachable` | `source=snapshot` | 快照回源不可达计数 |
| `gateway.perm.unreachable` | `source=check_interface` | fallback check-interface 不可达计数 |
| `gateway.perm.fallback` | `mode=closed, reason=denied` | fail-closed 拒绝次数 |
| `gateway.perm.fallback` | `mode=closed, reason=deadline_exceeded` | 快照加载超 5 秒硬截止次数 |

> 所有 `gateway.perm.fallback` counter 统一使用 `{mode, reason}` 标签集。原 `mode=open`/`mode=stale` 系列指标随 fail-mode 删除一并移除。

#### 统一缓存框架指标

快照缓存经统一 `CacheService`（L1_ONLY）自动接入框架指标：`cache.l1.hits` / `cache.l1.misses` / `cache.puts`（回源回填）等，tag `catalog=gw:interface-snapshot`；上游 access-service 侧另有 `cache.l2.hits`/`cache.l2.misses`/`cache.l2.errors`/`cache.invalidate.failures` 区分 L1/L2 命中、回源与失效失败。

#### Prometheus 告警规则示例

```yaml
- alert: GatewayPermCenterUnreachable
  expr: increase(gateway_perm_unreachable_total[5m]) > 0
  for: 1m
  labels: { severity: warning }
  annotations:
    summary: "Gateway 检测到 access-service 不可达"

- alert: GatewayPermFallbackClosed
  expr: increase(gateway_perm_fallback_total{mode="closed",reason="denied"}[5m]) > 10
  for: 2m
  labels: { severity: critical }
  annotations:
    summary: "Gateway fail-closed 拒绝过多"

- alert: GatewayPermSnapshotDeadlineExceeded
  expr: increase(gateway_perm_fallback_total{mode="closed",reason="deadline_exceeded"}[5m]) > 0
  for: 2m
  labels: { severity: warning }
  annotations:
    summary: "Gateway 快照加载超过 5 秒全链路截止"
```

## 与权限中心的约定

- 接口级鉴权契约以 `../permission-center/api-contract.md` 为准（§6.5 check-interface、§6.6 interface-snapshot）。
- CORS、限流、请求体大小、安全响应头按 `../project-rules.md` 和网关配置实现。

## 实现参考

- 整体架构见 `../architecture.md`。
- 权限中心流程见 `../permission-center/core-flows.md`。
- 旧版详细过滤器链和配置样例见归档文档。
