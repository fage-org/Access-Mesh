---
doc_type: task
id: T-PERM-036
title: 动态数据权限端到端验证（scopeMode → SQL 映射链路）
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.7
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
depends_on:
  - T-FE-013
  - T-PERM-033
blocks: []
acceptance:
  - "执行门禁：design-review §11 Q7/B 决策动态数据权限端到端测试延后到 example-service，且 example-service 暂不实现；进入 in-progress 前必须 PM 重申解除暂缓"
  - "梳理数据权限链路：用户属性 → 条件表达式 → 数据过滤"
  - "构造测试场景：普通员工（本人）/ 团队管理者（本团队）/ 部门总监（本部门）"
  - "端到端验证：ConditionEvalUtils → PermQueryEngine（范围权限事实）→ 业务服务按 scopeMode 映射 SQL：INSTANCE 加 items[].resourceCode 范围过滤、ALL 不加、DENIED/EMPTY 不发 SQL 或返回空"
  - "记录发现的缺口或问题"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-29
---

# T-PERM-036 动态数据权限端到端验证

> 状态：proposed
> ⚠️ 执行门禁：design-review §11 Q7/B — 动态数据权限端到端测试延后到 example-service，且 example-service 暂不实现。进入 in-progress 前必须 PM 重申解除暂缓。

## decision_refs（暂缓依据，非实现依据）

> 归档文档仅作决策溯源，**不作为实现依据**，不进入 design_refs 回写范围。

- `docs/archive/2026-06-17/design-review.md` §11 Q7/B — 动态数据权限端到端测试延后到 example-service，且 example-service 暂不实现
- `docs/archive/2026-08-27/improvement-plan.md` §3.1 痛点 #4 — 已标暂缓（2026-06-20 审计 S-026；计划本体已归档，暂缓决策仍有效）

## 背景

动态数据权限：按身份 ID 的数据范围筛选（普通员工看本人、团队管理者看本团队、部门总监看本部门）。设计已存在（条件评估、数据范围），但端到端链路未验证。对应已归档 improvement-plan（archive/2026-08-27/）§3.1 痛点 #4。

## 验证步骤（已归档 improvement-plan §2.3）

1. 梳理数据权限链路：用户属性 → 条件表达式 → 数据过滤
2. 构造测试场景：普通员工 / 团队管理者 / 部门总监
3. 端到端测试：ConditionEvalUtils → PermQueryEngine → 业务服务按 scopeMode 映射 SQL
4. 记录缺口

## 关联

scopeMode 四态契约已由 T-PERM-009 定义、T-PERM-015 前端落地。本任务验证其后端端到端链路。

## 验收标准

见 acceptance。
