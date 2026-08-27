---
doc_type: task
id: T-ACCESS-025
title: 操作日志收敛（默认不序列化参数，裁剪覆盖要求）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/project-rules.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/schema/access-service.sql
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "OperationLogAspect 默认不再序列化方法参数/请求体（maskRequestBody 无条件 writeValueAsString 移除默认路径），日志只保留租户、操作者、动作、目标、结果、耗时、requestId"
  - "高风险操作摘要复用现有能力：@OperationLog.summary 属性与 OperationLogRuntimeContext.setSummary()（已有生产使用），只记录对象 ID、动作、结果；密码、OAuth2 客户端密钥即使脱敏也不进入摘要；不新增摘要 Provider、策略接口、白名单注册表或新注解属性"
  - "AppServiceOperationLogCoverageTest「所有事务写方法必须标注 @OperationLog」的全覆盖强制要求删除或收敛为登记豁免+抽样校验；存量 32 文件/101 处注解不强制新增，仅保持已标注语义"
  - "SensitiveDataUtils 冻结：保留现状，不再扩展脱敏字典与递归规则（代码注释标注冻结口径）"
  - "permission_change_log 强事务日志不动（独立链路保持）"
  - "单测：默认路径日志无参数内容；高风险操作经现有 summary/runtime context 记录对象 ID、动作、结果（不含参数与密钥）；覆盖率测试不再阻断未标注方法"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-27
---

# T-ACCESS-025 操作日志收敛

## 背景

OperationLogAspect 默认序列化全部方法参数（递归脱敏后仍可能落大对象/嵌套 JSON），SensitiveDataUtils 递归处理嵌套 JSON；生产代码 32 文件/101 处 @OperationLog；AppServiceOperationLogCoverageTest 反射强制几乎所有事务写方法标注或登记豁免——审计负担与维护税偏高，与日志应记录的「谁在何时对什么做了什么、结果如何」目标不符。

## 范围

- Aspect 默认值收敛 + 复用现有 summary/runtime context 承载高风险操作摘要。
- 覆盖率测试要求裁剪。
- SensitiveDataUtils 冻结标注。

## 设计口径

- 操作日志记动作与目标，不记载荷；载荷级审计由 permission_change_log 等专用强事务日志承载（保持不变）。
- 收敛方向是减法：删默认序列化、删全覆盖要求；摘要复用现有 summary 机制，不再扩展注解模型。
- **敏感字段登记链路彻底删除**：`OperationLogRuntimeContext.markSensitiveField()`/`Snapshot.sensitiveFields` 随序列化删除失去唯一消费者，连同生产调用处（`OAuth2ServiceImpl.token()` 的 `markSensitiveField("code")`、`ConfigServiceImpl.updateConfig()` 的 `isSecretConfigKey` 判定块）一并移除——死链会误导后续维护者以为请求体仍在脱敏。
- **全覆盖断言直接删除**：`AppServiceOperationLogCoverageTest` 删除两个域内强制断言与豁免登记机制（`EXEMPT_WRITE_METHODS` 清单 + 失配校验），保留三域已标注方法契约校验（module 三值化/action 大写事件码/targetType 白名单/summary 与 targetId 合法 SpEL）；写方法是否标注由 project-rules §写入口通用清单规范指导。
- **`OperationLogEntry` 删除 `requestBody` 字段**：表列 `operation_log.request_body` 保留恒 NULL（存量数据兼容），record 不再承载该字段；`AuditDomainServiceImpl` 不再写入，`PermissionConflictDomainServiceImpl` 冲突通知构造参数同步。
- 存量 summary 生产使用核验：全部只含业务键/ID/计数（clientId、userId、configKey、serviceCode 等），无密码/密钥值，验收「摘要只记对象 ID、动作、结果」现状已满足，未改任何生产注解。

## 非目标 / 遗留

- 不重写 @OperationLog 注解模型：不新增注解属性、白名单注册表或扩展点（保持现状）。
- 不动 permission_change_log / 操作日志存储结构（request_body 列保留，注释更新停用口径）。

## 验收落地（2026-08-27）

- Aspect：`maskRequestBody`/`toSafeSerializableValue`/`ObjectMapper` 依赖删除，`OperationLogEntry` 构造不再传请求体；类 Javadoc 更新为「不序列化方法参数」口径。
- RuntimeContext：`markSensitiveField`/`sensitiveFields` 删除；`setSummary`/`setTargetType`/`setTargetId`/`setTenantId`/`markSkip` 保持。
- `SensitiveDataUtils`：类 Javadoc 标注冻结口径（不再有审计链路生产调用方，不扩展脱敏字典与递归规则）；`SensitiveDataUtilsTest` 20 用例随冻结保留。
- CoverageTest：保留 `shouldValidateContractOfAnnotatedMethodsInAllDomains`（三域扫描下限 ≥40 防扫描失效），删除两个全覆盖断言与豁免登记；类 Javadoc 重写。
- `NoticeServiceImpl.markNoticeAsRead` Javadoc 从「豁免登记」改为「审计口径」结论式表述。
- 测试：`OperationLogAspectTest` 新增默认路径无参数内容断言（`entry.toString()` 不含参数明文，旧实现下 requestBody 含参数值会失败）与高风险摘要只含对象标识断言（凭证参数不进任何字段）；删除 4 个请求体序列化用例；`ConfigServiceImplTest`（专测已删除的 markSensitiveField 链路）整体删除；`AuditDomainServiceImplTest` 构造同步并断言 `getRequestBody()` 为 null。
- 文档：access-service-architecture §8.2（唯一入口/参数不入库/T-ACCESS-014 落地三段）、project-rules §写入口通用清单第 2 条、schema access-service.sql 四处 request_body 注释同步；admin-service-api-contract 仅提及注解名不涉及请求体，无需改动。
- 回归：受影响测试 42/42 全绿（SensitiveDataUtilsTest 20 + OperationLogAspectTest 17 + AuditDomainServiceImplTest 4 + CoverageTest 1）；全量回归见提交信息。
