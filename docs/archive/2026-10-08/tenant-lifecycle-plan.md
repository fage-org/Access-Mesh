---
doc_type: plan
title: Q-063 租户开通与独立平台运营
status: archived
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md
tasks:
  - T-ACCESS-091
  - T-ACCESS-092
  - T-ACCESS-093
  - T-ACCESS-094
  - T-ACCESS-095
  - T-GW-013
  - T-FE-067
  - T-ACCESS-096
acceptance: "平台与租户身份隔离、运营开通、停用恢复及真实双租户产品链验收通过，Q-063 收敛"
last_updated: 2026-10-08
---

# Q-063 租户开通与独立平台运营

## 目标

交付[租户生命周期设计](../../design/tenant-lifecycle.md)定义的平台运营与租户开通闭环，收敛 [Q-063](../../pending-problems.md#q-063)。

## 非目标

范围以设计与任务卡为准，不扩展为客户代管理、租户删除、自助注册、计费套餐或在线迁移项目。

## 准入条件

各实现项依其对应设计章节已确认的规则推进；未决取舍交用户决定，不以实施计划替代设计定案。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-091](tasks/T-ACCESS-091.md) | 租户开通与独立平台运营设计定稿 | ✅ | — |
| [T-ACCESS-092](tasks/T-ACCESS-092.md) | 租户 Redis 即时门禁共享协议 | ✅ | — |
| [T-ACCESS-093](tasks/T-ACCESS-093.md) | 独立平台身份、账号与审计 | ✅ | — |
| [T-ACCESS-094](tasks/T-ACCESS-094.md) | 租户注册与统一模板开通 | ✅ | T-ACCESS-093 |
| [T-ACCESS-095](tasks/T-ACCESS-095.md) | 租户生命周期与认证任务入口接线 | ✅ | T-ACCESS-092, T-ACCESS-094 |
| [T-GW-013](tasks/T-GW-013.md) | 平台入口与租户即时门禁网关接线 | ✅ | T-ACCESS-092, T-ACCESS-093, T-ACCESS-095 |
| [T-FE-067](tasks/T-FE-067.md) | 独立平台运营界面与租户编码登录 | ✅ | T-ACCESS-093, T-ACCESS-094, T-ACCESS-095, T-GW-013 |
| [T-ACCESS-096](tasks/T-ACCESS-096.md) | 租户运营产品验收与文档收口 | ✅ | T-ACCESS-091, T-ACCESS-092, T-ACCESS-093, T-ACCESS-094, T-ACCESS-095, T-GW-013, T-FE-067 |

## 归档条件

任务全部达到终态，设计和契约完成回写，完整回归及产品链验收通过；按生命周期技能统一归档。

## 当前进度

全部任务已完成。验收与两轨结论见[验证记录](tasks/evidence/tenant-lifecycle/verification.md)，实时入口保留在任务看板。
