---
doc_type: design
title: Gateway 服务设计
status: adopted
domain: gateway
last_reviewed: 2026-06-20
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
| TTL | 10s | 30s 兜底（主靠 Redis pub/sub 主动广播，T-PERM-006） |
| 未命中处理 | 调 check-interface | 回源拉 interface-snapshot 快照后缓存再匹配 |

### 本地匹配规则（`InterfaceSnapshotMatcher`）

对快照 `allowedApis` 逐条判定，任一命中即放行：

1. `scopeAll=true` 且 `serviceCode` 匹配 → 放行（覆盖该服务全部接口）。
2. `httpMethod` 相等（条目为 null 视为通配）且 `pathPattern` 按 **Ant 风格**匹配请求路径 → 放行。
3. 否则拒绝。

> `hasCondition` 条目按放行处理——条件评估在权限中心快照构建时已完成，快照内仅含评估通过且互斥过滤后的条目。

### 失败模式

- permission-center 不可达 → **fail-close**，返回 503。
- stale-allow（用过期快照续命）由 **T-GW-003** 实现，本任务过渡期不做。

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
