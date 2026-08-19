---
doc_type: task
id: T-ACCESS-014
title: admin/application 域 AppService 操作日志强制覆盖
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/design/project-rules.md
depends_on:
  - T-ACCESS-007
blocks: []
acceptance:
  - "admin/application 域全部 *ServiceImpl / *AppServiceImpl 的 public @Transactional 非 readOnly 写方法必须标注 @OperationLog，或登记明确审计豁免"
  - "契约校验与 permission 域一致：module 三值化、action 大写事件码、targetType ∈ 物理表名白名单 ∪ 登记例外、summary/targetId 合法 SpEL"
  - "AppServiceOperationLogCoverageTest 将 admin/application 域纳入强制断言（扫描条件与类名实际一致，已标注方法不得因缺少 @Transactional 被跳过）"
  - "既有豁免（如 markNoticeAsRead）保持登记，不因强制覆盖回潮补标"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-16
---

# T-ACCESS-014 admin/application 域 AppService 操作日志强制覆盖

## 背景

T-ACCESS-007 将入口级操作日志收敛为 `@OperationLog`。当前 `AppServiceOperationLogCoverageTest` 只对 `permission.service.impl` 的 `*AppServiceImpl` 强制全覆盖；admin 实现类名为 `*ServiceImpl`，不在该扫描条件内。

## 范围

- 将强制覆盖扩展到 `admin.service.impl`、`application.impl`、`application.query.impl`。
- 扫描条件与真实类名一致；已标注 `@OperationLog` 的方法做与 permission 域相同的契约校验。
- 缺失标注的写方法补注解；高频低价值自操作保持豁免登记（先例：`NoticeServiceImpl.markNoticeAsRead`）。

## 当前口径

- 契约：module ∈ `ADMIN` / `PERMISSION` / `ACCESS`；action 大写事件码；targetType ∈ access-service.sql 物理表名 ∪ 已登记逻辑对象码例外；summary / 非空 targetId 为合法 SpEL。
- 豁免必须写在方法 Javadoc 与本任务验收中，禁止静默缺标。

## 验收对照

| 验收 | 结论 |
|---|---|
| admin/application 写方法强制标注或登记豁免 | 未实施 |
| 契约校验与 permission 域一致 | 未实施 |
| 覆盖测试扫描条件与类名一致 | 未实施 |
| markNoticeAsRead 等既有豁免不回潮 | 未实施 |

## 非目标 / 遗留

- 不实现 T-ACCESS-007 已交付的脱敏、独立短事务与 permission 域强制覆盖。
- 不修改 T-ACCESS-013（OAuth2 资源服务器与 scope 模型）。
