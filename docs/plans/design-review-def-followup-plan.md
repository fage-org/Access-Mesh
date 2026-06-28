---
doc_type: plan
title: 设计评审 D/E/F 后续任务拆分
status: proposed
domain: permission-center
design_refs:
  - docs/archive/2026-06-17/design-review.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/permission-center/api-contract.md
  - docs/design/schema/permission-center.sql
  - docs/design/architecture.md
  - docs/design/cross-service/admin-permission-sync.md
  - docs/design/services/admin-service.md
tasks:
  - T-PERM-019
  - T-PERM-020
  - T-PERM-021
acceptance: "D/E/F 三个暂缓工作单拆成 proposed 任务；冲突项已标注；每个任务进入 in-progress 前必须二次确认 scope 与设计回写目标。"
last_updated: 2026-06-28
---

# 设计评审 D/E/F 后续任务拆分

> 状态：proposed
> 来源：`docs/archive/2026-06-17/design-review.md` §11 的 D/E/F 暂缓项
> 约束：本计划只建立任务跟踪入口，不授权实现；任何任务进入 `in-progress` 前必须重新确认。

## 目标

将设计评审中暂缓的 D/E/F 三组内容转成可追踪任务，并把已知设计冲突直接标在任务内，避免后续实现时把旧审计建议误当作当前设计契约。

## 非目标

- 不在本计划内修改 `docs/design/` 权威契约。
- 不在本计划内改代码。
- 不直接执行带冲突的清理或 schema 变更。

## 冲突标记

| 标记 | 含义 | 执行要求 |
|---|---|---|
| `NO_HARD_CONFLICT` | 与当前设计没有实质冲突，但仍需按任务验收核对 | 确认后可进入设计回写/实现 |
| `DESIGN_DRIFT` | 当前设计文档之间已经不一致，需要先收敛设计 | 先改权威设计，再实现 |
| `CONFLICT_REQUIRES_DECISION` | 原建议与当前设计或实现边界冲突 | 必须先做取舍确认，禁止直接实现 |

## 任务拆分

| 任务 | 工作单 | 范围 | 冲突状态 | 执行门禁 |
|---|---|---|---|---|
| [T-PERM-019](../tasks/T-PERM-019.md) | D 防呆机制 | `type_value` 自动分配、`BusinessKeys`、`@AppliesTo`、SyncHandler 版本声明 | `DESIGN_DRIFT`：`typeValue` 入参旧描述与软删不复用保证方式需先收敛 | 执行前确认 D1 语义和 BusinessKeys 默认方案 |
| [T-PERM-020](../tasks/T-PERM-020.md) | E 清理预设 | `domain_config` 旧配置、PermQuery 工厂、RocketMQ 脚注、`auto-grant` TODO | `CONFLICT_REQUIRES_DECISION`：`forValidate` / `forResourceCheck` 仍被当前设计使用 | 执行前确认 E2 替代设计 |
| [T-PERM-021](../tasks/T-PERM-021.md) | F 文档准确性 + 代码简化 | 指标自动化、DTO 单源、ownership 单源、日志链路、full-sync runbook | `CONFLICT_REQUIRES_DECISION`：ownership 字段删除与 request_id NOT NULL 有当前设计约束；`requestId`/`traceId` 语义存在漂移 | 执行前确认 F1.c/F1.d 取舍 |

## 当前进度

- 2026-06-28：建立计划与任务；所有任务保持 `proposed`。
- 冲突项只登记，不执行。

## 归档条件

- T-PERM-019~021 全部完成或取消。
- 稳定结论已沉淀到 `docs/design/`。
- `docs/tasks/README.md` 和 `docs/plans/README.md` 已同步更新。
