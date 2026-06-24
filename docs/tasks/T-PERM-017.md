---
doc_type: task
id: T-PERM-017
title: 条件权限 Gateway 侧重评（部分下发 + 未下发回退实时鉴权）
status: review
plan: docs/plans/perm-cache-invalidation-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center-v3.5-design.md#§7.2-缓存一致性总线
  - docs/design/services/gateway.md
depends_on: [T-PERM-002, T-PERM-018]
blocks: []
acceptance:
  - "permission_condition 新增 gateway_evaluable 字段(或下发标志)，标记条件规则是否可下发 Gateway 评估"
  - "可下发规则：ConditionEvalUtils 搬入 perm-common，ApiPermissionEntry 内联 conditionRules JSON，快照构建保留条件条目不过滤，Gateway InterfaceSnapshotMatcher 按请求 context 重评"
  - "不可下发规则（含未来未知类型）：Gateway 对 hasCondition 且 gateway_evaluable=false 的请求回退 check-interface 实时鉴权(带 clientIp)"
  - "时钟一致性策略：由 NTP 同步保证（中小企业部署 Gateway 与 permission-center 同机房，业务粒度小时级容忍亚秒漂移），不通过 context 传递 timestamp；文档说明清晰"
  - "mvn test 通过 + gateway.md/v3.5 §7.2 回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-06-24
---

## 实施决策（2026-06-24）

- **初期可下发范围**：`IP_WHITELIST` / `IP_BLACKLIST` / `DATE_RANGE` / `TIME_RANGE`（全部 4 个已知条件类型）。
  - 时钟语义：本项目面向中小型企业部署，Gateway 与 permission-center 通常同机房 / 同云区域，跨进程时钟一致性由 NTP 同步保证（亚秒级漂移）。条件规则业务粒度（DATE_RANGE 按天、TIME_RANGE 通常按小时）远大于 NTP 漂移，故 4 类条件均可直接用本进程时钟评估，不通过 context 传递 `timestamp`。
  - 未来扩展类型（如 `ORG_SCOPE`）默认 fail-close 不下发，需显式审批加入白名单。
- **ConditionEvalUtils 迁移策略**：硬切 — 从 `permission-center.util` 删除，统一为 `perm-common.util.ConditionEvalUtils`。
- **未下发条件 fallback 方式**：Gateway 对 `gateway_evaluable=false` 的命中走同步 HTTP 调 `/perm/check-interface`（带 clientIp）；不再传 timestamp（context 仅承载 clientIp）。
- **白名单校验落点**（C2.5）：`ConditionAppServiceImpl.create/update` 写入校验 + `SnapshotAssembler` 内联前防御过滤共用 `ConditionEvalUtils.isGatewayPushable`。

## 实施 commit 拆分

| # | 范围 | 状态 |
|---|---|---|
| C1 | schema + entity + mapper 新增 `gateway_evaluable` 字段，design/schema 回写 | ✅ `4916b4210` |
| C2 | `ConditionEvalUtils` 搬至 `perm-common`，permission-center 删除原位 | ✅ `51a104a14` |
| C2.5 | `gatewayEvaluable=true` 规则类型白名单校验（create/update 写入门禁 + 单元测试） | ✅ `157d3982e` |
| C2.6 | 文档口径统一：4 类全可下发；时钟一致性由 NTP 保证；删除未消费的 `context.timestamp` 死代码 | ✅ `b53288dbb` |
| C3 | `ApiPermissionEntry` 内联 `conditionRules`；`PermQuery.markConditionsOnly` 选项；`SnapshotAssembler` 仅对 gateway_evaluable=true 内联 rules JSON + 防御性 `isGatewayPushable` 过滤 | ✅ `206f99baf` |
| C4 | `SnapshotAssembler` 不折叠多条同 API 授权（去重 key 加 conditionId，修 P1-②）；`InterfaceSnapshotMatcher` 改 OR 匹配 + 本地重评含 conditionRules 条目；`PermissionFilter` 未下发条件命中 HTTP 同步调 `/perm/check-interface` fallback（仅 clientIp）；补 INTERFACE_SNAPSHOT 失效注释承诺。**注：C3 与 C4 绑定发布，C3 单独部署会让条件 API 在 Gateway 直接放行** | ✅ `b976d7d6e` |
| C5 | 设计回写 `gateway.md`（本地匹配规则改三态 + 条件混合评估段 + fallback 失败模式）；`v3.5 §7.2` 追加 T-PERM-017 进度段；任务收尾 status=review | ✅ |

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
- 可下发类型：`IP_WHITELIST` / `IP_BLACKLIST` / `DATE_RANGE` / `TIME_RANGE`（4 类全部）。Gateway 用本进程时钟 + 请求 `clientIp` 评估；跨进程时钟一致性由 NTP 同步保证（详见决策 §时钟语义）
- `ConditionEvalUtils` 搬入 perm-common（已是纯静态函数，无 DB 依赖）
- `ApiPermissionEntry` 内联 `conditionRules` JSON（仅 gateway_evaluable=true 的条目）
- 快照构建：可下发条件条目**保留进快照不过滤**（需给快照路径一个"只标记不过滤"模式，触及 PermQueryEngine.evaluateIfNeeded 语义）
- `InterfaceSnapshotMatcher` 扩展：对含 conditionRules 的条目按请求 context（clientIp）+ 本进程时钟重评

### 不可下发条件（gateway_evaluable=false）

- 未来扩展的组织/数据属性条件（Gateway 无法判断，如 ORG_SCOPE / DATA_OWNER）
- 这类条目：Gateway 命中后**回退 check-interface 实时鉴权**（带 clientIp），由 permission-center 按真实上下文评估
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
- 跨进程时钟一致性：依赖 NTP 同步运维保证；中小企业部署场景下 Gateway 与 permission-center 同机房，亚秒漂移远小于业务规则粒度（小时级），无需引入 context 时钟传递
- 快照构建"不过滤"改造触及引擎核心，需谨慎
