---
doc_type: plan
title: example-service 持久化权限演示
status: proposed
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md
  - docs/design/services/example-service.md
tasks:
  - T-ACCESS-136
  - T-ACCESS-137
  - T-ACCESS-139
  - T-ACCESS-140
  - T-ACCESS-138
  - T-PERM-036
  - T-ACCESS-141
  - T-ACCESS-143
  - T-ACCESS-142
  - T-FE-079
acceptance: "真实数据、动态行列权限、报表字段隔离、读写与内部定时任务通过可重复 API 场景验证，持久化/幂等及设计契约回写完成。"
last_updated: 2026-10-18
---

# example-service 持久化权限演示计划

## 目标

按[需求设计](../design/example-permission-demo.md)交付真实数据库支撑的权限样板；重点是动态行范围、报表上下文的字段权限和任务执行权限，不建设 BI 分析产品。本次只完成需求和计划修订。

## 非目标

不做数据分析或业务页面，不接 access-service 任务管理，不建设通用 SQL/规则/ETL/备份平台，不自动启动其他暂缓事项。

## 准入条件

- 用户已确认 CSV、本人/团队/部门、报表上下文字段权限、动态配置、无页面和 example 内部调度；导入范围、策略生效流程及任务条件上下文按需求正文已确认口径执行，剩余详细契约由 T-ACCESS-136 定稿。
- 设计 adopted、API 与 DDL 边界及测试基建复用路径明确后再实施。
- T-PERM-036 保留实施暂缓；后续明确启动本计划时统一核定其准入，不以别卡绕过。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-136](../tasks/T-ACCESS-136.md) | 权限演示需求与实施契约定稿 | ⚙️ | — |
| [T-ACCESS-137](../tasks/T-ACCESS-137.md) | 权限演示数据库与持久化基础 | ⚙️ | T-ACCESS-136 |
| [T-ACCESS-139](../tasks/T-ACCESS-139.md) | 演示资源登记与可靠发布 | ⚙️ | T-ACCESS-137 |
| [T-ACCESS-140](../tasks/T-ACCESS-140.md) | 动态策略配置与真实 SQL 权限过滤 | ⚙️ | T-ACCESS-139 |
| [T-ACCESS-138](../tasks/T-ACCESS-138.md) | 演示业务记录与 CSV 导入 | ⚙️ | T-ACCESS-140 |
| [T-PERM-036](../tasks/T-PERM-036.md) | 动态数据权限端到端验证 | ⚙️ | T-ACCESS-138 |
| [T-ACCESS-141](../tasks/T-ACCESS-141.md) | 有界授权快照导出与结果领取 | ⚙️ | T-ACCESS-140、T-PERM-036 |
| [T-ACCESS-143](../tasks/T-ACCESS-143.md) | example 内部自定义定时任务与恢复 | ⚙️ | T-ACCESS-141 |
| [T-ACCESS-142](../tasks/T-ACCESS-142.md) | 权限场景包与持久化验收收口 | ⚙️ | T-ACCESS-143、T-PERM-036 |
| [T-FE-079](../tasks/T-FE-079.md) | BI 工作区与销售分析页面 | ❌ | — |

## 推进顺序

契约定稿 → 持久化基础/报表字段元数据与资源接入 → 动态行列执行器 → CSV 业务记录 → T-PERM-036 跨服务验证 → 有界快照与内部定时任务 → 场景包和重启验收。SQL 定向测试用独立夹具，不反向依赖导入实现；导入则必须复用已交付的行列权限执行器。具体范围和验收只在任务卡维护。

## 归档条件

任务全部 done/cancelled，动态配置及读写/任务负向实证通过，必要回归完成，现役设计/API/DDL/使用指南回写，证据可追溯。取消的前端卡随本计划一起归档，不复用编号。

## 当前进度

需求和计划已按权限演示目标收敛；T-FE-079 cancelled，其余 proposed。新任务 T-ACCESS-143 承接内部调度；T-ACCESS-142 依赖已重连，无在办任务依赖已取消前端卡。尚未修改代码、DDL 或部署，T-PERM-036 实施门禁保留。

整体检查提出的范围与裁剪取舍已确认并回写，检查记录无剩余用户选择项；这不替代实施契约定稿或行为验收。
