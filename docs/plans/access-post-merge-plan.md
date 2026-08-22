---
doc_type: plan
title: access-service 归并后续强化（OAuth2 资源服务器）
status: proposed
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/services/admin-service-api-contract.md
tasks:
  - T-ACCESS-013
  - T-ACCESS-014
acceptance: "T-ACCESS-013/014 全部 done 或经确认 cancelled；归并主链（T-ACCESS-001~012）验收结论保持有效；稳定结论只存在于 docs/design。"
last_updated: 2026-08-22
---

# access-service 归并后续强化（OAuth2 资源服务器）

> 状态：proposed
> 来源：归并主计划 [access-service-merge](../archive/2026-08-22/access-service-merge-plan.md)（T-ACCESS-001~012，2026-08-22 全部完成归档）收口后，其后续跟进任务迁入本计划。

## 目标

- 承接归并主链完成后挂起的后续任务：T-ACCESS-013（OAuth2 资源服务器与 scope 授权模型）、T-ACCESS-014（操作日志强制覆盖，已完成）。

## 非目标

- 不重开归并主链已完成范围。
- 不为旧服务名、旧部署单元提供兼容。

## 准入条件

- **CI 验证前置（原 merge-plan 归档前置责任转移至此，2026-08-22 用户决策）**：CI 环境跑通 `mvn test` 并确认 T-ACCESS-011 登记的 40 个 Docker 门控 Testcontainers 测试全绿（空库 PG DDL 12、任务租约并发 10、双实例容器 3、故障注入 IT 5、PG 集成 2 等）；跑绿前验收 2/6/9 的容器部分保持条件性关闭。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-013](../tasks/T-ACCESS-013.md) | OAuth2 资源服务器与 scope 授权模型（委托令牌访问业务 API 显式开放） | ⚙️ | T-ACCESS-012 ✅ |
| [T-ACCESS-014](../tasks/T-ACCESS-014.md) | admin/application 域 AppService 操作日志强制覆盖 | ✅ | T-ACCESS-007 ✅ |

## 归档条件

- T-ACCESS-013/014 全部 `done` 或经确认 `cancelled`。
- 准入条件中的 CI 验证前置已关闭（40 项 Docker 门控测试全绿）。
- 稳定结论已沉淀到 `docs/design/`，本计划不形成第二套契约。
