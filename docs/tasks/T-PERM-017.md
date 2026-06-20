---
doc_type: task
id: T-PERM-017
title: 条件权限 Gateway 侧重评（部分下发 + 未下发回退实时鉴权）
status: proposed
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/services/gateway.md
depends_on: [T-PERM-002]
blocks: []
acceptance:
  - "permission_condition 新增 gateway_evaluable 字段(或下发标志)，标记条件规则是否可下发 Gateway 评估"
  - "可下发规则：ConditionEvalUtils 搬入 perm-common，ApiPermissionEntry 内联 conditionRules JSON，快照构建保留条件条目不过滤，Gateway InterfaceSnapshotMatcher 按请求 context 重评"
  - "不可下发规则：Gateway 对 hasCondition 且 gateway_evaluable=false 的请求回退 check-interface 实时鉴权(带 clientIp/timestamp)"
  - "日期/时间条件时钟语义明确(Gateway 时钟 vs permission-center 时钟)，timestamp context 字段激活或文档说明"
  - "mvn test 通过 + gateway.md/v3.5 §7.2 回写"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-20
---

# T-PERM-017 条件权限 Gateway 侧重评（混合方案）

> 来源：T-PERM-001 评审 P1（条件权限在快照模式下可能被错误放行）
> 用户决策（2026-06-20）：部分条件规则下发到 Gateway（新增字段控制），未下发的走方案1（实时鉴权回退）

## 背景

T-PERM-001 快照模式下，`interfaceSnapshot` 用空 context 评估条件，导致：
1. IP 白/黑名单条件条目（`clientIp==null` 必 fail）在快照构建时被整体丢弃，不进快照
2. 日期/时间条件条目被 permission-center 服务端时钟评过一次，缓存复用

`InterfaceSnapshotMatcher` 对 `hasCondition=true` 条目直接放行，不二次评估 → 条件权限会误放行。

## 方案（混合：部分下发 + 未下发回退）

### 可下发条件（gateway_evaluable=true）

- `permission_condition` 新增字段 `gateway_evaluable`（BOOLEAN，默认 false），标记规则是否可安全下发 Gateway 评估
- 可下发类型初期限定：IP_WHITELIST/IP_BLACKLIST（输入 clientIp Gateway 天然持有）
- `ConditionEvalUtils` 搬入 perm-common（已是纯静态函数，无 DB 依赖）
- `ApiPermissionEntry` 内联 `conditionRules` JSON（仅 gateway_evaluable=true 的条目）
- 快照构建：可下发条件条目**保留进快照不过滤**（需给快照路径一个"只标记不过滤"模式，触及 PermQueryEngine.evaluateIfNeeded 语义）
- `InterfaceSnapshotMatcher` 扩展：对含 conditionRules 的条目按请求 context（clientIp）重评

### 不可下发条件（gateway_evaluable=false）

- 日期/时间条件（时钟跨进程不一致）或未来扩展的组织/数据属性条件（Gateway 无法判断）
- 这类条目：Gateway 命中后**回退 check-interface 实时鉴权**（带 clientIp/timestamp），由 permission-center 按真实上下文评估
- 即方案1，针对不可下发条件局部应用

## 前置依赖

- **T-PERM-002（令牌/缓存失效）必须先做**：否则 Gateway 重评用的快照本身可能陈旧，重评过期规则仍错
- 本任务也依赖 P1 陈旧修复（写路径清接口快照）已落地

## 关键文件（调查结论）

- 评估函数：`permission-center/.../util/ConditionEvalUtils.java`（纯静态，可移植）
- 规则存储：`perm-entity/.../PermissionCondition.java` + schema `permission-center.sql` permission_condition 表
- 快照构建预过滤点：`PermissionQueryAppServiceImpl.interfaceSnapshot` L614（空 context）+ `PermQueryEngine.evaluateIfNeeded` L500
- 快照 DTO：`perm-common/.../InterfaceSnapshotResp.java` ApiPermissionEntry（需增 conditionRules）
- Gateway 匹配器：`gateway/.../InterfaceSnapshotMatcher.java`（已预留扩展注释）

## 设计回写

- `gateway.md`：补条件权限混合评估策略段落
- `v3.5 §7.2`：条件权限数据面评估职责划分

## 风险

- DTO 内联规则 JSON 扩大敏感配置（IP CIDR 白名单）暴露面——靠 gateway_evaluable 标志最小化下发
- 日期/时间条件时钟语义需明确：建议不可下发（走回退），避免跨进程时钟不一致
- 快照构建"不过滤"改造触及引擎核心，需谨慎
