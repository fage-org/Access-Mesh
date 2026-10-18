---
doc_type: plan
title: 最近变更核实修复与状态校准
status: archived
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
  - docs/design/frontend/login.md
  - docs/design/org-user-permission-contract.md
tasks:
  - T-ACCESS-115
  - T-API-014
  - T-FE-074
  - T-FE-075
  - T-FE-076
acceptance: 任务全部完成，修复经验证，状态与文档入口校准
last_updated: 2026-10-18
---

## 目标

完成 `060500dc5..1f97aee3d` 评审核实后的修复及已批准裁剪；校准 T-ACCESS-097/Q-069 与暂缓 T-PERM-036，并修正当前依赖版本的恢复命令。

## 非目标

不自动续跑外部评审，不启动已暂缓能力；新的行为取舍由用户决定。

## 准入条件

用户已授权核实修复，并确认调度合并、SDK 局部 JSON 对象及角色撤回读回方案。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-115](tasks/T-ACCESS-115.md) | 统一部署任务登记与调度对账 | ✅ | — |
| [T-API-014](tasks/T-API-014.md) | 注册 SDK 无宿主 JSON 对象装配 | ✅ | — |
| [T-FE-074](tasks/T-FE-074.md) | Blob 错误响应会话边界 | ✅ | — |
| [T-FE-075](tasks/T-FE-075.md) | mock 菜单会话标记接线 | ✅ | — |
| [T-FE-076](tasks/T-FE-076.md) | 角色撤回后的有效关系反馈 | ✅ | — |

## 归档条件

任务全部终态，设计回写、代码与文档本地评审、完整回归通过；相关入口与问题索引同步。

## 当前进度

全部任务完成并于 2026-10-18 归档。修复、已批准裁剪、状态校准及完整验证见[统一验收](tasks/evidence/recent-change-review/verification.md)。
