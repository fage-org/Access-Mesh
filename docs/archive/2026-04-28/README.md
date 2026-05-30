# 2026-04-28 文档归档

本目录保存 `plan/` 重整前的旧版长文档和讨论清单。

这些文件可能包含旧接口、旧字段或已废弃设计，例如 `includeDataScope`、`query-data-scopes`、旧文件路径等。实现时不要以本目录内容作为权威来源。

## 当前权威来源

| 主题 | 当前文档 |
|------|----------|
| 文档入口 | `plan/README.md` |
| 权限中心概念模型 | `plan/permission-center/overview.md` |
| 权限中心 API 契约 | `plan/permission-center/api-contract.md` |
| 权限中心流程链路 | `plan/permission-center/core-flows.md` |
| 权限中心实现设计 | `plan/permission-center/implementation.md` |
| 表结构 | `plan/schema/*.sql` |

## 归档文件

| 文件 | 原文件 | 说明 |
|------|--------|------|
| `permission-center-design.full.md` | `plan/DESIGN.md` | 权限中心旧完整设计，已由 overview/api/schema 拆分承接 |
| `permission-center-product-features.md` | `plan/PRODUCT_FEATURES.md` | 旧产品功能长文档，包含过期接口字段 |
| `gateway-design.full.md` | `plan/GATEWAY_DESIGN.md` | Gateway 旧完整设计 |
| `admin-service-design.full.md` | `plan/ADMIN_SERVICE_DESIGN.md` | admin-service 旧完整设计 |
| `example-service-design.full.md` | `plan/EXAMPLE_SERVICE_DESIGN.md` | example-service 旧完整设计 |
| `service-module-checklist.md` | `plan/SERVICE_MODULE_CHECKLIST.md` | 服务模块讨论清单 |
| `permission-center-module-checklist.md` | `plan/MODULE_DISCUSSION_CHECKLIST.md` | 权限中心模块讨论清单 |
| `permission-center-feign-path-mismatch.md` | `.claude/PRPs/problems/001-feign-path-mismatch.md` | 旧 Feign 路径问题记录，包含过期接口路径 |
