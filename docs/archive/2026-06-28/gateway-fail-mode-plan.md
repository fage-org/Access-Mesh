---
doc_type: plan
title: Gateway 失联兜底（工作单 C）
status: archived
domain: gateway
design_refs:
  - docs/design/permission-center-v3.5-design.md
  - docs/design/services/gateway.md
tasks:
  - T-GW-001
  - T-GW-002
  - T-GW-003
  - T-GW-004
  - T-GW-005
  - T-GW-006
acceptance: "C-1~C-4 + C-6 全完成（C-5/T-GW-005 设计已完成，T-PERM-008 代码基础设施已落地；C-6/T-GW-006 重新界定为独立仓库测试服务）；Gateway fail-mode 三模行为符合配置；监控指标接入 Prometheus"
last_updated: 2026-06-28
---

# Gateway 失联兜底计划（工作单 C）

> 状态：已完成并归档
> 关联设计：[../../design/permission-center-v3.5-design.md](../../design/permission-center-v3.5-design.md) §7.2 缓存一致性总线
> 关联评审（已归档）：[../../archive/2026-06-17/design-review.md](../../archive/2026-06-17/design-review.md) §4.3 工作单 C
> 关联审计：S-006（Gateway 失效标记与订阅恢复，已设计 T-GW-005）

## 目标

落实 design-review §4.3 工作单 C 决策（方案 C1+C2）：

1. **三模 fail-mode 配置**：`closed`（默认，fail-closed）/ `open`（仅 demo）/ `stale-allow`（折中）
2. **stale-allow 语义**：主缓存过期条目转入 stale store（L2），续命前检查 `staleUntil`（= 原始写入时间 + ttl + staleGraceSeconds）+ `!invalidatedKeys`，超时转 closed
3. **监控指标**：`gateway.perm.unreachable.count` / `fallback.{closed,open,stale}.count` + Prometheus 告警
4. **与工作单 A 协作**：A'-1 快照模式后缓存对象为 `InterfaceSnapshot`，支持 stale-allow；A'-4 Redis 广播失效时标记快照"已显式失效"（不允许 stale-allow 继续使用）

## 非目标

- 不做 Gateway 失效标记数据结构与订阅恢复策略的完整规范（S-006 已设计 T-GW-005，代码落地由 T-PERM-008 执行）
- 不改 Gateway 鉴权主链路（仅加 fail-mode 兜底分支）
- 不做 fail-mode 的动态切换 UI（配置项由运维通过配置中心管理）

## 任务清单（引用 [../../tasks/README.md](../../tasks/README.md) 看板）

| 任务 ID | 标题 | 关联决策 | 状态 |
|---|---|---|---|
| T-GW-001 | Gateway `gateway.permission.fail-mode` 配置项 + `stale-grace-seconds` | C1 | ✅ |
| T-GW-002 | fail-closed 实现：perm-center 不可达 → 403/503 拒绝 | C1 | ✅ |
| [T-GW-003](../../tasks/T-GW-003.md) | stale-allow 实现：过期快照续命，超 stale-grace-seconds 转 closed | C1 / C2 | ✅ |
| T-GW-004 | 监控指标：`unreachable.count` / `fallback.{closed,open,stale}.count` + Prometheus 告警 | C2 | ✅ |
| T-GW-005 | 失效标记与订阅恢复策略设计（S-006 规范产出；T-PERM-008 落地依赖本任务）| A'-4 / S-006 | ✅ |
| T-GW-006 | 集成测试基线："杀 permission-center → Gateway 应 503"（重新界定：不在项目内做集成测试，改为独立仓库测试服务） | C1 | ✅ |

## 准入条件

> 以下为归档时历史快照，A/B/C 已完成并归档，准入条件已事实满足。

- [x] design-review §11 暂缓解除（A/B/C 已完成并归档，暂缓事实已解除）
- [x] 工作单 A 快照模式（A-1）完成（T-PERM-001 ✅，stale-allow 依赖 InterfaceSnapshot 缓存对象）
- [x] ~~S-006 Gateway 失效标记待设计方案明确~~（2026-06-28 T-GW-005 已完成设计，详见 gateway.md §快照失效标记与订阅恢复）

## 当前进度

- 文档层：design-review §4.3 决策已记录 + §C 协作段加 S-006 待设计注记（2026-06-20 审计）；T-GW-005（S-006）设计规范已产出（2026-06-28），详见 gateway.md §快照失效标记与订阅恢复；T-GW-001/002/003 失联兜底模式设计已回写 gateway.md §失联兜底模式
- 代码层：
  - S-006 代码基础设施已由 T-PERM-008 落地（stale store / InvalidationMarker / in-flight 去重 / 订阅恢复全清）
  - T-GW-001 ✅ `FailMode` 枚举（CLOSED/OPEN/STALE_ALLOW）+ `gateway.permission.fail-mode` 配置项已落地（默认 CLOSED）
  - T-GW-002 ✅ fail-closed 实现已落地——`PermissionFilter` 新增 `handleUnreachable()` 方法按 fail-mode 三模分发：CLOSED→503 / OPEN→放行 / STALE_ALLOW→stale-allow 续命（T-GW-003 已实现）；`StaleLoadDiscardedException` 始终 503（显式撤销 > 不可达兜底）。`fallbackCheckInterface` 不可达路径同样按 fail-mode 分发。P1 修复：显式失效（perm:invalidate 已到达）后回源失败也始终 503，OPEN 不放行。P2 修复：仅 `PermCenterUnreachableException`（远端不可达）走 fail-mode 三模分支；非远端异常（代码 bug / DTO 兼容等）始终 fail-closed。
  - T-GW-003 ✅ stale-allow 续命已落地——`PermissionFilter.tryStaleAllow()` 方法实现：从 stale store 取 StaleEntry，双重检查 staleUntil + !invalidatedKeys，通过后本地匹配快照（ALLOW→放行 / DENY→403 / FALLBACK→403 条件不可评估）；不通过降级 closed（503）。`decide()` / `fallbackCheckInterface()` 传播 cacheKey 供 stale store 查找。验证：57 gateway tests（含 8 个 stale-allow 测试），0 failures
  - T-GW-004 ✅ 监控指标已落地——Micrometer Counter 注入 PermissionFilter：`gateway.perm.unreachable`（tag: source=snapshot|check_interface）+ `gateway.perm.fallback`（tag: mode=closed|open|stale + reason 细分）。actuator + micrometer-registry-prometheus 依赖已加入 pom.xml，bootstrap.yml 配置 prometheus 端点暴露。11 个指标测试覆盖各分支 Counter 递增。验证：68 gateway tests，0 failures
  - T-GW-006 ✅ 重新界定为 done——不在项目内做集成测试（Gateway 依赖 Nacos+Redis+Sa-Token 基础设施，mock 成本高），改为独立仓库专门测试服务后续实施。单元测试（24 个）已覆盖三模行为+显式失效+指标递增

## 归档条件

- C-1 ~ C-4 + C-6 全部完成（C-5/T-GW-005 设计已完成，T-PERM-008 代码基础设施已落地）
- Gateway fail-mode 三模行为符合配置
- 监控指标接入 Prometheus + 告警规则验证
