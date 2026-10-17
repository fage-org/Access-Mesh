---
doc_type: plan
title: 权限生命周期边界收口
status: archived
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/service-authentication.md
  - docs/design/org-user-permission-contract.md
tasks:
  - T-FE-073
  - T-ACCESS-110
  - T-ACCESS-111
  - T-ACCESS-112
  - T-ACCESS-113
acceptance: 任务全部验收、设计回写完成，必要回归与本地双轨评审通过
last_updated: 2026-10-17
---

# 权限生命周期边界收口

## 目标

完成已确认的角色关系撤回、内置服务保护、服务凭证回收、导出领取边界和 OAuth2 密钥限制。

## 非目标

不重启其他暂缓事项，不部署或推送。

## 准入条件

行为选择已确认；新增不确定事项须先交用户决策。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-FE-073](tasks/T-FE-073.md) | 功能角色多关系精确撤回 | ✅ | — |
| [T-ACCESS-110](tasks/T-ACCESS-110.md) | 内置管理服务登记保护 | ✅ | — |
| [T-ACCESS-111](tasks/T-ACCESS-111.md) | 服务删除永久回收凭证 | ✅ | — |
| [T-ACCESS-112](tasks/T-ACCESS-112.md) | 已完成导出产物领取边界 | ✅ | — |
| [T-ACCESS-113](tasks/T-ACCESS-113.md) | OAuth2密钥写入字节上限 | ✅ | — |

## 归档条件

所有任务完成、设计回写和验收证据可追溯。

## 当前进度

已完成并归档；终态与链接见[任务看板](../../tasks/README.md)。

