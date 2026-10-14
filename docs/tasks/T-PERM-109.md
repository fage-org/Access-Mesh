---
doc_type: task
id: T-PERM-109
title: 条件 CIDR 参数校验收口（负前缀/超界退化为放行）
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md
depends_on: []
blocks: []
acceptance:
  - "共享判定器 ipMatchesCidr 拒绝负前缀与超地址族位数前缀（解析校验：0 ≤ prefix ≤ 总位数、字段数量与地址字面值），非法参数令整个 IP 条件不满足，黑名单同样不放行"
  - "条件写入口（ConditionAppServiceImpl 与 INLINE 路径共用的校验）拒绝非法 CIDR 参数，前端 ReConditionEditor 标签录入同步基础校验"
  - "回归锁：白名单 `/-1`/`/-100` 不再通过、黑名单 IPv4 `/33` 不再退化为放行（旧实现下失败）"
  - "数值地址解析去除 InetAddress.getByName 的主机名解析副作用"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2（安全面，建议最高优先）。外部持续审查发现（探针实证 + 2026-10-14 主代理核实）：`perm-sdk/perm-common/.../util/ConditionEvalUtils.java` 的 `ipMatchesCidr` 只检查 `prefixLength > totalBits`；负前缀经 `Integer.parseInt` 解析后，逐位循环 `for (i=0; i<prefixLength; ...)` 不执行、直落 `return true`（匹配一切）——白名单写错成 `192.168.0.0/-1` 时条件恒满足，扩大该授权分支适用范围。黑名单方向：`evalIpList` 无匹配返回 `!whitelist`，IPv4 `/33` 等超界值返回「未匹配」被反转为条件通过。该判定器位于 perm-common，后端与 Gateway 条件路径共同消费；条件写入口（`PermissionConditionDomainServiceImpl` 共用校验）只验证 JSON 语法与 gatewayEvaluable 类型白名单，不校验 CIDR 参数。

## 范围

共享判定器解析校验；条件写入口校验复用；前端录入基础校验；双端回归锁。

## 当前口径

共享小解析校验单源（判定器与写入口、表单共用），非法参数 = 条件不满足（fail-closed），黑名单不因「反转」放行非法配置。保留合法 /0 与单地址用途；不新增条件 DSL、JSON Schema 框架或 IP 管控平台；不重启限流/trusted-proxies 议题。

## 验收对照

见 frontmatter `acceptance`；三个回归锁用例须旧实现下失败。

## 非目标 / 遗留

四类条件模型、双轨共享校验、Gateway 可评估模型均维持现行定案。
