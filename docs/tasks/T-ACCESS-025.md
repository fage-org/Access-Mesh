---
doc_type: task
id: T-ACCESS-025
title: 操作日志收敛（默认不序列化参数，裁剪覆盖要求）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/project-rules.md
  - docs/design/services/admin-service-api-contract.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "OperationLogAspect 默认不再序列化方法参数/请求体（maskRequestBody 无条件 writeValueAsString 移除默认路径），日志只保留租户、操作者、动作、目标、结果、耗时、requestId"
  - "高风险操作摘要复用现有能力：@OperationLog.summary 属性与 OperationLogRuntimeContext.setSummary()（已有生产使用），只记录对象 ID、动作、结果；密码、OAuth2 客户端密钥即使脱敏也不进入摘要；不新增摘要 Provider、策略接口、白名单注册表或新注解属性"
  - "AppServiceOperationLogCoverageTest「所有事务写方法必须标注 @OperationLog」的全覆盖强制要求删除或收敛为登记豁免+抽样校验；存量 33 文件/104 处注解不强制新增，仅保持已标注语义"
  - "SensitiveDataUtils 冻结：保留现状，不再扩展脱敏字典与递归规则（代码注释标注冻结口径）"
  - "permission_change_log 强事务日志不动（独立链路保持）"
  - "单测：默认路径日志无参数内容；高风险操作经现有 summary/runtime context 记录对象 ID、动作、结果（不含参数与密钥）；覆盖率测试不再阻断未标注方法"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-025 操作日志收敛

## 背景

OperationLogAspect 默认序列化全部方法参数（递归脱敏后仍可能落大对象/嵌套 JSON），SensitiveDataUtils 递归处理嵌套 JSON；生产代码 33 文件/104 处 @OperationLog；AppServiceOperationLogCoverageTest 反射强制几乎所有事务写方法标注或登记豁免——审计负担与维护税偏高，与日志应记录的「谁在何时对什么做了什么、结果如何」目标不符。

## 范围

- Aspect 默认值收敛 + 复用现有 summary/runtime context 承载高风险操作摘要。
- 覆盖率测试要求裁剪。
- SensitiveDataUtils 冻结标注。

## 当前口径

- 操作日志记动作与目标，不记载荷；载荷级审计由 permission_change_log 等专用强事务日志承载（保持不变）。
- 收敛方向是减法：删默认序列化、删全覆盖要求；摘要复用现有 summary 机制，不再扩展注解模型。

## 非目标 / 遗留

- 不重写 @OperationLog 注解模型：不新增注解属性、白名单注册表或扩展点（保持现状）。
- 不动 permission_change_log / 操作日志存储结构。
