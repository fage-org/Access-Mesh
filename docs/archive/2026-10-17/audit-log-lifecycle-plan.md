---
doc_type: plan
title: 审计日志导出与保留策略
status: archived
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md#contract-section-16-7
  - docs/design/access-service-architecture.md
tasks:
  - T-ACCESS-107
  - T-ACCESS-108
  - T-FE-070
acceptance: 全部任务验收与设计回写完成；完整回归包含 E2E/heavy，前端工程门禁通过
last_updated: 2026-10-17
---

## 目标

承接 [Q-061](../../pending-problems.md#q-061)，按[契约](../../design/access-service-api-contract.md#contract-section-16-7)实施审计导出与保留策略。

## 非目标

不建立归档存储或异步导出中心，不扩展平台审计日志的保留/读取权限，不提供存量库在线迁移。

## 准入条件

产品范围、期限配置、启用方式、权限、CSV 容量、配置生效、空字段与停用租户边界均已确认。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-107](tasks/T-ACCESS-107.md) | 有界审计 CSV 导出与登录日志字段清理 | ✅ | — |
| [T-ACCESS-108](tasks/T-ACCESS-108.md) | 部署控制的审计期限清理 | ✅ | — |
| [T-FE-070](tasks/T-FE-070.md) | 审计日志导出交互 | ✅ | T-ACCESS-107 |

## 归档条件

全部任务完成，统一执行后端收口回归及前端门禁，核对 Q-061 验收覆盖与当前文档链接后归档。

## 当前进度

全部任务与设计回写完成；[统一验收](tasks/evidence/audit-log-lifecycle/verification.md)覆盖完整后端范围、跨服务/大规模用例与前端门禁。
