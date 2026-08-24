# 前端设计

本目录存放前端页面级权威设计（布局 / 字段 / 交互契约），作为前端任务（T-FE）的 `design_refs` 回写落点。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。设计只住 `docs/design/`，任务 done 前必须回写本目录对应页面文档。

## 与后端契约的关系

前端页面设计（本目录）描述 UI 层：布局、字段、交互、组件结构、权限接线。
后端接口契约（`docs/design/permission-center/api-contract.md` 等）描述数据层：请求/响应结构、路径、方法。

前端任务的 `design_refs` 同时指向两者：本目录页面文档（UI）+ 后端契约（数据）。

## 页面设计文档

> 随各 T-FE 任务推进产出，文件命名 `<page-slug>.md`。

| 页面 | 文档 | 任务 | 状态 |
|---|---|---|---|
| 登录页 | `login.md` | T-FE-041 | ✅ adopted（2026-08-24 真实登录链路实现回写） |
| 2.1 组织与用户 | （已归档至 `docs/archive/2026-06-21/org-user-page-impl-plan.md`，权威契约见 org-user-permission-contract.md） | — | ✅ |
| 2.2 角色管理 | `role-manage.md` | T-FE-002 | ✅ |
| 3.1 资源+操作定义 | `resource-operation.md` | T-FE-008 | ✅ |
| 3.2 权限条件 | `permission-condition.md` | T-FE-009 | ✅ |
| 3.3 冲突规则 | `conflict-rule.md` | T-FE-010 | ✅ |
| 3.4 资源依赖 | `resource-dependency.md` | T-FE-011 | ✅ |
| 4.1 权限授予 | `permission-grant.md`（v3，重建） | T-FE-036 | ✅ adopted（2026-08-02 实现回写；v1/v2 已归档 [`archive/2026-07-26/`](../../archive/2026-07-26/)） |
| 4.2 权限查询/校验 | `permission-query.md` | T-FE-013 | ✅ |
| 5.1 业务域 | `biz-domain.md` | T-FE-006 | ✅ |
| 5.2 服务+接口映射 | `service-interface-mapping.md` | T-FE-007 | ✅ |
| 6.1 类型定义 | `type-definition.md` | T-FE-003 | ✅ |
| 6.2 系统配置 | `system-config.md` | T-FE-004 | ✅ |
| 7.1 操作日志 | `operation-log.md` | T-FE-005 | ✅ |
| 7.2 权限变更日志 | `permission-change-log.md` | T-FE-012 | ✅ |
| 扩展指南 | `extension-guide.md` | T-FE-023（Phase 4） | 待产出 |

## 页面设计文档结构（建议）

```markdown
---
doc_type: design
title: <页面名> 前端设计
status: draft   # draft → adopted（任务 done 时）
domain: frontend
last_reviewed: YYYY-MM-DD
---

# <页面名> 前端设计

## 布局结构
## 字段定义
## 交互流程
## API 依赖（链接后端契约章节）
## 组件结构（含可复用组件识别）
## 权限接线（hasPerms → 按钮 → 降级）
```

## 参考实现

2.1 组织与用户页的设计范式（布局/组件结构/权限接线清单）见 `docs/archive/2026-06-21/org-user-page-impl-plan.md` §1/§4，可作为新页面设计的参照模板。
