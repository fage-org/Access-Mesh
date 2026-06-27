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

1. `scopeAll=true` 且 `serviceCode` 匹配 → 该条目纳入候选（覆盖该服务全部接口）。
2. `httpMethod` 相等（条目为 null 视为通配）且 `pathPattern` 按 **Ant 风格**匹配请求路径 → 该条目纳入候选。
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
